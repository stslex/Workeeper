---
name: write-handler-test
description: Write a JUnit 5 unit test for an MVI Handler or StoreImpl using the project's mocked `<Name>HandlerStore` + `MutableStateFlow` pattern from `feature/settings/`. Includes a NavigationHandler template that mocks `Navigator` and asserts `navTo` / `popBack` per `Action.Navigation`.
---

# Write a handler / store unit test

## When to use

- "Add a unit test for `<...>Handler`"
- "Test the click handler for feature X"
- "Cover `<...>StoreImpl` with unit tests"
- "Write tests for an MVI action"
- "Add a NavigationHandler test"

## Prerequisites

- The handler or store under test exists.
- The feature's module already pulls in `kotlin("test")` and the project's `test` bundle
  (every feature `build.gradle.kts` does — see `feature/settings/build.gradle.kts`).
- [documentation/testing.md](../../documentation/testing.md) has the broader strategy.

## Reference tests

Use the **`feature/settings/`** test classes as the canonical patterns — they are the only
real handler tests in the codebase right now. The older feature handler tests in
`feature/all-trainings`, `feature/all-exercises`, `feature/exercise`,
`feature/single-training`, and `feature/charts` are 10-line `placeholder() = Unit` stubs
with `TODO(feature-rewrite)` markers; **do not copy from them** — they exist only so the
modules still compile.

Canonical references:

- `feature/settings/src/test/kotlin/io/github/stslex/workeeper/feature/settings/mvi/handler/SettingsClickHandlerTest.kt`
  — straight `verify { store.sendEvent(...) }` / `verify { store.consume(...) }` with no
  state mutation.
- `feature/settings/src/test/kotlin/io/github/stslex/workeeper/feature/settings/mvi/handler/SettingsInputHandlerTest.kt`
  — uses `MutableStateFlow` + `every { updateState(any()) } answers { ... }` to actually
  mutate state inside the test.
- `feature/settings/src/test/kotlin/io/github/stslex/workeeper/feature/settings/mvi/handler/ArchiveClickHandlerTest.kt`
  — fuller example with `coVerify`, `slot<T>()` capture, and state-conditional branches.
