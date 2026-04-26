---
name: write-ui-test
description: Add a `@Smoke` Compose UI test for a feature screen — either a stub with a `TODO(feature-rewrite-tests)` marker (the current default during the v1 rewrite) or a full-fidelity test that drives a widget with mocked state, asserts on `testTag`s, and verifies dispatched MVI actions through `ActionCapture`.
---

# Write a UI test

## When to use

- "Add a UI test for `<Feature>Screen`"
- "Cover the `<Feature>` widget with Compose tests"
- "Write a smoke test for X"
- "Add an accessibility / edge-case test for screen Y"
- "Stub a `@Smoke` test for the new feature"

## Current state of UI tests in the repo

Every feature module currently has a placeholder `@Smoke` UI test class with no body —
just `BaseComposeTest` + `createComposeRule()` + a `TODO(feature-rewrite-tests)` (or
`TODO(feature-rewrite)`) comment. The Stage 3 cleanup left these in place so the modules
still compile and the `@Smoke` annotation surface is wired, but real assertions are
deferred until each feature stabilizes during the v1 rewrite. Examples:

- `feature/all-trainings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt`
- `feature/settings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/settings/SettingsScreenTest.kt`
- `feature/settings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/settings/ArchiveScreenTest.kt`
- All five `feature/*/src/androidTest/.../*ScreenTest.kt` follow the same shape.

