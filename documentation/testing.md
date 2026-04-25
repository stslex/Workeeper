# Testing

This document covers how tests are structured, written, and executed in Workeeper, plus where
they run in CI. For the architectural patterns the tests target, see
[architecture.md](architecture.md). For pipeline mechanics, see [ci-cd.md](ci-cd.md).

## Test types

Workeeper has two test source sets in every module that has tests:

- `src/test/...` — JVM unit tests. Run via `./gradlew testDebugUnitTest`. Use JUnit 5 (Jupiter)
  via the `junit-bom`, MockK for mocking, Robolectric for Android-class fakes, and the
  Kotlin coroutines test library. Bundles are declared in `gradle/libs.versions.toml` under
  `[bundles] test`.
- `src/androidTest/...` — instrumented UI tests. Run via `./gradlew connectedDebugAndroidTest`.
  Use the AndroidX Compose UI test runner (`androidx-compose-ui-test-junit4`),
  `androidx.test:runner`, Espresso, and Hilt's testing artifacts. Bundles are declared under
  `[bundles] android-test`.

Both source sets exist on a per-module basis. When a feature module needs the shared test
utilities, it adds `androidTestImplementation(project(":core:ui:test-utils"))` to its
`build.gradle.kts`.

## Unit tests

### Conventions

- Test classes end with `Test` (e.g. `ExerciseRepositoryImplTest`).
- Tests use the JUnit Jupiter API: `@Test`, `@BeforeEach`, `@AfterEach`, `@Nested`. The
  Robolectric extension is wired in via `tech.apter.junit5.jupiter.robolectric-extension-gradle-plugin`
  (alias `robolectric-junit5` in the version catalog) for tests that need Android stubs.
- MockK is the default mocking library; use `mockk<Type>()` and `every { ... } returns ...` /
  `coEvery { ... } returns ...`.

### Testing MVI handlers and stores

A typical handler test instantiates the handler with a mock `<Name>HandlerStoreImpl` (because
that is what `BaseStore` injects as `storeEmitter`), drives it with `invoke(action)`, and
verifies the resulting `state` mutations or `sendEvent` calls. Keep handler logic free of
direct framework dependencies so the unit test can run on the JVM.

For store-level tests, prefer covering the handler logic (smaller surface) rather than spinning
up the full `BaseStore` machinery. When a `BaseStore` test is necessary, use the `coroutine-test`
library (declared in the `test` bundle) and provide test dispatchers via `StoreDispatchers`.

## UI tests

### Categorization with `@Smoke` and `@Regression`

Annotations are defined in
`core/ui/test-utils/src/main/kotlin/io/github/stslex/workeeper/core/ui/test/annotations/{Smoke,Regression}.kt`.
Every UI test class must carry exactly one of them.

- `@Smoke` — fast, mocked-data tests using `createComposeRule()`. No Hilt, no real database, no
  full activity. Pick this for component-level checks: visibility, interactions, edge inputs,
  accessibility semantics.
- `@Regression` — full integration tests using `createAndroidComposeRule<MainActivity>()` and
  `@HiltAndroidTest`. Spin up the real DI container and database. Reserve for end-to-end flows
  that span multiple features.

The annotation governs which suite the test runs in. The CI workflow filters on the fully
qualified annotation name via
`-Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.{Smoke|Regression}`.

### Choosing a category

Use `@Smoke` when the test:

- Constructs `*Store.State` directly with mocked data.
- Does not need a real Hilt graph, real database, or real APIs.
- Targets a single widget or screen with `consume = ...` wired by hand.

Use `@Regression` when the test:

- Annotates with `@HiltAndroidTest` and uses a `HiltAndroidRule`.
- Launches `MainActivity` and exercises the real navigation graph.
- Asserts against persisted state across screens.

Smoke tests should be the default. Add a regression test only when the integration aspect is
itself the thing under test.

### `BaseComposeTest`

`core/ui/test-utils/src/main/kotlin/io/github/stslex/workeeper/core/ui/test/BaseComposeTest.kt`
provides:

- `ComposeContentTestRule.setTransitionContent { animatedContentScope, modifier -> ... }` —
  wraps content in an `AnimatedContent` + `SharedTransitionScope` so widgets that take a
  `SharedTransitionScope` and `AnimatedContentScope` can be exercised in tests without setting
  up the whole `AppNavigationHost`.
- `createActionCapture<Action>()` — returns an `ActionCapture<T>` instance.
- `ActionCapture<T : Store.Action>` — invokable as `(T) -> Unit`, suitable to pass as the
  widget's `consume` callback. Inspection helpers:
  - `assertCaptured<A>()` — returns all actions of type `A`, errors if none.
  - `captured<A>()` — returns the matching list (no error).
  - `capturedFirst<A>() / capturedLast<A>()` — convenience accessors.
  - `assertCapturedExactly(action)` — verifies a specific action value was emitted.
  - `assertCapturedCount<A>(n)` / `assertCapturedOnce<A>()` — count assertions.
  - `clear()` / `getAll()` — reset and inspect.

