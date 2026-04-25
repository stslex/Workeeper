---
name: add-feature
description: Scaffold a new `feature/<name>` Gradle module with the project's MVI + Hilt + Compose conventions, including Store contract, handlers, DI, navigation entry, and a smoke UI test stub.
---

# Add a feature module

## When to use

- "Add a new feature module"
- "Create a feature for X"
- "Scaffold a new screen with MVI"
- "I need a new Compose feature module"

## Prerequisites

- The feature has a name in kebab-case (e.g. `workout-history`).
- The corresponding `Screen` route does not yet exist in `core/ui/navigation/src/main/kotlin/io.github/stslex/workeeper/core/ui/navigation/Screen.kt`.
- `documentation/architecture.md` is available — every step below assumes its conventions and file paths.

## Step-by-step

1. Decide whether the feature needs a domain layer. Features with non-trivial business logic or
   their own interactor (see `feature/exercise/.../domain/`, `feature/single-training/.../domain/`)
   create a `domain/` package. Features that only forward to repositories
   (`feature/all-trainings`, `feature/charts`, `feature/all-exercises`) skip it.

2. Create the module directory tree under `feature/<name>/`:

   ```
   feature/<name>/
   ├── build.gradle.kts
   └── src/
       ├── main/kotlin/io/github/stslex/workeeper/feature/<name_snake>/
       │   ├── di/                 # <Name>Module, <Name>HandlerStore[+Impl], <Name>Processor, <Name>EntryPoint (if needed)
       │   ├── domain/             # only if the feature has its own business logic
       │   ├── mvi/
       │   │   ├── handler/        # ClickHandler, InputHandler, NavigationHandler, optionally PagingHandler / CommonHandler, plus <Name>Component
       │   │   ├── model/          # UI models, mappers
       │   │   └── store/          # <Name>Store contract + <Name>StoreImpl
       │   └── ui/
       │       ├── components/
       │       └── <Name>Screen.kt # or <Name>Widget.kt
       ├── test/kotlin/...         # JUnit 5 unit tests
       └── androidTest/kotlin/...  # @Smoke UI tests
   ```

