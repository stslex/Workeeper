---
name: add-feature
description: Scaffold a new `feature/<name>` Gradle module that follows the project's MVI + Hilt + Compose conventions — including the canonical navigation pattern (Action.Navigation + NavigationHandler with injected Navigator), the design system (`core/ui/kit` components and `AppUi.*` tokens), Store contract, handlers, DI, navigation entry, and a smoke UI test stub.
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
    contract, `Navigation flow (canonical pattern)`, DI scopes.
  - [documentation/design-system.md](../../documentation/design-system.md) — token catalog
    and the 21 `core/ui/kit` components.
  - [documentation/lint-rules.md](../../documentation/lint-rules.md) — the State / Action /
    Event / Handler / Composable rules that gate a new module.
  - [documentation/feature-specs/settings-archive.md](../../documentation/feature-specs/settings-archive.md)
    — canonical worked example of a v1 feature, including handler split and graph composable.

## Reference implementation

The fullest current example is `feature/settings/` (Stage 5.1). The most literal
"canonical navigation" reference is `feature/all-trainings/`:

- Graph composable: `feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/AllTrainingsGraph.kt`
- NavigationHandler: `feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/NavigationHandler.kt`
- Component: `feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/AllTrainingsComponent.kt`
- StoreImpl: `feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/store/TrainingStoreImpl.kt`
- Feature wiring: `feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/di/TrainingsFeature.kt`

Older features (`feature/exercise`, `feature/single-training`, `feature/charts`) keep the
MVI under `ui/mvi/` and use the older `*StoreProcessor.kt` naming. When extending those
modules, mirror what already exists there. When creating a **new** module, follow the
post-Stage-5.1 layout below.

## Step-by-step

1. Decide whether the feature needs a domain layer. Features with non-trivial business logic
   or their own interactor (`feature/settings/.../domain/`,
   `feature/exercise/.../domain/`, `feature/single-training/.../domain/`) create a `domain/`
   package. Features that only forward to repositories (`feature/all-trainings`,
   `feature/charts`, `feature/all-exercises`) skip it.

2. Create the module directory tree under `feature/<name>/`. Post-Stage-5.1 layout
   (mirrors `feature/settings/`):

   ```
   feature/<name>/
   ├── build.gradle.kts
   └── src/
       ├── main/AndroidManifest.xml
       ├── main/kotlin/io/github/stslex/workeeper/feature/<name_snake>/
       │   ├── di/                 # <Name>Module, <Name>HandlerStore[+Impl], <Name>Feature
       │   ├── domain/             # only if the feature has its own business logic
       │   ├── mvi/
       │   │   ├── handler/        # ClickHandler, InputHandler, NavigationHandler,
       │   │   │                   # optional PagingHandler / CommonHandler, plus <Name>Component
       │   │   ├── model/          # UI models, mappers
       │   │   └── store/          # <Name>Store contract + <Name>StoreImpl
       │   └── ui/
       │       ├── components/
       │       ├── <Name>Screen.kt
       │       └── <Name>Graph.kt   # NavGraphBuilder.<feature>Graph extension
       ├── test/kotlin/...         # JUnit 5 unit tests (handlers, interactor)
       └── androidTest/kotlin/...  # @Smoke UI tests (often stubs — see write-ui-test)
   ```

3. Generate `feature/<name>/build.gradle.kts`. Mirror `feature/settings/build.gradle.kts`:

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

   Drop dependencies the feature does not use (e.g. omit `:core:exercise` if the feature does
   not touch exercise / training / tag repositories).

4. Add the module to the root settings: `include(":feature:<name>")` in `settings.gradle.kts`.

5. Add the route. Edit
   `core/ui/navigation/src/main/kotlin/io.github/stslex/workeeper/core/ui/navigation/Screen.kt`
   and add a `@Serializable` entry. Bottom-bar destinations go under `Screen.BottomBar`;
   detail destinations are top-level `data class` types that carry route arguments;
   single-instance destinations are `data object`. Existing examples: `Screen.Settings` /
   `Screen.Archive` (data objects), `Screen.Training(uuid)` / `Screen.Exercise(uuid, trainingUuid)`
   (data classes).