### Mock data and paging helpers

- `MockDataFactory`
  (`core/ui/test-utils/.../MockDataFactory.kt`) — `createUuid()`, `createUuids(count)`,
  `createDateProperty(timestamp)`, `createTestNames(prefix, count, startIndex)`. Use these to
  keep test data deterministic across runs.
- `PagingTestUtils`
  (`core/ui/test-utils/.../PagingTestUtils.kt`) — `createPagingFlow(items)`,
  `createEmptyPagingFlow()`, `createErrorPagingFlow()`, plus a `TestPagingSource<T>` with
  `shouldFail` and `errorMessage` parameters when you need a real `PagingSource` that simulates
  failure.
- `ComposeTestUtils`
  (`core/ui/test-utils/.../ComposeTestUtils.kt`) — `SemanticsNodeInteraction.performTextReplacement(text)`
  clears existing text and types in one call.

### Skeleton: smoke test

```kotlin
@Smoke
@RunWith(AndroidJUnit4::class)
class MyFeatureScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun feature_action_expectedResult() {
        val state = MyStore.State.INITIAL.copy(/* ... */)
        val actionCapture = createActionCapture<MyStore.Action>()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            MyFeatureWidget(
                state = state,
                modifier = modifier,
                consume = actionCapture,
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("MyButton").performClick()

        actionCapture.capturedFirst<MyStore.Action.Click.MyButton>()
    }
}
```

### Skeleton: regression test

```kotlin
@Regression
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MyFullAppTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_navigates_from_a_to_b() {
        // Real DI graph, real database, full Compose host.
    }
}
```

### Test-tag naming

Use `Modifier.testTag("...")` with these prefixes so finders are stable across PRs:

- Screen-level tag: `"<Feature>Screen"`.
- Widget-level tag: `"<Feature>Widget"`.
- Buttons: `"<Feature>Button"`, `"<Feature>SaveButton"`, etc.
- List: `"<Feature>List"`. List item: `"<Feature>Item_${item.uuid}"`.
- Dialog: `"<Feature>Dialog"`.

`app/app/src/main/java/io/github/stslex/workeeper/host/AppNavigationHost.kt` uses graph-level
tags (`"ChartsGraph"`, `"AllTrainingsGraph"`, etc.) for cross-feature tests.

### Existing UI tests

| Feature | Test class |
|---|---|
| `feature/all-trainings` | `feature/all-trainings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt` |
| `feature/all-exercises` | `feature/all-exercises/src/androidTest/.../AllExercisesScreenTest.kt`, `AllExercisesScreenAccessibilityTest.kt`, `AllExercisesScreenEdgeCasesTest.kt` |
| `feature/single-training` | `feature/single-training/src/androidTest/.../SingleTrainingScreenTest.kt` |
| `feature/exercise` | `feature/exercise/src/androidTest/.../ExerciseScreenTest.kt` |
| `feature/charts` | `feature/charts/src/androidTest/.../ChartsScreenTest.kt` |

## Running tests

From the project root:

```bash
# Unit tests (JVM, fast)
./gradlew testDebugUnitTest

# Every UI test in every module (slow; emulator required)
./gradlew connectedDebugAndroidTest

# Smoke UI tests only
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke \
  --continue

# Regression UI tests only
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Regression \
  --continue

# Single module
./gradlew :feature:exercise:connectedDebugAndroidTest

# Single test class / method
./gradlew connectedDebugAndroidTest --tests "*.ExerciseScreenTest"
./gradlew connectedDebugAndroidTest --tests "*.ExerciseScreenTest.exercise_save_emitsAction"

# With diagnostic output
./gradlew connectedDebugAndroidTest --info --stacktrace
```

`--continue` lets all modules run even if one fails, which matters for the per-module
emulator setup used by the optional UI workflow (see [ci-cd.md](ci-cd.md#ui-test-workflow)).

## Reading reports

After a run, each module writes:

- HTML reports under `<module>/build/reports/androidTests/` (UI) or
  `<module>/build/reports/tests/` (unit).
- Raw JUnit XML under `<module>/build/outputs/androidTest-results/connected/` (UI) or
  `<module>/build/test-results/test*/` (unit). These are what CI consumes.

In CI, the `build` job uploads detekt and lint reports as artifacts and the optional UI
workflow uploads test reports, logcat, and screenshots-on-failure. Details and check names live
in [ci-cd.md](ci-cd.md).

## CI behavior

- **Unit tests run on every PR and on pushes to `master`** as part of `android_build_unified.yml`.
- **UI tests are optional and manual.** The `ui_tests.yml` workflow is `workflow_dispatch`-only
  with a `smoke` / `regression` / `all` selector. They do not block PR merges. Run them
  locally before opening a PR that touches Compose code.

See [ci-cd.md](ci-cd.md) for the full pipeline.