3. Generate `feature/<name>/build.gradle.kts`. Mirror `feature/charts/build.gradle.kts`:

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

       androidTestImplementation(libs.bundles.android.test)
       androidTestImplementation(libs.androidx.compose.ui.test.junit4)
       androidTestImplementation(project(":core:ui:test-utils"))
       debugImplementation(libs.androidx.compose.ui.test.manifest)
   }
   ```

   Drop dependencies the feature does not use (e.g. omit `:core:exercise` if the feature does
   not touch exercise/training/label repositories).

4. Add the module to the root settings: `include(":feature:<name>")` in `settings.gradle.kts`.

5. Add the route. Edit `core/ui/navigation/src/main/kotlin/io.github/stslex/workeeper/core/ui/navigation/Screen.kt`
   and add a `@Serializable` entry. Bottom-bar destinations go under `Screen.BottomBar`; detail
   destinations are top-level data classes that carry route arguments.

6. Generate the Store contract under `mvi/store/<Name>Store.kt`. Follow the contract conventions
   in [documentation/architecture.md#mvi-contract](../../documentation/architecture.md#mvi-contract):

   - `interface <Name>Store : Store<State, Action, Event>`
   - `data class State(val ...) : Store.State` — properties are `val`; collections are
     `kotlinx.collections.immutable.ImmutableList` / `ImmutableSet` (never `MutableList` etc.).
     Add a `companion object { val INITIAL = State(...) }` for tests.
   - `sealed interface Action : Store.Action` with nested `Click`, `Input`, `Navigation`,
     optionally `Paging` / `Common`.
   - `sealed interface Event : Store.Event` — names follow the patterns enforced by
     `MviEventNamingRule` (e.g. `*Success`, `*Error`, `Haptic*`, `Snackbar*`, `Navigate*`,
     `Scroll*`).

7. Generate handlers under `mvi/handler/`. Use `feature/exercise/.../mvi/handler/ClickHandler.kt`
   as the canonical reference. Each handler:

   - is `@ViewModelScoped` and uses `@Inject` constructor injection (except `NavigationHandler`,
     which constructs from the `<Name>Component`).
   - implements `Handler<Action.<Category>>` from `core/ui/mvi/.../handler/Handler.kt`.
   - receives the feature's `<Name>HandlerStore` to read state, mutate state, and emit events.

8. Generate `mvi/handler/<Name>Component.kt` extending `Component<Screen.<...>>` from
   `core/ui/navigation/.../Component.kt`, plus `<Name>ComponentImpl` that the navigation graph
   constructs. See `feature/exercise/.../mvi/handler/ExerciseComponent.kt`.

9. Generate `mvi/store/<Name>StoreImpl.kt` extending `BaseStore<State, Action, Event>`,
   annotated `@HiltViewModel(assistedFactory = <Name>StoreImpl.Factory::class)`. The
   `handlerCreator` lambda routes each `Action` subtype to the matching handler. See
   `feature/exercise/.../ui/mvi/store/ExerciseStoreImpl.kt`.

10. Generate the Hilt module at `di/<Name>Module.kt` with
    `@InstallIn(ViewModelComponent::class)`. Bind the `<Name>HandlerStore` (and any interactor)
    as `@ViewModelScoped`. See `feature/exercise/.../di/ExerciseModule.kt`.

11. Generate `di/<Name>HandlerStoreImpl.kt` as a `@ViewModelScoped` `@Inject` class extending
    `BaseHandlerStore<State, Action, Event>`. See
    `feature/exercise/.../di/ExerciseHandlerStoreImpl.kt`.

12. Generate `di/<Name>Processor.kt` implementing `Feature<TProcessor, TScreen, TComponent>`
    from `core/ui/mvi/.../Feature.kt` and a `<feature>Graph(...)` extension on `NavGraphBuilder`.
    See `feature/charts/.../di/ChartsStoreProcessor.kt`.

13. Wire the navigation graph. Edit `app/app/src/main/java/io/github/stslex/workeeper/host/AppNavigationHost.kt`
    and call your new `<feature>Graph(modifier = ..., sharedTransitionScope = this@SharedTransitionLayout)`.

14. If the feature is bottom-bar visible, add an entry in
    `app/app/src/main/java/io/github/stslex/workeeper/bottom_app_bar/BottomBarItem.kt`.

15. Generate the screen. `<Name>Screen.kt` (or `<Name>Widget.kt`) is a `@Composable` ending in
    `Screen` and must take a `*State` parameter and an action/event handler parameter — this is
    enforced by `ComposableStateRule`.

16. Add a smoke UI test stub under
    `feature/<name>/src/androidTest/kotlin/.../<Name>ScreenTest.kt` annotated `@Smoke`. Use
    `BaseComposeTest` with `setTransitionContent { ... }`. See
    [documentation/testing.md](../../documentation/testing.md) and the `write-ui-test` skill.

## Verification

```bash
./gradlew :feature:<name>:detekt :feature:<name>:lintDebug --no-configuration-cache
```

Both must pass. The detekt run exercises the custom MVI rules; if any fire, fix them rather
than baselining (see the `refactor-with-mvi-rules` skill).

## Common pitfalls

- **Do not skip `BaseStore`.** Stores must extend `BaseStore`; this is enforced by
  `MviStoreExtensionRule`.
- **Do not put state mutation in `@Composable` functions.** All mutation flows through
  `BaseStore.consume(action)` → handler → `updateState`.
- **Do not use `MutableList` / `MutableSet` / `MutableMap` in `State`.** Use the
  `kotlinx.collections.immutable` types. `MviStateImmutabilityRule` will reject them.
- **Do not use `var` in `State`.** Same rule — properties must be `val`.
- **Do not forget the `convention.composeLibrary` plugin alias** in `build.gradle.kts`. Plain
  `kotlin("jvm")` modules will not get Hilt, Compose, or the lint convention.
- **Do not bypass `Screen` for navigation.** Every navigable destination must be a
  `@Serializable` entry in `core/ui/navigation/.../Screen.kt`.
