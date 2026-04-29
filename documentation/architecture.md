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
point. Current feature modules live under `feature/`, including `feature/home`,
`feature/all-trainings`, `feature/all-exercises`, `feature/single-training`,
`feature/exercise`, `feature/live-workout`, `feature/past-session`, and
`feature/settings`. Feature contents are detailed in [features.md](features.md); their
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
helpers. The feature owns a `<Name>HandlerStoreImpl` annotated `@ViewModelScoped` that extends
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
  happened (`*Success`, `*Error`, `*Completed`, `Show*`, `Haptic*`, `Snackbar*`, `Scroll*`).
  **Events are for UI-side effects only** — haptic feedback, snackbar display, external Intent
  dispatch, scroll commands. **Navigation is never an Event.** Navigation flows through
  `Action.Navigation` consumed by the feature's `NavigationHandler` (see [Navigation
  flow](#navigation-flow) below).
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
while the simpler `feature/all-trainings` and `feature/all-exercises` keep it directly under
`mvi/`. Both layouts work with the linting rules; pick the one that already exists when adding
to an existing feature.

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
  via Hilt's `@AssistedFactory`.
- `store/CommonDataStore.kt` is the application-wide preferences interface; bound in
  `di/CoreDataStoreModule.kt`.

## Navigation

### Routes and the navigator

- `core/ui/navigation/.../Screen.kt` defines routes as a `@Serializable sealed interface`. The
  three bottom-bar destinations are nested under `Screen.BottomBar` (`Home`, `AllExercises`,
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

### Navigation flow (canonical pattern)

Navigation is **always** routed through a feature's `NavigationHandler`, never through the
graph composable directly. This keeps UI dumb (it knows nothing about routes or `Navigator`)
and lets navigation be tested in isolation. The pattern:

1. UI emits an `Action.Navigation.<Something>` via `processor.consume(...)`.
2. The store's `handlerCreator` lambda routes that action to the feature's `NavigationHandler`.
3. `NavigationHandler` has `Navigator` injected via Hilt DI and calls `navigator.navTo(...)` or
   `navigator.popBack()`.

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
internal class NavigationHandler @Inject constructor(
    private val navigator: Navigator,
) : <Feature>Component(), Handler<Action.Navigation> {
    override fun invoke(action: Action.Navigation) {
        when (action) {
            is Action.Navigation.Back -> navigator.popBack()
            is Action.Navigation.OpenArchive -> navigator.navTo(Screen.Archive)
        }
    }
}
```

The graph composable consumes only **UI-side events** through `processor.Handle { event -> ... }`:

- `Event.Haptic` — translated to `LocalHapticFeedback.current.performHapticFeedback(...)`.
- `Event.ShowExternalLink(url)` — translated to an `Intent.ACTION_VIEW` against `LocalContext`.
- `Event.Snackbar*` — emitted to the host snackbar manager.
- `Event.Scroll*` — translated to a `LazyListState` scroll command in scope.

The graph composable **never** reads `LocalNavigator` and **never** consumes an
`Event.Navigate*` (such an event must not exist — it would be misnamed). `LocalNavigator`
exists in `core/ui/navigation/Navigator.kt` so the root `App.kt` can provide a single
`Navigator` instance into the composition tree, but the canonical read site is
`NavigationHandler` via Hilt — not graph composables.

Reference implementation: `feature/all-trainings/ui/AllTrainingsGraph.kt` (graph) and
`feature/all-trainings/mvi/handler/NavigationHandler.kt` (handler).

#### Dispatching navigation from background coroutines

`NavigationHandler.invoke` calls into `Navigator` which touches `NavController` —
that work must happen on the main thread. When a click handler emits a navigation
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

// WRONG — raw consume from background dispatcher will crash NavController
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

When `interceptBack` is false: the gesture goes natively through `NavController`,
predictive preview animation runs, no store involvement.

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
  This breaks predictive back preview even in Read mode where there's nothing to
  intercept.
- `BackHandler(enabled = state.mode is Mode.Edit) { ... }` (gated only by mode, not
  by dirty status). This intercepts every back in Edit mode even when there's
  nothing to lose, again breaking predictive back unnecessarily.
- Top-bar arrow that calls `navigator.popBack()` directly. This bypasses the store
  and creates inconsistency: hardware back goes through ClickHandler with discard
  dialog, but top-bar arrow skips it.
- Three different click actions for three triggers (`OnBackGesture`, `OnTopBarBack`,
  `OnCancelClick`). Use one (`OnBackClick`) routed identically.

### Navigation host and shared element transitions

`app/app/src/main/java/io/github/stslex/workeeper/host/AppNavigationHost.kt` wraps the
`NavHost` in a `SharedTransitionLayout` (Jetpack Compose
`ExperimentalSharedTransitionApi`). The single shared `SharedTransitionScope` is passed to each
feature's `<Name>Graph` extension function (`homeGraph`, `allTrainingsGraph`,
`allExercisesGraph`, `singleTrainingsGraph`, `exerciseGraph`), so transitions can be wired
across the whole graph from a single root scope. The start destination is
`Screen.BottomBar.Home`. Each graph is added via `navComponentScreen<Feature>` which expands
to a `composable<Screen>` block under the hood (see
`core/ui/navigation/.../Screen.kt::navScreen`).

`NavHostControllerHolder` (`app/app/.../host/NavHostControllerHolder.kt`) tracks which
`BottomBar` screen is current so `App.kt` can show or hide the `WorkeeperBottomAppBar` with an
animated visibility transition.

### Bottom navigation

`app/app/.../bottom_app_bar/BottomBarItem.kt` declares three tab entries — `HOME`,
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
fun AppPlanEditor(
    exerciseName: String,
    draft: ImmutableList<PlanSetUiModel>,
    isWeighted: Boolean,
    onAction: (AppPlanEditorAction) -> Unit,
    modifier: Modifier = Modifier,
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

### Action wrapper pattern for reusable Composables

When a reusable Composable (typically in `core/ui/<specialized>`) emits a non-trivial
action surface, wrap it in a single Action variant in the consuming feature's Store
contract rather than expanding it into many Click variants.

```kotlin
// Inside the specialized module:
@Stable
sealed interface AppPlanEditorAction {
    @Stable data class OnSetWeightChange(val index: Int, val value: Double?) : AppPlanEditorAction
    @Stable data class OnSetRepsChange(val index: Int, val value: Int) : AppPlanEditorAction
    @Stable data class OnSetTypeChange(val index: Int, val value: SetTypeUiModel) : AppPlanEditorAction
    @Stable data class OnSetRemove(val index: Int) : AppPlanEditorAction
    @Stable object OnAddSet : AppPlanEditorAction
    @Stable object OnDismiss : AppPlanEditorAction
    @Stable object OnSave : AppPlanEditorAction
}

// In the feature's Store contract:
sealed interface Action : Store.Action {
    sealed interface Click : Action { /* feature-local clicks */ }
    sealed interface Navigation : Action { /* navigation */ }
    data class PlanEditAction(val action: AppPlanEditorAction) : Action
}
```

The store's `handlerCreator` routes the wrapper to a dedicated handler:

```kotlin
when (action) {
    is Action.Navigation     -> navigationHandler
    is Action.Click          -> clickHandler
    is Action.PlanEditAction -> planEditActionHandler
}
```

The graph forwards the editor's actions verbatim:

```kotlin
state.planEditorTarget?.let { target ->
    AppPlanEditor(
        exerciseName = target.exerciseName,
        draft = target.draft,
        isWeighted = target.isWeighted,
        onAction = { action -> processor.consume(Action.PlanEditAction(action)) },
    )
}
```

Why wrap rather than expand:

1. **Action surface stays flat.** Six per-action `Click` variants would clutter the
   feature's contract with concerns that belong to the editor module.
2. **No translation layer in the graph.** Without a wrapper, the graph would need a
   `toStoreAction()` mapping function. With a wrapper, it's a single line.
3. **The editor is replaceable.** Swapping `AppPlanEditor` for a different editor
   surface only changes the wrapper's payload type, not the surrounding Store contract.
4. **Handler stays focused.** `PlanEditActionHandler` reads only `AppPlanEditorAction`
   variants and operates only on plan-editor UI types. It doesn't know about the
   feature's other Click actions.

Apply this pattern when a specialized Composable emits more than 3-4 action variants.
For 1-2 callback Composables (e.g. a single `onClick`), individual Click variants in the
feature contract are fine.

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
  `bottom_bar_label_home`).
- Compose previews: keep them in the same file, suffixed `Preview`.
