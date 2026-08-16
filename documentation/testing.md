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
  `androidx.test:runner`, and Espresso. Bundles are declared under
  `[bundles] android-test`. There are no Hilt testing artifacts — DI is Metro, and the
  instrumented DI harness lives in `:app:app` (see [Integration tests](#integration-tests)).

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

### Testing repositories (DB-backed)

**Rule:** Repository tests for DB-backed persistence MUST use a real in-memory Room database
and verify state by reading it back through the same DAO/repository surface after the
operation. Tests that only verify mockk DAO interactions (e.g.
`coVerify { dao.update(...) }`) are not sufficient on their own and must not be the only
assertion in a persistence test.

Mock-DAO tests are acceptable only for tiny branch/order assertions that do not depend on
persisted state — for example, simulating a mid-transaction failure by stubbing a single DAO
to throw. Such tests must remain the minority and must not duplicate a real-DB test for the
same code path.

#### Test fixture

The shared in-memory Room fixture lives in the database module's `testFixtures` source set:

- `core/data/database/src/testFixtures/kotlin/io/github/stslex/workeeper/core/data/database/testfixtures/RepositoryTestEnv.kt`

It builds an `AppDatabase` via `Room.inMemoryDatabaseBuilder`, exposes every real DAO
(`sessionDao`, `exerciseDao`, etc.), provides a real `DbTransitionRunner` backed by Room 3's
`useWriterConnection { it.immediateTransaction { … } }`, and ships a `TestApplication` for the
Robolectric `@Config`.

Consumers depend on it via `testImplementation(testFixtures(project(":core:data:database")))`
in their `build.gradle.kts` (already wired for `core/data/exercise`).

#### Boilerplate

```kotlin
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class MyRepositoryImplDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var repository: MyRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        repository = MyRepositoryImpl(
            dao = env.myDao,
            transition = env.transition,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }
}
```

Each test must end with at least one DB-state assertion (read-back), not only a
return-value assertion or a mockk verification.

#### Canonical examples

- Multi-table transactional repository:
  `core/data/exercise/src/test/kotlin/io/github/stslex/workeeper/core/data/exercise/session/SessionRepositoryImplFinishAtomicDbTest.kt`
  covers happy paths and a hybrid failure path that injects a throwing DAO via mockk while
  the rest of the in-memory DB rolls back the transaction. State is read back with the real
  DAOs.
- Read-side repository:
  `core/data/exercise/src/test/kotlin/io/github/stslex/workeeper/core/data/exercise/session/SessionRepositoryImplReadDbTest.kt`
  seeds rows via DAO helpers, then asserts the repository's mapped output.
- Single-table writer:
  `core/data/exercise/src/test/kotlin/io/github/stslex/workeeper/core/data/exercise/tags/TagRepositoryImplDbTest.kt`
  exercises every public method with a single round-trip per method.

The DAO-test pattern (`core/data/database/src/test/.../BaseDatabaseTest.kt`) remains the
right tool for DAO-only assertions that do not exercise repository code.

## UI tests

### Categorization with `@Smoke` and `@Regression`

Annotations are defined in
`core/ui/test-utils/src/main/kotlin/io/github/stslex/workeeper/core/ui/test/annotations/{Smoke,Regression}.kt`.
Every UI test class must carry exactly one of them.

- `@Smoke` — fast, mocked-data tests using `createComposeRule()`. No DI graph, no real
  database, no full activity. Pick this for component-level checks: visibility, interactions,
  edge inputs, accessibility semantics. This is what `feature/<name>/src/androidTest/...`
  contains.
- `@Regression` — full integration tests that boot the real Metro app graph via `MetroTestRule`.
  They live in `app/app/src/androidTest/...` — the only source set that can build the graph,
  because `buildAppGraph` / `AppGraph` are `:app:app`-internal. Hosted by either
  `createAndroidComposeRule<MainActivity>()` (real navigation graph, cross-feature flows) or
  `createAndroidComposeRule<TestActivity>()` (mount one feature's nav graph directly). See
  [Integration tests](#integration-tests).

The annotation governs which suite the test runs in. The CI workflow filters on the fully
qualified annotation name via
`-Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.{Smoke|Regression}`.

### Choosing a category

Use `@Smoke` when the test:

- Constructs `*Store.State` directly with mocked data.
- Does not need a real DI graph, real database, or real APIs.
- Targets a single widget or screen with `consume = ...` wired by hand.

Use `@Regression` when the test:

- Declares a `MetroTestRule` and resolves Stores through the real app graph.
- Launches `MainActivity` (or mounts a feature nav graph in `TestActivity`) and exercises
  the real navigation graph.
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

### Skeleton: regression test (app scope)

Lives in `app/app/src/androidTest/kotlin/io/github/stslex/workeeper/app/`.

```kotlin
@Regression
@RunWith(AndroidJUnit4::class)
internal class MyFullAppTest {

    // order = 0 — OUTERMOST, so its @After closes the DB only after the activity is torn down.
    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_navigates_from_a_to_b() {
        // Real Metro app graph, in-memory Room database, full Compose host.
    }
}
```

### Integration tests

Every test that needs a real DI graph lives in **`app/app/src/androidTest/`**. That is not a
style preference: `buildAppGraph` and `AppGraph` are `:app:app`-`internal`, so no feature
module can construct the graph. A feature module's androidTest source set therefore holds
`@Smoke` render/dispatch tests only; when a scenario needs the Store → interactor →
repository → Room round trip, its real-graph half is written in `:app:app`. The Exercise
F-02 scenario is the canonical split — `ExerciseFormBasicsTest` (feature module, `@Smoke`,
form wiring) and `ExerciseCreatePersistenceTest` (`:app:app`, `@Regression`, DB read-back).

#### Stack

Four pieces, all in `:app:app` androidTest except where noted:

1. **`MetroTestRunner`** (`app/app/src/androidTest/.../harness/MetroTestRunner.kt`) — an
   `AndroidJUnitRunner` subclass whose `newApplication` boots `TestApplication`.
   `app/app/build.gradle.kts` sets `defaultConfig.testInstrumentationRunner` to it. Every
   other module keeps the convention plugin's default
   `androidx.test.runner.AndroidJUnitRunner`.
2. **`TestApplication`** (`.../harness/TestApplication.kt`) — a `BaseApplication` subclass, so
   it satisfies every seam production does (`AppGraphOwner`, `AppDepsHolder`,
   `RecoveryDepsHolder`, `BackupWorkerDepsHolder`, `Configuration.Provider`). Two overrides
   make it test-safe: `appGraph` reads the resettable `MetroTestGraphHolder` instead of
   building the production graph, and `onCreateGraphBootstrap()` is a no-op (the production
   body reads `appGraph` at process start, before any test could install one).
3. **`MetroTestRule`** (`.../harness/MetroTestRule.kt`) — the JUnit rule that builds a FRESH
   graph per test via `buildAppGraph(...)`, installs it into `MetroTestGraphHolder`, and on
   teardown resets the holder and closes the database. It exposes `appDatabase` so a test can
   read back what a Store→repository→Room write produced. Declare it at `@Rule(order = 0)` —
   the outermost slot — whenever the test also has an activity / compose rule, so its
   teardown runs last.
4. **`InMemoryDatabaseProvider`** (`core/data/database-test/.../InMemoryDatabaseProvider.kt`)
   — builds the in-memory `AppDatabase` that `MetroTestRule` installs by default. It is the
   androidTest counterpart to the `RepositoryTestEnv` fixture used by JVM repository tests.

The rule's two constructor parameters ARE the test-override seam, matching the app graph's
`create()` roots: `appDatabaseFactory` (defaults to `InMemoryDatabaseProvider.create`) and
`imageStorage` (defaults to `FakeImageStorage` from `core/ui/test-utils`). A test that needs
divergent behaviour passes its own — `RecoveryActivityDbFreeTest` installs an `AppDatabase`
built on a `SQLiteDriver` whose `open()` throws, which is how the Room-free bootstrap
invariant is enforced.

`TestActivity` (`core/ui/test-utils/.../TestActivity.kt`) is a bare `ComponentActivity` with
no DI annotation. Pair it with `createAndroidComposeRule<TestActivity>()` when the test wants
to mount one feature's nav graph via `composeRule.setContent { ... }` instead of booting
`MainActivity` (which sets its own content in `onCreate`). Stores still resolve through
`rememberMetroStoreProcessor`, retained in that activity's `ViewModelStore`.

#### Module wiring

`app/app/build.gradle.kts` already carries all of it:

```kotlin
android {
    defaultConfig {
        testInstrumentationRunner = "io.github.stslex.workeeper.harness.MetroTestRunner"
    }
}

dependencies {
    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    androidTestImplementation(project(":core:data:database-test"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

A feature module that only needs `@Smoke` tests drops the `:core:data:database-test` line and
keeps the default runner:

```kotlin
dependencies {
    androidTestImplementation(libs.bundles.android.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(project(":core:ui:test-utils"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

#### File layout

```
app/app/src/androidTest/kotlin/io/github/stslex/workeeper/
├── app/
│   └── <Scenario>Test.kt      # @Regression tests over the real graph
└── harness/
    ├── MetroTestRunner.kt
    ├── TestApplication.kt
    ├── MetroTestGraphHolder.kt
    └── MetroTestRule.kt

feature/<name>/src/androidTest/kotlin/io/github/stslex/workeeper/feature/<name>/
└── <Feature><Group>Test.kt    # @Smoke render / dispatch tests, no DI graph
```

#### Skeleton

```kotlin
@Regression
@RunWith(AndroidJUnit4::class)
internal class MyFeaturePersistenceTest {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<TestActivity>()

    // The SAME database instance the graph's DAOs / repositories derive from.
    private val myDao get() = metroRule.appDatabase.myDao

    @Test
    fun scenario_does_thing() {
        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                TestSingleScreenHost(start = Screen.MyFeature(...)) {
                    myFeatureGraph()
                }
            }
        }

        composeRule.onNodeWithTag("MyFieldTag").performTextInput("...")
        composeRule.onNodeWithTag("MySaveButtonTag").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { myDao.getCount() } == 1
        }

        // Read-back assertion against the same DAO surface.
    }
}
```

`TestSingleScreenHost` (`core/ui/test-utils`) mounts the graph in a real `NavDisplay`
with the production decorator pair, through the project DSL — the test names no
`androidx.navigation*` type, which the androidTest import gate bans.

The rule builds a fresh in-memory database per test, so there is no `clearAllTables()`
step and no cross-test bleed to guard against.

The working references are
`app/app/src/androidTest/.../app/ExerciseCreatePersistenceTest.kt` (feature nav graph in
`TestActivity` + DB read-back) and
`app/app/src/androidTest/.../app/ApplicationBottomBarTest.kt` (`MainActivity`, full
navigation).

#### Adding a new fake

When a scenario needs a deterministic fake for a type the graph provides (e.g. `Clock`,
`SystemFeedback`):

1. Add the fake under
   `core/ui/test-utils/src/main/kotlin/io/github/stslex/workeeper/core/ui/test/fakes/`.
2. If the type is one of the app graph's `create()` roots, add a `MetroTestRule` constructor
   parameter defaulting to the fake (the shape `imageStorage` already uses). If it is an
   ordinary contributed binding, there is no test-only override seam — construct the
   collaborator directly in a `@Smoke` test instead, or promote the type to a `create()` root
   only if a real integration scenario demands it.
3. Update the relevant `documentation/test-scenarios/<feature>.md` under "Test
   infrastructure prerequisites" so the next test author knows the fake exists.

#### Test-tag conventions for integration tests

The mounted screen still relies on the production composable's testTags. Add a tag
only when a scenario actually needs to query that node — speculative tags are not
welcome (see [Test-tag naming](#test-tag-naming)). The Exercise feature's existing
production tags (e.g. `ExerciseEditNameField`, `ExerciseEditSaveButton`) are the
source of truth; the names listed in
`documentation/test-scenarios/exercise.md` track those production names rather than
introducing parallel ones.

### Test-tag naming

Use `Modifier.testTag("...")` with these prefixes so finders are stable across PRs:

- Screen-level tag: `"<Feature>Screen"`.
- Widget-level tag: `"<Feature>Widget"`.
- Buttons: `"<Feature>Button"`, `"<Feature>SaveButton"`, etc.
- List: `"<Feature>List"`. List item: `"<Feature>Item_${item.uuid}"`.
- Dialog: `"<Feature>Dialog"`.

`app/common/src/main/kotlin/io/github/stslex/workeeper/host/AppNavigationHost.kt` uses graph-level
tags (`"HomeGraph"`, `"AllTrainingsGraph"`, etc.) for cross-feature tests.

### Existing UI tests

| Feature | Test class |
|---|---|
| `feature/all-trainings` | `feature/all-trainings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt` |
| `feature/all-exercises` | `feature/all-exercises/src/androidTest/.../AllExercisesScreenTest.kt`, `AllExercisesScreenAccessibilityTest.kt`, `AllExercisesScreenEdgeCasesTest.kt` |
| `feature/single-training` | `feature/single-training/src/androidTest/.../SingleTrainingScreenTest.kt` |
| `feature/exercise` | `feature/exercise/src/androidTest/.../ExerciseScreenTest.kt`, `ExerciseFormBasicsTest.kt` |
| `feature/settings` | `feature/settings/src/androidTest/.../SettingsScreenTest.kt` |
| `app/app` (`@Regression`, real graph) | `app/app/src/androidTest/.../app/ApplicationBottomBarTest.kt`, `ExerciseCreatePersistenceTest.kt`, `NavigationLifecycleRegressionTest.kt`, `RecoveryActivityDbFreeTest.kt`, `AllTrainingsExtensionDbVisibilityTest.kt` |

## Visual gate — Paparazzi screenshot goldens

Applies to `:core:ui:kit`. Goldens live in
`core/ui/kit/src/test/snapshots/images/` and are committed.

```bash
# Verify the committed goldens (what CI runs, before detekt)
./gradlew verifyPaparazziDebug

# Re-record after an intentional visual change, then commit the PNGs
./gradlew :core:ui:kit:recordPaparazziDebug
```

**Re-record workflow.** `recordPaparazziDebug` regenerates every golden, the PNGs are
committed alongside the code change, and the reviewer reads the image diff. During the v3
redesign the goldens are re-recorded at every visual step — they are the *before* picture for
the next one, and are disposable by design.

**Rule for the redesign branch: a golden change must be intentional and explained in the
commit body. An unexplained golden delta is a review stop.** A commit that touches
`src/test/snapshots/` without saying why in its message should not be approved.

**Pinned rendering inputs** (change these and every golden moves): `DeviceConfig.PIXEL_5` —
1080×2340 at 440 dpi, `fontScale = 1.0`, `softButtons = false`, locale `en` (`ru` for the
Cyrillic golden), recorded at native device resolution.
`maxPercentDifference = 0.0`, set explicitly.

**A flaky golden is a finding, never a reason to raise the tolerance.** Adding a single glyph
to a golden moves ~0.03% of the frame, so the commonly copied `0.1` would not catch it.

**Not snapshotted, by design.** `Dialog`, `ModalBottomSheet`, `DropdownMenu`,
`DatePickerDialog` and `TooltipBox` render in separate windows; Paparazzi models one window.
Those sites stay on manual verification.

**Liveness.** A Paparazzi task that discovers no tests still exits 0. `verifyPaparazziDebug`
and `recordPaparazziDebug` are therefore finalized by `:core:ui:kit:assertGoldenLiveness`,
which fails the build unless at least as many golden test cases executed as there are
committed golden images.

That assertion reads the JUnit XML — a cacheable *output* of the test task. So with the build
cache warm it would read restored XML and vouch for a run that never happened: measured, a
wiped `core/ui/kit/build` plus a warm cache printed *"Visual gate live: 10 golden test case(s)
executed"* in a 565 ms build that executed none. The goldens themselves are declared inputs, so
a *changed* golden always misses the cache and is still caught — only the liveness claim was
hollow. `testDebugUnitTest` is therefore marked `doNotCacheIf` + `upToDateWhen { false }` when a
Paparazzi task was requested, so the gate executes on every invocation, CI or local.

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
- **UI tests do not gate PRs.** The `ui_tests.yml` workflow runs weekly (Mondays
  05:00 UTC, against `dev`; the cron only evaluates from the default branch, so it
  activates once the workflow reaches `master` with a release) and on manual dispatch
  with a `smoke` / `regression` / `all` selector. They do not block PR merges. Run them
  locally before opening a PR that touches Compose code.

See [ci-cd.md](ci-cd.md) for the full pipeline.
