# Architecture

This document is the canonical reference for how the Workeeper codebase is laid out and how its
moving parts fit together: the module graph, the MVI contract, dependency injection, the data
layer, navigation, build conventions, and naming.

## Module map

The build is configured in `settings.gradle.kts`. Every module is included from there.

### `app/`

- `app/app` — shared application code: `App.kt` composable root, `MainActivity`,
  `bottom_app_bar/`, `host/AppNavigationHost.kt`, `navigation/NavigatorImpl.kt`,
  `navigation/RootComponentImpl.kt`, `di/NavigationModule.kt`.
- `app/dev` — debuggable development variant with its own application id and Firebase config.
- `app/store` — release variant signed for Play Store distribution.

### `core/`

- `core/core` — base utilities: `AppCoroutineScope`, dispatcher qualifiers
  (`MainDispatcher`, `MainImmediateDispatcher`, `DefaultDispatcher`, `IODispatcher`),
  Firebase logging holders, `AppResult`, common extensions.
- `core/database` — Room database (`AppDatabase`), entities, DAOs, type converters, migrations,
  schemas under `core/database/schemas/`.
- `core/exercise` — repository contracts and implementations
  (`ExerciseRepository`, `TrainingRepository`, `LabelRepository`) plus their data models.
- `core/dataStore` — Preferences DataStore wiring (`CommonDataStore`, `BaseDataStore`,
  `DataStoreProviderFactory`).
- `core/ui/kit` — reusable Compose UI: theme (`AppTheme`, `AppDimension`, `AppUi`), components
  (`AppSnackBar`, `BasePagingColumnItem`, `TextInputField`), shared models
  (`PropertyHolder`, `MenuItem`, `PagingUiState`), `SnackbarManager`, `ActivityHolder`.
