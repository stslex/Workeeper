# Architecture

This document is the canonical reference for how the Workeeper codebase is laid out and how its
moving parts fit together: the module graph, the MVI contract, dependency injection, the data
layer, navigation, build conventions, and naming.

Architectural rules in this document are aspirational where the codebase doesn't yet
match. Known violations live in [documentation/tech-debt.md](tech-debt.md) — they're
not accepted as the right shape; they're tracked for cleanup. When you change code in
a file listed there, fix the cited debt as part of the change and remove the entry.

## Module map

The build is configured in `settings.gradle.kts`. Every module is included from there.

### `app/`

- `app/app` — the Android application shell and the Metro app graph: `BaseApplication`,
  `MainActivity`, and `di/AppGraph.kt`, `di/AppGraphBuilder.kt`, `di/AppGraphOwner.kt`. It
  depends on `app/common`, so the graph sits **above** the composition root and
  `@DependencyGraph(AppScope::class)` stays internal here.
- `app/common` — the CMP-shaped composition root, extracted in KMP phase 4: `App.kt`,
  `AppRootViewModel.kt`, `bottom_app_bar/BottomBarItem.kt`, `host/AppNavigationHost.kt`
  (plus `host/BottomBarNavigationListener.kt`, `host/ClearFocusOnDestinationChanged.kt`),
  `navigation/NavigatorEventBus.kt`, `navigation/NavigatorExt.kt`, the `bottom_bar_label_*`
  strings, and `app/common/di/AppRootDeps.kt` — the narrow contract this module declares and
  `:app:app`'s `AppGraph` implements, because the graph is not nameable from below. See
  [feature-specs/kmp-phase-4-app-common.md](feature-specs/kmp-phase-4-app-common.md).
  (`navigation/NavigatorReceiver.kt` went to `core/ui/navigation` — it is navigation tooling.)
- `app/dev` — debuggable development variant with its own application id and Firebase config.
- `app/store` — release variant signed for Play Store distribution.

### `core/`

- `core/core` — KMP base module (android + iosSimulatorArm64). `commonMain`: `AppCoroutineScope`,
  dispatcher qualifiers (`MainDispatcher`, `MainImmediateDispatcher`, `DefaultDispatcher`,
  `IODispatcher`), Firebase logging holders, the platform expect/actual seams
  (`PlatformInfoProvider`, `AppReinitializer`), `ResourceWrapper`/`ImageStorage` interfaces,
  `AppResult`, common extensions. `androidMain`: the Android actuals and framework impls plus
  the Metro binding containers (`DispatchersBindingContainer`, `ResourceWrapperBindingContainer`,
  `ImageStorageFactory`, `TempFileProvider`).
- `core/data/database` — KMP Room module. `commonMain` owns `AppDatabase`, entities, DAOs,
  type converters, and `migration/` (including `MigrationsRegistry`); `androidMain` owns
  `AppDatabaseFactory` and Android snapshot I/O. Exported schemas live under
  `core/data/database/schemas/`.
- `core/data/database-test` — `RepositoryTestEnv` and `InMemoryDatabaseProvider`, shared
  Android/JVM in-memory `AppDatabase` fixtures for repository and integration tests.
- `core/data/exercise` — KMP repository contracts and implementations in `commonMain`
  (`ExerciseRepository`, `TrainingRepository`, `SessionRepository`, …) plus their data models;
  Android host and device tests remain platform source sets.
- `core/data/dataStore` — Preferences DataStore wiring (`CommonDataStore`, `BaseDataStore`,
  `DataStoreProviderFactory`).
- `core/ui/kit` — reusable Compose UI: theme (`AppTheme`, `AppDimension`, `AppUi`), components
  (`AppSnackBar`, `BasePagingColumnItem`, `TextInputField`), shared models
  (`PropertyHolder`, `MenuItem`, `PagingUiState`), `SnackbarManager`, `ActivityHolder`.
