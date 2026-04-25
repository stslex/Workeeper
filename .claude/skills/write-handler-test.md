---
name: write-handler-test
description: Write a JUnit 5 unit test for an MVI Handler or StoreImpl using the project's mocked `HandlerStore` + `TestScope` pattern from `feature/single-training`.
---

# Write a handler / store unit test

## When to use

- "Add a unit test for `<...>Handler`"
- "Test the click handler for feature X"
- "Cover `<...>StoreImpl` with unit tests"
- "Write tests for an MVI action"

## Prerequisites

- The handler or store under test exists.
- The feature's module already pulls in `kotlin("test")` and the project's `test` bundle (most
  feature `build.gradle.kts` files do — see `feature/charts/build.gradle.kts`).
- `documentation/testing.md` is available for the broader strategy.

## Step-by-step

1. Locate a reference test in the same feature when one exists. Otherwise use:

   - `feature/single-training/src/test/kotlin/io/github/stslex/workeeper/feature/single_training/ui/mvi/handler/ClickHandlerTest.kt`
   - `feature/exercise/src/test/kotlin/io/github/stslex/workeeper/feature/exercise/ui/mvi/handler/ClickHandlerTest.kt`
   - `feature/all-exercises/src/test/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/mvi/handler/PagingHandlerTest.kt`

   These set the project conventions.

2. Place the new test under
   `feature/<name>/src/test/kotlin/io/github/stslex/workeeper/feature/<name_snake>/<sub-package>/<HandlerName>Test.kt`,
   mirroring the production package. Use `internal class` visibility.

3. Use these imports:

   ```kotlin
   import org.junit.jupiter.api.Test
   import org.junit.jupiter.api.Assertions.assertEquals
   import io.mockk.coEvery
   import io.mockk.coVerify
   import io.mockk.every
   import io.mockk.mockk
   import io.mockk.verify
   import kotlinx.coroutines.flow.MutableStateFlow
   import kotlinx.coroutines.test.TestCoroutineScheduler
   import kotlinx.coroutines.test.TestScope
   import kotlinx.coroutines.test.UnconfinedTestDispatcher
   import kotlinx.coroutines.test.runTest
   import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
   ```

   Always use `org.junit.jupiter.api.Test` (JUnit 5), never `org.junit.Test`.

4. Build the test fixture:

   - Construct any collaborators (interactor, mapper, repository) with
     `mockk<X>(relaxed = true)`.
   - Build a `TestCoroutineScheduler`, an `UnconfinedTestDispatcher(scheduler)`, and a
     `TestScope(dispatcher)`.
   - Build the initial `<Store>.State` and put it inside a `MutableStateFlow`.
   - Construct a `mockk<<Name>HandlerStore>(relaxed = true)` and stub:
     - `every { state } returns stateFlow`
     - `every { scope } returns AppCoroutineScope(testScope, dispatcher, dispatcher)`
     - For handlers that call `store.launch { ... }`, stub the `launch` overload to actually
       execute the coroutine. Copy the `every { this@mockk.launch<Any>(...) } answers { ... }`
       block from `feature/single-training/.../ClickHandlerTest.kt` lines 67-80.

5. Construct the handler under test with the mocked store and collaborators.

6. Write **one `@Test` method per `Action` subclass** the handler dispatches. The naming
   convention used in the codebase: `<actionInWords>_<expectation>()`, for example
   `clickSave_callsInteractor()` or `clickClose_emitsHapticAndPopsBack()`.

7. Inside each test:

   - Call the handler: `handler.invoke(Action.Click.Save)` (or use `@Test fun foo() = runTest { ... }` if you need to await suspending behavior).
   - Assert state mutations with `assertEquals(expected, stateFlow.value)`.
   - Assert events with `verify { store.sendEvent(eq(Event.Haptic)) }`.
   - Assert collaborator calls with `coVerify { interactor.save(any()) }` (use `coVerify` for
     suspend functions; `verify` for plain ones).
   - Capture argument shapes with `slot<T>()` when the assertion needs the actual payload.

8. Use `kotlinx.collections.immutable.persistentListOf()` / `persistentSetOf()` for collection
   literals in the fixture — `State` requires immutable collections.

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
  `Action` subtypes into one `@Test`. The reference tests have one assertion thread per case;
  match that.
- **Do not mock the `Store` itself.** Mock the `<Name>HandlerStore` (the handler's collaborator).
  The store is the system under test for `*StoreImpl` tests, never a mock there.
- **Do not import `org.junit.Test`.** This codebase is on JUnit 5. The Robolectric extension
  plugin (`robolectric-junit5`) is wired for JVM tests that need Android stubs.
- **Do not use real dispatchers.** Use `UnconfinedTestDispatcher` and `TestScope`. Real
  dispatchers will cause flaky waits.
- **Do not assert on private fields.** Drive tests through public `Action` invocations and
  observe the public `state`/`event` surface.