- `feature/settings/src/test/kotlin/io/github/stslex/workeeper/feature/settings/mvi/handler/SettingsNavigationHandlerTest.kt`
  — minimal `Navigator` mock + per-`Action.Navigation` assertion. See
  [NavigationHandler test template](#navigationhandler-test-template) below.

## Step-by-step

1. Place the new test under
   `feature/<name>/src/test/kotlin/io/github/stslex/workeeper/feature/<name_snake>/<sub-package>/<HandlerName>Test.kt`,
   mirroring the production package. Use `internal class` visibility.

2. Use these imports (drop the ones you do not need):

   ```kotlin
   import io.mockk.coEvery
   import io.mockk.coVerify
   import io.mockk.every
   import io.mockk.mockk
   import io.mockk.slot
   import io.mockk.verify
   import kotlinx.coroutines.flow.MutableStateFlow
   import org.junit.jupiter.api.Assertions.assertEquals
   import org.junit.jupiter.api.Assertions.assertTrue
   import org.junit.jupiter.api.Test
   ```

   Always use `org.junit.jupiter.api.Test` (JUnit 5), never `org.junit.Test`.

3. Build the test fixture. The current project pattern is intentionally minimal — no
   `TestScope`, no `UnconfinedTestDispatcher`, no `AppCoroutineScope` plumbing. Mock the
   `<Name>HandlerStore` directly and stub only what the handler actually reads:

   ```kotlin
   private val store = mockk<<Name>HandlerStore>(relaxed = true)
   private val handler = <Name>ClickHandler(store)
   ```

   For a handler that mutates state via `updateState { it.copy(...) }`, wire the state
   flow so the lambda runs:

   ```kotlin
   private val initialState = State(/* ... */)
   private val stateFlow = MutableStateFlow(initialState)
   private val store = mockk<<Name>HandlerStore>(relaxed = true).apply {
       every { state } returns stateFlow
       every { updateState(any()) } answers {
           val update = firstArg<(State) -> State>()
           stateFlow.value = update(stateFlow.value)
       }
   }
   ```

   For collaborators (interactor, mapper, repository), use
   `mockk<X>(relaxed = true)` and stub specific calls with `coEvery { ... } returns ...`
   (suspend) or `every { ... } returns ...` (plain).

4. Construct the handler under test with the mocked store and collaborators.

5. Write **one `@Test` method per `Action` subclass** the handler dispatches. Naming
   convention used in the codebase (backtick-quoted descriptions, mirroring
   `SettingsClickHandlerTest`):

   ```kotlin
   @Test
   fun `OnArchiveClick emits Haptic ContextClick then consumes OpenArchive`() { /* ... */ }
   ```

6. Inside each test:

   - Call the handler: `handler.invoke(Action.Click.OnArchiveClick)`. If the handler is
     suspending, wrap in `runTest { ... }`.
   - Assert state mutations with `assertEquals(expected, stateFlow.value.<field>)`.
   - Assert events with a `mutableListOf<Event>()` capture or `slot<Event>()`:
     ```kotlin
     val captured = mutableListOf<Event>()
     verify(exactly = 1) { store.sendEvent(capture(captured)) }
     assertHaptic(captured.single(), HapticFeedbackType.ContextClick)
     ```
     Or for a single emission:
     ```kotlin
     val captured = slot<Event>()
     verify(exactly = 1) { store.sendEvent(capture(captured)) }
     assertEquals(Event.ShowExternalLink(URL), captured.captured)
     ```
   - Assert dispatched actions with `verify { store.consume(eq(Action.Navigation.OpenArchive)) }`.
   - Assert collaborator calls with `coVerify { interactor.save(any()) }` for suspend
     functions, `verify` for plain ones.
   - Capture argument shapes with `slot<T>()` when the assertion needs the actual payload.
   - Pull a small `assertHaptic(event, expected)` private helper (see
     `SettingsClickHandlerTest.kt:56-59`) when many tests assert on `Event.Haptic` shape.

7. Use `kotlinx.collections.immutable.persistentListOf()` / `persistentSetOf()` for
   collection literals in the State fixture — `State` requires immutable collections.

## NavigationHandler test template

The full template, lifted directly from
`feature/settings/.../SettingsNavigationHandlerTest.kt`:

```kotlin
package io.github.stslex.workeeper.feature.<name_snake>.mvi.handler

import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.<name_snake>.mvi.store.<Name>Store.Action
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

internal class NavigationHandlerTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val handler = NavigationHandler(navigator)

    @Test
    fun `Back triggers popBack`() {
        handler.invoke(Action.Navigation.Back)
        verify(exactly = 1) { navigator.popBack() }
    }

    @Test
    fun `OpenArchive navigates to Screen Archive`() {
        handler.invoke(Action.Navigation.OpenArchive)
        verify(exactly = 1) { navigator.navTo(Screen.Archive) }
    }
}
```

Notes:

- `NavigationHandler` is constructed directly with the mocked `Navigator` — no Hilt graph,
  no store, no `HandlerStore` mock. The class skips `@Inject` because
  `MviHandlerConstructorRule` exempts the literal name `NavigationHandler` (see
  `lint-rules/.../MviHandlerConstructorRule.kt:74`); test instantiation uses the same
  plain constructor.
- One `@Test` per `Action.Navigation.<X>` subclass. Each verifies exactly one
  `navigator.navTo(Screen.<X>)` or `navigator.popBack()` call, with `exactly = 1`.
- For variants like `SettingsNavigationHandler` that nest under a `*Component` for
  feature-specific reasons, instantiate with the same plain constructor — the test does
  not need to know about the `Component` superclass.

## Verification

```bash
./gradlew :feature:<name>:testDebugUnitTest
```

Run the full module test task. Targeting a single class also works:

```bash
./gradlew :feature:<name>:testDebugUnitTest --tests "*.<HandlerName>Test"
```

## Common pitfalls

- **One action per test.** Do not pack multiple `handler.invoke(...)` calls with different
  `Action` subtypes into one `@Test`. The `feature/settings/` tests have one assertion
  thread per case; match that.
- **Do not mock the `Store` itself.** Mock the `<Name>HandlerStore` (the handler's
  collaborator). The store is the system under test for `*StoreImpl` tests, never a mock
  there.
- **Do not import `org.junit.Test`.** This codebase is on JUnit 5. The Robolectric
  extension plugin (`robolectric-junit5`) is wired for JVM tests that need Android stubs.
- **Do not stand up `TestScope` / `UnconfinedTestDispatcher` / `AppCoroutineScope`
  unless the handler under test actually launches coroutines through `store.launch { ... }`.**
  The current `feature/settings/` tests demonstrate that mocking the `HandlerStore` with
  `relaxed = true` and stubbing only `state` + `updateState` covers most handler shapes.
  Reach for the test scheduler stack only when a handler uses `launch { }` or `flow {}`.
- **Do not assert on private fields.** Drive tests through public `Action` invocations and
  observe the public `state`/`event`/`consume` surface on the mocked HandlerStore.
- **Do not copy the older 10-line `placeholder() = Unit` stubs.** They mark handlers
  awaiting a real test pass; the patterns above are what to write in their place.