- `core/ui/mvi` — the MVI contract (see [MVI contract](#mvi-contract)).
- `core/ui/navigation` — `Navigator`, `Screen`, `Component`, `RootComponent`, `LocalNavigator`,
  `LocalRootComponent`, `navScreen` extension.
- `core/ui/test-utils` — shared test infrastructure (`BaseComposeTest`, `MockDataFactory`,
  `PagingTestUtils`, `@Smoke`, `@Regression`).

### `feature/`

Each feature is a self-contained module that owns a Store, Handlers, DI, and a Compose entry
point. The five features are `feature/all-trainings`, `feature/all-exercises`,
`feature/single-training`, `feature/exercise`, and `feature/charts`. Feature contents are
detailed in [features.md](features.md); their conventional layout is described under
[Per-feature MVI layout](#per-feature-mvi-layout).

### `build-logic/`

Convention plugins applied via the version catalog plugin aliases
(`convention.application.{store,dev,common}`, `convention.androidLibrary`,
`convention.composeLibrary`, `convention.roomLibrary`, `convention.lint`). See
[Build conventions](#build-conventions).

### `lint-rules/`

Custom Detekt rule set, centralized `detekt.yml` and `lint.xml`, single baselines, and the
`baseline-manager.sh` helper. Wired into every module via `LintConventionPlugin`. Documented in
[lint-rules.md](lint-rules.md).

## MVI contract

The MVI contract lives in `core/ui/mvi`. Three roles cooperate:

1. UI dispatches an `Action` via the store's `consume` callback.
2. A `Handler` matches the action type and updates `State` and/or emits an `Event`.
3. UI observes `state: StateFlow<S>` and reacts to `event: SharedFlow<E>`.

### `Store`

Defined in `core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/Store.kt`:

```kotlin
interface Store<out S : State, in A : Action, out E : Event> {
    val state: StateFlow<S>
    val event: SharedFlow<E>
    fun consume(action: A)

    interface State
    interface Event
    interface Action {
        interface RepeatLast : Action
    }
}
```

`Action.RepeatLast` is a marker for actions that bypass the duplicate-action filter in
`BaseStore`.

### `BaseStore`

`core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/BaseStore.kt` is a
`ViewModel` that implements `Store` and `StoreConsumer`. Concrete feature stores subclass it.
Constructor parameters:

- `name` — used for logging and analytics screen names.
- `initialState` — the State value emitted before any action is consumed.
- `storeEmitter` — a `HandlerStoreEmitter` that bridges handlers back to the store
  (the feature's `<Name>HandlerStoreImpl`).
- `handlerCreator` — a `HandlerCreator<A>` lambda that maps an action to the right `Handler`.
- `initialActions` — actions consumed once `init()` is called (typically `Common.Init`).
- `disposeActions` — actions consumed when the ViewModel is cleared.
- `storeDispatchers`, `analyticsHolder`, `loggerHolder` — injected singletons.

`BaseStore` deduplicates consecutive identical actions unless they implement `Action.RepeatLast`,
logs every action and event, and exposes `launch(...)` helpers built on `AppCoroutineScope`.

### `Handler`

`core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/handler/Handler.kt` is a
single-method functional interface:

```kotlin
fun interface Handler<in A : Action> {
    operator fun invoke(action: A)
}
```

A feature typically has one `Handler` per top-level action category (`ClickHandler`,
`InputHandler`, `NavigationHandler`, `PagingHandler`, sometimes `CommonHandler`). The feature's
`StoreImpl` selects which handler runs in its `handlerCreator` lambda — see
`feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/store/ExerciseStoreImpl.kt`
for the canonical pattern.

### `HandlerStore` and `BaseHandlerStore`

Handlers receive a `HandlerStore<S, A, E>` (see
`core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/handler/HandlerStore.kt`)
that exposes `state`, `lastAction`, `consume(action)`, `updateState`, `sendEvent`, and `launch`
helpers. The feature owns a `<Name>HandlerStoreImpl` annotated `@ViewModelScoped` that extends
`BaseHandlerStore<State, Action, Event>` and is passed into the Store via DI. Example:
`feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/di/ExerciseHandlerStoreImpl.kt`.

### `StoreProcessor`

Compose talks to MVI through `StoreProcessor` (see
`core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/processor/StoreProcessor.kt`).
The `rememberStoreProcessor` composable in the same file:

1. Resolves the navigation `Component` from `LocalRootComponent`.
2. Obtains the Store via Hilt's `hiltViewModel<TStoreImpl, TFactory> { it.create(component) }`
   helper, so each navigation entry gets its own ViewModel-scoped graph.
3. Wires `init()` / `dispose()` to a `DisposableEffect` and reports screen names to Firebase
   Crashlytics and Analytics.
4. Returns a `StoreProcessor` exposing `state: ComposeState<S>`, `consume(action)`, and a
   `Handle { event -> ... }` composable for one-shot side effects.

`Feature<TProcessor, TScreen, TComponent>` (`core/ui/mvi/.../Feature.kt`) and
`navComponentScreen(...)` (`core/ui/mvi/.../NavComponentScreen.kt`) are the small abstractions
each feature uses to plug its processor into the `NavGraphBuilder`.

### Holders

`AnalyticsHolder` (`core/ui/mvi/.../holders/AnalyticsHolder.kt`) and `LoggerHolder` are passed
to every `BaseStore` and produce per-store `StoreAnalytics` and `Logger` instances keyed by
store name.

### State / Action / Event conventions

These are enforced by custom Detekt rules; see [lint-rules.md](lint-rules.md) for the full list.
Briefly:

- `State` is a data class implementing `Store.State`. All properties are `val`. Collections are
  immutable (`ImmutableList`, `ImmutableSet`, or read-only types).
- `Action` is a sealed interface or sealed class implementing `Store.Action`. Top-level
  categories are typically `Click`, `Input`, `Navigation`, `Paging`, `Common`.
- `Event` is a sealed interface or sealed class implementing `Store.Event`. Names describe what
  happened (`*Success`, `*Error`, `*Completed`, `Navigate*`, `Show*`, `Haptic*`, `Snackbar*`,
  `Scroll*`).

## Per-feature MVI layout

Each feature module follows the same conventional shape. Using
`feature/exercise` as the canonical example:

```
feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/
├── di/
│   ├── ExerciseModule.kt            # Hilt @InstallIn(ViewModelComponent::class)
│   ├── ExerciseEntryPoint.kt        # Hilt EntryPoint when needed at composition time
│   ├── ExerciseHandlerStore.kt      # HandlerStore facade interface
│   ├── ExerciseHandlerStoreImpl.kt  # @ViewModelScoped BaseHandlerStore subclass
│   └── ExerciseProcessor.kt         # Feature object exposing the StoreProcessor
├── ui/
│   ├── ExerciseSingleWidget.kt      # Top-level Compose widget
│   ├── components/                  # Sub-widgets (e.g. ExerciseSetsCreateWidget)
│   └── mvi/
│       ├── store/
│       │   ├── ExerciseStore.kt     # Contract: State, Action, Event
│       │   └── ExerciseStoreImpl.kt # @HiltViewModel(assistedFactory=Factory::class)
│       └── handler/
│           ├── ClickHandler.kt
│           ├── InputHandler.kt
│           ├── NavigationHandler.kt
│           ├── CommonHandler.kt
│           └── ExerciseComponent.kt # navigation::Component subclass + Impl
```

Note that `feature/exercise` and `feature/single-training` keep MVI under a `ui/mvi/` package
while the simpler `feature/all-trainings`, `feature/all-exercises`, and `feature/charts` keep it
directly under `mvi/`. Both layouts work with the linting rules; pick the one that already
exists when adding to an existing feature.

`<Name>HandlerStore` interfaces and their `Impl`s live under both `mvi/` and `di/` packages in
some features for historical reasons — the `Impl` is in `di/` because it is a Hilt binding; the
public interface used by handlers stays close to the Store contract.

## Dependency injection (Hilt)

The DI graph is built around two scopes.

### Singleton graph (`SingletonComponent`)

Lives in `core/*/di/Core*Module.kt`:

- `core/database/.../di/CoreDatabaseModule.kt` provides `AppDatabase`, `ExerciseDao`,
  `TrainingDao`, `TrainingLabelDao`. Database is built with `Room.databaseBuilder`,
  `MIGRATION_1_2` is registered, schemas are exported under `core/database/schemas/`.
- `core/dataStore/.../di/CoreDataStoreModule.kt` binds `CommonDataStore`.
- `core/exercise/.../di/CoreExerciseModule.kt` binds `ExerciseRepository`, `TrainingRepository`,
  `LabelRepository`.
- `core/core/.../di/CoreModule.kt` provides four qualified `CoroutineDispatcher` instances
  (`@MainDispatcher`, `@MainImmediateDispatcher`, `@DefaultDispatcher`, `@IODispatcher`).
- `core/ui/mvi/.../di/StoreDispatchers.kt` is a singleton data class injecting
  `@DefaultDispatcher` and `@MainImmediateDispatcher` for use by every store.
- `app/app/src/main/java/io/github/stslex/workeeper/di/NavigationModule.kt` binds
  `NavigatorImpl` to `Navigator` at the application level.

Repositories, DataStores, and `AppDatabase` are `@Singleton`. `HiltScopeRule` enforces this for
classes whose name contains `Repository`, `DataStore`, `Database`, or `StoreDispatchers`.

### Feature graph (`ViewModelComponent`)

Each feature owns `feature/<name>/.../di/<Name>Module.kt` annotated
`@InstallIn(ViewModelComponent::class)`. Bindings:

- `<Name>Interactor` (where present) — `@ViewModelScoped`.
- `<Name>HandlerStore` — `@ViewModelScoped`, implementation extends `BaseHandlerStore`.

Handlers (`ClickHandler`, `InputHandler`, etc.) are `@ViewModelScoped` classes that
constructor-inject the feature's `<Name>HandlerStoreImpl` plus any repositories they need. They
implement `Handler<Action.<Category>>`. `MviHandlerConstructorRule` requires a primary
constructor with `@Inject` (the only exception is `NavigationHandler`, which constructs from
the feature's `Component` rather than via `@Inject`).

`HiltScopeRule` enforces `@ViewModelScoped` for classes whose name contains `Handler`, `Store`,
`Interactor`, or `Mapper`.

### `@HiltViewModel` and assisted factories

`<Name>StoreImpl` is annotated:

```kotlin
@HiltViewModel(assistedFactory = StoreImpl.Factory::class)
internal class StoreImpl @AssistedInject constructor(
    @Assisted component: <Name>Component,
    /* handlers and singletons */
) : BaseStore<...>(...) {

    @AssistedFactory
    interface Factory : StoreFactory<<Name>Component, StoreImpl>
}
```

The `StoreFactory<TComponent, TStoreImpl>` interface is defined in
`core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/processor/StoreFactory.kt`.
`rememberStoreProcessor` in `core/ui/mvi/.../processor/StoreProcessor.kt` calls
`hiltViewModel<TStoreImpl, TFactory>(key) { it.create(component) }` so the screen's
`Component` (which carries route arguments) is injected at composition time.

Plain `DataStoreProvider` instances are created via the assisted factory in
`core/dataStore/src/main/kotlin/io/github/stslex/workeeper/core/dataStore/core/DataStoreProviderFactory.kt`
when a runtime parameter (e.g. file name) is required.

### Application bootstrap

- `app/app/src/main/java/io/github/stslex/workeeper/BaseApplication.kt` is `abstract` and
  initializes `FirebaseCrashlyticsHolder` and the `Log.isLogging` flag.
- `app/dev/src/main/java/.../App.kt` and `app/store/src/main/java/.../App.kt` (one per variant)
  apply `@HiltAndroidApp` and override `isDebugLoggingAllow`.
- `MainActivity` (`app/app/src/main/java/io/github/stslex/workeeper/MainActivity.kt`) is
  `@AndroidEntryPoint`, injects `ActivityHolderProducer`, and sets the Compose root via
  `setContent { App() }`.

## Data layer

### Room database

`core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/AppDatabase.kt` declares
three entities (`ExerciseEntity`, `TrainingEntity`, `TrainingLabelEntity`), three DAOs, and
three type converters (`UuidConverter`, `SetsTypeConverter`, `StringConverter`). The current
schema version is 2; schemas for both versions are exported to
`core/database/schemas/io.github.stslex.workeeper.core.database.AppDatabase/{1,2}.json`.
The 1→2 migration lives in
`core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/migrations/Migration12.kt`.

### Repositories

`core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/` exposes three
repository interfaces, each with an `Impl` that wraps a DAO and maps between entities and
domain models:

- `exercise/ExerciseRepository` plus `ExerciseDataModel`, `ExerciseChangeDataModel`,
  `SetsDataModel`, `SetsDataType`.
- `training/TrainingRepository` plus `TrainingDataModel`, `TrainingChangeDataModel`.
- `labels/LabelRepository` plus `LabelDataModel`.

### DataStore (preferences)

`core/dataStore/src/main/kotlin/io/github/stslex/workeeper/core/dataStore/`:

- `core/BaseDataStore.kt` is the abstract reader/writer base.
- `core/DataStoreProvider.kt` and `DataStoreProviderFactory.kt` build a `DataStore<Preferences>`
  via Hilt's `@AssistedFactory`.
- `store/CommonDataStore.kt` is the application-wide preferences interface; bound in
  `di/CoreDataStoreModule.kt`.

## Navigation

### Routes and the navigator

- `core/ui/navigation/.../Screen.kt` defines routes as a `@Serializable sealed interface`. The
  three bottom-bar destinations are nested under `Screen.BottomBar` (`Charts`, `AllExercises`,
  `AllTrainings`), and the two detail destinations are `Screen.Training(uuid)` and
  `Screen.Exercise(uuid, trainingUuid)`. Bottom-bar screens declare `isSingleTop = true`.
- `core/ui/navigation/.../Navigator.kt` is a small `Navigator` interface exposing a
  `NavHostController`, `navTo(screen)`, and `popBack()`. `NavigatorImpl`
  (`app/app/src/main/java/io/github/stslex/workeeper/navigation/NavigatorImpl.kt`) is a
  `@Singleton` bound by `NavigationModule`. `LocalNavigator` makes it available via
  `CompositionLocalProvider`.
- `core/ui/navigation/.../Component.kt` is the per-screen DI handle. Concrete
  `<Name>Component` types are defined in each feature's `mvi/handler/` package and carry the
  serialized route (`data: Screen.Training`, etc.). `RootComponentImpl`
  (`app/app/.../navigation/RootComponentImpl.kt`) creates the right `Component` for a screen and
  is provided through `LocalRootComponent`.

### Navigation host and shared element transitions

`app/app/src/main/java/io/github/stslex/workeeper/host/AppNavigationHost.kt` wraps the
`NavHost` in a `SharedTransitionLayout` (Jetpack Compose
`ExperimentalSharedTransitionApi`). The single shared `SharedTransitionScope` is passed to each
feature's `<Name>Graph` extension function (`chartsGraph`, `allTrainingsGraph`,
`allExercisesGraph`, `singleTrainingsGraph`, `exerciseGraph`), so transitions can be wired
across the whole graph from a single root scope. The start destination is
`Screen.BottomBar.Charts`. Each graph is added via `navComponentScreen<Feature>` which expands
to a `composable<Screen>` block under the hood (see
`core/ui/navigation/.../Screen.kt::navScreen`).

`NavHostControllerHolder` (`app/app/.../host/NavHostControllerHolder.kt`) tracks which
`BottomBar` screen is current so `App.kt` can show or hide the `WorkeeperBottomAppBar` with an
animated visibility transition.

### Bottom navigation

`app/app/.../bottom_app_bar/BottomBarItem.kt` declares three tab entries — `CHARTS`,
`TRAININGS`, `EXERCISES` — each pointing at a `Screen.BottomBar`. `BottomAppBar.kt` renders
them with haptic feedback on selection.

## Cross-cutting channels

### Snackbars

`core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/snackbar/SnackbarManager.kt`
is a singleton object exposing `snackbar: SharedFlow<AppSnackbarModel>` and a `showSnackbar(...)`
emitter. Any layer can call `SnackbarManager.showSnackbar(...)` to surface a message;
`App.kt` collects the flow and forwards each `AppSnackbarModel` to a `SnackbarHostState` that
backs `AppSnackBar` (`core/ui/kit/.../components/snackbar/AppSnackBar.kt`).

Stores express snackbar intent through their `Event` channel — `feature/exercise` emits a
`Event.Snackbar` event and the screen-level Compose layer translates it into a
`SnackbarManager.showSnackbar(...)` call. The naming pattern is enforced by
`MviEventNamingRule` (`Snackbar` is in the rule's `validPatterns` list).

### Haptics

Stores emit `Event.Haptic*` events (e.g. `feature/all-trainings` emits `Event.Haptic`,
`feature/exercise` emits `Event.HapticClick`). The screen-level Compose layer responds by
calling `LocalHapticFeedback.current.performHapticFeedback(...)` — see
`app/app/.../bottom_app_bar/BottomAppBar.kt` for a non-event-driven example using
`HapticFeedbackType.SegmentTick`. The `Haptic` token is in `MviEventNamingRule.validPatterns`.

### Coroutine scope and dispatchers

- `core/core/.../coroutine/scope/AppCoroutineScope.kt` wraps a `CoroutineScope`,
  a `defaultDispatcher` (work), and an `immediateDispatcher` (delivery). Both `BaseStore` and
  `HandlerStore` expose `launch { ... }` helpers built on top of it that automatically catch
  exceptions, invoke `onError`, and switch to the immediate dispatcher for `onSuccess` / per-flow
  emissions.
- `StoreDispatchers` (`core/ui/mvi/.../di/StoreDispatchers.kt`) injects `@DefaultDispatcher`
  and `@MainImmediateDispatcher` from `core/core/.../di/CoreModule.kt`.

## Build conventions

Convention plugins live in `build-logic/convention/src/main/kotlin/`:

- `AndroidApplicationComposeConventionPlugin` — applied to `:app:store` via
  `convention.application.store`. Configures the production application module
  (`AppType.STORE`) using `configureApplication`.
- `AndroidDevApplicationComposeConventionPlugin` — applied to `:app:dev` via
  `convention.application.dev`. Same flow with `AppType.DEV` (adds `dev` postfix to the
  application id).
- `AndroidLibraryConventionPlugin` — base Android library plugin. Applies `library`, `ksp`, and
  `convention.lint`. Configures Kotlin via `configureKotlinAndroid`.
- `AndroidLibraryComposeConventionPlugin` — Android library + Compose. Adds
  `composeCompiler`, `serialization`, `ksp`, `convention.lint`. Calls
  `configureAndroidCompose`.
- `RoomLibraryConventionPlugin` — applies `room` and `ksp`, sets `room.generateKotlin=true`,
  configures `schemaDirectory("$projectDir/schemas")`, and adds the `room` bundle plus
  `androidx-paging-runtime` and `androidx-room-testing`.
- `LintConventionPlugin` — applies `detekt`, points lint and detekt at the centralized configs
  (`lint-rules/lint.xml`, `lint-rules/detekt.yml`) and baselines, registers the
  `:lint-rules` project as a `detektPlugins` dependency. See
  [lint-rules.md](lint-rules.md).

Helpers in the same directory:

- `AppType.kt` enumerates `STORE` and `DEV` and exposes the package-id postfix.
- `AppExt.kt` exposes `libs`, `findPluginId(alias)`, and per-configuration helpers
  (`implementation`, `implementationBundle`, `androidTestImplementation`, etc.) that look up
  aliases in the version catalog.
- `io/github/stslex/workeeper/{ConfigureApplication.kt, KotlinAndroid.kt, ComposeAndroid.kt,
  LocalPropertiesConstants.kt}` contain the actual `configureApplication`,
  `configureKotlinAndroid`, and `configureAndroidCompose` functions that the plugins call.

### Toolchain

Versions live in `gradle/libs.versions.toml`. The notable pins at the time of writing:

- Kotlin `2.3.20` with KSP `2.3.6`.
- Android Gradle Plugin `9.1.0`. `compileSdk = 36`, `targetSdk = 36`, `minSdk = 28`.
- Compose BOM `2025.12.01`, `compose-compiler` plugin tied to Kotlin.
- Hilt `2.59.2` with `hilt-navigation-compose 1.3.0`.
- Room `2.8.4` with paging support.
- Detekt `1.23.8` plus `detekt-rules-compose 0.5.3`.
- JUnit Jupiter `5.13.4`, Robolectric `4.16`, MockK `1.14.7`,
  Compose UI Test (`androidx-compose-ui-test-junit4 1.10.0`).

## Naming conventions

Architectural names that the Detekt rules enforce; full rule details and code examples are in
[lint-rules.md](lint-rules.md):

- Files inside an `mvi/` package or whose package name contains `mvi`: classes ending in
  `State` must be `data` or `sealed`, must have `val` properties, and must use immutable
  collections; classes ending in `Action`/`Event` must be `sealed class` or `interface`; the
  inner `State` of a `*Store` must be a data class implementing `Store.State`.
- `*StoreImpl` must extend `BaseStore`. `*Store` interfaces (excluding `*HandlerStore`) must
  implement `Store`.
- `*Handler` classes must have a primary constructor annotated `@Inject` (with the documented
  exception of `NavigationHandler`) and constructor-inject their dependencies.
- Classes whose name contains `Repository`, `DataStore`, `Database`, or `StoreDispatchers` must
  carry `@Singleton`. Classes whose name contains `Handler`, `Store`, `Interactor`, or `Mapper`
  must carry `@ViewModelScoped`.
- Composables ending in `Screen` must have both a `*State` parameter and an `Action`/`Event`
  handler parameter.

General Kotlin/Android conventions:

- Kotlin official style; 4-space indentation; no tabs.
- Classes/objects: UpperCamelCase. Functions/properties: lowerCamelCase. Constants:
  UPPER_SNAKE_CASE.
- Packages: lowercase, dot-separated; respect the `core/*`, `feature/*`, `app/*` layout.
- Android resources: snake_case (e.g. `ic_bottom_app_bar_chart_icon_24`,
  `bottom_bar_label_charts`).
- Compose previews: keep them in the same file, suffixed `Preview`.
