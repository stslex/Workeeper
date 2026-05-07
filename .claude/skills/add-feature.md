---
name: add-feature
description: Scaffold a new `feature/<name>` Gradle module that follows the project's MVI + Hilt + Compose conventions — including the lifecycle-safe navigation command-bus pattern (Action.Navigation + NavigationHandler with @Inject Navigator routing through the singleton NavigatorEventBus), the design system (`core/ui/kit` components and `AppUi.*` tokens), Store contract, handlers, DI, navigation entry, and a smoke UI test stub.
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
  `core/ui/navigation/src/main/kotlin/io.github/stslex/workeeper/core/ui/navigation/Screen.kt`.
- These docs are the source of truth for everything below — read them before scaffolding:
  - [documentation/architecture.md](../../documentation/architecture.md) — module map, MVI
    contract, [Navigation](../../documentation/architecture.md#navigation), DI scopes.
  - [documentation/design-system.md](../../documentation/design-system.md) — token catalog
    and the 21 `core/ui/kit` components.
  - [documentation/lint-rules.md](../../documentation/lint-rules.md) — the State / Action /
    Event / Handler / Composable rules that gate a new module.

## Reference implementations

The cleanest references for the **current** architecture (post-navigation-lifecycle PR):

- **No route args (plain `Feature`):** `feature/home/`,
  `feature/all-trainings/`, `feature/all-exercises/`, `feature/settings/`.
- **Route args (`FeatureAssisted` + `StoreFactory`):** `feature/exercise/`,
  `feature/single-training/`, `feature/live-workout/`, `feature/past-session/`,
  `feature/image-viewer/`, `feature/exercise-chart/`, `feature/plan-editor/`.

Pick the closest match for the new feature's needs:

- Graph composable: `feature/home/.../ui/HomeGraph.kt`,
  `feature/exercise/.../ui/ExerciseGraph.kt` (uses `navComponentScreenWithState` to
  read PlanEditor saved-result).
- NavigationHandler: `feature/home/.../mvi/handler/NavigationHandler.kt`,
  `feature/exercise/.../ui/mvi/handler/NavigationHandler.kt` — both `@ViewModelScoped`
  with `@Inject Navigator`.
- StoreImpl (no args): `feature/home/.../mvi/store/HomeStoreImpl.kt`.
- StoreImpl (assisted): `feature/exercise/.../ui/mvi/store/ExerciseStoreImpl.kt`,
  `feature/live-workout/.../mvi/store/LiveWorkoutStoreImpl.kt`.
- Feature (no args): `feature/home/.../di/HomeFeature.kt`.
- Feature (assisted): `feature/exercise/.../di/ExerciseFeature.kt`,
  `feature/live-workout/.../di/LiveWorkoutFeature.kt`.

Older features (`feature/exercise`, `feature/single-training`) keep the MVI under
`ui/mvi/`; newer ones keep it under `mvi/`. Both layouts compile; mirror the existing
shape when extending an existing module.

## Step-by-step

1. Every feature has a `domain/` package containing:
   - `domain/<Name>Interactor.kt` and `<Name>InteractorImpl.kt`
   - `domain/model/` with at least one `*Domain` type per concept the feature surfaces
   - `domain/mapper/<Name>DomainMapper.kt` with `toDomain()` extensions on every
     `core.data.*` type the interactor consumes (and `toData()` for write-side mappings)
   - `domain/usecase/` only when the interactor has thick methods — see the use-case
     extraction convention in [AGENTS.md](../../AGENTS.md).

   The interactor's public surface uses `*Domain` types only — never `core.data.*`
   types. Display fallbacks like "Unnamed" / "Track Now" go in the UI mapper via
   `stringResource(R.string.*)` or `resourceWrapper.getString(R.string.*)`, not in
   domain. See [documentation/architecture.md → Domain model
   layer](../../documentation/architecture.md#domain-model-layer) for the contract.
   Reference: `feature/exercise/domain/`.

2. Create the module directory tree under `feature/<name>/`. Current layout (mirrors
   `feature/home/`, `feature/all-trainings/`, `feature/exercise/`):

   ```
   feature/<name>/
   ├── build.gradle.kts
   └── src/
       ├── main/AndroidManifest.xml
       ├── main/kotlin/io/github/stslex/workeeper/feature/<name_snake>/
       │   ├── di/                 # <Name>Module, <Name>HandlerStore[+Impl], <Name>Feature
       │   ├── domain/             # only if the feature has its own business logic
       │   ├── mvi/                # OR ui/mvi/ — mirror the closest existing feature
       │   │   ├── handler/        # ClickHandler, InputHandler, NavigationHandler,
       │   │   │                   # optional PagingHandler / CommonHandler
       │   │   ├── mapper/         # Domain → Ui mappers
       │   │   ├── model/          # *UiModel types
       │   │   └── store/          # <Name>Store contract + <Name>StoreImpl
       │   └── ui/
       │       ├── components/
       │       ├── <Name>Screen.kt
       │       └── <Name>Graph.kt   # NavGraphBuilder.<feature>Graph extension
       ├── test/kotlin/...         # JUnit 5 unit tests (handlers, interactor)
       └── androidTest/kotlin/...  # @Smoke UI tests
   ```

   There is no `<Name>Component.kt` file. Route arguments come into the Store via
   Dagger assisted injection (see step 9), not via a navigation `Component` subclass.

3. Generate `feature/<name>/build.gradle.kts`. Mirror `feature/home/build.gradle.kts`:

   ```kotlin
   plugins {
       alias(libs.plugins.convention.composeLibrary)
   }

   dependencies {
       implementation(project(":core:core"))

       implementation(project(":core:dataStore"))
       implementation(project(":core:ui:kit"))
       implementation(project(":core:ui:mvi"))
       implementation(project(":core:ui:navigation"))
       implementation(project(":core:exercise"))

       testImplementation(kotlin("test"))
       testImplementation(libs.androidx.paging.testing) // only if the feature uses PagingHandler

       androidTestImplementation(libs.bundles.android.test)
       androidTestImplementation(libs.androidx.compose.ui.test.junit4)
       androidTestImplementation(project(":core:ui:test-utils"))
       debugImplementation(libs.androidx.compose.ui.test.manifest)
   }
   ```

   Drop dependencies the feature does not use.

4. Add the module to the root settings: `include(":feature:<name>")` in
   `settings.gradle.kts`.

5. Add the route. Edit
   `core/ui/navigation/src/main/kotlin/io.github/stslex/workeeper/core/ui/navigation/Screen.kt`
   and add a `@Serializable` entry. Bottom-bar destinations go under `Screen.BottomBar`;
   detail destinations are top-level `data class` types that carry route arguments;
   single-instance destinations are `data object`. Existing examples: `Screen.Settings`
   / `Screen.Archive` (data objects), `Screen.Training(uuid)` /
   `Screen.Exercise(uuid)` / `Screen.LiveWorkout(sessionUuid, trainingUuid)` /
   `Screen.PlanEditor(performedExerciseUuid, exerciseUuid, trainingUuid)`.

   Route arguments must be value-type fields (`String?`, `Long`, etc.) — never
   `NavController`, `NavBackStackEntry`, `SavedStateHandle`, or `Context`. Use the
   companion object slot for navigation-result `SaveHandlerAttr` declarations
   (`Screen.PlanEditor.Companion.planEditorSavedAttr`).

6. Generate the Store contract under `mvi/store/<Name>Store.kt`. Conventions enforced
   by the custom Detekt rules in
   [documentation/lint-rules.md](../../documentation/lint-rules.md#custom-detekt-mvi-rules):

   - `internal interface <Name>Store : Store<State, Action, Event>`.
   - `data class State(val ...) : Store.State` — properties are `val`; collections use
     `kotlinx.collections.immutable` (`ImmutableList`, `ImmutableSet`). A
     `companion object { fun create(...) = State(...) }` (or `INITIAL = State(...)`)
     keeps fixture construction in tests cheap. For an assisted-injected Store the
     factory takes the route-arg fields (e.g.
     `fun create(uuid: String?): State`).
   - `sealed interface Action : Store.Action` with nested categories under
     `Click`, `Input`, `Navigation`, optionally `Paging`, `Common`. **Navigation actions
     are always grouped under `Action.Navigation` — never modeled as `Event.Navigate*`.**
   - `sealed interface Event : Store.Event` for **UI side effects only**. Allowed name
     patterns from `MviEventNamingRule` are `*Success`, `*Error`, `*Completed`,
     `*Started`, `*Failed`, `*Requested`, plus tokens `Show`, `Haptic`, `Snackbar`,
     `Scroll`. Although `Navigate` is technically in the rule's pattern list, the
     project's convention is **never to use it** — emit `Action.Navigation.*` instead.

7. Generate handlers under `mvi/handler/`. Each handler:

   - Is `internal class`, `@ViewModelScoped`, with `@Inject` constructor injection.
     `MviHandlerConstructorRule` enforces `@Inject` and at least one parameter for
     every handler. (The literal name `NavigationHandler` is technically exempt at the
     rule level for historical reasons, but the current architecture uses
     `@Inject Navigator` constructor injection on it identically to other handlers —
     do not rely on the exemption in new code.)
   - Implements `Handler<Action.<Category>>` from
     `core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/handler/Handler.kt`.
   - Receives the feature's `<Name>HandlerStore` interface (defined in `di/`) to read
     state, mutate state via `updateState { it.copy(...) }`, dispatch other actions via
     `consume(...)`, and emit events via `sendEvent(...)`. The `NavigationHandler` is
     the exception — it receives only the singleton `Navigator` and never touches
     store state.

8. Generate `mvi/handler/NavigationHandler.kt`. The canonical shape:

   ```kotlin
   @ViewModelScoped
   internal class NavigationHandler @Inject constructor(
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

   The injected `Navigator` is the singleton `NavigatorEventBus` (bound by
   `app/app/.../di/NavigationModule.kt`). It is a controller-free command bus — the
   handler **never** holds a `NavController`, `NavBackStackEntry`, `SavedStateHandle`,
   `Activity`, or `Context`. Calling `navigator.navTo` / `navigator.popBack` /
   `navigator.replaceTo` is pure command dispatch; the App/UI bridge
   (`NavigatorExt.NavigationEventBusSetup` in `app/app/.../navigation/NavigatorExt.kt`)
   collects each command on the current `NavController` and executes the AndroidX
   Navigation operation. See
   [architecture.md → Navigation](../../documentation/architecture.md#navigation).

9. Generate `mvi/store/<Name>StoreImpl.kt` extending `BaseStore<State, Action, Event>`.

   **Without route args** — plain `@HiltViewModel`:

   ```kotlin
   @HiltViewModel
   internal class <Name>StoreImpl @Inject constructor(
       navigationHandler: NavigationHandler,
       clickHandler: ClickHandler,
       commonHandler: CommonHandler,
       storeDispatchers: StoreDispatchers,
       handlerStore: <Name>HandlerStoreImpl,
       analyticsHolder: AnalyticsHolder,
       loggerHolder: LoggerHolder,
   ) : BaseStore<State, Action, Event>(
       name = NAME,
       initialState = State.INITIAL,
       handlerCreator = { action ->
           when (action) {
               is Action.Navigation -> navigationHandler
               is Action.Common -> commonHandler
               is Action.Click -> clickHandler
           }
       },
       storeEmitter = handlerStore,
       storeDispatchers = storeDispatchers,
       initialActions = listOf(Action.Common.Init),
       analyticsHolder = analyticsHolder,
       loggerHolder = loggerHolder,
   )
   ```

   **With route args** — `@HiltViewModel(assistedFactory = Factory::class)` plus
   `@AssistedInject` constructor and an `@AssistedFactory interface Factory : StoreFactory<Screen.<X>, StoreImpl>`:

   ```kotlin
   @HiltViewModel(assistedFactory = <Name>StoreImpl.Factory::class)
   internal class <Name>StoreImpl @AssistedInject constructor(
       @Assisted screen: Screen.<X>,
       navigationHandler: NavigationHandler,
       /* other handlers, dispatchers, holders */
   ) : BaseStore<State, Action, Event>(
       /* ... */
       initialState = State.create(uuid = screen.uuid),
       /* ... */
   ) {

       @AssistedFactory
       interface Factory : StoreFactory<Screen.<X>, <Name>StoreImpl>

       companion object {

           @VisibleForTesting
           private const val NAME = "<Name>"
       }
   }
   ```

   The Store retains only the screen's value-type fields it needs in initial state
   (`screen.uuid`, `screen.sessionUuid`, etc.). It MUST NOT retain
   `NavBackStackEntry`, `SavedStateHandle`, or any controller reference.

10. Generate the Hilt module at `di/<Name>Module.kt` with
    `@InstallIn(ViewModelComponent::class)`. Bind the `<Name>HandlerStore` (and any
    interactor) as `@ViewModelScoped`. Reference: `feature/all-trainings/.../di/AllTrainingsModule.kt`.

11. Generate `di/<Name>HandlerStore.kt` (the interface, e.g.
    `internal interface SettingsHandlerStore : HandlerStore<State, Action, Event>`)
    and `di/<Name>HandlerStoreImpl.kt` (`@ViewModelScoped`, `@Inject constructor()`,
    extending `BaseHandlerStore<State, Action, Event>`). Reference:
    `feature/home/.../di/HomeHandlerStoreImpl.kt`.

12. Generate `di/<Name>Feature.kt`.

    **Without route args** — `Feature<TProcessor, TScreen>`:

    ```kotlin
    internal typealias <Name>StoreProcessor = StoreProcessor<State, Action, Event>

    internal object <Name>Feature : Feature<<Name>StoreProcessor, Screen.<X>>() {

        @Composable
        override fun processor(): <Name>StoreProcessor = createProcessor<<Name>StoreImpl>()
    }
    ```

    **With route args** — `FeatureAssisted<TProcessor, TScreen>`:

    ```kotlin
    internal typealias <Name>StoreProcessor = StoreProcessor<State, Action, Event>

    internal object <Name>Feature : FeatureAssisted<<Name>StoreProcessor, Screen.<X>>() {

        @Composable
        override fun processor(screen: Screen.<X>): <Name>StoreProcessor =
            createProcessor<<Name>StoreImpl, <Name>StoreImpl.Factory>(screen)
    }
    ```

    Reference (no args): `feature/home/.../di/HomeFeature.kt`,
    `feature/all-trainings/.../di/AllTrainingsFeature.kt`. Reference (assisted):
    `feature/exercise/.../di/ExerciseFeature.kt`,
    `feature/live-workout/.../di/LiveWorkoutFeature.kt`.

13. Generate `ui/<Name>Graph.kt` — a
    `fun NavGraphBuilder.<feature>Graph(modifier: Modifier = Modifier, ...)` extension.

    Inside, call `navComponentScreen(<Name>Feature) { processor -> ... }` and consume
    only **UI-side** events through `processor.Handle { event -> ... }` (haptics,
    external links, snackbar emissions, scroll commands, dialog state). Pass
    `processor.state.value` and `processor::consume` into your `<Name>Screen`.

    **If the feature reads a navigation result from a return-screen**, use
    `navComponentScreenWithState(<Name>Feature) { stateHandle, processor -> ... }` and
    collect via `stateHandle.getStateFlow(<SaveHandlerAttr>).collectAsState()`. Reset
    the flag after consumption with
    `stateHandle.setAttrDefaultValue(<SaveHandlerAttr>)` so re-entry does not
    retrigger the consumer. Reference: `feature/exercise/.../ui/ExerciseGraph.kt`,
    `feature/single-training/.../ui/SingleTrainingGraph.kt`,
    `feature/live-workout/.../ui/LiveWorkoutGraph.kt`. The `stateHandle` is the
    current `NavBackStackEntry.savedStateHandle` — keep it scoped to the graph block;
    do not pass it into Store, Handler, or any DI binding.

14. Wire the navigation graph into the host. Edit
    `app/app/src/main/java/io/github/stslex/workeeper/host/AppNavigationHost.kt` and
    call your new
    `<feature>Graph(modifier = ..., sharedTransitionScope = this@SharedTransitionLayout)`.
    The `sharedTransitionScope` parameter is only required for graphs that participate
    in shared element transitions — see how `allTrainingsGraph` and `settingsGraph`
    differ.

    The `modifier` you pass into the graph **must** include
    `Modifier.reportScreenPlace<Screen.<X>>()` so the TTID / AppCreate /
    ActivityCreate Firebase traces stop on first display. Without it those metrics
    will be aborted by the next navigation. See
    [documentation/performance.md → New-screen contributor checklist](../../documentation/performance.md#new-screen-contributor-checklist).

15. If the feature is bottom-bar visible, add an entry in
    `app/app/src/main/java/io/github/stslex/workeeper/bottom_app_bar/BottomBarItem.kt`.

16. Generate the screen. `<Name>Screen.kt` is a `@Composable` ending in `Screen` and
    must take both a `*State` parameter and an action/event handler parameter —
    enforced by `ComposableStateRule`.

17. Build the UI from `core/ui/kit` components and tokens (see Design system contract
    below). Hardcoded `Color()`, `sp`, or `dp` outside `core/ui/kit/theme/` are not
    allowed. Every `public` or `internal` `@Composable` you add must ship with
    `@Preview` functions in the same file — see Composable previews below.
    Keep UI-layer boundaries strict: Composables/Graph files should render
    preformatted state only. Date/time/number formatting, relative-time labels,
    list-to-string shaping, and locale-sensitive text mapping belong in
    handler/mapper/state layers.

18. Add a smoke UI test stub under
    `feature/<name>/src/androidTest/kotlin/.../<Name>ScreenTest.kt` annotated `@Smoke`.
    Most new features start as a stub with a `TODO(feature-rewrite-tests)` marker
    (see the `write-ui-test` skill).

## Canonical navigation pattern (recap)

Navigation is **always** routed through a feature's `NavigationHandler`. The graph
composable knows nothing about routes or AndroidX Navigation. The full rationale lives
at [documentation/architecture.md → Navigation](../../documentation/architecture.md#navigation).
The shape:

1. UI emits `Action.Navigation.<Something>` via `processor::consume(...)`.
2. The `StoreImpl.handlerCreator` routes that action to the feature's
   `NavigationHandler` (typically `is Action.Navigation -> navigationHandler`).
3. `NavigationHandler` calls `navigator.navTo(Screen.X)`, `navigator.replaceTo(Screen.X)`,
   or `navigator.popBack(...)`.
4. `NavigatorEventBus` (the singleton implementation of `Navigator`) emits a
   `NavigationCommand` on its internal `SharedFlow`.
5. The App/UI bridge (`NavigatorExt.NavigationEventBusSetup`) collects the command
   keyed on the current `NavController` and executes the AndroidX Navigation operation.

```kotlin
// In <Name>Store.kt:
internal interface <Name>Store : Store<State, Action, Event> {

    sealed interface Action : Store.Action {
        sealed interface Navigation : Action {
            data object Back : Navigation
            data object OpenArchive : Navigation
            // ... any other navigation targets
        }
        // ... Click, Input, Paging, Common as needed
    }

    sealed interface Event : Store.Event {
        // ONLY UI-side effects: Haptic*, Snackbar*, Show*, Scroll*, *Success, *Error, *Completed.
        // NEVER Navigate*. Navigation is Action.Navigation, full stop.
    }
}

// In mvi/handler/NavigationHandler.kt:
@ViewModelScoped
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

The graph composable (`<feature>Graph`) consumes **only** UI-side events:

```kotlin
fun NavGraphBuilder.<feature>Graph(modifier: Modifier = Modifier) {
    navComponentScreen(<Name>Feature) { processor ->
        val haptic = LocalHapticFeedback.current
        val context = LocalContext.current

        processor.Handle { event ->
            when (event) {
                is Event.Haptic -> haptic.performHapticFeedback(event.type)
                is Event.ShowExternalLink -> context.startActivity(
                    Intent(Intent.ACTION_VIEW, event.url.toUri())
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
                )
                // snackbar emissions, scroll commands, ...
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
`NavController` outside `App.kt` / `NavigatorExt`. Stores, Handlers, ViewModels,
Interactors, and any DI singleton MUST NOT retain `NavHostController`,
`NavController`, `NavBackStackEntry`, `SavedStateHandle`, `Activity`, or
`Context`. The only object that may live in singleton scope is the command-only
`NavigatorEventBus`.

## Design system contract

All visible UI is built from `core/ui/kit` components and `AppUi.*` token accessors.
Full catalog is in [documentation/design-system.md](../../documentation/design-system.md).
Quick reference:

**Tokens** — read via `AppUi.*` inside any `@Composable`:

- `AppUi.colors` — `LocalAppColors.current` (semantic palette: `accent`, `textPrimary`,
  `textSecondary`, `textTertiary`, surface tiers, semantic statuses).
- `AppUi.typography` — `LocalAppTypography.current` (Inter family, 13-slot M3 scale).
- `AppUi.shapes` — `LocalAppShapes.current` (`small` / `medium` / `large`).
- `AppUi.motion` — `LocalAppMotion.current` (durations + easings).
- `AppUi.elevation` — `LocalAppElevation.current` (color-based surface tier mapping).
- `LocalAppDimension.current` for spacing aliases (`screenEdge`, `cardPadding`,
  `iconMd`, `heightSm`, etc.).

**Components** — every shared UI primitive lives under
`core/ui/kit/src/main/kotlin/io/github/stslex/workeeper/core/ui/kit/components/`:

| Component | File |
|---|---|
| `AppButton` | `components/button/AppButton.kt` |
| `AppCard` | `components/card/AppCard.kt` |
| `AppTextField` | `components/input/AppTextField.kt` |
| `AppNumberInput` | `components/input/AppNumberInput.kt` |
| `AppDialog` | `components/dialog/AppDialog.kt` |
| `AppConfirmDialog` | `components/dialog/AppConfirmDialog.kt` |
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
| `AppSwipeAction` | `components/swipe/AppSwipeAction.kt` |

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

@Preview
@Composable
private fun MyComponentErrorPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        MyComponent(state = stubErrorState())
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
```

The detekt run exercises the custom MVI rules; if any fire, fix them rather than
baselining (see the `refactor-with-mvi-rules` skill).

## Common pitfalls

- **Do not skip `BaseStore`.** Stores must extend `BaseStore`; this is enforced by
  `MviStoreExtensionRule`.
- **Do not put state mutation in `@Composable` functions.** All mutation flows through
  `BaseStore.consume(action)` → handler → `updateState`.
- **Do not use `MutableList` / `MutableSet` / `MutableMap` in `State`.** Use the
  `kotlinx.collections.immutable` types. `MviStateImmutabilityRule` will reject them.
- **Do not use `var` in `State`.** Same rule — properties must be `val`.
- **Do not model navigation as `Event.Navigate*`.** Every navigation target is
  `Action.Navigation.<X>` consumed by the feature's `NavigationHandler`. See the
  Canonical navigation pattern above and the `refactor-with-mvi-rules` skill.
- **Do not retain `NavController`, `NavHostController`, `NavBackStackEntry`,
  `SavedStateHandle`, `Activity`, or `Context` in any Store, Handler, ViewModel, or
  Hilt singleton.** Stores and Handlers depend on `Navigator` (the command-bus
  abstraction) only. The bridge code in `App.kt` /
  `NavigatorExt.NavigationEventBusSetup` is the only place that touches the
  `NavController`.
- **Do not introduce a `Component<Screen>` subclass for the feature.** Route arguments
  enter the Store via `@Assisted screen: Screen.<X>` (assisted injection) — the old
  `Component` / `RootComponent` / `LocalRootComponent` / `Component.create` machinery
  no longer exists.
- **Do not call `navController.navigate(...)` or `navController.popBackStack()` from a
  graph composable.** Dispatch `Action.Navigation.<X>` instead. The bridge executes
  it.
- **Do not pass `SavedStateHandle` into a Store, Handler, or DI binding.** It is
  passed only as a parameter to the graph block via
  `navComponentScreenWithState(...)` and used in-place.
- **Do not hardcode colors / sizes / type styles in feature code.** Pull from
  `AppUi.*` and the `core/ui/kit` components. See the Design system contract above.
- **Do not ship a `public` or `internal` `@Composable` without a `@Preview`.** Both
  light and dark must be covered (two preview functions, or one with a
  `@PreviewParameter`-driven `ThemeMode`), each visually-distinct state needs its own
  preview, and stub data must be realistic. See Composable previews above. Private
  helpers are exempt.
- **Do not forget the `convention.composeLibrary` plugin alias** in
  `build.gradle.kts`. Plain `kotlin("jvm")` modules will not get Hilt, Compose, or
  the lint convention.
- **Do not bypass `Screen` for navigation.** Every navigable destination must be a
  `@Serializable` entry in `core/ui/navigation/.../Screen.kt`.
