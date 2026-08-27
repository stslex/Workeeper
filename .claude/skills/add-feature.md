---
name: add-feature
description: Scaffold a new `feature/<name>` Gradle module that follows the project's MVI + Metro + Compose conventions — the feature-scope token, the contributed `@GraphExtension` and its factory, the `Feature` / `FeatureAssisted` composition seam, Store contract, handlers, the lifecycle-safe navigation command bus (Action.Navigation + a NavigationHandler injecting `Navigator`, routed through the app-scoped NavigatorEventBus), the design system (`core/ui/kit` components and `AppUi.*` tokens), the navigation entry, and the test stubs.
---

# Add a feature module

## When to use

- "Add a new feature module"
- "Create a feature for X"
- "Scaffold a new screen with MVI"
- "I need a new Compose feature module"

## Prerequisites

- The feature has a name in kebab-case (e.g. `workout-history`).
- The corresponding `Screen` route does not yet exist in
  `core/ui/navigation/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/navigation/Screen.kt`.
- These docs are the source of truth for everything below — read them before scaffolding:
  - [documentation/architecture.md](../../documentation/architecture.md) — module map, MVI
    contract, [Dependency injection (Metro)](../../documentation/architecture.md#dependency-injection-metro),
    [Navigation](../../documentation/architecture.md#navigation).
  - [documentation/design-system.md](../../documentation/design-system.md) — token catalog
    and the shared `core/ui/kit` components.
  - [documentation/lint-rules.md](../../documentation/lint-rules.md) — the State / Action /
    Event / Handler / Composable / Metro-scope rules that gate a new module.

DI is 100% Metro (`dev.zacsweers.metro`). There is no Hilt anywhere in the tree: no
`@HiltViewModel`, `@InstallIn`, `@ViewModelScoped`, `@AndroidEntryPoint`, `@Module`, and no
`javax.inject.Inject` in feature code. If a snippet you are copying from an old branch has
any of those, it is stale.

## Reference implementations

- **No route args (`Feature`):** `feature/archive/` — the template every other feature graph
  follows, and the most heavily commented. Also `feature/home/`, `feature/all-trainings/`,
  `feature/all-exercises/`, and `feature/settings/` (the widest graph in the repo — read it
  when the new feature has many collaborators).
- **Route args (`FeatureAssisted`):** `feature/exercise/`, `feature/single-training/`,
  `feature/live-workout/`, `feature/past-session/`, `feature/image-viewer/`,
  `feature/exercise-chart/`, `feature/plan-editor/`. The route arg is a `@Provides` bound
  instance on the extension factory — there is **no** assisted injection on any Store.

Per-file:

- Scope token: `feature/archive/.../di/ArchiveScope.kt`.
- Graph extension: `feature/archive/.../di/ArchiveGraph.kt`,
  `feature/settings/.../di/SettingsGraph.kt` (3 `@Binds`),
  `feature/exercise/.../di/ExerciseGraph.kt` (route arg on the factory).
- Composition seam: `feature/archive/.../di/ArchiveFeature.kt` (plain),
  `feature/exercise/.../di/ExerciseFeature.kt` (assisted).
- HandlerStore: `feature/archive/.../di/ArchiveHandlerStore.kt` + `ArchiveHandlerStoreImpl.kt`.
- StoreImpl: `feature/archive/.../mvi/store/ArchiveStoreImpl.kt` (plain),
  `feature/exercise/.../ui/mvi/store/ExerciseStoreImpl.kt` (route arg as a ctor param).
- NavigationHandler: `feature/archive/.../mvi/handler/ArchiveNavigationHandler.kt`.
- Nav graph composable: `feature/archive/.../ui/ArchiveGraph.kt`.
- Identity test: `app/app/src/test/kotlin/.../di/ArchiveExtensionIdentityTest.kt`.

Older features (`feature/exercise`, `feature/single-training`) keep the MVI under
`ui/mvi/`; newer ones keep it under `mvi/`. Both layouts compile; mirror the existing
shape when extending an existing module.

## DI shape and scoping (get this right first)

A feature owns five DI files under `feature/<name>/.../di/`: the scope token, the graph
extension, the HandlerStore interface, its `Impl`, and the `<Name>Feature` composition seam.
The graph extension is *contributed*
to `AppScope`, so it inherits every app-scoped binding — nothing is hand-threaded across the
boundary and the factory's creator method takes no arguments (except a route arg, if any).

Which nodes carry `@SingleIn(<Name>Scope::class)`:

| Node | Annotations |
|---|---|
| `<Name>StoreImpl` | `@Inject` on the class — **never `@SingleIn`** |
| `<Name>HandlerStoreImpl` | `@Inject` + `@SingleIn(<Name>Scope::class)` |
| every `*Handler` | `@SingleIn(<Name>Scope::class)` + `@Inject constructor(...)` |
| `<Name>InteractorImpl`, use cases | `@Inject` + `@SingleIn(<Name>Scope::class)` |
| `*Repository` / `*DataStore` / `*Storage` | `@SingleIn(AppScope::class)`, in `core/` — never in a feature |

**Why the Store must NOT be scoped.** Retention belongs to the Android `ViewModelStore`:
`rememberMetroStoreProcessor` builds the Store inside a `viewModel { }` initializer, so it
survives configuration change and is cleared on back-stack pop. `MetroScopeRule` exempts
`*StoreImpl` by name for exactly this reason. Because the accessor is not cached, **read the
store accessor exactly once per created extension** — each read builds a fresh Store whose
`BaseStore.init` re-runs `setStore(this)` on the shared emitter.

**Why the HandlerStoreImpl MUST be scoped.** The Store injects the **concrete** key
(`storeEmitter: <Name>HandlerStoreImpl`); every handler injects the **interface** key
(`store: <Name>HandlerStore`, reached through `@Binds`). Both are legal bindings of the same
`@Inject` class, and `@SingleIn(<Name>Scope::class)` is the only thing collapsing them to one
object. Drop it and **everything still compiles** — `nonPublicContributionSeverity=ERROR`
(root `gradle.properties`) gates `AppScope` contributions only, so an internal feature scope
gets no compiler check. Metro then builds emitter #1 for the handlers and #2 for the Store;
`BaseStore.init {}` calls `setStore(this)` on #2 only, and the first dispatched action hits
`requireNotNull(_store)` on #1 — the screen crashes on open. `MetroScopeRule` deliberately
does **not** let `*HandlerStoreImpl` inherit the `*StoreImpl` exemption, and
`ArchiveExtensionIdentityTest` in `:app` pins the invariant with two `assertSame`s read from
one extension.

## Step-by-step

1. Every feature has a `domain/` package containing:
   - `domain/<Name>Interactor.kt` (interface) and `<Name>InteractorImpl.kt`
   - `domain/model/` with at least one `*Domain` type per concept the feature surfaces
   - `domain/mapper/<Name>DomainMapper.kt` with `toDomain()` extensions on every
     `core.data.*` type the interactor consumes (and `toData()` for write-side mappings)
   - `domain/usecase/` only when the interactor has thick methods — see the use-case
     extraction convention in [AGENTS.md](../../AGENTS.md).

   The interactor's public surface uses `*Domain` types only — never `core.data.*`
   types. Display fallbacks like "Unnamed" / "Track Now" go in the UI mapper via
   `stringResource(R.string.*)` or `resourceWrapper.getString(R.string.*)`, not in
   domain. See [documentation/architecture.md → Domain model
   layer](../../documentation/architecture.md#domain-model-layer).

   Both the interface and the `Impl` are **public** (not `internal`): the `@Binds` that
   binds them is declared on the public graph-extension interface, which cannot expose an
   internal type. Reference: `feature/archive/.../domain/ArchiveInteractorImpl.kt`.

2. Create the module directory tree under `feature/<name>/`:

   ```
   feature/<name>/
   ├── build.gradle.kts
   └── src/
       ├── main/
       │   ├── res/values/strings.xml   # + values-ru/strings.xml
       │   └── kotlin/io/github/stslex/workeeper/feature/<name_snake>/
       │       ├── di/             # <Name>Scope, <Name>Graph, <Name>HandlerStore[+Impl],
       │       │                   # <Name>Feature
       │       ├── domain/         # only if the feature has its own business logic
       │       ├── mvi/            # OR ui/mvi/ — mirror the closest existing feature
       │       │   ├── handler/    # ClickHandler, InputHandler, NavigationHandler,
       │       │   │               # optional PagingHandler / CommonHandler
       │       │   ├── mapper/     # Domain → Ui mappers (internal objects, not injected)
       │       │   ├── model/      # *UiModel types
       │       │   └── store/      # <Name>Store contract + <Name>StoreImpl
       │       └── ui/
       │           ├── components/
       │           ├── <Name>Screen.kt
       │           └── <Name>Graph.kt   # NavGraphScope.<feature>Graph extension
       ├── test/kotlin/...         # JUnit 5 unit tests (handlers, interactor)
       └── androidTest/kotlin/...  # @Smoke UI tests
   ```

   There is **no** `AndroidManifest.xml` and no `<Name>Component.kt`. The namespace is
   derived from the Gradle path by `configureKotlinAndroid` (`:feature:workout-history` →
   `io.github.stslex.workeeper.feature.workout_history`), so the package directory is the
   snake_case form of the module name.

3. Generate `feature/<name>/build.gradle.kts`. Mirror `feature/archive/build.gradle.kts`:

   ```kotlin
   plugins {
       alias(libs.plugins.convention.composeLibrary)
       alias(libs.plugins.metro)
   }

   // Metro reads javax.inject qualifiers so the inherited app-scoped bindings keep them —
   // @DefaultDispatcher / @IODispatcher are core:core annotations meta-annotated
   // @javax.inject.Qualifier. Without this, two same-typed CoroutineDispatcher bindings
   // would silently merge; with it, (type + qualifier) stays the Metro binding key.
   metro {
       interop {
           includeJavax()
       }
   }

   dependencies {
       implementation(project(":core:core"))

       implementation(project(":core:ui:kit"))
       implementation(project(":core:ui:mvi"))
       implementation(project(":core:ui:navigation"))
       implementation(project(":core:data:exercise"))

       testImplementation(kotlin("test"))
       testImplementation(libs.androidx.paging.testing) // only with a PagingHandler

       androidTestImplementation(libs.bundles.android.test)
       androidTestImplementation(libs.androidx.compose.ui.test.junit4)
       androidTestImplementation(project(":core:ui:test-utils"))
       debugImplementation(libs.androidx.compose.ui.test.manifest)
   }
   ```

   Both plugin lines are required: `convention.composeLibrary` brings Compose / lint /
   KSP, `libs.plugins.metro` brings the DI compiler plugin, and the `metro { interop { … } }`
   block is what keeps the qualified dispatchers distinct. Drop dependencies the feature does
   not use; add `project(":core:core-android")` only if it touches an Android-only core type,
   and `project(":core:data:dataStore")` only if it reads preferences.

4. Register the module:
   - `include(":feature:<name>")` in `settings.gradle.kts`.
   - `implementation(project(":feature:<name>"))` in `app/app/build.gradle.kts` — the
     contributed `@GraphExtension.Factory` only merges into `AppGraph` from `:app`'s compile
     classpath.

   `app/app/.../di/AppGraph.kt` itself needs **no edit**: `@ContributesTo(AppScope::class)`
   on the feature's extension factory is what merges it in. Add an accessor to `AppGraph`
   only when something actually reads it (see the accessor policy in its KDoc).

5. Add the route. Edit
   `core/ui/navigation/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/navigation/Screen.kt`
   and add a `@Serializable` entry. Bottom-bar destinations go under `Screen.BottomBar`;
   detail destinations are top-level `data class` types that carry route arguments;
   single-instance destinations are `data object`. Existing examples: `Screen.Settings`
   / `Screen.Archive` (data objects), `Screen.Training(uuid)` /
   `Screen.Exercise(uuid)` / `Screen.LiveWorkout(sessionUuid, trainingUuid)` /
   `Screen.PlanEditor(...)`.

   Route arguments must be value-type fields (`String?`, `Long`, etc.) — never
   `NavController`, `NavBackStackEntry`, `SavedStateHandle`, or `Context`. Use the
   If the destination hands a value back, declare it on the type:
   `data class <Name>(...) : Screen, ScreenWithResult<R>` — the result type lives on
   the destination, not at the call site (`Screen.PlanEditor : ScreenWithResult<Boolean>`).

6. Generate the Store contract under `mvi/store/<Name>Store.kt`. Conventions enforced
   by the custom Detekt rules in
   [documentation/lint-rules.md](../../documentation/lint-rules.md#custom-detekt-mvi-rules):

   - `interface <Name>Store : Store<State, Action, Event>`.
   - `data class State(val ...) : Store.State` — properties are `val`; collections use
     `kotlinx.collections.immutable` (`ImmutableList`, `ImmutableSet`). A
     `companion object { fun init(...) = State(...) }` (or `INITIAL = State(...)`)
     keeps fixture construction in tests cheap. For a route-arg Store the factory takes
     the route-arg fields (e.g. `fun create(uuid: String?): State`).
   - `sealed interface Action : Store.Action` with nested categories under
     `Click`, `Input`, `Navigation`, optionally `Paging`, `Common`. **Navigation actions
     are always grouped under `Action.Navigation` — never modeled as `Event.Navigate*`.**
   - `sealed interface Event : Store.Event` for **UI side effects only**. Allowed name
     patterns from `MviEventNamingRule` are `*Success`, `*Error`, `*Completed`,
     `*Started`, `*Failed`, `*Requested`, plus tokens `Show`, `Haptic`, `Snackbar`,
     `Scroll`. Although `Navigate` is technically in the rule's pattern list, the
     project's convention is **never to use it** — emit `Action.Navigation.*` instead.

7. Generate handlers under `mvi/handler/`. Each handler:

   - Is `internal class`, `@SingleIn(<Name>Scope::class)`, with a Metro
     `@Inject constructor` taking at least one parameter — `MviHandlerConstructorRule`
     enforces both. (The literal name `NavigationHandler` is exempt from the `@Inject`
     half at the rule level for historical reasons; do not rely on it in new code.)
   - Implements `Handler<Action.<Category>>` from
     `core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/handler/Handler.kt`.
   - Receives the feature's `<Name>HandlerStore` **interface** (never the `Impl`) and
     delegates to it — `internal class ClickHandler @Inject constructor(..., store:
     <Name>HandlerStore) : Handler<Action.Click>, <Name>HandlerStore by store` — to read
     `state`, mutate via `updateState { it.copy(...) }`, dispatch via `consume(...)`, and
     emit via `sendEvent(...)`. The `NavigationHandler` is the exception: it receives only
     the `Navigator` and never touches store state.
   - Never injects the `Screen` route arg. `ScreenInjectionRule` fails any `@Inject` class
     other than a `*StoreImpl` primary constructor that takes a `Screen` type — route state
     reaches a handler as Store state, not out of DI.

8. Generate `mvi/handler/<Name>NavigationHandler.kt`. The canonical shape:

   ```kotlin
   @SingleIn(<Name>Scope::class)
   internal class <Name>NavigationHandler @Inject constructor(
       private val navigator: Navigator,
   ) : Handler<Action.Navigation> {

       override fun invoke(action: Action.Navigation) {
           when (action) {
               Action.Navigation.Back -> navigator.popBack()
               Action.Navigation.OpenArchive -> navigator.navTo(Screen.Archive)
               // ... per-target branch
           }
       }
   }
   ```

   The injected `Navigator` is the app-scoped `NavigatorEventBus`
   (`@SingleIn(AppScope) @ContributesBinding(AppScope, binding<Navigator>()) @Inject` in
   `app/common/.../navigation/NavigatorEventBus.kt`) — the extension inherits it, nothing is
   passed in. It is a controller-free command bus: the handler **never** holds a
   `NavController`, `NavBackStackEntry`, `SavedStateHandle`, `Activity`, or `Context`.
   Calling `navigator.navTo` / `popBack` / `replaceTo` is pure command dispatch; the App/UI
   bridge (`NavigatorExt.NavigationEventBusSetup`) collects each command on the current
   `NavController`. See
   [architecture.md → Navigation](../../documentation/architecture.md#navigation).

9. Generate `mvi/store/<Name>StoreImpl.kt` extending `BaseStore<State, Action, Event>`.
   Class-level `@Inject`, **no `@SingleIn`**, `public` class with an `internal`
   constructor (the accessor is on the public extension; `:app` calls the ctor at the IR
   level, so `internal` handler params are fine).

   **Without route args:**

   ```kotlin
   @Inject
   class <Name>StoreImpl internal constructor(
       navigationHandler: <Name>NavigationHandler,
       clickHandler: <Name>ClickHandler,
       commonHandler: <Name>CommonHandler,
       storeDispatchers: StoreDispatchers,
       storeEmitter: <Name>HandlerStoreImpl,
       analyticsHolder: AnalyticsHolder,
       loggerHolder: LoggerHolder,
   ) : BaseStore<State, Action, Event>(
       name = NAME,
       initialState = State.INITIAL,
       handlerCreator = { action ->
           when (action) {
               is Action.Navigation -> navigationHandler
               is Action.Click -> clickHandler
               is Action.Common -> commonHandler
           }
       },
       storeEmitter = storeEmitter,
       storeDispatchers = storeDispatchers,
       initialActions = listOf(Action.Common.Init),
       analyticsHolder = analyticsHolder,
       loggerHolder = loggerHolder,
   ) {

       companion object {

           @VisibleForTesting
           private const val NAME = "<Name>"
       }
   }
   ```

   **With route args** — identical, plus the `Screen` as an ordinary first parameter:

   ```kotlin
   @Inject
   class <Name>StoreImpl internal constructor(
       screen: Screen.<X>,
       navigationHandler: <Name>NavigationHandler,
       /* other handlers, dispatchers, holders */
   ) : BaseStore<State, Action, Event>(
       /* ... */
       initialState = State.create(uuid = screen.uuid),
       /* ... */
   )
   ```

   Note `storeEmitter` takes the **concrete** `<Name>HandlerStoreImpl`, while handlers take
   the interface — that split is what step "DI shape and scoping" is about. The Store
   retains only the screen's value-type fields it needs; it MUST NOT retain
   `NavBackStackEntry`, `SavedStateHandle`, or any controller reference.

10. Generate `di/<Name>HandlerStore.kt` — a **public** interface, because the `@Binds` for it
    sits on the public extension:

    ```kotlin
    interface <Name>HandlerStore : HandlerStore<State, Action, Event>
    ```

    and `di/<Name>HandlerStoreImpl.kt`:

    ```kotlin
    @Inject
    @SingleIn(<Name>Scope::class)
    class <Name>HandlerStoreImpl : <Name>HandlerStore,
        BaseHandlerStore<State, Action, Event>()
    ```

11. Generate `di/<Name>Scope.kt` — the inert feature-scope token, the Metro analogue of
    Hilt's old `@ViewModelScoped`. It stays `internal` (Metro reads the scope `KClass` at
    the IR level):

    ```kotlin
    internal abstract class <Name>Scope private constructor()
    ```

12. Generate `di/<Name>Graph.kt` — the contributed graph extension. Interface and factory
    are **public** because `:app` generates the extension impl and references them:

    ```kotlin
    @GraphExtension(<Name>Scope::class)
    interface <Name>Graph {

        /** Root accessor: the retained Store. Read EXACTLY ONCE per created extension. */
        val <name>Store: <Name>StoreImpl

        @Binds
        val <Name>InteractorImpl.bindInteractor: <Name>Interactor

        @Binds
        val <Name>HandlerStoreImpl.bindHandlerStore: <Name>HandlerStore

        @ContributesTo(AppScope::class)
        @GraphExtension.Factory
        fun interface Factory {
            fun create<Name>Graph(): <Name>Graph
        }
    }
    ```

    - `AppScope` is the **project** token
      (`io.github.stslex.workeeper.core.core.di.AppScope`), never
      `dev.zacsweers.metro.AppScope` — the built-in has the same simple name, is a different
      class, and a contribution to it silently fails to aggregate. Detekt does **not** cover
      you here: `ContributesBindingScopeRule` only inspects `@ContributesBinding` and
      `ContributesToScopeRule` only inspects `@BindingContainer`, so a `@GraphExtension.Factory`
      with the wrong `@ContributesTo` scope compiles green. Check the import.
    - The creator method name must be **unique across all contributed extension factories**
      (they all merge into `AppGraph`), so `create<Name>Graph()`, never a bare `create()`.
    - **With route args**, bind the arg on the factory instead of taking an argument list:
      `fun create<Name>Graph(@Provides screen: Screen.<X>): <Name>Graph`. One extension is
      built per navigation entry, carrying that entry's arg.
    - Do **not** re-declare app-scoped deps as `@Provides` — the extension inherits every
      `AppScope` binding. Add an extra accessor only when a test reads it (see the
      dispatcher/`Context` accessors on `SettingsGraph` and `ExerciseGraph`, and the two
      handler-store observability accessors on `ArchiveGraph`).

13. Generate `di/<Name>Feature.kt` — the composition seam.

    **Without route args:**

    ```kotlin
    internal typealias <Name>StoreProcessor = StoreProcessor<State, Action, Event>

    internal object <Name>Feature : Feature<<Name>StoreProcessor, Screen.<X>>() {

        @Suppress("UNCHECKED_CAST")
        @Composable
        override fun processor(): <Name>StoreProcessor {
            val context = LocalContext.current
            return rememberMetroStoreProcessor<<Name>StoreImpl> {
                context.appDeps<<Name>Graph.Factory>()
                    .create<Name>Graph()
                    .<name>Store
            } as <Name>StoreProcessor
        }
    }
    ```

    **With route args** — subclass `FeatureAssisted` and pass the screen to the factory:

    ```kotlin
    internal object <Name>Feature : FeatureAssisted<<Name>StoreProcessor, Screen.<X>>() {

        @Suppress("UNCHECKED_CAST")
        @Composable
        override fun processor(screen: Screen.<X>): <Name>StoreProcessor { /* … */ }
    }
    ```

    `context.appDeps<T>()` reads the app graph through the `AppDepsHolder` seam on
    `BaseApplication` and re-narrows it to the contributed factory. The extension is created
    **inside** the `rememberMetroStoreProcessor` lambda, so it is built at most once per
    retained Store (per `NavBackStackEntry` `ViewModelStore`) — that is what bounds the
    `@SingleIn(<Name>Scope)` instances to the Store's lifetime. `LocalContext` is read only
    to reach the seam; never pass it into the graph.

14. Generate `ui/<Name>Graph.kt` — a
    `fun NavGraphScope.<feature>Graph(modifier: Modifier = Modifier, ...)` extension.
    **Never `NavGraphBuilder`**: no feature module imports `androidx.navigation`, and a
    detekt gate plus stage 1.2's exit criterion both depend on that staying true.

    Inside, call `navComponentScreen(<Name>Feature) { processor -> ... }` and consume
    only **UI-side** events through `processor.Handle { event -> ... }` (haptics,
    external links, snackbar emissions, scroll commands). Pass
    `processor.state.value` and `processor::consume` into your `<Name>Screen`.

    **If the feature reads a navigation result from a return-screen**, use
    `navComponentScreenWithResults(<Name>Feature) { results, processor -> ... }`:

    ```kotlin
    results.OnResult(Screen.<X>::class) { value ->
        processor.consume(Action.Common.<Something>(value))
    }
    ```

    **Forward it; do not interpret it here.** Resolving what the result means is
    state-shaped work and belongs in a Handler — see
    `ExerciseStore.Action.Common.ImageRequestReceived` and its `CommonHandler` branch.
    `OnResult` delivers once and clears, so there is no flag to reset; reading is
    nullable and `null` means "no result". References:
    `feature/live-workout/.../ui/LiveWorkoutGraph.kt`,
    `feature/exercise/.../ui/ExerciseGraph.kt`. There is no `SavedStateHandle` to keep
    scoped any more — `NavResults` holds it privately.

15. Wire the navigation graph into the host. Edit
    `app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt` and
    call your new `<feature>Graph(modifier = ...)` inside the `with(NavGraphScope(this))`
    block, alongside its siblings. **Do not add a `sharedTransitionScope` parameter** — no
    graph takes one, and nothing in the app performs a shared-element transition yet; the
    first one to be written brings the accessor that reaches the scope with it.

    The `modifier` you pass into the graph **must** include
    `.reportScreenPlace<Screen.<X>>()` so the TTID / AppCreate / ActivityCreate Firebase
    traces stop on first display, and a `.testTag("<Name>Graph")` to match the siblings.
    See [documentation/performance.md → New-screen contributor
    checklist](../../documentation/performance.md#new-screen-contributor-checklist).

16. If the feature is bottom-bar visible, add an entry in
    `app/common/src/main/kotlin/io/github/stslex/workeeper/bottom_app_bar/BottomBarItem.kt`.

17. Generate the screen. `<Name>Screen.kt` is a `@Composable` ending in `Screen` and
    must take both a `*State` parameter and an action/event handler parameter —
    enforced by `ComposableStateRule`.

18. Build the UI from `core/ui/kit` components and tokens (see Design system contract
    below). Hardcoded `Color()`, `sp`, or `dp` outside `core/ui/kit/theme/` are not
    allowed. Every `public` or `internal` `@Composable` you add must ship with
    `@Preview` functions in the same file — see Composable previews below.
    Keep UI-layer boundaries strict: Composables/Graph files render preformatted state
    only. Date/time/number formatting, relative-time labels, list-to-string shaping, and
    locale-sensitive text mapping belong in handler/mapper/state layers.

19. Add the tests:
    - A smoke UI stub under
      `feature/<name>/src/androidTest/kotlin/.../<Name>ScreenTest.kt`, annotated `@Smoke`
      and `@RunWith(AndroidJUnit4::class)`, extending `BaseComposeTest` with a
      `createComposeRule()` `@get:Rule`. Most new features start as a stub with a
      `TODO(feature-rewrite-tests)` marker and one `@Ignore`d placeholder `@Test` so
      AndroidJUnit4 has something to discover (see the `write-ui-test` skill and
      `feature/archive/.../ArchiveScreenTest.kt`).
    - A `<Name>ExtensionIdentityTest` in
      `app/app/src/test/kotlin/io/github/stslex/workeeper/di/` — a `@GraphExtension`
      cannot be created standalone, so the assertion must live where the parent `AppGraph`
      is compiled. Build the graph with `createGraphFactory<AppGraph.Factory>().create(...)`,
      reach the extension via `asContribution<<Name>Graph.Factory>().create<Name>Graph()`,
      and assert the Store resolves and its app-scoped deps are `assertSame` as the
      parent's. All 13 features that own a `@GraphExtension` have one. Copy
      `ArchiveExtensionIdentityTest` when the feature has handlers that share the emitter —
      its last two tests are the ones sensitive to a missing `@SingleIn` on the
      `HandlerStoreImpl`.
    - Handler unit tests per the `write-handler-test` skill.

## Canonical navigation pattern (recap)

Navigation is **always** routed through a feature's `NavigationHandler`. The graph
composable knows nothing about routes or AndroidX Navigation. The full rationale lives
at [documentation/architecture.md → Navigation](../../documentation/architecture.md#navigation).
The shape:

1. UI emits `Action.Navigation.<Something>` via `processor::consume(...)`.
2. The `StoreImpl.handlerCreator` routes that action to the feature's
   `NavigationHandler` (typically `is Action.Navigation -> navigationHandler`).
3. `NavigationHandler` calls `navigator.navTo(Screen.X)`, `navigator.replaceTo(Screen.X)`,
   or `navigator.popBack(...)`. (`navigator.restartApp()` also exists but is NOT part of
   this flow — it bypasses the bus and has no live producer; restart is runtime-owned.)
4. `NavigatorEventBus` (the app-scoped implementation of `Navigator`) emits a
   `NavCommand` on its internal `SharedFlow`.
5. The App/UI bridge (`NavigatorExt.NavigationEventBusSetup`) collects the command
   keyed on the current `NavController` and executes the AndroidX Navigation operation.

```kotlin
// In <Name>Store.kt:
interface <Name>Store : Store<State, Action, Event> {

    sealed interface Action : Store.Action {
        sealed interface Navigation : Action {
            data object Back : Navigation
            data object OpenArchive : Navigation
        }
        // ... Click, Input, Paging, Common as needed
    }

    sealed interface Event : Store.Event {
        // ONLY UI-side effects: Haptic*, Snackbar*, Show*, Scroll*, *Success, *Error.
        // NEVER Navigate*. Navigation is Action.Navigation, full stop.
    }
}
```

The graph composable (`<feature>Graph`) consumes **only** UI-side events:

```kotlin
fun NavGraphScope.<feature>Graph(modifier: Modifier = Modifier) {
    navComponentScreen(<Name>Feature) { processor ->
        val haptic = LocalHapticFeedback.current

        processor.Handle { event ->
            when (event) {
                is Event.Haptic -> haptic.performHapticFeedback(event.type)
                is Event.ShowRestoredSnackbar -> SnackbarManager.showSnackbar(/* ... */)
                // scroll commands, ...
            }
        }

        <Name>Screen(
            modifier = modifier,
            state = processor.state.value,
            consume = processor::consume,
        )
    }
}
```

The graph composable **never** calls `navController.navigate(...)` /
`popBackStack()` directly, **never** consumes a `Navigate*` event (no such event
exists — emitting one is wrong by convention), and **never** captures
`NavController` outside `App.kt` / `NavigatorExt`. Stores, Handlers, Interactors, and any
app-scoped graph node MUST NOT retain `NavHostController`, `NavController`,
`NavBackStackEntry`, `SavedStateHandle`, `Activity`, or `Context` (the app `Context` bound
instance on `AppGraph` is the one legitimate exception, and it is the *application*
context). The only navigation object living in app scope is the command-only
`NavigatorEventBus`.

## Design system contract

All visible UI is built from `core/ui/kit` components and `AppUi.*` token accessors.
Full catalog is in [documentation/design-system.md](../../documentation/design-system.md);
the directory itself is the authority on what exists today.

**Tokens** — read via `AppUi.*` inside any `@Composable`:

- `AppUi.colors` — `LocalAppColors.current` (semantic palette: `accent`, `textPrimary`,
  `textSecondary`, `textTertiary`, surface tiers, semantic statuses).
- `AppUi.typography` — `LocalAppTypography.current`. Three bundled families (IBM Plex Sans,
  Archivo `wdth 116` for numerals only, IBM Plex Mono) over six sizes, with the 15 M3 names
  as aliases: `display*` / `headline*` / `title*` / `body*` / `label*`, plus `timer`.
  **Never route a `stringResource` through the numeric family** — it has no Cyrillic, and a
  detekt rule fails the build on it.
- `AppUi.shapes` — `LocalAppShapes.current` (`small` / `medium` / `large`).
- `AppUi.motion` — `LocalAppMotion.current` (durations + easings).
- `AppUi.elevation` — `LocalAppElevation.current` (color-based surface tier mapping).
- `AppDimension` — a plain `object` (no CompositionLocal), referenced directly for spacing
  and sizing aliases: `AppDimension.screenEdge`, `.cardPadding`, `.Space.xs`, `.iconSm`,
  `.heightXs`, `.Radius.medium`, etc.

**Components** — every shared UI primitive lives under
`core/ui/kit/src/commonMain/kotlin/io/github/stslex/workeeper/core/ui/kit/components/`:

| Component | File |
|---|---|
| `AppButton` | `components/button/AppButton.kt` |
| `AppCheckmarkButton` | `components/button/AppCheckmarkButton.kt` |
| `AppCard` | `components/card/AppCard.kt` |
| `AppTextField` | `components/input/AppTextField.kt` |
| `AppNumberInput` | `components/input/AppNumberInput.kt` |
| `AppDialog` | `components/dialog/AppDialog.kt` |
| `AppConfirmDialog` | `components/dialog/AppConfirmDialog.kt` |
| `AppConfirmationDialog` | `components/dialog/AppConfirmationDialog.kt` |
| `AppBlockedArchiveDialog` | `components/dialog/AppBlockedArchiveDialog.kt` |
| `AppDatePickerDialog` | `components/dialog/AppDatePickerDialog.kt` |
| `AppEmptyState` | `components/empty/AppEmptyState.kt` |
| `AppListItem` | `components/list/AppListItem.kt` |
| `AppTagChip` | `components/tag/AppTagChip.kt` |
| `AppTagPicker` | `components/tag/AppTagPicker.kt` |
| `AppTopAppBar` | `components/topbar/AppTopAppBar.kt` |
| `AppBottomBar` | `components/bottombar/AppBottomBar.kt` |
| `AppBottomSheet` | `components/sheet/AppBottomSheet.kt` |
| `AppFAB` | `components/fab/AppFAB.kt` |
| `AppLoadingIndicator` | `components/loading/AppLoadingIndicator.kt` |
| `AppSetTypeChip` | `components/setchip/AppSetTypeChip.kt` |
| `AppSegmentedControl` | `components/segmented/AppSegmentedControl.kt` |
| `AppSnackbar` | `components/snackbar/AppSnackbar.kt` |
| `AppTooltip` | `components/tooltip/AppTooltip.kt` |

Plus the non-`App*`-prefixed helpers in the same tree: `PagingUiState.kt`, the
`components/pr/` personal-record widgets, and `components/reorderable/`.

**Forbidden in feature code**:

- Hardcoded `Color(0xFFRRGGBB)` outside `core/ui/kit/theme/AppColors.kt`.
- Hardcoded `12.sp` / `16.dp` outside `core/ui/kit/theme/{AppTypography,AppDimension,AppShapes}.kt`.
- Re-implementing dialogs, snackbars, list rows, or segmented controls inside a feature
  module — extend the shared component or land the variant in `core/ui/kit/`.

## Composable previews

Every `public` or `internal` `@Composable` function under `feature/*` and `core/ui/kit/`
must ship with at least one `@Preview` function in the same file, placed directly below
the composable it covers. Private `@Composable` helpers do **not** require previews — the
parent composable's preview already exercises them.

Rules:

- **Both theme modes are required.** A composable must render in both light and dark.
  Pick one of:
  1. **Two preview functions** — one wraps the body in
     `AppTheme(themeMode = ThemeMode.LIGHT) { ... }`, the other in
     `AppTheme(themeMode = ThemeMode.DARK) { ... }`.
  2. **One preview with `@PreviewParameter`** — declare a
     `ThemeModeProvider : PreviewParameterProvider<ThemeMode>` that yields
     `ThemeMode.LIGHT` and `ThemeMode.DARK`, take it as a `@PreviewParameter` argument,
     and pass it into `AppTheme(themeMode = mode) { ... }`.

  Implicit / system theme is not enough — the preview must lock the mode explicitly with
  `AppTheme(themeMode = ...)`. Do not rely on `uiMode = Configuration.UI_MODE_NIGHT_YES`
  alone; always pass the mode through `AppTheme`.

- **Realistic stub data.** Use values the production feature actually shows (e.g. exercise
  names like `"Bench press"`, plausible workout dates, real-looking set counts). No
  `Lorem ipsum`, no `"asdf"` / `"test"` placeholders. `MockDataFactory` from
  `core/ui/test-utils/` is fine to reach for when the preview is in a module that already
  depends on it; otherwise inline a small stub.

- **One preview per visually-distinct state.** If the composable branches on `loading` /
  `empty` / `error` / `populated` / `dirty form` / `validation error` / `success` (etc.),
  each state gets its own `@Preview` so the full visual surface is reviewable in the IDE
  preview pane. A single `populated` preview is **not** enough when other states are
  reachable.

Shape:

```kotlin
@Preview
@Composable
private fun MyComponentPopulatedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        MyComponent(state = stubPopulatedState())
    }
}

@Preview
@Composable
private fun MyComponentPopulatedDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        MyComponent(state = stubPopulatedState())
    }
}

@Preview
@Composable
private fun MyComponentEmptyPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        MyComponent(state = stubEmptyState())
    }
}
```

Or with `@PreviewParameter` to halve the function count:

```kotlin
private class ThemeModeProvider : PreviewParameterProvider<ThemeMode> {
    override val values = sequenceOf(ThemeMode.LIGHT, ThemeMode.DARK)
}

@Preview
@Composable
private fun MyComponentPopulatedPreview(
    @PreviewParameter(ThemeModeProvider::class) themeMode: ThemeMode,
) {
    AppTheme(themeMode = themeMode) {
        MyComponent(state = stubPopulatedState())
    }
}
```

Preview functions are `private` and named `<ComponentName>Preview` (or
`<ComponentName><Variant>Preview` when there are multiple states).

## Verification

```bash
./gradlew :feature:<name>:detekt :feature:<name>:lintDebug --no-configuration-cache
./gradlew :feature:<name>:assembleDebug
./gradlew :feature:<name>:testDebugUnitTest
./gradlew :app:app:testDebugUnitTest   # runs the <Name>ExtensionIdentityTest
```

`:feature:<name>:assembleDebug` alone does **not** prove the DI wiring: a
`@GraphExtension` is only merged when `:app` compiles, and a missing `@SingleIn` on the
`HandlerStoreImpl` compiles green everywhere. The `:app:app` identity test is the check
that actually fires.

The detekt run exercises the custom MVI + Metro rules; if any fire, fix them rather than
baselining (see the `refactor-with-mvi-rules` skill).

## Common pitfalls

- **Do not put `@SingleIn` on the `*StoreImpl`.** It is deliberately unscoped — the
  `ViewModelStore` retains it. `MetroScopeRule` exempts `*StoreImpl` by name, so a stray
  `@SingleIn` will not be caught by lint; it will just pin the Store to the extension.
- **Do not forget `@SingleIn(<Name>Scope::class)` on the `*HandlerStoreImpl`.** It compiles
  green and crashes the screen on the first action. See "DI shape and scoping" above.
- **Do not read the store accessor twice** off one created extension — each read builds a
  fresh Store and rebinds the shared emitter away from the previous one.
- **Do not name two extension factory creators the same.** Every
  `@ContributesTo(AppScope::class)` factory merges into `AppGraph`; two `create()` methods
  collide with "return types are incompatible". Use `create<Name>Graph()`.
- **Do not use `dev.zacsweers.metro.AppScope`.** The project token is
  `io.github.stslex.workeeper.core.core.di.AppScope`; the Metro built-in has the same simple
  name and silently fails to aggregate. `ContributesBindingScopeRule` catches it on a
  `@ContributesBinding` and `ContributesToScopeRule` on a `@BindingContainer` — but **not**
  on a `@GraphExtension.Factory`. Verify that import by hand.
- **Do not inject `Screen` anywhere but a Store's primary constructor.**
  `ScreenInjectionRule` fails handlers, interactors, mappers, and secondary constructors.
- **Do not use Hilt annotations or `javax.inject.Inject`.** Metro's `@Inject` /
  `@SingleIn` / `@Binds` / `@Provides` / `@GraphExtension` are the only DI vocabulary.
  `javax.inject` survives on the classpath **only** for the qualifier interop, so
  `@Singleton` still resolves while the graph ignores it — `MetroScopeRule` flags the
  resulting missing `@SingleIn`.
- **Do not skip `BaseStore`.** Stores must extend `BaseStore`; enforced by
  `MviStoreExtensionRule`.
- **Do not put state mutation in `@Composable` functions.** All mutation flows through
  `BaseStore.consume(action)` → handler → `updateState`.
- **Do not use `MutableList` / `MutableSet` / `MutableMap` or `var` in `State`.** Use the
  `kotlinx.collections.immutable` types and `val`. `MviStateImmutabilityRule` rejects them.
- **Do not model navigation as `Event.Navigate*`.** Every navigation target is
  `Action.Navigation.<X>` consumed by the feature's `NavigationHandler`.
- **Do not retain `NavController`, `NavHostController`, `NavBackStackEntry`,
  `SavedStateHandle`, `Activity`, or a UI `Context` in any Store, Handler, or graph node.**
  Stores and Handlers depend on `Navigator` only.
- **Do not hand app-scoped deps to the extension factory.** The extension inherits every
  `AppScope` binding; a `@Provides` duplicate fails Metro's duplicate-binding check.
- **Do not hardcode colors / sizes / type styles in feature code.** Pull from
  `AppUi.*` and the `core/ui/kit` components.
- **Do not ship a `public` or `internal` `@Composable` without a `@Preview`.** Both
  light and dark must be covered, each visually-distinct state needs its own preview, and
  stub data must be realistic. Private helpers are exempt.
- **Do not forget either plugin line.** `convention.composeLibrary` alone gives no DI;
  `libs.plugins.metro` without `metro { interop { includeJavax() } }` merges the qualified
  dispatcher bindings.
- **Do not forget `implementation(project(":feature:<name>"))` in `app/app`.** Aggregation
  happens on `:app`'s compile classpath; without the edge the extension factory never
  reaches `AppGraph` (and `AppNavigationHost`'s import of the graph will not resolve).
- **Do not bypass `Screen` for navigation.** Every navigable destination must be a
  `@Serializable` entry in `core/ui/navigation/.../Screen.kt`.