Default: when scaffolding a brand-new feature, ship the **stub** form (see
[Stub form](#stub-form-default-for-new-features) below). When the feature stabilizes and
you want real coverage, expand the stub into the [Full form](#full-form-when-the-feature-is-stable).

## Prerequisites

- The feature module's `build.gradle.kts` already has the test dependencies (mirror
  `feature/settings/build.gradle.kts`):

  ```kotlin
  androidTestImplementation(libs.bundles.android.test)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(project(":core:ui:test-utils"))
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  ```

- For full-form tests, the screen / widget under test exposes its interactive elements via
  `Modifier.testTag(...)`.
- [documentation/testing.md](../../documentation/testing.md) has the broader strategy.

## Stub form (default for new features)

Place the new test under
`feature/<name>/src/androidTest/kotlin/io/github/stslex/workeeper/feature/<name_snake>/<Name>ScreenTest.kt`.
Mirror the production package. Lifted directly from
`feature/settings/.../SettingsScreenTest.kt`:

```kotlin
package io.github.stslex.workeeper.feature.<name_snake>

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import org.junit.Rule
import org.junit.runner.RunWith

@Smoke
@RunWith(AndroidJUnit4::class)
class <Name>ScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // TODO(feature-rewrite-tests): exercise <name> after Stage <X> stabilises.
}
```

Notes on the stub:

- The class still extends `BaseComposeTest` and declares `createComposeRule()` so the test
  surface is wired and the module compiles cleanly with the `@Smoke` annotation
  filterable from CI.
- The `TODO(...)` text records what to add and when. Two markers exist in the codebase —
  `TODO(feature-rewrite-tests)` (newer, used by `feature/settings/`) and
  `TODO(feature-rewrite)` (older, used by `feature/all-trainings/` etc.). Either is fine;
  the new convention is `TODO(feature-rewrite-tests)`.
- No `@Test` methods are required for the stub. The class compiles and is picked up by the
  `@Smoke` filter as a no-op.

## Full form (when the feature is stable)

When the feature is no longer in active rewrite, expand the stub into a fully-asserting
smoke test. There are no current full-form examples in the repo (all real tests are in the
handler-test layer), so the template below is derived from `BaseComposeTest` and the
test-utils helpers in `core/ui/test-utils/`.

1. Default to `@Smoke`. Reach for `@Regression` only when the test must drive the real
   Hilt graph and database via `@HiltAndroidTest` + `createAndroidComposeRule<MainActivity>()`.
   See [documentation/testing.md → Choosing a category](../../documentation/testing.md#choosing-a-category).

2. Use this skeleton for a screen that participates in shared element transitions:

   ```kotlin
   @Smoke
   @RunWith(AndroidJUnit4::class)
   @SuppressLint("UnusedContentLambdaTargetStateParameter")
   internal class <Name>ScreenTest : BaseComposeTest() {

       @get:Rule
       val composeRule = createComposeRule()

       @Test
       fun <feature>_<scenario>_<expectation>() {
           val state = <Name>Store.State.INITIAL.copy(/* ... */)
           val actionCapture = createActionCapture<<Name>Store.Action>()

           composeRule.mainClock.autoAdvance = false
           composeRule.setTransitionContent { animatedContentScope, modifier ->
               <Name>Widget(
                   state = state,
                   modifier = modifier,
                   consume = actionCapture,
                   sharedTransitionScope = this,
                   animatedContentScope = animatedContentScope,
               )
           }

           composeRule.mainClock.advanceTimeBy(100)
           composeRule.waitForIdle()

           composeRule.onNodeWithTag("<Name>Button").performClick()

           actionCapture.capturedFirst<<Name>Store.Action.Click.<...>>()
       }
   }
   ```

   For screens without shared element transitions (e.g. Settings / Archive), drop the
   `setTransitionContent` wrapper and call `composeRule.setContent { ... }` with the
   screen composable directly.

3. Helpers from `core/ui/test-utils/.../BaseComposeTest.kt`:

   - `setTransitionContent { animatedContentScope, modifier -> ... }` wraps the widget in
     `SharedTransitionScope` + `AnimatedContent`, so widgets expecting
     `sharedTransitionScope` and `animatedContentScope` parameters work without standing
     up `AppNavigationHost`.
   - `createActionCapture<Action>()` returns an `ActionCapture<Action>` you can pass
     directly as the `consume` callback. Inspection helpers:
     `assertCaptured<A>()`, `captured<A>()`, `capturedFirst<A>()`, `capturedLast<A>()`,
     `assertCapturedExactly(action)`, `assertCapturedCount<A>(n)`,
     `assertCapturedOnce<A>()`, `clear()`, `getAll()`.

4. Helpers for data and inputs:

   - **Paging** state: `PagingTestUtils.createPagingFlow(items)` /
     `PagingTestUtils.createEmptyPagingFlow()` /
     `PagingTestUtils.createErrorPagingFlow()`. For richer error scenarios:
     `PagingTestUtils.TestPagingSource(items, shouldFail = true, errorMessage = "...")`.
   - **Mock data**: `MockDataFactory.createUuids(count)`,
     `MockDataFactory.createTestNames(prefix, count)`,
     `MockDataFactory.createDateProperty(timestamp)`.
   - **Text input**: `composeRule.onNodeWithTag("...").performTextReplacement("new value")`
     (clears + types in one call).

5. Test-tag conventions (must match what the production widget sets):

   - Screen-level: `"<Feature>Screen"`.
   - Widget root: `"<Feature>Widget"`.
   - Buttons: `"<Feature>Button"`, `"<Feature>SaveButton"`, `"<Feature>ActionButton"`.
   - List: `"<Feature>List"`. List item: `"<Feature>Item_${item.uuid}"`.
   - Dialog: `"<Feature>Dialog"`.

   If the production widget is missing a needed tag, add it via `Modifier.testTag(...)`
   — do not work around with positional finders.

6. Cover the obvious cases first (basic render, primary click, primary input). Extend with
   edge cases (empty state, very long text, special characters, large lists, rapid clicks)
   and accessibility checks (`assertHasClickAction()`, focus / semantics) in separate test
   classes when there are enough of them, mirroring the all-exercises naming split into
   `<Name>ScreenTest`, `<Name>ScreenEdgeCasesTest`, `<Name>ScreenAccessibilityTest`. Note
   that those split files currently also exist as 18-line stubs — the naming pattern is
   what to mirror, not the bodies.

## Verification

```bash
# Run smoke tests for the module on a connected device or emulator
./gradlew :feature:<name>:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke
```

Reports land at `feature/<name>/build/reports/androidTests/connected/index.html`.

## Common pitfalls

- **Never combine `@Smoke` with `@HiltAndroidTest`.** Smoke tests do not stand up a real
  DI graph. Use `createComposeRule()`, not `createAndroidComposeRule<MainActivity>()`.
- **Do not use real repositories or the database.** All collaborators are mocked or fed
  via `State`. Real DI belongs in `@Regression` tests only.
- **Do not delete an existing stub when scaffolding a new feature** — the stub is the
  intended scaffold. Replace it with the full form when the feature stabilizes.
- **Do not skip `composeRule.mainClock.advanceTimeBy(100)` + `waitForIdle()`** after
  `setTransitionContent`. The shared-transition wrapper schedules animations, and the
  assertion may run before composition settles.
- **Do not assert on positional `onChildAt(...)` finders.** Use `testTag` /
  `onNodeWithText` finders so the test does not break when layout changes.
- **Do not use `org.junit.jupiter.api.Test`** in instrumented tests. UI tests use
  AndroidX's JUnit 4 runner: `org.junit.Test`, `@RunWith(AndroidJUnit4::class)`.
