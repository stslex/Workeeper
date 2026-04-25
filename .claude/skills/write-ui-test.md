---
name: write-ui-test
description: Write a `@Smoke` Compose UI test that drives a feature widget with mocked state, asserts on `testTag`s, and verifies dispatched MVI actions through `ActionCapture`.
---

# Write a UI test

## When to use

- "Add a UI test for `<Feature>Screen`"
- "Cover the `<Feature>` widget with Compose tests"
- "Write a smoke test for X"
- "Add an accessibility / edge-case test for screen Y"

## Prerequisites

- The feature module's `build.gradle.kts` already has the test dependencies (mirror
  `feature/charts/build.gradle.kts`):

  ```kotlin
  androidTestImplementation(libs.bundles.android.test)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(project(":core:ui:test-utils"))
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  ```

- The screen / widget under test exposes its interactive elements via `Modifier.testTag(...)`.
- `documentation/testing.md` is available for the broader strategy.

## Step-by-step

1. Default to `@Smoke`. Reach for `@Regression` only when the test must drive the real Hilt
   graph and database via `@HiltAndroidTest` + `createAndroidComposeRule<MainActivity>()`.
   See [documentation/testing.md](../../documentation/testing.md) for the choice criteria.

2. Use these reference tests to copy the structure:

   - `feature/all-exercises/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenTest.kt`
   - `feature/all-exercises/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenEdgeCasesTest.kt`
   - `feature/all-exercises/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenAccessibilityTest.kt`

3. Place the new test under
   `feature/<name>/src/androidTest/kotlin/io/github/stslex/workeeper/feature/<name_snake>/<NameOfTest>.kt`.
   Mirror the production package.

4. Skeleton:

   ```kotlin
   @Smoke
   @RunWith(AndroidJUnit4::class)
   @SuppressLint("UnusedContentLambdaTargetStateParameter")
   internal class <Feature>ScreenTest : BaseComposeTest() {

       @get:Rule
       val composeRule = createComposeRule()

       @Test
       fun <feature>_<scenario>_<expectation>() {
           val state = <Feature>Store.State.INITIAL.copy(/* ... */)
           val actionCapture = createActionCapture<<Feature>Store.Action>()

           composeRule.mainClock.autoAdvance = false
           composeRule.setTransitionContent { animatedContentScope, modifier ->
               <Feature>Widget(
                   state = state,
                   modifier = modifier,
                   consume = actionCapture,
                   sharedTransitionScope = this,
                   animatedContentScope = animatedContentScope,
               )
           }

           composeRule.mainClock.advanceTimeBy(100)
           composeRule.waitForIdle()

           composeRule.onNodeWithTag("<Feature>Button").performClick()

           actionCapture.capturedFirst<<Feature>Store.Action.Click.<...>>()
       }
   }
   ```

   Key methods come from `core/ui/test-utils/.../BaseComposeTest.kt`:

   - `setTransitionContent { animatedContentScope, modifier -> ... }` wraps the widget in
     `SharedTransitionScope` + `AnimatedContent`, so widgets expecting
     `sharedTransitionScope` and `animatedContentScope` parameters work without standing up
     `AppNavigationHost`.
   - `createActionCapture<Action>()` returns an `ActionCapture<Action>` you can pass directly as
     the `consume` callback. Inspection helpers: `assertCaptured<A>()`, `capturedFirst<A>()`,
     `capturedLast<A>()`, `assertCapturedExactly(action)`, `assertCapturedCount<A>(n)`,
     `assertCapturedOnce<A>()`, `clear()`, `getAll()`.

5. Use the test-utils helpers for data and inputs:

   - **Paging** state: `PagingTestUtils.createPagingFlow(items)` or
     `PagingTestUtils.createEmptyPagingFlow()`. For error scenarios use
     `PagingTestUtils.TestPagingSource(items, shouldFail = true, errorMessage = "...")`.
   - **Mock data**: `MockDataFactory.createUuids(count)`,
     `MockDataFactory.createTestNames(prefix, count)`,
     `MockDataFactory.createDateProperty(timestamp)`.
   - **Text input**: `composeRule.onNodeWithTag("...").performTextReplacement("new value")`
     (clears + types in one call).

6. Test-tag conventions (must match what the production widget sets):

   - Screen-level: `"<Feature>Screen"`.
   - Widget root: `"<Feature>Widget"`.
   - Buttons: `"<Feature>Button"`, `"<Feature>SaveButton"`, `"<Feature>ActionButton"`.
   - List: `"<Feature>List"`. List item: `"<Feature>Item_${item.uuid}"`.
   - Dialog: `"<Feature>Dialog"`.

   If the production widget is missing a needed tag, add it via `Modifier.testTag(...)` —
   do not work around with positional finders.

7. Cover the obvious cases first (basic render, primary click, primary input). Extend with edge
   cases (empty state, very long text, special characters, large lists, rapid clicks) and
   accessibility checks (`assertHasClickAction()`, focus / semantics) in separate test classes
   when there are enough of them, mirroring the all-exercises split into
   `<...>ScreenTest`, `<...>ScreenEdgeCasesTest`, `<...>ScreenAccessibilityTest`.

## Verification

```bash
# Run smoke tests for the module on a connected device or emulator
./gradlew :feature:<name>:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke
```

Reports land at `feature/<name>/build/reports/androidTests/connected/index.html`.

## Common pitfalls

- **Never combine `@Smoke` with `@HiltAndroidTest`.** Smoke tests do not stand up a real DI
  graph. Use `createComposeRule()`, not `createAndroidComposeRule<MainActivity>()`.
- **Do not use real repositories or the database.** All collaborators are mocked or fed via
  `State`. Real DI belongs in `@Regression` tests only.
- **Do not skip `composeRule.mainClock.advanceTimeBy(100)` + `waitForIdle()`** after
  `setTransitionContent`. The shared-transition wrapper schedules animations, and the assertion
  may run before composition settles.
- **Do not assert on positional `onChildAt(...)` finders.** Use `testTag` / `onNodeWithText`
  finders so the test does not break when layout changes.
- **Do not use `org.junit.jupiter.api.Test`** in instrumented tests. UI tests use AndroidX's
  JUnit 4 runner: `org.junit.Test`, `@RunWith(AndroidJUnit4::class)`.