- `core/ui/mvi` — the MVI contract (see [MVI contract](#mvi-contract)).
- `core/ui/navigation` — `Navigator` (command-bus interface), `NavCommand` (sealed
  command set emitted on the bus), `Screen` (sealed `@Serializable` route catalog),
  `ScreenWithResult<R>` (result type declared on the destination), `NavResultsSource`
  (the result transport interface), `NavigatorHolder` (Compose-scoped wrapper around
  the app-owned `NavBackStack<NavKey>`), `screenSavedStateConfiguration`
  (`ScreenSerialization.kt` — the polymorphic serializer registry that lets the back
  stack survive process death), `NavGraphScope` (the project-owned registration
  receiver) and its `navScreen` / `navScreenWithResults` extensions.
- `core/ui/test-utils` — shared test infrastructure (`BaseComposeTest`, `MockDataFactory`,
  `PagingTestUtils`, `@Smoke`, `@Regression`).

### `feature/`

Each feature is a self-contained module that owns a Store, Handlers, DI, and a Compose entry
point. Current feature modules live under `feature/`, including `feature/home`,
`feature/all-trainings`, `feature/all-exercises`, `feature/single-training`,
`feature/exercise`, `feature/live-workout`, `feature/past-session`,
`feature/settings`, and `feature/archive`. Feature contents are detailed in [features.md](features.md); their
conventional layout is described under [Per-feature MVI layout](#per-feature-mvi-layout).

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
helpers. The feature owns a `<Name>HandlerStoreImpl` annotated
`@SingleIn(<Name>Scope::class)` that extends
`BaseHandlerStore<State, Action, Event>` and is passed into the Store via DI. Example:
`feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/di/ExerciseHandlerStoreImpl.kt`.

The interface includes `consumeOnMain(action)` (suspend) for cases where a handler
needs to dispatch an `Action.Navigation.*` from a background coroutine. It wraps
the `consume` call in `withContext(immediateDispatcher)`, ensuring `Navigator`
invocations land on the main thread. See [Navigation flow → Dispatching navigation
from background coroutines](#dispatching-navigation-from-background-coroutines).

### `StoreProcessor`

Compose talks to MVI through `StoreProcessor` (see
`core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/processor/StoreProcessor.kt`).
There is ONE backend-agnostic `rememberStoreProcessor(StoreCreator)` overload; features reach
it through `rememberMetroStoreProcessor` (`.../processor/MetroStoreProcessor.kt`), which
retains the Metro-constructed Store directly in the `ViewModelStore` of the current
`LocalViewModelStoreOwner` via `viewModel {}` — `BaseStore` already IS an
`androidx.lifecycle.ViewModel`, so no shim is needed. Under Nav3 that per-entry owner is
provided by `rememberViewModelStoreNavEntryDecorator`, listed EXPLICITLY in
`AppNavigationHost`'s `entryDecorators` — `NavDisplay`'s default decorator set is
saveable-only, and omitting the ViewModel decorator makes `viewModel {}` resolve against
the Activity's store, silently process-scoping every Store
(`StoreRetentionTest.isolation` guards it). Both store shapes go through it:

1. Plain `Feature` — the feature's `processor()` resolves `context.appDeps<XxxGraph.Factory>()`,
   creates its graph extension, and reads the Store accessor. No route arguments (e.g.
   `Screen.BottomBar.Home`).
2. `FeatureAssisted` — `processor(screen)` passes the route arg to the extension factory as a
   bound instance, so the Store receives it as a normal constructor parameter. The screen
   object is the typed back-stack key itself, handed to the graph composable by
   `navScreen<TScreen>` — the key IS the argument object, so the Store never retains any
   navigation library type.

The graph extension is created INSIDE the `rememberMetroStoreProcessor` factory lambda, so it
is built at most once per retained Store — binding the extension and its feature-scoped nodes
to exactly the Store's lifetime.

In both cases the helper:

- Wires `init()` / `dispose()` to a `DisposableEffect` keyed on the Store and the
  current `LifecycleOwner` so the Store's `AppCoroutineScope` follows screen lifecycle.
- Reports the Store's screen name to `FirebaseCrashlyticsHolder`,
  `FirebaseAnalyticsHolder`, and `FirebaseScreenRenderRecorder`.
- Returns a `StoreProcessor` exposing `state: ComposeState<S>`, `consume(action)`, and a
  `Handle { event -> ... }` composable for one-shot UI side effects.

`Feature<TProcessor, TScreen>` (`core/ui/mvi/.../Feature.kt`) and
`FeatureAssisted<TProcessor, TScreen>` (`core/ui/mvi/.../FeatureAssisted.kt`) are the two
composition-time entry points each feature picks between. Their `processor()` method is
invoked from the feature's graph composable through `navComponentScreen` /
`navComponentScreenWithResults` (`core/ui/mvi/.../NavComponentScreen.kt`).

#### When to use `Feature` vs `FeatureAssisted`

- **`Feature<TProcessor, TScreen>`** — the screen has no route arguments (e.g.
  `Screen.BottomBar.Home`, `Screen.Settings`, `Screen.Archive`). The Store derives its
  initial state from defaults / repository observations only. The extension factory's
  creator method takes no arguments.
- **`FeatureAssisted<TProcessor, TScreen>`** — the screen carries arguments that seed
  the initial state (e.g. `Screen.Exercise(uuid)`, `Screen.LiveWorkout(sessionUuid,
  trainingUuid)`, `Screen.PastSession(sessionUuid)`, `Screen.PlanEditor(...)`,
  `Screen.ExerciseChart(exerciseUuid)`, `Screen.ExerciseImage(model)`,
  `Screen.Training(uuid)`). The route arg enters as a `@Provides` bound instance on the
  feature's `@GraphExtension.Factory` creator method, and the Store takes it as a plain
  constructor parameter — there is no assisted factory. See
  [Store construction and route arguments](#store-construction-and-route-arguments).

The screen object passed to the Store is the typed back-stack key (handed through by
`navScreen<TScreen>` — there is no `toRoute()` decode step in Nav3); it is NOT an entry
reference, a `SavedStateHandle`, or the back stack itself. The Store retains
only the screen's value-type fields it needs.

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
  happened (`*Success`, `*Error`, `*Completed`, `Show*`, `Haptic*`, `Snackbar*`, `Scroll*`).
  **Events are for UI-side effects only** — haptic feedback, snackbar display, external Intent
  dispatch, scroll commands. **Navigation is never an Event.** Navigation flows through
  `Action.Navigation` consumed by the feature's `NavigationHandler` (see [Navigation
  flow](#navigation-flow) below).
- **Dialogs and bottom sheets live in State**, never in `Event`. `Show*Dialog` /
  `Show*Sheet` events are forbidden — anything that must remain visible across
  recomposition (and configuration changes) until the user dismisses it is State.
  - A separate sealed `DialogState` (or `BottomSheetState`) interface in the same
    `mvi/store/` package as the feature's `Store.kt`, with `Hidden` as the default
    `data object` and one `data class` per concrete dialog/sheet variant carrying
    its display payload (titles, labels, IDs).
  - The screen renders via `when (state.dialogState)` / `when (state.bottomSheetState)`.
  - Open: handler calls `updateState { it.copy(dialogState = DialogState.X(...)) }`.
    Close: handler calls `updateState { it.copy(dialogState = DialogState.Hidden) }`.
  - Display strings are pre-resolved in the handler via `ResourceWrapper`
    (consistent with the "no `R.*` in domain, no computation inside `updateState`
    lambdas" rules) — never resolved in the Composable.
  - The `interceptBack: Boolean` derived property (where present) reads
    `dialogState` / `bottomSheetState` so the back gesture dismisses the topmost
    dialog before propagating.
  - Reference: `feature/live-workout`. Specifically `LiveWorkoutStore.State`,
    `DialogState.kt`, `BottomSheetState.kt`, `LiveWorkoutScreen.kt`,
    `LiveWorkoutGraph.kt`.
  - **Hard rule for two-or-more dialogs on one screen.** The shape above is
    mandatory; independent `*Visible: Boolean` / `*Confirmation: <X>?` flags are
    forbidden because they admit invalid combinations at the type level. Naming
    (`DialogState` unqualified, `Hidden` default), handler/test conventions, and
    common pitfalls are codified in the [`mvi-dialog-state`](../.claude/skills/mvi-dialog-state.md)
    skill, which is the authoritative reference for this shape.
  - **Known limitation.** `dialogState` lives in the in-memory `StateFlow` of
    `BaseStore`. Configuration changes survive (same VM-scoped store). Process
    death does not — `dialogState` is not round-tripped through `SavedStateHandle`.
    Round-tripping critical dialogs is out of scope for the state-discipline rule
    and tracked separately in `tech-debt.md`.
- **Error events carry enum types, not raw strings.** When a handler needs to surface a
  user-visible error (e.g. `Event.ShowError`), the event payload is a feature-local enum
  whose variants reference a `string` resource:

  ```kotlin
  // In feature/<n>/.../mvi/model/ErrorType.kt
  internal enum class ErrorType(val msgRes: Int) {
      InvalidReps(R.string.feature_<n>_error_invalid_reps),
      SaveFailed(R.string.feature_<n>_error_save_failed),
      // ...
  }

  // In Store.Event:
  data class ShowError(val type: ErrorType) : Event
  ```

  This keeps handlers free of localized strings, gives the compiler exhaustive `when`
  coverage in the graph, and ensures every error path is localized for free. Raw
  `String message` payloads on error events are forbidden — they bypass localization
  and skip the compiler check.

### State mutation discipline

`BaseStore.updateState` and `updateStateImmediate` schedule the lambda on `Main.immediate`.
The lambda's job is **state transformation only** — given `current`, return a copy with
new field values. Mapping, formatting, and any work involving `ResourceWrapper` or
domain-to-UI conversions runs *before* the lambda body, in the collector or handler that
calls `updateState`.

Wrong:

```kotlin
scope.launch(interactor.observePersonalRecord(uuid, type)) { record ->
    updateStateImmediate { current ->
        current.copy(personalRecord = record?.toUi(resourceWrapper, typeUi))
    }
}
```

Right:

```kotlin
scope.launch(interactor.observePersonalRecord(uuid, type)) { record ->
    val pr = record?.toUi(resourceWrapper, typeUi)
    updateStateImmediate { current -> current.copy(personalRecord = pr) }
}
```

The collector body runs on the dispatcher of `scope.launch(flow, ...)` — not the main
thread. The lambda receives an already-mapped value and just produces the new State.

Pure state transforms — operations that read only `current` and return a new `current`,
e.g. `current.tags.filterNot { ... }` — are exempt; they're cheap and lifting them out
introduces races. The boundary: if you're calling a mapper, `ResourceWrapper`, or building
a UI model from non-state inputs, hoist it.

## Per-feature MVI layout

Each feature module follows the same conventional shape. Using
`feature/exercise` as the canonical example:

```
feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/
├── di/
│   ├── ExerciseScope.kt             # inert feature-scope token
│   ├── ExerciseGraph.kt             # @GraphExtension(ExerciseScope) + contributed Factory
│   ├── ExerciseHandlerStore.kt      # HandlerStore facade interface
│   ├── ExerciseHandlerStoreImpl.kt  # @SingleIn(ExerciseScope) BaseHandlerStore subclass
│   └── ExerciseFeature.kt           # Feature / FeatureAssisted object exposing the StoreProcessor
├── ui/
│   ├── ExerciseDetailScreen.kt      # Top-level Compose screen (Read mode)
│   ├── ExerciseEditScreen.kt        # Top-level Compose screen (Edit mode)
│   ├── ExerciseGraph.kt             # NavGraphScope.exerciseGraph extension (navigation, not DI)
│   ├── components/                  # Sub-widgets
│   └── mvi/
│       ├── store/
│       │   ├── ExerciseStore.kt     # Contract: State, Action, Event
│       │   └── ExerciseStoreImpl.kt # @Inject, unscoped; takes Screen.Exercise as a ctor param
│       ├── handler/
│       │   ├── ClickHandler.kt
│       │   ├── InputHandler.kt
│       │   ├── NavigationHandler.kt # @SingleIn(ExerciseScope) @Inject (Navigator)
│       │   └── CommonHandler.kt
│       ├── mapper/                  # Domain → Ui mappers
│       └── model/                   # *UiModel types
```

Notes:

- `feature/exercise` and `feature/single-training` keep MVI under a `ui/mvi/` package
  while the simpler `feature/all-trainings`, `feature/all-exercises`, and `feature/home`
  keep it directly under `mvi/`. Both layouts work with the linting rules; pick the one
  that already exists when adding to an existing feature.
- There is no per-feature `Component<Screen>` subclass any more. Route arguments reach the
  Store as a `@Provides` bound instance on the feature's `@GraphExtension.Factory` (see
  [Store construction and route arguments](#store-construction-and-route-arguments) and
  [`Feature` vs `FeatureAssisted`](#when-to-use-feature-vs-featureassisted)).
- `<Name>HandlerStore` interfaces and their `Impl`s live under both `mvi/` and `di/`
  packages in some features for historical reasons — the `Impl` is in `di/` because it
  is a graph binding; the public interface used by handlers stays close to the Store
  contract.

## Dependency injection (Metro)

DI is [Metro](https://github.com/ZacSweers/metro) (`dev.zacsweers.metro`) end to end.
Hilt and Dagger are gone: there is no `@HiltAndroidApp`, `@AndroidEntryPoint`,
`@HiltViewModel`, `@InstallIn`, or `SingletonComponent` anywhere in the tree, and no Hilt
artifact in `gradle/libs.versions.toml`. The graph is built around two tiers.

### App-scope graph (`AppScope`)

`AppGraph` (`app/app/src/main/java/io/github/stslex/workeeper/di/AppGraph.kt`) is the single
`@DependencyGraph(scope = AppScope::class)` for the process — the tier Hilt's `@Singleton` /
`SingletonComponent` used to occupy. `AppScope` itself is an inert token in
`core/core/src/commonMain/kotlin/io/github/stslex/workeeper/core/core/di/AppScope.kt`.

`buildAppGraph(...)` (`app/app/.../di/AppGraphBuilder.kt`) is the ONLY construction site. It
threads three `create()` bound-instance roots — `applicationContext`, `appDatabase`,
`imageStorage`. The latter two come from plain top-level factories,
`buildAppDatabase(...)` in `core/data/database/.../AppDatabaseFactory.kt` and
`buildImageStorage(...)` in `core/core/src/androidMain/.../images/ImageStorageFactory.kt`. Those are
deliberately NOT Metro `@Provides`/`@ContributesBinding`: the values enter the graph as bound
instances, so a binding would duplicate them and fail Metro's duplicate-binding check. The
same three roots are the test-override seam: `MetroTestRule`
(`app/app/src/androidTest/.../harness/MetroTestRule.kt`) rebuilds the graph per test over an
in-memory `AppDatabase` and a `FakeImageStorage` — see [Testing](testing.md).

Everything else contributes INTO that graph rather than being listed on it:

- `core/data/database/.../di/DbCascadeBindingContainer.kt` — a
  `@BindingContainer @ContributesTo(AppScope::class)` object deriving the 9 Room DAOs and
  `DbTransitionRunner` from the `AppDatabase` root.
- `core/core/src/androidMain/.../di/DispatchersBindingContainer.kt` — the four qualified
  `CoroutineDispatcher`s (`@MainDispatcher`, `@MainImmediateDispatcher`, `@DefaultDispatcher`,
  `@IODispatcher`; the qualifier annotations live in `core/core` `commonMain`).
- `core/data/exercise/.../*RepositoryImpl.kt` and
  `core/data/dataStore/.../store/CommonDataStoreImpl.kt` — `@ContributesBinding(AppScope::class)`
  `@SingleIn(AppScope::class)` `@Inject` on the impl. A contributing class must be `public`:
  `@ContributesBinding` on an `internal` class does not aggregate across Gradle modules.
- `app/common/.../navigation/NavigatorEventBus.kt` —
  `@SingleIn(AppScope) @ContributesBinding(AppScope, binding<Navigator>()) @Inject`, a
  controller-free command bus (see [Navigation](#navigation)).
- `core/ui/mvi/.../di/StoreDispatchers.kt` — the app-scoped pair (`@DefaultDispatcher` +
  `@MainImmediateDispatcher`) every Store takes as a constructor dependency.

Any Metro-constructed class whose name contains `Repository`, `DataStore`, `Database`,
`Storage`, or `StoreDispatchers` must be `@SingleIn(AppScope::class)` — `MetroScopeRule`
enforces it by name match. `AppDatabase` itself carries no annotation: it is a `create()`
bound instance, which already gives it graph lifetime.

`BaseApplication` holds the graph for the whole process and hands it out through interface
seams, never a concrete-`Application` cast: `AppGraphOwner` (in-module readers such as
`MainActivity`), `AppDepsHolder` + `Context.appDeps<T>()` (feature-side readers), and the
typed `RecoveryDepsHolder` / `BackupWorkerDepsHolder` (the two framework readers that must
not depend on `core:ui:mvi`).

### Feature graphs (`@GraphExtension`)

Each Store-hosting feature owns two files under `feature/<name>/.../di/`:

- `<Name>Scope.kt` — an inert scope token (`abstract class <Name>Scope private constructor()`),
  the Metro analogue of Hilt's `@ViewModelScoped`.
- `<Name>Graph.kt` — a `@GraphExtension(<Name>Scope::class)` interface whose nested
  `@GraphExtension.Factory` carries `@ContributesTo(AppScope::class)`. The extension is merged
  into `AppGraph` when `:app` compiles and INHERITS every app-scoped binding, so nothing is
  hand-threaded across the boundary.

Bindings on the extension:

- Root accessor — the feature's `*StoreImpl`.
- `<Name>Interactor` (where present) — `@Binds` from its `Impl`, `@SingleIn(<Name>Scope::class)`.
- `<Name>HandlerStore` — `@Binds` from the `BaseHandlerStore` subclass.

The factory's creator method name must be UNIQUE across all contributed extension factories
(every one of them merges into `AppGraph`), hence `createExerciseGraph(...)` /
`createHomeGraph()` rather than a shared `create()`.

Handlers (`ClickHandler`, `InputHandler`, `NavigationHandler`, etc.) are
`@SingleIn(<Name>Scope::class)` classes that constructor-inject the feature's
`<Name>HandlerStoreImpl` plus any repositories or `Navigator` they need. They implement
`Handler<Action.<Category>>`. `MviHandlerConstructorRule` requires a primary constructor with
`@Inject`. The literal class name `NavigationHandler` is exempt at the rule level for
historical reasons, but the current architecture uses `@Inject Navigator` constructor
injection on it identically to other handlers. New code should not rely on the exemption.

`MetroScopeRule` enforces `@SingleIn(<Feature>Scope::class)` for classes whose name contains
`Handler`, `Interactor`, or `Mapper` (a `*Handler` must not be `@SingleIn(AppScope)` —
feature-scoped only). `*Store` classes are UNSCOPED: they carry a class-level `@Inject` and
are retained by the `ViewModelStore` via `rememberMetroStoreProcessor`. Names containing
`Repository`, `DataStore`, `Database`, `Storage`, or `StoreDispatchers` must be
`@SingleIn(AppScope::class)`. The `NavigatorEventBus` class is named with the `Bus` suffix
specifically so it does not match any of those scope predicates — it is
`@SingleIn(AppScope) @ContributesBinding(AppScope, binding<Navigator>()) @Inject`.

### Store construction and route arguments

Every Store is a plain Metro `@Inject` class — there is no assisted machinery on any Store.
A Store that needs no route arguments:

```kotlin
@Inject
class HomeStoreImpl internal constructor(
    navigationHandler: NavigationHandler,
    /* other handlers, dispatchers, holders */
) : BaseStore<State, Action, Event>(/* ... */)
```

A Store that needs route arguments takes the `Screen` as an ordinary constructor
dependency. The arg enters the feature's graph extension as a `@Provides` bound instance on
the factory, so one extension is built per navigation entry, carrying that entry's arg:

```kotlin
@Inject
class ExerciseStoreImpl internal constructor(
    screen: Screen.Exercise,
    navigationHandler: NavigationHandler,
    /* other handlers, dispatchers, holders */
) : BaseStore<State, Action, Event>(
    /* ... */
    initialState = State.create(uuid = screen.uuid),
    /* ... */
)
```

```kotlin
@ContributesTo(AppScope::class)
@GraphExtension.Factory
fun interface Factory {
    fun createExerciseGraph(@Provides screen: Screen.Exercise): ExerciseGraph
}
```

Because the arg is an ordinary binding in the feature scope, any node in that scope could
declare it as a dependency and read navigation state straight out of DI. The Detekt rule
`ScreenInjectionRule` forbids that: a `Screen` type may be injected ONLY into a Store's
primary constructor. The screen object is the typed back-stack key, handed by
`navScreen<TScreen>` to the feature's `processor(screen)`. The Store retains only the
screen's value-type fields it needs in initial state (e.g. `screen.uuid`,
`screen.sessionUuid`, `screen.trainingUuid`); no navigation library type is ever
referenced by the Store.

Plain `DataStoreProvider` instances are created via a Metro-native `@AssistedFactory` in
`core/data/dataStore/src/main/kotlin/io/github/stslex/workeeper/core/data/dataStore/core/DataStoreProviderFactory.kt`
when a runtime parameter (e.g. file name) is required — the one remaining assisted
construction in the tree.

### Application bootstrap

- `app/app/src/main/java/io/github/stslex/workeeper/BaseApplication.kt` is `abstract`. It
  initializes `FirebaseCrashlyticsHolder` and the `Log.isLogging` flag, holds the Metro
  `AppGraph` (`by lazy`, built from the three `create()` roots), and implements the
  `AppGraphOwner` / `AppDepsHolder` / `RecoveryDepsHolder` / `BackupWorkerDepsHolder` seams.
  Its graph-touching startup work sits behind the overridable `onCreateGraphBootstrap()`
  seam so the androidTest `TestApplication` can no-op it.
- `app/dev/src/main/kotlin/.../DevMobileApp.kt` and
  `app/store/src/main/kotlin/.../StoreMobileApp.kt` (one per variant) subclass it and override
  `isDebugLoggingAllow`. No DI annotation is involved — plain subclasses.
- `MainActivity` (`app/app/src/main/java/io/github/stslex/workeeper/MainActivity.kt`) is a
  plain `ComponentActivity`. It reads its app-scope deps as
  `(application as AppGraphOwner).appGraph`, produces the `ActivityHolderProducer`, and sets
  the Compose root via `setContent { App() }`.

### Domain model layer

- Each feature owns its domain model layer. Public surface of interactors
  and use cases takes and returns `*Domain` types only.
- `*DataModel` types from `core.data.*` are visible only inside
  `feature/<X>/domain/mapper/<Name>DomainMapper.kt`, where they get
  converted to `*Domain` via `toDomain()` extension functions.
- `*UiModel` types are visible only in `feature/<X>/mvi/mapper/`, which
  consumes `*Domain` and produces `*UiModel`.
- Sealed result types live in `feature/<X>/domain/model/`, never nested
  inside the interactor interface (e.g. `ArchiveResult`,
  `TrackNowConflict`, `SaveResult`, `BulkArchiveResult`,
  `StartSessionConflict`).
- The domain layer never imports `androidx.compose.*`, never references
  `R.*`, never injects `ResourceWrapper`. Display fallbacks live in UI
  mappers, not in domain.
- Repository INTERFACES (e.g. `ExerciseRepository`,
  `SessionConflictResolver`) are abstractions, not data models — they
  remain importable by interactor implementations and use cases. The
  Detekt rule `DomainLayerPurityRule` flags imports of types matching
  data-shape suffixes (`*DataModel`, `*Entity`, `*Dto`, etc.) and
  packages containing `.model.`, but lets infrastructure imports through.
- Two Detekt rules guard this boundary: `DomainLayerPurityRule`
  (data → domain leak) and `DomainLayerNoUiRule` (UI/Compose/R/mvi →
  domain leak). Both run at error severity; mappers under
  `domain/mapper/` are exempt from `DomainLayerPurityRule` since their
  job is the data → domain conversion.
- Reference: `feature/exercise/domain/`. The pre-migration baseline is
  recorded at `documentation/research/domain-boundary-audit-codex.md`.

## Data layer

### Room database

The schema is defined by `AppDatabase` in `core/data/database/.../AppDatabase.kt`. Every
`@Entity` is registered in the `@Database(entities = [...])` array, and every
`TypeConverter` is on `@TypeConverters` at the database level (project-wide
converters live next to the entities they serialize, e.g.
`PlanSetsConverter` for `List<PlanSetDataModel>?`). The `version` on that annotation is
the `APP_DATABASE_VERSION` constant, not a literal.

The database is constructed by `buildAppDatabase(context)` in
`core/data/database/.../AppDatabaseFactory.kt` — the only `Room.databaseBuilder` chain in
the app. `BaseApplication` calls it and threads the result into `buildAppGraph(...)` as the
`appDatabase` bound instance; the 9 DAOs and `DbTransitionRunner` derive from it inside
`DbCascadeBindingContainer`. The factory must live in this module because `MIGRATIONS` is
`internal` to it.

**Migration policy (release).** From schema version 5 onward, no destructive
migrations. Every schema bump requires:

1. `APP_DATABASE_VERSION` bumped in `core/data/database/.../migration/MigrationsRegistry.kt`.
2. An explicit `Migration(from, to)` object under
   `core/data/database/.../migration/`, appended to the `MIGRATIONS` array in that same
   `MigrationsRegistry.kt`. That array is the single registration site —
   `buildAppDatabase(...)` spreads it onto the builder, and no other `addMigrations(...)`
   call exists. `MigrationsRegistryTest` fails any commit that bumps the version without a
   matching entry, and `hasMigrationPath(from, to)` exposes the same registry to the
   pre-restore backup compatibility check.
3. A migration test in
   `core/data/database/src/androidDeviceTest/.../AppDatabaseMigrationTest.kt`
   using Room's `MigrationTestHelper`. The test runs the migration against a seeded
   v(N) DB and asserts the resulting v(N+1) DB has the expected shape and data.
4. The new schema JSON committed under `core/data/database/schemas/<full-class>/` —
   Room's `exportSchema = true` produces it during build.

Versions 1-4 were pre-Play-Store only; no published users ever held those schemas, so no
`fallbackToDestructiveMigrationFrom` clause is registered for them, and
`MIN_SUPPORTED_SCHEMA_VERSION` is derived from `MIGRATIONS` (currently 5). The builder chain
has **no destructive fallback and must never gain one** — a missing or failing migration
fails closed and routes to the Scenario 2 startup-migration recovery flow, rather than
silently dropping and recreating the user's database.

`androidx.room:room-testing` is wired by the `roomLibrary` convention plugin
(`build-logic/.../RoomLibraryConventionPlugin.kt`) as `androidDeviceTestImplementation`
so `MigrationTestHelper` is available to migration tests. The step-by-step recipe lives in
[`.claude/skills/add-database-migration.md`](../.claude/skills/add-database-migration.md).

### Repositories

`core/data/exercise/src/commonMain/kotlin/io/github/stslex/workeeper/core/data/exercise/` exposes the
repository interfaces, each with an `Impl` that wraps one or more DAOs and maps between
entities and `*DataModel` types. Every `Impl` is
`@ContributesBinding(AppScope::class) @SingleIn(AppScope::class) @Inject`:

- `exercise/ExerciseRepository`
- `training/TrainingRepository`, `training/TrainingExerciseRepository`
- `session/SessionRepository`, `session/PerformedExerciseRepository`, `session/SetRepository`
- `tags/TagRepository`
- `stats/StatsRepository`, `personal_record/PersonalRecordRepository`

### Reactive aggregations

Repository methods that return `Flow<T>` follow a single pattern: the DAO emits a Room-backed
`Flow`, the repo wraps it with `.map { ... }.flowOn(ioDispatcher)`. Room recompiles the query
and re-emits whenever any of the involved tables changes, so consumers never need an explicit
invalidation channel.

**One-shot vs subscription matters.** When a screen needs PRs / aggregates / lookups for
N entities, the right shape depends on whether the result is observed long-term or read
once.

For **one-shot reads** (e.g. `loadSession` at session start), parallel suspend calls via
`asyncMap` (helper in `core/core/coroutine/CoroutineExt.kt`) are fine. The work runs
once and disappears. `firstOrNull()` on a `combine`-of-N flow falls into this bucket too —
the combined flow runs once and is cancelled.

For **long-lived subscriptions** (screens that stay live and react to edits — Past session,
Exercise detail), expose a batch DAO method (`SELECT ... WHERE x IN (:uuids) ORDER BY ...`)
and one repo `Flow` that maps the result. Do not wire N per-entity `Flow`s through `combine`
for long-lived subscribers: every change to participating tables fires one per-entity Flow,
combine recomputes, downstream re-emits — amplification, not parallelism.

If the consumer only needs a subset of the data (e.g. a `Set<String>` of setUuids for a
badge match), expose **that shape** from the repo, not a full `Map<String, FullModel>`
that the consumer then collapses. Decoupling consumer from data model.

```kotlin
// One-shot — fine
val plans = coroutineScope {
    rows.map { row -> async { repository.getPlan(row.uuid) } }.awaitAll()
}

// Long-lived subscriber — wrong (amplification)
fun observe(uuids: Map<String, Type>): Flow<Map<String, Model?>> =
    combine(uuids.map { (u, t) -> observe(u, t).map { u to it } }) { it.toMap() }

// Long-lived subscriber — right (batch)
fun observeBatch(uuids: Map<String, Type>): Flow<Map<String, Model>> =
    dao.observeBatch(uuids.keys.toList())
        .map { rows -> rows.groupBy { it.key }.mapValues { it.value.first() } }
        .flowOn(ioDispatcher)
```

**SQLite version constraint.** minSdk = 28 → system SQLite ≈ 3.22, no window functions
(added in 3.25). For "best row per group" on bundled data, write a single ordered query
that returns all rows for the requested groups and `groupBy { ... }.mapValues { it.first() }`
on the Kotlin side. The SQL ordering guarantees the first row per group is the desired
one.

Do **not** add manual caches at the repo level (e.g.
`mutableMapOf<Key, MutableStateFlow>`); they leak subscriptions and obscure lifecycle
ownership. If a heavy aggregation needs to suppress wasted recomputation, cache at the
**consumer** level using `stateIn(viewModelScope, WhileSubscribed(...), initial)` — the
StateFlow disappears with the screen and the upstream collection cancels cleanly.

**Heavy-aggregation chart series (v2.2) deviate intentionally.** The per-exercise progress
chart is a leaf surface — users open it, look, leave. It reads
`SessionRepository.getHistoryByExercise(uuid)` *one-shot* on screen entry and re-runs the
read on user-driven selection / preset change, not as a `Flow` subscription. A `Flow`
variant would re-execute the heavy aggregation on every set insert / delete / edit across
the DB (Room invalidates per table), which is wasted work for a consumer that is no longer
observing by the time the data changes. This is the consumer-side cache pattern stated
above, taken to its logical end: the data lives in `State`, bound to the screen's
lifecycle, and disappears when the consumer leaves. See
[feature-specs/v2.2-exercise-charts.md](feature-specs/v2.2-exercise-charts.md)
*Architectural notes*.

Live workout's pre-session PR snapshot is a deliberate exception: it collects the reactive
PR Flow once via `firstOrNull()` to freeze a session-scoped baseline. Other consumers
(Exercise detail PR card, Past session PR badges) subscribe normally.

### DataModel hygiene

When implementing a feature, audit the `*DataModel` types you touch. If any field is
obsolete, redundant, or violates the current schema — **delete it**, don't map around it.

Phantom fields are dangerous because they look harmless. They survive mapping, get
serialized through Action variants, take up state, and silently leak into UI. Mapping
around them feels safe (it's "compatible") but compounds drift on every PR.

Concrete rules:

- A field that nothing reads — delete it.
- A field that's always null in current writes — delete it.
- A field that duplicates information now sourced from a join table — delete it (the
  join is canonical).
- A field whose semantics changed (e.g. `sets` on `ExerciseDataModel` used to be
  performed sets, now sets live in `SetEntity` via `performed_exercise`) — delete it.

If removal touches multiple callsites, surface it explicitly in the PR description:
"removing X required updating Y callsites in features Z and W". Reviewers must sign off
on the cleanup as part of the feature.

The repository → interactor → store flow reflects the **current** schema. Legacy
compatibility shims belong in migration code (which we don't have because migrations are
destructive — see [Room database](#room-database)), not in domain models.

### DataStore (preferences)

`core/dataStore/src/main/kotlin/io/github/stslex/workeeper/core/dataStore/`:

- `core/BaseDataStore.kt` is the abstract reader/writer base.
- `core/DataStoreProvider.kt` and `DataStoreProviderFactory.kt` build a `DataStore<Preferences>`
  via a Metro `@AssistedFactory`.
- `store/CommonDataStore.kt` is the application-wide preferences interface; `CommonDataStoreImpl`
  contributes it with `@ContributesBinding(AppScope::class, binding = binding<CommonDataStore>())`.

## Navigation

The navigation architecture is a **lifecycle-safe command bus**. Navigation
**decisions** live in Store/Handler layer and use the `Navigator` interface;
navigation **execution** (the actual list operations on the app-owned back
stack) lives in the App/UI bridge under composition. No
ViewModel/Store/Handler/Singleton ever retains the `NavBackStack`, any other
`navigation3` type, an `Activity`, or a `Context` — the singleton bus stores
only a `SharedFlow` (plus the keyed result flows).

### Routes

`core/ui/navigation/.../Screen.kt` defines all routes as a `@Serializable sealed
interface`. Bottom-bar destinations are nested under `Screen.BottomBar`
(`Home`, `AllExercises`, `AllTrainings`) and declare `isSingleTop = true`.
Detail destinations carry route arguments as value-type fields:
`Screen.Training(uuid)`, `Screen.Exercise(uuid)`,
`Screen.LiveWorkout(sessionUuid, trainingUuid)`,
`Screen.PastSession(sessionUuid)`,
`Screen.PlanEditor.Existing(performedExerciseUuid, exerciseUuid, trainingUuid)` — see
[Plan editor: the one destination](#plan-editor-the-one-destination) below,
`Screen.ExerciseChart(exerciseUuid)`, `Screen.ExerciseImage(model)`. Pure
single-instance destinations are `data object` (`Screen.Settings`,
`Screen.Archive`).

`Screen` extends Nav3's `NavKey` — a pure marker interface from the KMP
`navigation3-runtime` artifact, the one library type on the hierarchy. Features
never name it; the androidTest import gate bans it. It is what lets the
app-owned back stack persist through `rememberNavBackStack(configuration, …)`:
the serializers are registered polymorphically under `NavKey` in
`screenSavedStateConfiguration` (`ScreenSerialization.kt`), and
`ScreenSerializationTest` round-trips every sealed leaf so a destination added
without registration is a red unit test, not a process-death crash.

Two `NavGraphScope` extensions consume these routes:

- `navScreen<TScreen>(content)` — registers an `entry<TScreen>` and passes the
  typed key straight through to the graph composable. The `toRoute()` decode
  step Nav2 needed is gone; the key IS the argument object.
- `navScreenWithResults<TScreen>(content)` — same, plus the app-owned
  `NavResultsSource`. **Not for feature use**: its only caller is
  `navComponentScreenWithResults`, which wraps the source in a `NavResults`
  before anything sees it (see
  [Navigation results](#navigation-results) below).

### `Navigator` (command bus interface)

`core/ui/navigation/.../Navigator.kt` exposes six operations and nothing
else — no controller, no back stack:

```kotlin
interface Navigator {
    fun navTo(screen: Screen)
    fun popBack()
    fun <S, R : Any> popBackWithResult(destination: KClass<S>, result: R)
        where S : ScreenWithResult<R>
    fun replaceTo(screen: Screen)
    fun restartApp()
    fun openRecovery()
}
```

The contract is intentionally controller-free. Stores, Handlers, Interactors,
and any other layer that wants to make a navigation **decision** depends on
`Navigator` only. They never know whether the underlying executor is wired or
not — emitting a navigation command at any point is safe; it queues until the
bridge is attached.

`restartApp()` is the destructive variant, and unlike the queued commands
above it does **not** travel over the command bus. `NavigatorEventBus`
constructor-injects an `AppReinitializer` (an expect/actual class in
`core/core/.../platform/`) and `restartApp()` calls
`appReinitializer.reinitialize()` directly. The Android actual
cold-starts the app from a fresh process: it
relaunches the launcher intent with
`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` on the **application**
`Context` and calls `Runtime.exit(0)`. It exists because some operations
(e.g. a Room database file swap after a Drive backup restore) invalidate the
in-process DAO graph and singletons, and the only safe recovery is a full
process restart. Restart bypasses the bus on purpose: the bus is `replay = 0`,
so a command emitted while no bridge subscriber is attached would be silently
dropped — resolving the seam directly removes that hazard. Feature code never
imports `Context` or `Intent` to do this — it just calls
`navigator.restartApp()` like any other command.

### `NavigatorEventBus` (singleton command bus implementation)

`app/common/.../navigation/NavigatorEventBus.kt` is the singleton
implementation. It implements three interfaces:

- `Navigator` — the producer side called by feature `NavigationHandler`s.
- `NavigatorReceiver` (`commands: SharedFlow<NavCommand>`) — the
  consumer side collected by the App/UI bridge.
- `NavResultsSource` — the result transport: a keyed store of nullable
  `StateFlow`s, written by the command executor before the pop and cleared by
  `NavResults` after delivery (see
  [Navigation results](#navigation-results)).

```kotlin
@ContributesBinding(AppScope::class, binding = binding<Navigator>())
@SingleIn(AppScope::class)
@Inject
class NavigatorEventBus(
    private val appReinitializer: AppReinitializer,
) : Navigator, NavigatorReceiver, NavResultsSource {

    private val _commands = MutableSharedFlow<NavCommand>(
        extraBufferCapacity = 64,
    )
    override val commands: SharedFlow<NavCommand> = _commands.asSharedFlow()

    private val results = ConcurrentHashMap<String, MutableStateFlow<Any?>>()

    override fun result(key: String): StateFlow<Any?> = resultFlow(key)

    override fun setResult(key: String, result: Any) {
        resultFlow(key).value = result
    }

    override fun clearResult(key: String) {
        resultFlow(key).value = null
    }

    private fun resultFlow(key: String): MutableStateFlow<Any?> =
        results.getOrPut(key) { MutableStateFlow(null) }

    override fun navTo(screen: Screen) {
        consume(NavCommand.NavTo(screen))
    }

    // popBack / replaceTo / openRecovery consume() their NavCommand the same way;
    // popBackWithResult mints the key first:
    override fun <S, R : Any> popBackWithResult(
        destination: KClass<S>,
        result: R,
    ) where S : ScreenWithResult<R> {
        consume(NavCommand.PopBackWithResult(NavResultKey.of(destination), result))
    }

    override fun restartApp() {
        // Restart is terminal and platform-owned — resolve the process-scoped
        // AppReinitializer by constructor injection and invoke it directly rather than
        // routing a NavCommand through the replay=0 command bus (which would silently
        // drop with no mounted subscriber, the OpenRecovery hazard).
        appReinitializer.reinitialize()
    }

    private fun consume(command: NavCommand) {
        log.d { "Processing navigation command: $command" }
        _commands.tryEmit(command).also { emitted ->
            if (emitted.not()) {
                log.w { "Failed to emit navigation command: $command" }
            }
        }
    }
}
```

Why singleton:

- Stores live as long as a back-stack entry's ViewModel scope (the per-entry
  `ViewModelStore` from `rememberViewModelStoreNavEntryDecorator`); the bridge
  lives as long as the current Compose composition. The bus must outlive both,
  so a Store can emit a command at any time without coupling its lifetime to
  the current bridge instance. The bridge re-attaches on every recomposition /
  activity recreation and observes commands emitted **after** its
  subscription.
- The bus stores **no back stack**. It holds a `SharedFlow`, the emit
  methods, and the keyed result flows. There is nothing for the Android
  Framework to leak through it.

The bus uses `MutableSharedFlow(replay = 0, extraBufferCapacity = 64)`. The
`extraBufferCapacity` lets `tryEmit` succeed without blocking when subscribers
are slow, but it is **not a replay buffer**: emissions made while no
subscriber is attached are not redelivered to a subscriber that attaches
later. This matches the production lifecycle — the bridge attaches in
`App.kt` via `LaunchedEffect(navigatorHolder)` before any feature
`NavigationHandler` could fire `Action.Navigation.<X>` for that composition,
so pre-subscription emissions are not part of the lifecycle contract. The
contract that **is** load-bearing: the bus stays usable across bridge
detach / re-attach cycles, and the next bridge observes every command
emitted after its subscription point in dispatch order.

The class is annotated `@SingleIn(AppScope::class)` directly and constructor-injects
with `@Inject`. It carries
`@ContributesBinding(AppScope, binding<Navigator>())` so callers depending on the
abstract interface receive the same app-scoped instance — no separate module binding is
needed. The class name carries the
`Bus` suffix on purpose so it does not match any `MetroScopeRule` predicate
(`Repository`, `DataStore`, `Database`, `Storage`, `StoreDispatchers`,
`Handler`, `Interactor`, `Mapper`, `Store`).

`NavCommand` (`core/ui/navigation/.../NavCommand.kt`) is a
`sealed interface` with five variants — `NavTo(screen)`,
`ReplaceTo(screen)`, `PopBack`, `PopBackWithResult(key, result)`, and
`OpenRecovery`. The four back-stack commands correspond 1-to-1 with `Navigator`
operations; `PopBackWithResult` is the one place the result's key and value are
untyped, and it is confined to this module for that reason —
`restartApp()` is deliberately **not** a `NavCommand` — it invokes the
`AppReinitializer` seam directly (see above). Living in `core/ui/navigation`
(next to `Navigator`) lets the bus, the bridge, and any test double share
the same sealed surface without crossing the `app/app` module boundary.

### App/UI bridge: `NavigatorExt.NavigationEventBusSetup`

`App.kt` owns the back stack. It is created with `rememberNavBackStack` —
the COMMON overload with an explicit `SavedStateConfiguration`, never the
Android-only reflection one: the configuration
(`screenSavedStateConfiguration`, `core/ui/navigation/.../ScreenSerialization.kt`)
is what lets the stack survive process death, and `ScreenSerializationTest`
round-trips every `Screen` leaf through it. The stack is wrapped in a
`NavigatorHolder` for type clarity:

```kotlin
@Composable
fun App() {
    AppTheme(themeMode = themeMode) {
        val backStack = rememberNavBackStack(
            screenSavedStateConfiguration,
            Screen.BottomBar.Home,
        )
        val holder = remember(backStack) { NavigatorHolder(backStack) }
        val navigatorEventBus = viewModel.navigatorEventBus

        NavigationEventBusSetup(
            navigatorHolder = holder,
            navigator = navigatorEventBus,
            results = navigatorEventBus,
        )

        // ... NavDisplay wired through
        // AppNavigationHost(navigatorHolder = holder, results = navigatorEventBus)
    }
}
```

`NavigatorExt.NavigationEventBusSetup` (`app/common/.../navigation/NavigatorExt.kt`)
is the **only** place navigation commands are executed. It collects
`navigator.commands` keyed on the holder and processes each command:

```kotlin
@Composable
fun NavigationEventBusSetup(
    navigatorHolder: NavigatorHolder,
    navigator: NavigatorReceiver,
    results: NavResultsSource,
) {
    val context = LocalContext.current
    LaunchedEffect(navigatorHolder) {
        navigator.commands.collect { command ->
            processCommand(
                holder = navigatorHolder,
                command = command,
                results = results,
                context = context,
            )
        }
    }
}
```

The `LaunchedEffect(navigatorHolder)` is the lifecycle anchor: when the
composition is destroyed and a new one starts (config change, activity
recreation), the effect cancels its old collection and re-collects on the
freshly-remembered holder. The `NavigatorEventBus` instance is the
same; the executor is new — and the stack's *contents* survive the
recreation (and process death) through the `SavedStateConfiguration` above.
The new executor observes commands emitted
**after** it subscribes — the bus's `MutableSharedFlow(replay = 0,
extraBufferCapacity = 64)` does not replay pre-subscription emissions.
That trade-off is intentional: the production bridge is attached
synchronously inside `App.kt` before any Compose-driven Store action could
fire, so a "lost" pre-subscription navigation command would only happen if
a long-lived background coroutine emitted while the activity was being
recreated — and the next user-visible navigation will originate from a
post-subscription action regardless.

`processCommand` executes each `NavCommand` as a list operation on the
app-owned stack:

- `NavTo` of an `isSingleTop` destination (a bottom-bar root) — replace the
  top entry (`stack[stack.lastIndex] = screen`). Replace-last IS the
  singleTop semantic here: tab round trips arrive reset (pinned by
  `BackStackStateRestorationTest.selectionModeArrivesResetAfterABottomBarRoundTrip`),
  and re-tapping the ACTIVE tab does not mint a fresh entry — the roots are
  `data object`s and entry state is keyed by the key's identity, so replacing
  with an equal key is deliberately a no-op.
- `NavTo` of a normal destination — `stack.add(screen)`.
- `PopBack` — `removeLastOrNull()`, guarded so it only pops when something is
  underneath: system back at the root belongs to the platform (the activity
  finishes), and `NavDisplay` must never be handed an empty stack.
- `PopBackWithResult` — `results.setResult(key, result)` into the app-owned
  `NavResultsSource` **before** the pop, which is how navigation results flow
  back to the previous screen (see [Navigation results](#navigation-results)).
- `ReplaceTo` — replace the top entry.
- `OpenRecovery` — the one branch that does not touch the stack: it uses the
  `Context` captured at bridge attach to launch `RecoveryActivity` in a fresh
  task (`FLAG_ACTIVITY_NEW_TASK`).

Process restart is **not** one of these branches — it never reaches
`processCommand` at all, because `restartApp()` invokes the `AppReinitializer`
seam directly instead of emitting a `NavCommand` (see the destructive
app-restart section below). The bridge is the only place that holds `Context`
for the navigation pipeline; no feature module imports `Context` for restart
or recovery purposes.

### Lifetime rules

The reason the bus is split between a singleton command bus and a
composition-scoped executor is that the back stack is composition-owned
state: `rememberNavBackStack` creates it inside the composition and its saved
state follows the activity's `SavedStateRegistry`. Retaining it (or any other
`navigation3` type) in a longer-lived layer pins a stale instance across
recreation — the restored composition builds a fresh stack, and anything the
old reference mutates is no longer what `NavDisplay` renders.

The rules, enforced by reading code review (no Detekt rule yet, but the
class naming makes it greppable — and the androidTest import gate bans
`androidx.navigation3` imports outside the allowed modules):

- The `NavBackStack`, `NavKey` entry references, `EntryProviderScope`,
  `Activity`, and `Context` MUST NOT be stored as a
  field of any `ViewModel` / `Store` / `Handler` / `Interactor` / `Mapper`
  class, and MUST NOT be passed into a `@SingleIn(AppScope::class)` binding.
- `NavigatorEventBus` IS allowed in singleton / ViewModel / Store / Handler
  layers because it stores only a `SharedFlow<NavCommand>`, the emit
  methods, and the keyed result flows. No back-stack reference exists inside
  it.
- `NavigatorHolder` (`core/ui/navigation/.../NavigatorHolder.kt`) wraps the
  app-owned `NavBackStack` and MUST stay scoped to composition (created via
  `remember(backStack)` in `App.kt`). It MUST NOT be cached statically,
  passed through DI, or stored in a `Singleton`. It is exposed to `:app:app`'s
  command executor and host ONLY; no feature module may name a `NavBackStack`.
- No raw transport reaches a graph composable at all. The result transport
  (`NavResultsSource`) is held privately by `NavResults`
  (`core/ui/mvi/.../NavResults.kt`), which is what
  `navComponentScreenWithResults` hands to the content lambda — the typed
  surface is the only thing a feature sees, by construction rather than by
  rule.

### Navigation flow (canonical pattern)

Navigation is **always** routed through a feature's `NavigationHandler`, never
through the graph composable directly. The pattern:

1. UI emits an `Action.Navigation.<Something>` via `processor.consume(...)`.
2. The Store's `handlerCreator` lambda routes that action to the feature's
   `NavigationHandler`.
3. `NavigationHandler` has `Navigator` constructor-injected by Metro and calls
   `navigator.navTo(...)`, `navigator.replaceTo(...)`, `navigator.popBack(...)`,
   or `navigator.restartApp()`. The `Navigator` is the singleton
   `NavigatorEventBus` — the back-stack calls are pure command dispatch
   with no side effect on the stack itself; `restartApp()` is the exception,
   invoking the `AppReinitializer` seam directly (see below).
4. The App/UI bridge collects the command and executes it as a list
   operation on the app-owned back stack.

Concretely, a feature defines:

```kotlin
// In the Store contract:
sealed interface Action : Store.Action {
    sealed interface Navigation : Action {
        data object Back : Navigation
        data object OpenArchive : Navigation
        // ... any other navigation targets
    }
}

// As a separate handler class in mvi/handler/:
@SingleIn(<Name>Scope::class)
internal class NavigationHandler @Inject constructor(
    private val navigator: Navigator,
) : Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.Back -> navigator.popBack()
            Action.Navigation.OpenArchive -> navigator.navTo(Screen.Archive)
        }
    }
}
```

The handler holds **no back stack**, **no transport**, **no entry
reference** — only the `Navigator` command-bus reference. It is
JVM-unit-testable by mocking `Navigator` and verifying the emitted method
call.

The graph composable consumes only **UI-side events** through
`processor.Handle { event -> ... }`:

- `Event.Haptic*` — translated to
  `LocalHapticFeedback.current.performHapticFeedback(...)`.
- `Event.Show*` (e.g. `ShowExternalLink(url)`, `ShowError`) — translated to
  `Intent.ACTION_VIEW` or `SnackbarManager.showSnackbar(...)`. `Show*` events
  cover **fire-and-forget side effects only** — Intent dispatch, snackbar
  text, Activity Result Contract launches. Anything that must remain visible
  until the user dismisses it (dialogs, bottom sheets) belongs in `State`,
  not `Event`. See "Dialogs and bottom sheets live in State" in the
  conventions section below.
- `Event.Scroll*` — translated to a `LazyListState` scroll command in scope.

The graph composable **never** mutates the back stack directly, **never**
consumes an `Event.Navigate*` (such an event must not exist — it would be
misnamed), and **never** captures the `NavBackStack` outside the bridge.

Reference implementation: `feature/all-trainings/ui/AllTrainingsGraph.kt`
(graph) and `feature/all-trainings/mvi/handler/NavigationHandler.kt`
(handler). For an assisted-injected route-arg variant, see
`feature/exercise/ui/ExerciseGraph.kt` +
`feature/exercise/ui/mvi/handler/NavigationHandler.kt`.

### Destructive app-restart through the `AppReinitializer` seam

Some operations invalidate the in-process Singleton graph (Room DAOs, cached
repository state, observed Flows). The canonical case is a Drive backup
restore that swaps the live database file via
`DatabaseSnapshotProvider.restoreFromSnapshot` — after the swap, every
already-resolved DAO points at a stale file handle, so only a cold start
recovers correctness.

The pattern still expresses restart as a `Navigator` call (not a feature-local
helper), but — unlike the back-stack commands — it resolves to a direct seam
invocation rather than a bus emission:

1. The feature's domain/MVI layer (e.g. `BackupClickHandler` after a
   successful restore) emits `Action.Navigation.RestartApp` via
   `consume(Action.Navigation.RestartApp)` — typically wrapped in a
   `delay(RESTART_DELAY_MS)` so the success snackbar / "Completed" UI state
   has a chance to render.
2. The feature's `NavigationHandler` adds a single `when` branch:

   ```kotlin
   Action.Navigation.RestartApp -> navigator.restartApp()
   ```

3. `NavigatorEventBus.restartApp()` invokes the constructor-injected
   `AppReinitializer` seam directly — `appReinitializer.reinitialize()` —
   rather than emitting a `NavCommand`. Restart is terminal and
   platform-owned, so it bypasses the `replay = 0` bus, which would silently
   drop the command when no bridge subscriber is attached.
4. The Android `AppReinitializer` actual relaunches the
   package's launch intent (`FLAG_ACTIVITY_NEW_TASK | CLEAR_TASK`) on the
   application `Context` and calls `Runtime.getRuntime().exit(0)`. (The iOS
   actual throws until Phase 5 delivers the in-place reinit — iOS has no
   self-restart API; see the expect KDoc for the DataStore-memoization
   precondition.)

What this pattern replaces — and why:

- An older revision shipped a feature-local `restartApp(context: Context)`
  helper invoked from `SettingsGraph.kt` in response to an
  `Event.AppRestartRequested`. That pushed an `Activity`/`Context`
  dependency into a feature module and forked the "execute a side effect
  that touches the framework" surface in two: most navigation went through
  the bus, restart went through an Event. Promoting restart into
  `Navigator.restartApp()` re-unifies the surface: every navigation-shaped
  side effect is expressed as a `Navigator` call, and no feature module
  imports `Context` / `Runtime` / `Intent` for navigation purposes. The
  back-stack commands are `NavCommand`s translated by `NavigatorExt`; process
  restart resolves to the `AppReinitializer` seam (`reinitialize()`), keeping
  both the framework `Context` and the process-kill primitive out of feature
  and domain code.
- `Event.AppRestartRequested` is intentionally not part of the contract —
  app restart is a navigation **decision**, not a UI-side effect. Encoding it
  as an `Action.Navigation` variant means the same MVI rules apply
  (Handler-routed, JVM-unit-testable by mocking `Navigator`).

Reference implementation:
`feature/settings/.../mvi/handler/BackupClickHandler.kt::scheduleAppRestart`
(producer), `feature/settings/.../mvi/handler/SettingsNavigationHandler.kt`
(router), `app/common/.../navigation/NavigatorEventBus.kt::restartApp`
(seam dispatch), and
`core/core/src/androidMain/.../platform/AppReinitializer.kt::reinitialize` (executor).

### Navigation results

Some destinations return a value to whoever opened them — `Screen.PlanEditor`
hands back `true` on save so the caller reloads, and `Screen.ExerciseImage`
hands back a request name.

**The result type is declared on the destination**, by implementing
`ScreenWithResult<R>`:

```kotlin
sealed interface PlanEditor : Screen, ScreenWithResult<Boolean>
data class ExerciseImage(...) : Screen, ScreenWithResult<String>
```

The ten destinations that produce nothing stay plain `Screen`.

The mechanics:

1. The producer (e.g. `feature/plan-editor`'s `NavigationHandler`) calls
   `navigator.popBackWithResult(Screen.PlanEditor::class, true)`. `R` is not
   chosen at the call site — it resolves from the destination's own
   `ScreenWithResult` parameter, so passing the wrong type does not compile.
2. `NavigatorExt` publishes the value into the app-owned `NavResultsSource`
   (implemented by `NavigatorEventBus` — a keyed store of nullable
   `StateFlow`s, keyed by `NavResultKey.of(destination)` strings) *before*
   the pop. The order is load-bearing: the value has to be readable before
   the pop reveals the consumer, or it recomposes on arrival with nothing
   there and the result is lost. This is the only place the transport is
   untyped. **Accepted delta:** a result does not survive process death
   inside the set→collect window — the window is one recomposition wide, and
   no user journey holds a result across process death (see
   `NavResultsSource`'s KDoc).
3. The consumer's graph registers with `navComponentScreenWithResults`, whose
   content lambda receives a `NavResults` rather than the raw
   `NavResultsSource`:

   ```kotlin
   navComponentScreenWithResults(LiveWorkoutFeature) { results, processor ->
       results.OnResult(Screen.PlanEditor::class) { saved ->
           processor.consume(Action.Common.PlanResultReceived(saved))
       }
   ```

   `OnResult` delivers once per result and clears it, so re-entry does not
   re-fire. **Reading is nullable — `null` means "no result"**; there is no
   `Cancelled` case, because "did not save" and "pressed back" were already
   the same state and no consumer distinguishes them.

**A graph forwards a result; it does not interpret one.** Reading a result is
state, and state belongs in the Store — so the shape at every call site is
`OnResult` → `processor.consume(Action…)`, with the parsing and the decision on
the far side of that call. `ExerciseStore.Action.Common.ImageRequestReceived`
is the reference: the graph passes the raw name, and `CommonHandler` resolves
it exhaustively over `Screen.ExerciseImageRequest`.

`NavResults` holds the `NavResultsSource` privately and exposes nothing that
leaks it, so the transport still never reaches a Store, Handler,
ViewModel, or graph branch — by construction rather than by convention.

> **Note for tests.** `AppCoroutineScopeImpl.launch(flow, …)` applies
> `.catch { onError(it) }`, so a flow error inside a Store is swallowed: a
> broken result path surfaces as a screen quietly holding default state, not as
> a throw. Assert the observable effect — the originating screen reflecting the
> save — never the absence of an exception, or the test passes vacuously.

### `CompositionLocal` in the navigation path — a decision for Nav3, not a rule in force

The project's navigation rule is **no `CompositionLocal`; `Navigator` is injected**
(`@Inject Navigator`, per the v2.1/v2.2 reference features). Recording the boundary of
that rule here so the Nav3 swap does not have to re-derive it, and so the code it will
produce does not later read as a violation.

**The rule targets `Navigator`.** Its point is that navigation *decisions* are made in
Store/Handler against an injected command bus, rather than reached for ambiently from
composition — which is what keeps them testable and keeps the back stack out of the
ViewModel layer. An animation scope is not the navigator: it decides nothing, it is
read only while composing, and nothing about it can be asserted in a Store test.

Under Nav3 the animated-content scope is delivered as
`LocalNavAnimatedContentScope` — a `CompositionLocal`, by the library's design, with no
alternative supplied. Consuming it there is **within** the rule, not an exception to it.

**Nothing consumes it today, and nothing was added to.** Stage 1.2 removed the unused
`AnimatedContentScope` receiver from the content-lambda signature and added no accessor,
because the repo contains no `sharedElement(` / `sharedBounds(` / `animatedEnterExit(`
call at all — an accessor would have been API serving no caller. When a shared-element
transition is actually written, that change introduces the accessor and its first
consumer together.

Currently `Screen.LiveWorkout` consumes the PlanEditor result this way, and
`Screen.Exercise` consumes the `Screen.ExerciseImage` request name.

### Plan editor: the one destination

`Screen.PlanEditor` is a sealed interface with ONE destination:

- **`Screen.PlanEditor.Existing(performedExerciseUuid, exerciseUuid, trainingUuid)`** —
  the "edit a persisted plan" route. `CommonHandler.Init` reads `(type,
  plan)` from disk via `PlanEditorInteractor.loadPlan`. On Save the editor
  persists `(type, plan)` (Mode.Exercise also writes `exercise_table.type`
  and runs `clearWeightsFromAllPlansForExercise` when type flips to
  WEIGHTLESS) and pops back handing `true` to `Screen.PlanEditor`'s declared
  result. The caller
  performs a *partial* reload — only `(type, adhocPlan)` are refreshed in
  parent state — so any unsaved name/description/tag/image edit is
  preserved (this is the v1.41.0 dirty-baseline regression fix).

**There is no creation destination.** An exercise with no persisted UUID is
built on the exercise form, which hosts `PlanEditorBody` inline — so there is
no in-flight draft to carry to another screen and hand back. Every
destination here edits something that exists.

Type ownership lives in PlanEditor. The toggle and
the type-change-confirm dialog (with weight-wipe semantics for
WEIGHTED → WEIGHTLESS flips) are the plan editor's responsibility, not the
parent form's. `Mode.PerformedExercise` (used by single-training and
live-workout callsites) hides the toggle — the type lives on the parent
exercise and isn't editable through a training-scoped editor.

`planEditorGraph` registers the concrete route via
`navScreen<Screen.PlanEditor.Existing>` — the one graph that calls `navScreen`
rather than `navComponentScreen`, because `PlanEditorFeature` is typed on the
sealed parent `Screen.PlanEditor` (what the store's DI factory takes) while
the registered ROUTE is the concrete `Existing`, and `navComponentScreen`
reifies one type for both. See the graph's KDoc for the alternatives
considered and rejected.

#### Dispatching navigation from background coroutines

`NavigationHandler.invoke` calls into `Navigator`, whose commands end in
writes to the snapshot-state back stack — that work must happen on the main
thread. When a click handler emits a navigation
action from inside a background coroutine (e.g. `repository.archive` success
callback), it must dispatch to main before calling `consume`.

The `HandlerStore` interface exposes `consumeOnMain(action)` exactly for this:

```kotlin
// In HandlerStore.kt:
suspend fun consumeOnMain(action: A)

// In BaseHandlerStore.kt:
override suspend fun consumeOnMain(action: A) {
    withContext(store.scope.immediateDispatcher) {
        store.consume(action)
    }
}
```

Use it from any handler that emits `Action.Navigation.*` after a suspend call:

```kotlin
// CORRECT — use consumeOnMain for navigation from background
fun processArchiveClick(uuid: String) {
    launch(defaultDispatcher) {
        interactor.archive(uuid)
        consumeOnMain(Action.Navigation.Back)
    }
}

// WRONG — raw consume from background dispatcher mutates the back stack off-main
fun processArchiveClick(uuid: String) {
    launch(defaultDispatcher) {
        interactor.archive(uuid)
        consume(Action.Navigation.Back)  // background → main thread violation
    }
}
```

Plain `consume` is still correct from main-dispatched contexts (UI clicks routed
through `processor.consume`, callbacks already on main, etc). The rule: any
`consume(Action.Navigation.*)` invocation that originates from a coroutine
launched on a non-main dispatcher must use `consumeOnMain`.

### Back gesture handling

Three different surfaces can trigger "go back" — the system back gesture (or hardware
back button), the `AppTopAppBar` navigation icon, and a Cancel button on edit forms.
The decision logic must be the same for all three (the user expects consistent
behavior regardless of which surface they touched), but the **interception mechanics
differ between gesture and explicit taps**.

#### Why interception is conditional

Android 13+ introduced [predictive back gesture](https://developer.android.com/guide/navigation/predictive-back-gesture):
during a back swipe, the system animates a preview of the destination screen
(parallax, peek behind). This animation only runs when the system can pop the back
stack itself. As soon as a `BackHandler` with `enabled = true` is registered, the
system **disables** the preview because the app intercepts the gesture.

Therefore: keep `BackHandler` **disabled by default** (preserve native predictive
back), and **enable it only when the screen actually needs to intercept** — typically
when a form has unsaved changes that would be lost on pop.

#### The `interceptBack` derived flag

State carries the intercept condition as a derived boolean:

```kotlin
data class State(
    val mode: Mode,
    val originalSnapshot: Snapshot?,
    val name: String,
    // ... other form fields
) : Store.State {
    val interceptBack: Boolean
        get() = mode is Mode.Edit && hasUnsavedChanges()

    fun hasUnsavedChanges(): Boolean {
        val snapshot = originalSnapshot ?: return false
        return currentSnapshot() != snapshot
    }

    private fun currentSnapshot(): Snapshot = Snapshot(name, /* ... */)
}
```

`interceptBack` is **computed**, not stored — it recomputes on every recomposition
based on current state values. This way the BackHandler enabled status is reactive:
the moment a user types in a clean form, `hasUnsavedChanges()` flips to true,
`interceptBack` flips to true, and `BackHandler` becomes active.

#### BackHandler wiring in Composables

```kotlin
val state by processor.state.collectAsState()

BackHandler(enabled = state.interceptBack) {
    processor.consume(Action.Click.OnBackClick)
}
```

When `interceptBack` is false: the gesture goes natively through `NavDisplay`'s
back handling, the predictive preview animation runs, no store involvement. That preview is
**drawn by the app**, not by the system: it is the host's `predictivePopTransitionSpec`,
seeked with raw gesture progress — see
[the navigation host](#navigation-host-and-shared-element-transitions).

When `interceptBack` is true: gesture is intercepted, emits `OnBackClick` into the
store, and the store decides what to do (typically: show a discard dialog).

#### Explicit back triggers (top-bar arrow, Cancel button)

These are explicit UI taps, not gesture interception. They **always** emit
`Click.OnBackClick` directly:

```kotlin
AppTopAppBar(
    navigationIcon = {
        IconButton(onClick = { processor.consume(Action.Click.OnBackClick) }) { /* ... */ }
    },
    ...
)
```

They do not depend on `interceptBack` — every tap goes into the store, and the
store's `ClickHandler` decides whether to navigate back, show a discard dialog, etc.

#### ClickHandler logic for `OnBackClick`

The handler is the single source of truth for back behavior. Same logic for all
three triggers:

```kotlin
fun processBackClick() {
    when (state.value.mode) {
        is Mode.Read -> consume(Action.Navigation.Back)
        is Mode.Edit -> {
            if (state.value.hasUnsavedChanges()) {
                consume(Event.ShowDiscardConfirmDialog)
            } else {
                consume(Action.Navigation.Back)
            }
        }
    }
}
```

Two outcomes:

1. **`Action.Navigation.Back`** — handled by the feature's `NavigationHandler`, which
   calls `navigator.popBack()`.
2. **`Event.ShowDiscardConfirmDialog`** — handled by the graph composable, which
   renders an `AppDialog` (or `AppConfirmDialog`) asking the user to confirm losing
   unsaved changes. Confirm → `consume(Action.Click.OnConfirmDiscard)` → emits
   `Action.Navigation.Back`. Dismiss → just dismiss, stay on screen.

#### Anti-patterns to avoid

- `BackHandler { processor.consume(Action.Click.OnBackClick) }` (always enabled).
  This breaks the predictive-back preview even in Read mode where there's nothing to
  intercept — and it breaks it *completely*: an enabled `BackHandler` means the gesture
  never reaches `NavDisplay`, so the screen plays neither the preview nor the pop
  transition. Every screen now registers its handler as `BackHandler(enabled = ...)` or
  registers none; `image-viewer` was the last unconditional one and no longer has a
  handler at all. A screen that wants a side effect on *every* back — a haptic, an
  analytics event — cannot have one on the gesture path, and that is the correct trade:
  it is what buys the preview.
- `BackHandler(enabled = state.mode is Mode.Edit) { ... }` (gated only by mode, not
  by dirty status). This intercepts every back in Edit mode even when there's
  nothing to lose, again breaking predictive back unnecessarily.
- Top-bar arrow that calls `navigator.popBack()` directly. This bypasses the store
  and creates inconsistency: hardware back goes through ClickHandler with discard
  dialog, but top-bar arrow skips it.
- Three different click actions for three triggers (`OnBackGesture`, `OnTopBarBack`,
  `OnCancelClick`). Use one (`OnBackClick`) routed identically.

### SQLite query-planner statistics

`BaseApplication.warmQueryPlanner()` runs `ANALYZE` once per process, off the main thread and
best-effort, via `refreshQueryPlannerStatistics` in `core:data:database`. Measured on
`:app:dev:installRelease` against a long-term database: 878ms on the first launch after install,
45–107ms on every launch after that. Holding the writer connection that long is free only under
WAL, where readers do not block on a writer. Room's default `AUTOMATIC` journal mode resolves to
WAL everywhere except `isLowRamDevice` hardware, where it is `TRUNCATE` — so the warm-up is
**skipped on low-RAM devices** rather than assumed safe there. The cost is that the planner keeps
guessing on those devices; the alternative, pinning `WRITE_AHEAD_LOGGING` in `AppDatabaseFactory`,
would override a platform-aware Room default for every query in the app to buy one startup chore,
on the hardware least able to afford WAL's memory. Without `sqlite_stat1`
SQLite plans on guesswork, and the guess it made in production was to drive the live-workout
personal-record read from `session_table.state` — an index over two distinct values — walking every
finished session the user had ever logged instead of the handful of exercises the query asked
about. The statistics change no SQL and no result: only the access path.

Two constraints hold it in place. It runs **after** the recovery pre-flight chain and **not at all**
on the `RouteToRecovery` path, because `ANALYZE` writes and therefore opens the database, which is
exactly what that start must avoid. And it is `ANALYZE` rather than the more usual
`PRAGMA optimize`, which is a no-op when called before its own connection has read anything and
whose override bit needs a newer SQLite than `minSdk 28` can assume.

### Navigation host and shared element transitions

`app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt` receives the
composition-scoped `NavigatorHolder` (which wraps the back stack created by
`rememberNavBackStack` in `App.kt`) and wraps the `NavDisplay` in a
`SharedTransitionLayout` (Jetpack Compose
`ExperimentalSharedTransitionApi`). The layout is the anchor a shared-element transition
would attach to; **no graph receives the scope as a parameter.** Nothing in the app performs
a shared-element transition today, so the graphs that used to take a
`SharedTransitionScope` and never read it no longer declare one. When the first transition
is written, the scope reaches it through `LocalNavAnimatedContentScope` (see
[the CompositionLocal decision](#compositionlocal-in-the-navigation-path--a-decision-for-nav3-not-a-rule-in-force)),
and that change introduces the accessor and its first consumer together. The start
destination is `Screen.BottomBar.Home`.

`NavDisplay` mounts with an EXPLICIT `entryDecorators` list —
`rememberSaveableStateHolderNavEntryDecorator()` plus
`rememberViewModelStoreNavEntryDecorator()`. The default set is saveable-only:
without the ViewModel decorator, `viewModel {}` resolves against the
Activity's store, nothing crashes, and every Store silently becomes
process-scoped — the exact failure `StoreRetentionTest.isolation`'s
activity-scoped-store mutation pins. Its `onBack` pops only while the stack
holds more than one entry: system back at the root belongs to the platform
(the activity finishes — `ApplicationBottomBarTest` pins it), and
`NavDisplay` must never see an empty stack.

`NavDisplay` takes **three** transition specs and this host passes all three, from
`app/common/.../host/NavTransitions.kt`. `transitionSpec` (forward navigation — and every
bottom-tab switch, because `NavigatorExt` REPLACES the top entry for `isSingleTop`, which
`NavDisplay.isPop` correctly reads as *not* a pop) and `popTransitionSpec` (the top-bar
chevron, `navigator.popBack()`, three-button back) both run one shared `ContentTransform`:
the 260ms crossfade from alpha 0.3 that the Nav2 host ran.

`predictivePopTransitionSpec` — the finger-driven back gesture, and only that — runs a
preview instead. The leaving screen stays opaque and scales 1 -> 0.9 about the edge
*opposite* the swiped one, which reproduces Material 3's own predictive-back geometry
(`SearchBarPredictiveBackMinScale = 9/10`, and a centre shift of `w/20` =
`SearchBarPredictiveBackMaxOffsetXRatio`) with one channel instead of two. The screen being
uncovered grows from 0.95 into place under a black scrim at 32%, and never fades — it is
already there, and only its depth changes. **0.95 rather than anything deeper** because the band
a shallower scale would open around the incoming screen is filled by the root `Box`, which paints
the colour that screen paints: a deeper reveal buys no more depth cue and only widens a band the
reader cannot see.

**Two curves, both file-local and neither on the motion scale.** The shrink and the reveal
ride `PREVIEW_EASING` = `CubicBezierEasing(0.1f, 0.1f, 0f, 1f)`, which is Material's own
`PredictiveBackEasing` — deliberately front-loaded (0.68 of the travel in the first quarter of
the drag) because a preview that lags the finger reads as unresponsive and one that keeps
moving reads as unbounded; the platform's preview saturates and so does this. No curve on the
motion scale stands in for it: `AppMotion.out` is the nearest and reaches **0.83** at a quarter
drag, which leaves the card all but settled a quarter of the way through the gesture, and
`AppMotion.travel` reaches **0.58**, which leaves it trailing the thumb. The dissolve and
the scrim's lift ride `DEPARTURE_EASING` = `CubicBezierEasing(0.8f, 0f, 1f, 1f)`, its ease-in
mirror, so the card is still 90% opaque at a 40% drag and reaches zero smoothly. They live in
`NavTransitions.kt` rather than in `AppMotion` on purpose: they reproduce Android, they do not
express Workeeper, and the scale is the app's own vocabulary.

**No channel is delayed, and that is the point rather than a detail.** The first version of
this preview held the card's alpha flat for `AppMotion.fast` and then ran it linearly — same
endpoints, same duration, same intent, and a *corner* at the delay boundary. Under a seek the
fraction is the finger, so that corner is a single frame in which the card goes from perfectly
solid to visibly dissolving, and it reads as the screen being thrown away halfway through the
gesture. Every channel is now one continuous `tween(AppMotion.base)` with delay 0, which also
makes "each channel lands exactly at fraction 1.0" hold by inspection instead of by arithmetic.
`NavTransitionsTest` gates the absence of the corner and runs the delayed predecessor through
the same detector to show it discriminates.

**The card's rounded edge is a clip, not a transition.** `ContentTransform` has no corner
radius, so the host clips its clipped destinations' root modifiers to the display's own corner
shape (`displayCornerShape`, an `AbsoluteRoundedCornerShape` because `RoundedCorner` positions are
physical and a start/end shape would mirror them under RTL). Each of the four corners is read
separately, because a display whose corners differ is the case the whole per-corner read exists for.
`RoundedCorner` is API 31+; below that the platform reports nothing and `AppDimension.Radius.big`
stands in, and a device that reports a **zero or absent** radius (square panel, most emulators)
takes the same fallback deliberately — the point of the clip is the shrunken card, and a square
card is the defect being fixed. Asked of the platform and re-asked on every **layout pass of the view tree**, rather than inferred
from a proxy. Narrower triggers all have holes: an inset VALUE is a proxy, since `rootWindowInsets`
is null until attach and the dispatch that first makes it answerable need not move any edge Compose
exposes; the configuration is too early, because `MainActivity` declares `configChanges` and at the
instant it changes the insets still describe the previous orientation; and the host View's own
layout can stay silent when its bounds do not move, which is exactly a 180-degree rotation. A
global layout has none of them — `ViewRootImpl` dispatches insets during the same traversal before
layout, and a configuration change lays the tree out whether or not any bounds move. The signal is
cheap in a Compose app, where tree-level passes are attach, configuration and window changes rather
than content ones. A `ViewTreeObserver` listener is additive; `OnApplyWindowInsetsListener` would
have been the more direct hook and is single-listener per View, owned by `AndroidComposeView`. **The
clip and the background come before the inset padding**, so the card is the whole window and not
the content area: with the padding first, the corners would begin at the inset boundary and the
bar strips would fall outside the shrinking card. Unconditional, at rest as well as in motion, which is
what the platform does too: the window always carries that shape and you only notice once it
shrinks away from the display edge. Invisible at rest because `android:windowBackground`,
`App.kt`'s root `Box` and every clipped graph paint the same colour, so whatever the corners cut
away, what shows through is that colour.

**`image-viewer` is exempt, and the exemption is about what is behind the clip rather than about
the screen.** It paints `Color.Black`, so on hardware where the display reports no radius and the
fallback stands in (API < 31, square panels), rounding it cuts four theme-coloured wedges into an
otherwise black frame — permanently, for as long as the viewer is open, in exchange for a
gesture-only benefit. It takes a square card instead. Any future destination that paints something
other than `colorScheme.background` owes the same decision.

Three mechanics constrain anything written there, and none of them is optional.
**First**, `NavDisplay` places the incoming scene *below* the outgoing during predictive back
(`targetZIndex = initialZIndex - 1f`), so the exit transition must render nothing at fraction
1.0 or it covers the screen it just revealed. The fade is what clears it; the library's own
default omits one, which is why the default preview ends in a visible cut — that default is
what shipped between the Nav3 swap and this host passing a third spec.
**Second**, the fraction is the finger: `SeekableTransitionState.seekTo` is fed raw gesture
progress and maps it to playtime as `fraction x transition.totalDurationNanos`, the max over
*every* animation on the scene transition. Every channel spans exactly `AppMotion.base`, and
that equality is what makes "alpha reached 0" and "the gesture completed" the same instant.
The first `sharedBounds` / `sharedElement` added to any screen registers a spring on this
transition and breaks it; re-derive the windows then.
**Third**, a `ContentTransform` has exactly six channels — `TransitionData` is exhaustively fade,
slide, changeSize, scale, veil and hold, and its `effectsMap` is `internal` end to end, so nothing
outside the library can add a seventh. There is no corner radius, no shadow, no elevation and no per-frame hook; the spec
lambda receives one `Int` (the swipe edge) and is invoked twice per segment. Anything the
transition cannot express has to come from the content instead — the corner radius does, as a
clip; a shadow and a touch-Y follow would need `LocalNavAnimatedContentScope.current.transition`
driving a `graphicsLayer` inside a `NavEntryDecorator`, which is the seam AndroidX provides for
exactly this and which nothing here uses yet. `slide` and `changeSize` are avoided on purpose:
both are read in the *placement* block, so they invalidate layout every frame, and every
graph here carries `Modifier.reportScreenPlace<...>()` whose `onPlaced` would then fire per
frame per scene.

A route does not compose until it has loaded (§26), and it arrives with a fade rather than a
snap: `AppLoadedContent` (`core:ui:kit/.../loading/`) wraps the content of `exercise`,
`single-training`, `plan-editor`, `live-workout` and `past-session`, composing it only once the
route knows what it is showing and fading it in on `continuityAlphaSpec`. The predicate differs by
what each store models: `isLoading` for `exercise`, `single-training` and `plan-editor`;
`isLoading || loadFailed` for `live-workout`; and, for `past-session`, `phase !is Loading || hasResolved`
(its Error phase must still compose, and `hasResolved` is latched so the FIRST load is the only one
withheld — Retry re-enters `Loading` with the screen already up, and withholding it there blanks the
route mid-flow).

**`live-workout`'s second term is load-bearing and must not be simplified away.** A failed load
clears `isLoading` deliberately — a latched flag behind the gate is a permanently empty frame — and
records `loadFailed` in the same update. Without that term in the predicate the route would compose
the failed session as a successfully empty workout, Finish dock and all, for as long as the
asynchronous pop takes.

`exercise-chart` is the deliberate exception and must stay one: its top bar carries no title and
its exercise header renders only inside `state.selectedExercise?.let`, so nothing on its shell can
state something it has not loaded — the reason the other five are withheld does not apply. Wrapping
it was tried and reverted: `Content.Loading` is reachable *with the shell already on screen* when
the picker selects a new exercise out of an already-empty chart (`processPickerItemSelect` clears
`emptyReason` while `points` still holds the previous exercise's data), and the wrapper then blanked
the whole route mid-flow.

**No screen draws a spinner while it waits.** One appears only long enough to be noticed and
replaced, which costs two layout changes to say less than the blank does. Nothing is drawn while it waits —
no mockup draws a loading surface, and the host paints the background under every destination.
**The precondition travels with the gate:** every load behind it must clear `isLoading` on
FAILURE as well as on success, because `HandlerStore.launch` defaults `onError` to `{}` — a
latched flag is a permanently empty screen. Each of the four closes its own arm, and a
handler test pins it.

Each graph is added via `navComponentScreen<Feature>` /
`navComponentScreenWithResults<Feature>`, which expands to an `entry<Screen>`
registration under the hood (see `core/ui/navigation/.../NavGraphScope.kt::navScreen`).
The graphs themselves register against `NavGraphScope`, never the library's
`EntryProviderScope`; the host wraps the builder once in `AppNavigationHost` —
the one place the navigation library's builder is named, so re-pointing that
line is enough to change what backs the twelve registrations.

Every graph composable's `modifier` chain must include
`Modifier.reportScreenPlace<Screen.X>()` — the `onPlaced` callback that stops the TTID,
AppCreate, and ActivityCreate traces. Skipping it leaves all three pipelines
mis-attributed for that screen. See
[performance.md → New-screen contributor checklist](performance.md#new-screen-contributor-checklist).

`BottomBarNavigationListener` (`app/common/.../host/BottomBarNavigationListener.kt`) tracks
which `BottomBar` screen is current so `App.kt` can show or hide the bottom bar with an
animated visibility transition. It collects a `snapshotFlow { holder.currentScreen }`
inside a `LaunchedEffect(holder)` — `NavBackStack` is a `SnapshotStateList`, so the flow
fires on every stack change, and `snapshotFlow` emits the CURRENT value on first
collection, preserving the fires-for-the-initial-destination semantic the Nav2 listener
got from registration replay. The visible screen maps to its tab via
`BottomBarItem.getByScreen` — value identity (`entry.screen == screen`; the roots are
`data object`s, so `==` IS type identity), with no route string to parse. The listener
also latches `selectedIndex` separately from the nullable `bottomBarDestination`, so the
nav pill does not snap back to the first item while the bar's exit animation is still
composing — see the class KDoc.

`ClearFocusOnDestinationChanged` (`app/common/.../host/ClearFocusOnDestinationChanged.kt`)
follows the same `snapshotFlow`-over-the-stack pattern to clear keyboard focus on every
navigation tick, including once at startup.

### Bottom navigation

`app/common/.../bottom_app_bar/BottomBarItem.kt` declares three tab entries — `HOME`,
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
`app/common/.../bottom_app_bar/BottomAppBar.kt` for a non-event-driven example using
`HapticFeedbackType.SegmentTick`. The `Haptic` token is in `MviEventNamingRule.validPatterns`.

### Coroutine scope and dispatchers

- `core/core/.../coroutine/scope/AppCoroutineScope.kt` wraps a `CoroutineScope`,
  a `defaultDispatcher` (work), and an `immediateDispatcher` (delivery). Both `BaseStore` and
  `HandlerStore` expose `launch { ... }` helpers built on top of it that automatically catch
  exceptions, invoke `onError`, and switch to the immediate dispatcher for `onSuccess` / per-flow
  emissions.
- `StoreDispatchers` (`core/ui/mvi/.../di/StoreDispatchers.kt`) injects `@DefaultDispatcher`
  and `@MainImmediateDispatcher`, both contributed by
  `core/core/src/androidMain/.../di/DispatchersBindingContainer.kt`.

### Localization

Workeeper supports two locales out of the box: **English** (default) and **Russian**.
English is the default for international audience and contributors on GitHub; Russian is
overlaid via Android's resource qualifier system for users with `system locale = ru`.

Resource layout per module:

```
<module>/src/main/res/values/strings.xml        — English (default fallback)
<module>/src/main/res/values-ru/strings.xml     — Russian overlay
```

Every user-facing string is extracted to `strings.xml` from the start. Compose code reads
strings via `stringResource(R.string.xxx)`, never as Kotlin literals. This applies to:

- Screen titles, button labels, list headers, empty state copy.
- Error messages and snackbar text.
- Field labels and placeholders.
- Date/time format strings (use `androidx.compose.ui.text.intl.Locale.current` if format
  varies by language).

Locale-sensitive value shaping (durations, relative time, decimal text, joined labels) is
also localization work. It must happen before rendering (handler/mapper/state), not inside
Composables.

It does **not** apply to:

- Internal log messages and analytics event names — these stay English-only.
- Domain identifiers (entity types, set types, action names in MVI) — these stay English
  in code, translated on display.

#### Naming convention

```
feature_<feature>_<context>_<purpose>
```

Examples:

```xml
<string name="feature_settings_title">Settings</string>
<string name="feature_settings_section_about">About</string>
<string name="feature_settings_section_appearance">Appearance</string>
<string name="feature_archive_segment_exercises">Exercises</string>
<string name="feature_archive_action_restore">Restore</string>
<string name="feature_archive_action_permanent_delete">Delete permanently</string>
<string name="feature_archive_dialog_permanent_delete_title">Delete '%1$s' permanently?</string>
<string name="feature_archive_dialog_permanent_delete_body_with_history">
    This will permanently delete the %1$s along with %2$d sessions of history. This cannot be undone.
</string>
```

Strings shared across features (e.g. "Cancel", "Save", "Back") live in the relevant `core/`
module — typically `core/ui/kit` for UI verbs:

```xml
<string name="core_ui_kit_action_cancel">Cancel</string>
<string name="core_ui_kit_action_save">Save</string>
<string name="core_ui_kit_action_back">Back</string>
```

#### Pluralization

Use `<plurals>` resources for any number-driven text ("1 session" vs "5 sessions" vs Russian
forms "1 сессия" / "2 сессии" / "5 сессий"). Read with `pluralStringResource(R.plurals.xxx, count, count)`.

Example:

```xml
<plurals name="feature_archive_session_count">
    <item quantity="one">%d session</item>
    <item quantity="other">%d sessions</item>
</plurals>
```

Russian needs the `few` quantity for 2-4:

```xml
<plurals name="feature_archive_session_count">
    <item quantity="one">%d сессия</item>
    <item quantity="few">%d сессии</item>
    <item quantity="many">%d сессий</item>
    <item quantity="other">%d сессии</item>
</plurals>
```

#### Forbidden patterns

- Hardcoded user-facing string literals in Composables.
- Concatenation of localized fragments — always use full sentences as resources, with
  format placeholders for variable parts. (`"$name was archived"` is forbidden;
  `getString(R.string.archived_format, name)` is correct.)
- Manual locale switching in code — let Android resolve from system locale.
- Value-to-text shaping in Composables (date/time/number formatting, joined message lists,
  summary string construction). Perform it in handler/mapper/state and pass preformatted text
  to UI.

#### Adding a new feature

When creating a new feature module:

1. Create `src/main/res/values/strings.xml` with all English strings.
2. Create `src/main/res/values-ru/strings.xml` with the Russian translations.
3. Both files must contain the same set of keys — adding a key to one without the other
   means the missing locale falls back to English (which is acceptable but visible).
4. Reference all strings via `stringResource(R.string.xxx)` from Composables and
   `context.getString(R.string.xxx)` from non-Compose code.

## Compose UI conventions

Composable functions follow strict conventions to keep recompositions predictable, state
ownership clear, and components reusable across features.

### Stateless components

Composables that render UI are **stateless** by default. They receive their data via
parameters and emit events via callbacks. They do not own their data, do not call
business logic, and do not hold mutable state about what they display.

```kotlin
@Composable
fun ColumnScope.PlanEditorBody(
    draft: ImmutableList<PlanSetUiModel>,
    isWeighted: Boolean,
    onAction: (PlanEditorBodyAction) -> Unit,
    setTypeTooltipText: String? = null,
)
```

The state lives in the parent's `Store.State`. Each user input flows through the standard
MVI cycle: UI emits Action → Handler updates State → State propagates back to UI. The
component does not call `remember { mutableStateOf(...) }` for the data it displays.

**The single allowed exception** is ephemeral local UI state with no persistence semantics:
focus state, transient animation values, scroll position when not part of restored state.
For these, `remember` / `rememberSaveable` is fine. But **never** for domain data like form
inputs, drafts, dirty flags, or pending changes.

When tempted to put data state in a Composable, ask: would another part of the app care
about this value? If yes — it belongs in Store. If the answer is uncertain, default to
Store.

### Why stateless

Three concrete consequences:

1. **Discard and confirmation flows live in one place.** If a sheet has its own draft
   state, it needs its own discard dialog. The parent screen also has its own discard
   handling for surrounding edits. Two dialogs, unsynchronized, different UX. With
   stateless components, the parent owns all draft state, all dirty detection, and the
   single discard dialog flow per [Back gesture handling](#back-gesture-handling).

2. **State changes from elsewhere are reflected.** If a background coroutine updates the
   plan (e.g. live workout finishes and rewrites the plan), a stateless editor will
   re-render with the new value. A stateful editor will keep showing its own draft and
   silently overwrite the update on save.

3. **Components are testable in isolation.** Stateless components are pure functions of
   their input — write a `@Preview` per state, snapshot-test them, no DI required.

### `@Stable` and `@Immutable`

Every data class passed to a Composable is annotated `@Stable` or `@Immutable`. This
allows Compose to skip recompositions when the value is unchanged (referential equality
on stable types).

```kotlin
@Stable
data class PlanEditorTarget(
    val exerciseUuid: String,
    val exerciseName: String,
    val exerciseType: ExerciseTypeUiModel,
    val initialPlan: ImmutableList<PlanSetUiModel>,
    val draft: ImmutableList<PlanSetUiModel>,
)
```

`@Immutable` is the stronger contract — all properties are val and themselves immutable.
`@Stable` is weaker — properties may change but reads of the same instance are
consistent. For Store.State implementations, `@Stable` is the convention. For pure value
types (no mutable state, all properties val), `@Immutable` is preferred.

Enums are stable by default (Compose treats them as `@Stable` automatically). Sealed
interfaces and their data variants need explicit `@Stable` annotations on each variant.

### UI types vs domain types

Composables consume **UI types**, not domain types. Domain types like `ExerciseDataModel`,
`TrainingDataModel`, `PlanSetDataModel` live in `core/<feature>/` and represent the
canonical data model. They are the contract between repository and use case.

UI types are tailored to what the UI needs to render: kit-local enums, derived display
strings, no business identifiers unless required for actions. They live in the module
that owns the corresponding Composable.

```kotlin
// In core/ui/plan-editor/.../model/PlanSetUiModel.kt — module-local UI type
@Stable
data class PlanSetUiModel(
    val weight: Double?,
    val reps: Int,
    val type: SetTypeUiModel,
)

enum class SetTypeUiModel { WARMUP, WORK, FAILURE, DROP }
```

The mapper `DomainType <-> UiModel` lives in the module that owns the UiModel:

```kotlin
// In core/ui/plan-editor/.../mappers/PlanEditorMapper.kt
fun PlanSetDataModel.toUi(): PlanSetUiModel = ...
fun PlanSetUiModel.toData(): PlanSetDataModel = ...
fun List<PlanSetDataModel>.toUi(): ImmutableList<PlanSetUiModel> = ...
```

**Mapping is a boundary operation.** Domain types appear only at:

- Load boundary — interactor returns domain, handler maps to UI.
- Persist boundary — handler maps UI to domain before calling repository.

**Inside MVI** — Store.State, Action variants, Handler logic — only UI types flow.
This includes feature-level state, not just kit-level.

Mapping is **boundary-only**, not maintenance scaffolding. If a domain field is no longer
relevant to the UI, remove it from the domain model — don't keep it alive by mapping it
to a UI field nobody reads. Stale domain fields propagate through every feature that
touches them. See [DataModel hygiene](#datamodel-hygiene).

Why this matters:

1. **Layer ordering stays correct.** A Composable in `core/ui/kit` that imports
   `PlanSetDataModel` from `core/exercise` couples the kit to that domain. The kit can no
   longer be reused if the exercise domain changes shape, and architectural ordering is
   inverted (lower layer depending on higher layer).
2. **UI types can be tailored.** Pre-formatted display strings, computed flags, sorted
   collections — all things the UI needs but the domain doesn't care about.
   Locale-sensitive text (durations, relative timestamps, decimal rendering) belongs here as
   preformatted UI fields, not inside Composable functions.
3. **Domain refactors don't break UI tests.** When `PlanSetDataModel` gains a field, only
   the mapper changes; the Composable and its previews are unaffected.

This applies to every `@Composable` parameter in the codebase — at no point should a
domain `*DataModel` cross into a kit component or remain in a handler's State after the
load boundary.

### Pre-formatted UI fields

Display text — durations, relative timestamps, decimal weight rendering, joined
label lists, summary strings — is computed in the handler/mapper layer and stored
on State as a ready-to-render String. Composables render the string; they do not
shape it.

```kotlin
// CORRECT — state carries pre-formatted text
@Stable
data class State(
    val startedAt: Long,
    val nowMillis: Long,
    val elapsedDurationLabel: String,         // "12:34", computed in CommonHandler.TimerTick
    val tagsLabel: String,                    // "Push · Upper body", computed in mapper
    val planSummaryLabel: String,             // "100×5 · 100×5 · 102.5×5", computed in mapper
)

@Composable
fun WorkoutHeader(state: State) {
    Text(state.elapsedDurationLabel)         // just renders
}

// WRONG — Composable derives display text every recomposition
@Composable
fun WorkoutHeader(startedAt: Long, nowMillis: Long) {
    val label = formatElapsedDuration(nowMillis - startedAt)   // recomputed on every recompose
    Text(label)
}
```

Why:

1. **Recomposition cost.** Formatting is non-trivial — locale lookups, allocations,
   number-to-string conversion. Pulling it out of the render path is free perf.
2. **Locale-correctness.** Locale-sensitive shaping (durations, decimal separators,
   relative time, plurals) must run before Compose render so the result respects
   the user's locale at the moment of shaping. Doing it in a Composable risks
   stale locale snapshots and bypasses our localization layer.
3. **Testability.** A mapper that produces "12:34" from `(startedAt, now)` is
   trivially unit-testable. A Composable that does the same is testable only
   through screenshot tests or instrumentation.
4. **Stateless components stay stateless.** A Composable that derives display
   text is implicitly stateful in time (`now` changes). Pulling the derivation
   into the State machine restores statelessness.

The shaping helper itself lives wherever it's most reusable — `core/core/.../time/`
for time/duration formatters, feature-local mappers for feature-specific
summaries. It's invoked by handlers (e.g. `CommonHandler.TimerTick` updates
`elapsedDurationLabel` once per second) or by mappers (e.g. when
`PerformedExerciseSnapshot` is mapped to `LiveExerciseUiModel`).

When debt accumulates (Composables still doing shaping), tracked in
[documentation/tech-debt.md](tech-debt.md) "UI Mapping Boundary Debt".

### Source-of-truth merging belongs to mappers, not Composables

A Composable that merges multiple state sources to decide what to render is doing
state derivation, not rendering. That work belongs in the MVI mapper / state-derivation
layer. Composables receive an already-prepared list/value and emit actions.

Concretely: if your render function reaches across `performedSets`, `planSets`,
`setDrafts`, and a fallback row to compute "what to show at position N", you have a
state-derivation function with a `@Composable` annotation on it. It will produce silent
data-loss bugs the moment any one of those sources is updated independently — the
Composable's local merge rule and the handler's draft-seed rule have to stay in sync,
and Compose offers no help with that.

Bad — composable merges sources:

```kotlin
@Composable
private fun ExerciseCardBody(
    exercise: LiveExerciseUiModel,
    drafts: ImmutableMap<State.DraftKey, LiveSetUiModel>,
    /* ... */
) {
    val rows = buildSetRowList(exercise, drafts)        // merges performed/plan/draft/fallback
    rows.forEach { LiveSetRow(it, /* ... */) }
}
```

Two further smells follow from this shape:

- A reusable UI component imports `Store.State.DraftKey` (or any other store-internal
  shape). UI components must not know how the state is keyed inside the store.
- TextField local state (`AppNumberInput`, `OutlinedTextField`) hides broken store state
  from the user — the field keeps its typed value while the store mirror is wrong.
  Controls that read state directly (chips, toggles, summaries) reveal the bug
  immediately. Don't lean on TextField's local cache as a correctness signal.

Good — mapper builds a flat list, composable renders it:

```kotlin
@Stable
data class LiveExerciseUiModel(
    /* ... */
    val visibleSets: ImmutableList<LiveSetUiModel>,     // derived in mapper
)

@Composable
private fun ExerciseCardBody(exercise: LiveExerciseUiModel, /* ... */) {
    exercise.visibleSets.forEach { row ->
        key(exercise.performedExerciseUuid, row.position) {
            LiveSetRow(row, /* ... */)
        }
    }
}
```

The visible-row priority for the live-workout exercise card is **performed > draft >
plan > fallback**, and the resolver lives in the feature's mapper layer
(`feature/live-workout/.../mvi/mapper/`). Any mutation that touches one of those four
sources runs the resolver as part of the same state transition — `init` mapping,
draft updates from `InputHandler` / `ClickHandler`, mark-done / uncheck, add set,
reset / skip, plan-editor save reload. Composable bodies never recompute the list.

### Draft seed/update invariant

Editable state that has multiple upstream sources (a plan template, a performed value,
and a user-edited draft) needs one canonical place to decide where the seed comes from
when a draft is first created, and what fields the next edit preserves. Spreading this
across handlers — one branch in `InputHandler.OnSetWeightChange`, another in
`ClickHandler.OnSetTypeSelect` — is how reset bugs sneak in: the type-chip path picks
up an empty seed while the weight-input path picks up the plan, and the two paths
disagree about what the row "is".

The rule, as applied in `feature/live-workout`:

```
draft update = current visible row seed + changed field
```

Concretely:

- The seed lookup priority is **performed > draft > plan > fallback** — the same
  priority the visible-row resolver uses, so the chip click reads the row the user
  sees.
- A draft update keeps every field of the seed and overwrites only the field the user
  changed. Type chip click preserves weight + reps. Weight input preserves type +
  reps. Reps input preserves type + weight.
- Both `lookupSetDraftSeed(performedExerciseUuid, position)` and
  `updateSetDraft(..., transform)` live in **one** helper file in the MVI handler
  layer (e.g. `mvi/handler/LiveWorkoutDraftExt.kt`), and every handler that mutates
  drafts goes through it. No handler reaches into `setDrafts` directly to decide the
  seed.

When refactoring code that already exhibits this debt — multiple seed lookups, in-UI
merging, draft fields silently resetting — write characterization tests against the
current behavior **before** moving the logic. The bug class is exactly the kind that
re-grows after a clean refactor, and a green test suite that exercises every
field-preservation pair (type↔weight, type↔reps, weight↔reps, type→type-after-edit)
is the only durable defence.

### Specialized UI modules — `core/ui/<specialized>`

Three architectural layers exist for UI code:

1. **`core/ui/kit`** — pure Compose primitives (buttons, dialogs, sheets, list rows,
   theme tokens, text fields). Domain-agnostic. Reusable across any product.
   No `*DataModel` imports allowed.
2. **`core/ui/<specialized>`** — domain-aware UI bridges. Sit one rung above the kit.
   Allowed to import from `core/database` and `core/<feature>` for domain types.
   Define their own UI types and mappers. Provide Composables consumed by features.
   Examples: `core/ui/plan-editor` (the plan editor sheet + its UI types and mappers),
   `core/ui/exercise-picker` (hypothetical), `core/ui/calendar-widget` (hypothetical).
3. **`feature/<name>/ui/`** — feature-specific Composables that compose the kit and
   specialized modules into a screen. Define feature-local UI state (Store.State)
   and orchestrate the MVI cycle.

Promote a Composable from feature to a specialized module when:

- More than one feature consumes it (or will consume it within v1+v2 horizon).
- It owns its own non-trivial UI types and mappers.
- It can be specified independently of any single feature's lifecycle.

Do **not** put it in `core/ui/kit` if it has any domain coupling. The kit boundary is
strict — domain-agnostic. Specialized modules are the right slot for things that are
"reusable but domain-aware".

### Body-action mapping pattern for reusable Composables

When a reusable Composable (typically in `core/ui/<specialized>`) emits a non-trivial
action surface, define a body-local sealed action interface inside the same module and
have the parent screen map each variant to its store's `Action`. This keeps the body
free of `Store` plumbing and previewable in isolation.

```kotlin
// Inside the specialized module — emitted by the reusable body:
@Stable
sealed interface PlanEditorBodyAction {
    @Stable data class OnSetWeightChange(val index: Int, val value: Double?) : PlanEditorBodyAction
    @Stable data class OnSetRepsChange(val index: Int, val value: Int) : PlanEditorBodyAction
    @Stable data class OnSetTypeChange(val index: Int, val value: SetTypeUiModel) : PlanEditorBodyAction
    @Stable data class OnSetRemove(val index: Int) : PlanEditorBodyAction
    @Stable data object OnAddSet : PlanEditorBodyAction
    @Stable object OnDismiss : PlanEditorBodyAction
    @Stable object OnSave : PlanEditorBodyAction
}

// In the parent screen — `PlanEditorScreen.kt`:
PlanEditorBody(
    draft = state.draft,
    isWeighted = state.isWeighted,
    onAction = { action -> consume(action.toStoreAction()) },
)

private fun PlanEditorBodyAction.toStoreAction(): Action = when (this) {
    PlanEditorBodyAction.OnAddSet           -> Action.Click.OnAddSet
    PlanEditorBodyAction.OnDismiss          -> Action.Click.OnBackClick
    PlanEditorBodyAction.OnSave             -> Action.Click.OnSave
    is PlanEditorBodyAction.OnSetRemove     -> Action.Click.OnSetRemove(index)
    is PlanEditorBodyAction.OnSetRepsChange -> Action.Input.OnSetRepsChange(index, value)
    is PlanEditorBodyAction.OnSetTypeChange -> Action.Click.OnSetTypeChange(index, value)
    is PlanEditorBodyAction.OnSetWeightChange -> Action.Input.OnSetWeightChange(index, value)
}
```

Why this pattern:

1. **Body stays previewable.** `PlanEditorBody` does not import any `Store` types — it
   only knows about its own `PlanEditorBodyAction`, so `@Preview` instantiates it with a
   trivial `onAction = {}` callback.
2. **Mapping is co-located with the screen.** A single private `toStoreAction()` extension
   lives next to the screen composable, which keeps the routing surface obvious to the
   reader.
3. **The body is replaceable.** Swapping `PlanEditorBody` for a different editor body
   only changes the local mapping function, not the store contract.

For action surfaces that span feature modules (where the body lives in a *feature* module
that already owns its own store contract), the mapping is unnecessary — emit the store's
own `Action.Click.*` variants directly.

#### Shared draft transformer (`PlanDraftReducer`)

The full-screen `PlanEditor` and the inline plan editor used by the exercise
create-flow (see below) both mutate an `ImmutableList<PlanSetUiModel>` in response to
`PlanEditorBodyAction`. To prevent drift between the two paths, set-list semantics live
in a single pure transformer in `core/ui/plan-editor/.../domain/PlanDraftReducer.kt`:

```kotlin
object PlanDraftReducer {
    fun reduce(
        draft: ImmutableList<PlanSetUiModel>,
        action: PlanEditorBodyAction,
        isWeighted: Boolean,
    ): ImmutableList<PlanSetUiModel>
}
```

The reducer is pure (no coroutines, no IO, no resource access). Both
`feature/plan-editor`'s `ClickHandler` / `InputHandler` and `feature/exercise`'s
`ClickHandler.processAdhocPlanEditorAction` delegate to it, so adding new
`PlanEditorBodyAction` variants only requires updating one place.

#### Inline `PlanEditorBody` exception (exercise create-flow)

The default rule is "plan editing always opens the full-screen `Screen.PlanEditor`
route". The exercise create-flow (`State.create(uuid = null)`) is a deliberate
exception: a brand-new exercise has no UUID yet, and `Screen.PlanEditor` keys off
`exercise_table.last_adhoc_sets` which requires the row to already exist.

`feature/exercise/ui/ExerciseEditScreen.kt` therefore renders `PlanEditorBody(
scrollable = false, ...)` inline inside the form when
`(state.mode as? Mode.Edit)?.isCreate == true`. Body actions are wrapped in
`Action.Click.OnAdhocPlanEditorAction(action)` and the handler updates the in-memory
`state.adhocPlan`; persistence happens on the existing Save path via
`ExerciseChangeDomain.lastAdhocSets`. Read-mode and edit-mode-on-existing keep the
full-screen route via `Action.Click.OnEditPlanClick → Action.Navigation.OpenPlanEditor`.

Process-death recovery for the inline draft is a known limitation — see
`tech-debt.md` for the planned DB-draft follow-up.

### Collections in UI parameters

Always use `kotlinx.collections.immutable.ImmutableList` /
`ImmutableSet` / `ImmutableMap` (or their `Persistent*` variants) when passing
collections into Composables — never `List` / `Set` / `Map`.

```kotlin
// WRONG — kotlin.collections.List is not @Stable
@Composable
fun ExerciseListScreen(exercises: List<ExerciseDataModel>)

// CORRECT — ImmutableList is @Stable
@Composable
fun ExerciseListScreen(exercises: ImmutableList<ExerciseDataModel>)
```

Why: `kotlin.collections.List` is an interface with no `@Stable` annotation. Compose
treats it as unstable, which means every recomposition compares by reference (not
content), and any change anywhere in the parent forces this Composable to recompose even
when its data is unchanged. `ImmutableList` is annotated stable; Compose skips
recompositions when the reference and content are unchanged.

The dependency `org.jetbrains.kotlinx:kotlinx-collections-immutable` is already in the
project. Convert to `ImmutableList` at the boundary where data leaves the data layer
and enters MVI/UI flow.

### Modifier stability

Composables that conditionally pick between `Modifier` chains based on a state value
force recomposition every time. `Modifier.then(...)` produces a new instance whose
`equals()` is reference-based; switching between two structurally different chains
defeats Compose's skip-if-same heuristic.

Wrong — modifier graph changes on state flip:

```kotlin
val rowModifier = if (set.isPersonalRecord) {
    baseModifier.personalRecordAccent()
} else {
    baseModifier
}
Row(modifier = rowModifier) { /* ... */ }
```

Right — modifier graph is stable, parameter inside it changes:

```kotlin
val accentColor by animateColorAsState(
    targetValue = if (set.isPersonalRecord) AppUi.colors.record.border else Color.Transparent,
    label = "pr-accent",
)
Row(modifier = baseModifier.personalRecordAccent(color = accentColor)) { /* ... */ }
```

Same rule for clickable: prefer `clickable(enabled = ...)` over conditionally adding
or removing the modifier.

```kotlin
// Wrong
val mod = if (editable) Modifier.clickable { onTypeChange(...) } else Modifier

// Right
Box(modifier = Modifier.clickable(enabled = editable) { onTypeChange(...) }) { /* ... */ }
```

The exception is constructor-time branching that never flips (e.g. screen-layout choice
based on a `@Composable` parameter that's fixed for the instance) — there's no
recomposition-skip benefit to defend in that case.

### TextField inputs and recomposition

`OutlinedTextField` and `TextField` re-derive their internal state from the `value`
parameter on each recomposition. If the parent State updates frequently and the
TextField's `value` is computed from State (e.g. `state.draft[index].weight.toString()`),
the new String instance on each recomposition can cause focus or selection state to
reset, which dismisses the keyboard.

To keep the keyboard open and cursor stable across user typing:

- Pass `value` as the canonical String from State, **not** a recomputed-on-every-render
  expression. If the State already holds a String form, use it directly.
- Use `key = stableKey` on the parent layout when the TextField is inside a list, so
  Compose can match the same TextField identity across recompositions.
- Never call `softwareKeyboardController.hide()` from input handlers — the keyboard
  should stay visible until the user taps elsewhere or submits.

For lists of TextField rows (e.g. plan editor sets), each row needs a stable key so its
TextField identity is preserved when adjacent rows are added, removed, or reordered.

### Composable previews

Every public or internal `@Composable` function has at least one `@Preview` next to it.

- Public/internal Composables in `feature/*`, `core/ui/kit`, and `core/ui/<specialized>`
  modules MUST have previews.
- Private `@Composable` helpers do not require previews.
- Previews use `AppTheme` with both `ThemeMode.LIGHT` and `ThemeMode.DARK` — either two
  preview functions or one with `PreviewParameter`.
- Previews use realistic stub data, not Lorem Ipsum.
- Composables with multiple visually-distinct states (loading, empty, error, populated,
  dirty form, selection mode, weighted vs weightless, etc.) get one `@Preview` per state.

Previews are validated as part of code review — a Composable without a preview is
incomplete. Reviewers should ask "where's the preview?" before approving.

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
- Metro `1.3.2` — the sole DI framework, applied repo-wide as a Kotlin compiler plugin.
  No Hilt / Dagger artifact is in the catalog.
- Room 3 (`androidx.room3`) `3.0.0` with paging support.
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
  carry `@SingleIn(AppScope::class)`. Classes whose name contains `Handler`, `Interactor`, or
  `Mapper` must carry `@SingleIn(<Feature>Scope::class)` (a `*Handler` must not be
  `@SingleIn(AppScope)`); `*Store` classes are unscoped (class-level `@Inject`, retained by the
  `ViewModelStore` via `rememberMetroStoreProcessor`).
- Composables ending in `Screen` must have both a `*State` parameter and an `Action`/`Event`
  handler parameter.

General Kotlin/Android conventions:

- Kotlin official style; 4-space indentation; no tabs.
- Classes/objects: UpperCamelCase. Functions/properties: lowerCamelCase. Constants:
  UPPER_SNAKE_CASE.
- Packages: lowercase, dot-separated; respect the `core/*`, `feature/*`, `app/*` layout.
- Android resources: snake_case (e.g. `ic_bottom_app_bar_chart_icon_24`,
  `bottom_bar_label_home`).
- Compose previews: keep them in the same file, suffixed `Preview`.