6. Generate the Store contract under `mvi/store/<Name>Store.kt`. Conventions enforced by
   the custom Detekt rules in
   [documentation/lint-rules.md](../../documentation/lint-rules.md#custom-detekt-mvi-rules):

   - `internal interface <Name>Store : Store<State, Action, Event>`.
   - `data class State(val ...) : Store.State` — properties are `val`; collections use
     `kotlinx.collections.immutable` (`ImmutableList`, `ImmutableSet`). A
     `companion object { fun init(...) = State(...) }` (or `INITIAL = State(...)`) keeps
     fixture construction in tests cheap.
   - `sealed interface Action : Store.Action` with nested categories under
     `Click`, `Input`, `Navigation`, optionally `Paging`, `Common`. **Navigation actions
     are always grouped under `Action.Navigation` — never modeled as `Event.Navigate*`.**
     See the Canonical navigation pattern below.
   - `sealed interface Event : Store.Event` for **UI side effects only**. Allowed name
     patterns from `MviEventNamingRule` are `*Success`, `*Error`, `*Completed`, `*Started`,
     `*Failed`, `*Requested`, plus tokens `Show`, `Haptic`, `Snackbar`, `Scroll`. Although
     `Navigate` is technically in the rule's pattern list, the project's convention is
     **never to use it** — emit `Action.Navigation.*` instead.

7. Generate handlers under `mvi/handler/`. Use
   `feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/mvi/handler/SettingsClickHandler.kt`
   as the canonical reference. Each handler:

   - Is `internal class`, `@ViewModelScoped`, with `@Inject` constructor injection (the
     only exception is `NavigationHandler` — see Canonical navigation pattern below).
     `MviHandlerConstructorRule` enforces `@Inject` and at least one parameter.
   - Implements `Handler<Action.<Category>>` from
     `core/ui/mvi/src/main/kotlin/io/github/stslex/workeeper/core/ui/mvi/handler/Handler.kt`.
   - Receives the feature's `<Name>HandlerStore` interface (defined in `di/`) to read
     state, mutate state via `updateState { it.copy(...) }`, dispatch other actions via
     `consume(...)`, and emit events via `sendEvent(...)`.

8. Generate `mvi/handler/<Name>Component.kt` extending `Component<Screen.<...>>` from
   `core/ui/navigation/.../Component.kt`. The component carries the route data and exposes
   a `companion object create(navigator: Navigator, ...)` factory that constructs the
   feature's `NavigationHandler`. Reference:
   `feature/all-trainings/.../mvi/handler/AllTrainingsComponent.kt` (no route args) or
   `feature/exercise/.../ui/mvi/handler/ExerciseComponent.kt` (route args carried via
   `data class Screen.Exercise`).

9. Generate `mvi/store/<Name>StoreImpl.kt` extending `BaseStore<State, Action, Event>`,
   annotated `@HiltViewModel(assistedFactory = <Name>StoreImpl.Factory::class)`. The
   `handlerCreator` lambda routes each `Action` subtype to the matching handler; the
   `Action.Navigation` branch casts the assisted `<Name>Component` to the
   `NavigationHandler`. Reference:
   `feature/all-trainings/.../mvi/store/TrainingStoreImpl.kt` (lines 38-45) or
   `feature/settings/.../mvi/store/SettingsStoreImpl.kt`.

10. Generate the Hilt module at `di/<Name>Module.kt` with
    `@InstallIn(ViewModelComponent::class)`. Bind the `<Name>HandlerStore`
    (and any interactor) as `@ViewModelScoped`. Reference:
    `feature/all-trainings/.../di/AllTrainingsModule.kt`.

11. Generate `di/<Name>HandlerStore.kt` (the interface, e.g. `internal interface
    SettingsHandlerStore : HandlerStore<State, Action, Event>`) and
    `di/<Name>HandlerStoreImpl.kt` (`@ViewModelScoped`, `@Inject constructor()`,
    extending `BaseHandlerStore<State, Action, Event>`). Reference:
    `feature/all-trainings/.../di/TrainingHandlerStoreImpl.kt`.

12. Generate `di/<Name>Feature.kt` extending
    `Feature<<Name>StoreProcessor, Screen.<X>, <Name>Component>` and overriding `processor`
    to call `createProcessor<<Name>StoreImpl, <Name>StoreImpl.Factory>(screen)`. Reference:
    `feature/all-trainings/.../di/TrainingsFeature.kt` or
    `feature/settings/.../di/SettingsFeature.kt`. (The legacy `*StoreProcessor.kt` /
    `*Processor.kt` naming in `feature/charts`, `feature/single-training`, and the older
    `feature/exercise/di/ExerciseProcessor.kt` does the same job; new code uses
    `*Feature.kt`.)

13. Generate `ui/<Name>Graph.kt` — a `fun NavGraphBuilder.<feature>Graph(modifier: Modifier
    = Modifier, ...)` extension. Inside, call `navComponentScreen(<Name>Feature) { processor
    -> ... }` and consume only **UI-side** events through `processor.Handle { event -> ... }`
    (haptics, external links, snackbar emissions, scroll commands). Pass
    `processor.state.value` and `processor::consume` into your `<Name>Screen` /
    `<Name>Widget`. Reference:
    `feature/settings/.../ui/SettingsGraph.kt` and
    `feature/all-trainings/.../ui/AllTrainingsGraph.kt`.

14. Wire the navigation graph into the host. Edit
    `app/app/src/main/java/io/github/stslex/workeeper/host/AppNavigationHost.kt` and call
    your new `<feature>Graph(modifier = ..., sharedTransitionScope = this@SharedTransitionLayout)`.
    The `sharedTransitionScope` parameter is only required for graphs that participate in
    shared element transitions — see how `allTrainingsGraph` and `settingsGraph` differ.

15. If the feature is bottom-bar visible, add an entry in
    `app/app/src/main/java/io/github/stslex/workeeper/bottom_app_bar/BottomBarItem.kt`.

16. Generate the screen. `<Name>Screen.kt` (or `<Name>Widget.kt`) is a `@Composable` ending
    in `Screen` and must take both a `*State` parameter and an action/event handler
    parameter — enforced by `ComposableStateRule`.

17. Build the UI from `core/ui/kit` components and tokens (see Design system contract
    below). Hardcoded `Color()`, `sp`, or `dp` outside `core/ui/kit/theme/` are not allowed.
    Every `public` or `internal` `@Composable` you add must ship with `@Preview` functions
    in the same file — see Composable previews below.

18. Add a smoke UI test stub under
    `feature/<name>/src/androidTest/kotlin/.../<Name>ScreenTest.kt` annotated `@Smoke`. Most
    new features start as a stub with a `TODO(feature-rewrite-tests)` marker (see the
    `write-ui-test` skill).

## Canonical navigation pattern

Navigation is **always** routed through a feature's `NavigationHandler`. The graph
composable knows nothing about routes or the `Navigator`. The full rationale lives at
[documentation/architecture.md → Navigation flow (canonical pattern)](../../documentation/architecture.md#navigation-flow-canonical-pattern).
The shape:

1. UI emits `Action.Navigation.<Something>` via `processor::consume(...)`.
2. The `StoreImpl.handlerCreator` routes that action to the feature's `NavigationHandler`
   (typically `is Action.Navigation -> component as NavigationHandler`).
3. `NavigationHandler` receives `Navigator` (constructed by the feature's `Component.create`
   factory at navigation time) and calls `navigator.navTo(Screen.X)` or `navigator.popBack()`.

Concretely, a feature defines:

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
internal class NavigationHandler(
    private val navigator: Navigator,
) : <Name>Component(), Handler<Action.Navigation> {

    override fun invoke(action: Action.Navigation) {
        when (action) {
            Action.Navigation.Back -> navigator.popBack()
            Action.Navigation.OpenArchive -> navigator.navTo(Screen.Archive)
        }
    }
}

// In mvi/handler/<Name>Component.kt:
abstract class <Name>Component : Component<Screen.<X>>(Screen.<X>) {
    companion object {
        fun create(navigator: Navigator): <Name>Component = NavigationHandler(navigator)
    }
}
```

`MviHandlerConstructorRule` explicitly skips the `@Inject` requirement for classes named
`NavigationHandler` (see `lint-rules/.../MviHandlerConstructorRule.kt:74`). Older
NavigationHandlers in the codebase carry `@Suppress("MviHandlerConstructorRule")` — that
suppression is unnecessary for the literal name `NavigationHandler`, only for variants like
`SettingsNavigationHandler`. Either name compiles; prefer the bare `NavigationHandler` per
the all-trainings reference.

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

The graph composable **never** reads `LocalNavigator`, **never** calls `navigator.navTo` /
`navigator.popBack` directly, and **never** consumes a `Navigate*` event (no such event
exists — emitting one is wrong by convention). `LocalNavigator` lives in
`core/ui/navigation/Navigator.kt` only so the root `App.kt` can provide a single instance
into the composition tree.

## Design system contract

All visible UI is built from `core/ui/kit` components and `AppUi.*` token accessors. Full
catalog is in [documentation/design-system.md](../../documentation/design-system.md).
Quick reference:

**Tokens** — read via `AppUi.*` inside any `@Composable`:

- `AppUi.colors` — `LocalAppColors.current` (semantic palette: `accent`, `textPrimary`,
  `textSecondary`, `textTertiary`, surface tiers, semantic statuses).
- `AppUi.typography` — `LocalAppTypography.current` (Inter family, 13-slot M3 scale).
- `AppUi.shapes` — `LocalAppShapes.current` (`small` / `medium` / `large`).
- `AppUi.motion` — `LocalAppMotion.current` (durations + easings).
- `AppUi.elevation` — `LocalAppElevation.current` (color-based surface tier mapping).
- `LocalAppDimension.current` for spacing aliases (`screenEdge`, `cardPadding`, `iconMd`,
  `heightSm`, etc.).

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

The detekt run exercises the custom MVI rules; if any fire, fix them rather than baselining
(see the `refactor-with-mvi-rules` skill).

## Common pitfalls

- **Do not skip `BaseStore`.** Stores must extend `BaseStore`; this is enforced by
  `MviStoreExtensionRule`.
- **Do not put state mutation in `@Composable` functions.** All mutation flows through
  `BaseStore.consume(action)` → handler → `updateState`.
- **Do not use `MutableList` / `MutableSet` / `MutableMap` in `State`.** Use the
  `kotlinx.collections.immutable` types. `MviStateImmutabilityRule` will reject them.
- **Do not use `var` in `State`.** Same rule — properties must be `val`.
- **Do not model navigation as `Event.Navigate*`.** Every navigation target is
  `Action.Navigation.<X>` consumed by the feature's `NavigationHandler`. See the Canonical
  navigation pattern above and the `refactor-with-mvi-rules` skill.
- **Do not read `LocalNavigator` from the graph composable.** The root `App.kt` provides it
  for the composition tree; the canonical read site is `NavigationHandler` (constructed via
  the feature's `Component.create(navigator)` factory).
- **Do not hardcode colors / sizes / type styles in feature code.** Pull from `AppUi.*` and
  the `core/ui/kit` components. See the Design system contract above.
- **Do not ship a `public` or `internal` `@Composable` without a `@Preview`.** Both light
  and dark must be covered (two preview functions, or one with a `@PreviewParameter`-driven
  `ThemeMode`), each visually-distinct state needs its own preview, and stub data must be
  realistic. See Composable previews above. Private helpers are exempt.
- **Do not forget the `convention.composeLibrary` plugin alias** in `build.gradle.kts`.
  Plain `kotlin("jvm")` modules will not get Hilt, Compose, or the lint convention.
- **Do not bypass `Screen` for navigation.** Every navigable destination must be a
  `@Serializable` entry in `core/ui/navigation/.../Screen.kt`.
