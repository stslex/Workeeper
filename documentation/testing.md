# Testing

This document covers how tests are structured, written, and executed in Workeeper, plus where
they run in CI. For the architectural patterns the tests target, see
[architecture.md](architecture.md). For pipeline mechanics, see [ci-cd.md](ci-cd.md).

## Test types

Workeeper uses two test tiers. Their source-set names depend on the module shape:

- `src/test/...` in classic Android modules and `src/androidHostTest/...` in KMP modules —
  JVM host tests. Run via the repo-level `./gradlew testDebugUnitTest` alias. Use JUnit 5 (Jupiter)
  via the `junit-bom`, MockK for mocking, Robolectric for Android-class fakes, and the
  Kotlin coroutines test library. Bundles are declared in `gradle/libs.versions.toml` under
  `[bundles] test`.
- `src/androidTest/...` in classic Android modules and `src/androidDeviceTest/...` in KMP
  modules — instrumented device tests. Run via the repo-level
  `./gradlew connectedDebugAndroidTest` alias.
  Use `androidx.test:runner`; UI suites additionally use Espresso and the AndroidX Compose UI
  test runner (`androidx-compose-ui-test-junit4`). Bundles are declared under `[bundles]
  android-test`. There are no Hilt testing artifacts — DI is Metro, and the instrumented DI
  harness lives in `:app:app` (see [Integration tests](#integration-tests)).

Source sets exist only where a module has tests of that tier. When a feature module needs the
shared test utilities, it adds `androidTestImplementation(project(":core:ui:test-utils"))` to
its `build.gradle.kts`.

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

The shared in-memory Room fixture lives in the `core:data:database-test` module's `src/main`:

- `core/data/database-test/src/main/kotlin/io/github/stslex/workeeper/core/data/database/testfixtures/RepositoryTestEnv.kt`

It builds an `AppDatabase` via `Room.inMemoryDatabaseBuilder`, exposes every real DAO
(`sessionDao`, `exerciseDao`, etc.), provides a real `DbTransitionRunner` backed by Room 3's
`useWriterConnection { it.immediateTransaction { … } }`, and ships a `TestApplication` for the
Robolectric `@Config`.

Consumers depend on it from their host-test configuration: `testImplementation` for classic
Android modules or `androidHostTestImplementation` for KMP modules. The KMP configuration is
already wired for `core/data/exercise`. It is a normal module rather than a `testFixtures` source
set because KMP has no such source set — see
`documentation/feature-specs/kmp-phase-6-data-layer.md` -> §3.1.

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
  `core/data/exercise/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/data/exercise/session/SessionRepositoryImplFinishAtomicDbTest.kt`
  covers happy paths and a hybrid failure path that injects a throwing DAO via mockk while
  the rest of the in-memory DB rolls back the transaction. State is read back with the real
  DAOs.
- Read-side repository:
  `core/data/exercise/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/data/exercise/session/SessionRepositoryImplReadDbTest.kt`
  seeds rows via DAO helpers, then asserts the repository's mapped output.
- Single-table writer:
  `core/data/exercise/src/androidHostTest/kotlin/io/github/stslex/workeeper/core/data/exercise/tags/TagRepositoryImplDbTest.kt`
  exercises every public method with a single round-trip per method.

The DAO-test pattern (`core/data/database/src/androidHostTest/.../BaseDatabaseTest.kt`) remains the
right tool for DAO-only assertions that do not exercise repository code.

#### Room 3 behaviours these fixtures rely on

- **In-transaction reads see uncommitted rows.** Room 3's connection pool confines the writer
  connection to the coroutine context, so a read issued from INSIDE
  `useWriterConnection { transactor.immediateTransaction { … } }` reuses that same connection.
  That is what lets `AtomicRollbackDeviceTest` assert "written, then rolled back" rather than
  only "table empty afterwards".
- **`async` children inside `transition` do not contend.** `RepositoryTestEnv.transition` runs
  `coroutineScope` INSIDE `withTransaction`, so the receiver passed to `block` inherits Room's
  `TransactionElement` and the `async` children reuse the parent's transaction connection
  (`TrainingRepositoryImpl.getTraining` is the shape). Atomicity of such a block is delegated to
  Room and is not verifiable at unit-test level — there is no observable mid-transaction side
  effect to interrupt. Do not assert parallelism of the branches either: a delay-based
  scheduling assertion re-trips the fixture deadlock.
- **`runMigrationsAndValidate` does not validate dropped/extra tables.** Room 3 removed the
  `validateDroppedTables` argument, and a device probe (a stale unregistered table injected at
  v5) confirmed the default passes. `migrate5to6_validatesNoUnregisteredTablesSurvive`'s explicit
  `sqlite_master` assertion is therefore the ONLY guard for unregistered-table drift, not
  belt-and-braces. Column / index / FK drift is still caught by `runMigrationsAndValidate`'s own
  validation against the exported `6.json`.
- **The File-based `MigrationTestHelper` leaves a real file between test methods.** A stale one
  makes the next `createDatabase()` try to migrate an existing DB and fail with "A migration
  should never occur while creating a new database". Delete the `.db` plus its `-wal` and `-shm`
  sidecars in both `@Before` and `@After`.
- **There is no public `isOpen`, and `close()` is idempotent** on a closed or never-opened
  database, so teardowns close unguarded. `MetroTestRule.after()` does so on purpose even for
  `RecoveryActivityDbFreeTest`'s tripwire database whose driver throws from `open()`: verified
  against androidx.room3 3.0.0, `RoomDatabase.close()` is `closeBarrier.close()` ->
  `coroutineScope.cancel()` + `invalidationTracker.stop()` (Android's `stop()` only stops the
  multi-instance client, it runs no SQL) + `connectionManager.close()` ->
  `connectionPool.close()`, and BOTH pool implementations skip connections that were never
  created (`ConnectionPoolImpl` iterates an `arrayOfNulls(capacity)`, `PassthroughConnectionPool`
  guards on `::connection.isInitialized`). No path calls `SQLiteDriver.open()`, and leaving the
  close unguarded means a real close failure surfaces instead of being swallowed.
- **`Room.databaseBuilder` resolves its name against the app's databases dir**, not an arbitrary
  path — a snapshot written into `cacheDir` cannot be opened through Room and must be read via
  direct `android.database.sqlite.SQLiteDatabase.openDatabase(...)`
  (`DatabaseSnapshotProviderImplTest`).
- **The two DB fixtures deliberately run different SQLite drivers.** `RepositoryTestEnv`
  (Robolectric) pins `AndroidSQLiteDriver` even though production flipped to
  `BundledSQLiteDriver`: the bundled driver's android variant ships Android-ABI natives only and
  dies with `UnsatisfiedLinkError` when loaded under Robolectric on a desktop JVM (measured).
  `InMemoryDatabaseProvider` (on-device, via `MetroTestRule`) runs `BundledSQLiteDriver`, the
  production driver since the flip. Both `libs.androidx.sqlite.framework` and
  `libs.androidx.sqlite.bundled` are therefore `api` deps of `core:data:database-test`. Driver
  behaviour is a device-suite concern; the Robolectric fixture's oracle value is repository logic
  over a real schema.
- **The ANALYZE gate asserts `sqlite_stat1`, never the query plan.** `QueryPlannerStatisticsTest`
  asserts `sqlite_master` has no `sqlite_stat1` BEFORE `refreshQueryPlannerStatistics(database)`
  and has it AFTER, and deliberately asserts nothing about the resulting plan: `EXPLAIN QUERY
  PLAN` output is a string SQLite may reword, the plan depends on a data volume the fixture does
  not synthesise, and a plan assertion on an empty table would pass regardless.
  Existence-of-table rather than return-of-function is the only honest signal because
  `warmQueryPlanner` swallows exceptions by design (a corrupt database must not kill the process
  at launch), so a misspelled or dropped ANALYZE would leave every device on the bad plan with
  nothing thrown. Running it twice is asserted safe because it runs on every start.

#### Shared PR-rule fixture

`PrRuleFixture` (`core/data/database-test/.../testfixtures/PrRuleFixture.kt`) is deliberately
plain data — no Room, no Android — so a module with no database on its test classpath is held to
the same answers. It exists because the PR rule has three independent implementations that cannot
share code across modules: the DAO SQL queries, `PrComparator`, and `ChartFolder`'s per-session
representative-set choice. `WEIGHTLESS_WITH_RESIDUAL_WEIGHTS` is the load-bearing scenario: every
pre-existing weightless test seeds `set_table.weight = null`, precisely the input on which the
batch query and the single-exercise query happened to agree — which is why their disagreement
survived undetected for as long as it did. Residual weights on weightless rows exist in the wild
(`set_table` carries no type-conditional constraint) and no migration scrubs them.
exercise-chart's `ChartFolderPrRuleParityTest` reads the fixture from `androidHostTest` only,
because `core:data:database-test` is Android-only (decision D4 of
[kmp-phase-7-13-exercise-chart-feature.md](feature-specs/kmp-phase-7-13-exercise-chart-feature.md)),
while `ChartFolderTest` runs on both targets.

### Off-device hazards: logging, Firebase, Main

These bite any JVM host test, not only the ones named as carriers.

- **Kermit's sink throws off-device.** A host test that can reach a `Log` / `logger.*` call must
  set `Log.isLogging = false` in `@BeforeEach` and restore the previous value in `@AfterEach`: on
  the Android-library unit-test classpath kermit selects the Logcat writer, which fails with
  `UnsatisfiedLinkError` on the JVM. `Log.isLogging` is the call-time gate in front of the sink
  and `Log` exposes no injectable writer, so flipping the gate is the whole fix. Carriers:
  `SnackbarManagerTest`; `SnackbarOutcomeTest` (its drain can meet a stale-epoch leftover from a
  sibling class, and the discard branch logs); `BackupInteractorImplTest`
  (`RestoreLatestBackupUseCase`'s committed-without-a-durable-record `logger.w {}`);
  `StoreGenerationJoinTest` (a real `BaseStore.consume`, whose first call would otherwise throw).
  Since Phase 7.16 a failing Store launch logs too, so a host test that drives a real `BaseStore`
  into a failure needs the same gate.
- **`mockkObject(Log)` cannot replace the gate for a logging `object`.** `SnackbarManager`'s
  private `logger` is captured at class init — possibly by an earlier test in the same JVM — so
  stubbing `Log.tag` after the fact never reaches it.
- **Where the logger is per-instance, `mockkObject(Log)` IS the fix.** `NavigatorEventBus`
  constructs a `Log.tag(...)` logger that funnels through `FirebaseCrashlyticsHolder` ->
  `Firebase.crashlytics` on every emit; with Firebase uninitialised and `Process.myPid()`
  unmocked the real logger throws. Stub `Log` to return a relaxed `Logger` in `@BeforeEach` and
  `unmockkObject(Log)` after — duplicated verbatim in `NavigatorEventBusTest` and
  `NavigatorEventBusLifecycleTest`.
- **Firebase sinks self-guard.** `FirebaseCrashlyticsHolder` resolves its client through a
  `runCatching` that yields `null` off-device, so every method there is already a no-op and needs
  no handling. Robolectric repository tests that read plans still `mockkObject` / `unmockkObject`
  it around the suite, because `ExerciseRepository.getAdhocPlans` and
  `TrainingExerciseRepository.getPlan` / `getPlans` wrap their JSON deserialisation in
  `traceExecutionTime`, which fans out to `Log.i { }` (`ExerciseRepositoryImplDbTest`,
  `TrainingExerciseRepositoryImplDbTest`).
- **Install `Dispatchers.setMain(...)` BEFORE any Store is constructed**, and
  `Dispatchers.resetMain()` in `@AfterEach`. `BaseStore` reads
  `storeDispatchers.mainImmediateDispatcher` while constructing — the parent `AppGraph` binds it
  to `Dispatchers.Main.immediate` through
  `core/core/src/androidMain/.../di/DispatchersBindingContainer.kt` — and `lifecycleScope` builds
  its scope on `Dispatchers.Main.immediate`. Every `*ExtensionIdentityTest` under
  `app/app/src/test/kotlin/io/github/stslex/workeeper/di/` carries the pair for exactly this
  reason. Installing a `StandardTestDispatcher` there also makes the surrounding `runTest` adopt
  ITS scheduler, which is what puts the Store's jobs, the lifecycle registration and the test
  body on one deterministic timeline.
- **Build a JVM-usable `LifecycleOwner` with `LifecycleRegistry.createUnsafe(this)`.** That
  factory drops the main-thread assertion `ArchTaskExecutor` makes (it reads
  `Looper.getMainLooper()`, which is not mocked in a JVM unit test). The registry is otherwise
  the production one, so `lifecycleScope` and `BaseStore`'s lifecycle observer behave as they do
  in the app — which is what makes the test non-vacuous.

### Coroutine pitfalls in host tests

- A **nested** unconfined `launch` (on `UnconfinedTestDispatcher(testScheduler)` or
  `Dispatchers.Unconfined`) queues on the thread-local event loop rather than running
  immediately. Add `kotlinx.coroutines.yield()` after it so the job genuinely STARTS — enters its
  `try` — before the enclosing call returns; without it the job's `finally` never registers and
  `AppRuntimeReplacementTest`'s teardown-ordering assertions have nothing to order.
- A test that parks an in-flight commit on a `CompletableDeferred` gate must complete that gate
  inside a `finally`. `resolveSnackbarOutcome` runs `onDismissed` under
  `withContext(NonCancellable)`, so a gate left closed by a failed assertion hangs `runTest` on
  an uncancellable child instead of reporting the failure.
- Handler-test `HandlerStore` mocks run the action as `supervisorScope { action() }`, never as a
  bare `action()`. `processInit` fans out through two `async` children; in a plain scope the first
  child's failure cancels the parent before the mock's `catch` can invoke `onError`, so the
  LOAD_FAILED tests would not exercise the arm they exist for. Production survives the same shape
  through `AppCoroutineScopeImpl`'s `CoroutineExceptionHandler` backstop; the `supervisorScope`
  reproduces that observable (the action throws, `onError` runs) without modelling the backstop's
  plumbing. See `CommonHandlerTest`.

### MockK limits

- A `relaxed = true` mock cannot synthesize a value of a **sealed** type, so a branch matched by
  an exhaustive `when` must be stubbed explicitly. `MetroWorkerFactoryTest.newWorkerDeps()` stubs
  `databaseSnapshotProvider.captureSnapshot` with a real
  `BackupResult.Failure(BackupError.NetworkUnavailable)`; without it
  `BackupWorker.executeBackup` never takes the early-return failure path.
- A relaxed mock answers `false` for `Boolean`. `RestoreRecoveryCoordinatorTest` must stub
  `beginAttempt` / `recordAttemptCommitted` / `resolveAttempt` `returns true` and pin
  `getAttempt()` to `null` ("no unresolved attempt"): the coordinator's
  `DatabaseReplacementEffects` implementations (`UndoRollbackEffects` and
  `RecoveryRollbackEffects`, `onBeforeMutation` / `onMutationCommitted`) read `false` as
  "another unresolved attempt owns the journal slot" and throw out of their `check(...)` calls.
- MockK cannot cleanly mock the `Flow<T>.launch` **member extension** declared on `HandlerStore`
  — a real implementation is the cheaper option, which is why `FakeSettingsHandlerStore` is
  hand-written. It runs `launch {}` / `launchDefault {}` closures synchronously via
  `runBlocking(dispatcher)` so state and event transitions are readable immediately after
  `handler.invoke(...)`, and collects `Flow<T>.launch` into a `CoroutineScope(Job() + dispatcher)`
  that `dispose()` cancels.

### Process-wide state across tests in one JVM

`SnackbarManager` is a process-wide `object`: queued models, the resolve fence and the generation
epoch all outlive the test that set them.

- Drain leftovers before asserting. The drain's real terminator is a **null poll**, not the count.
- Call `SnackbarManager.unfenceResolves()` in `@AfterEach`. A fenced gate refuses EVERY later
  routing, so a fence test that fails midway poisons every sibling in the JVM into silently
  requeueing instead of resolving. The call is idempotent, so cases that never fenced pay nothing.
- Take a `DeliveredSnackbar` off the real flow rather than hand-building one: it carries the epoch
  it was ENQUEUED under, and only the live delivery path stamps the current one (sibling tests in
  the same JVM advance it).
- `pendingModelCount` is approximate and can sit ABOVE zero over an EMPTY queue: `showSnackbar`
  increments after `trySend`, and a collector parked on the channel under an unconfined dispatcher
  consumes the entry inline INSIDE `trySend`, so that decrement hits the `coerceAtLeast(0)` floor
  and is swallowed before the increment lands. Assert a return to the test's own baseline, never
  to absolute zero.
- `SnackbarManagerTest.BURST_SIZE = 17` is deliberately one past the 16-slot buffer the queue must
  NOT have — it is `Channel(capacity = Channel.UNLIMITED)`. A 17-deep burst queued behind one
  visible toast plus one deferred-commit model reds any cap or overflow policy added later. What
  it guards is not a skipped toast: `AppSnackbarModel.onDismissed` carries the ED11 deferred
  permanent-delete commit and its screen has already popped, so an eviction is a confirmed delete
  that silently never runs.

The `pending_*` dialog flags are process-wide in the instrumentation process too — see
[Instrumented-suite hazards](#instrumented-suite-hazards).

### `app:app` graph and runtime host tests

- `AppGraph.restoreStateRepository` has **no production reader**: `RestoreRecoveryCoordinator`,
  `StartupMigrationCoordinator` and `RecoveryBootstrap` take `RestoreStateRepository` as a
  constructor dep inside the recovery cluster. The accessor exists so
  `AppScopeDataStoreSingletonTest` can read `restore_state_prefs` through the real app-scope
  binding (`RestoreStateRepositoryImpl`, contributed by `@ContributesBinding(AppScope)` in
  `core:data:backup:scheduling`) from two graphs — a read routed through a coordinator would
  assert the coordinator's behaviour instead of the store's identity. Do not delete it as unread.
- `AppGraph.stringResolver` still has **no production reader**: no production code reads
  `StringResolver` from the graph. all-exercises' `ClickHandler` and `PagingHandler`,
  past-session's `CommonHandler`, home's `ClickHandler`, `CommonHandler` and `PagingHandler`,
  exercise-chart's `CommonHandler`, single-training's `ClickHandler` and `CommonHandler`,
  exercise's `ClickHandler` and `CommonHandler`, and live-workout's `CommonHandler`,
  `ClickHandler`, `DialogClickHandler` and `ExercisePickerHandler` receive the binding by
  constructor injection through their graph extensions, which inherit it
  from `AppGraph`. The accessor exists so
  `StringResolverIdentityTest` can prove the binding to
  `ComposeStringResolver` (contributed by `@ContributesBinding(AppScope)` in `core:ui:kit`
  `commonMain`) and that two reads give one app-scoped instance. Do not delete it as unread.
- `AppGraph.cameraPermission` has **no production reader**: exercise's `ClickHandler` receives
  `CameraPermission` by constructor injection through its graph extension. The accessor exists so
  `ExerciseExtensionIdentityTest` can prove the extension inherits the app-scoped check
  (`AndroidCameraPermission`, contributed by `@ContributesBinding(AppScope)` in `core:core`
  `androidMain`) as the parent graph's instance. Do not delete it as unread.
- `app/app`'s unit-test classpath has **no room3**, so `AppDatabase`'s own members cannot be
  resolved there at all. That is why the ordering pins in `AppRuntimeReplacementTest` record a
  database touch with
  `every { db.toString() } answers { protocolLog += "db-touch-$index"; "AppDatabase#$index" }`
  rather than a DAO read: `toString()` is a call ON the database object that the mockk mock can
  record, proving a job reached the handle before it was closed. `touchedDatabases` then asserts
  `listOf("AppDatabase#1")` — that the job touched the CANDIDATE's database, not the outgoing one.

### Deliberate coverage boundaries

#### Shared start-mode UI

`core:ui:start-mode` keeps its catalog/order oracle in `commonTest`, so the same assertion runs
through both Android-host and iOS-simulator test hierarchies. Its unchanged RU Paparazzi test and
two PNGs live in `androidHostTest`; `iosTest` renders the production
`AppTheme { StartCardModeSheetContent(...) }` scene with the production CMP resources and verifies
the title, all four names, selected-only check semantics, and one real row callback. Generated
`Res` accessors remain internal to the leaf. The exact source/resource/test layout is enforced by
`.github/scripts/assert_kmp_ui_source_topology.py`.

#### Shared plan-editor UI

`core:ui:plan-editor` keeps all 19 `PlanDraftReducerTest` cases in `commonTest`, so the same reducer
contract executes through the Android-host aggregate and `iosSimulatorArm64Test`. Its three
Paparazzi classes and 18 unchanged PNGs live in `androidHostTest`. The exact native identity
`io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorSceneIosTest.readOnlyCopyAndEditableAddRenderAndDispatch`
renders the production read-only and editable branches with the production plan-editor and kit CMP
resources, verifies their semantics, rejects premature dispatch, and proves exactly one real
`PlanEditorBodyAction.OnAddSet` after clicking `AppSetBarAdd`. Generated plan-editor resources remain
module-private. See
[kmp-phase-7-5-plan-editor.md](feature-specs/kmp-phase-7-5-plan-editor.md) for the measured topology,
resource partition, and exact identities.

#### Shared plan-editor feature

`feature:plan-editor` keeps all six pre-migration suites in `commonTest`, so the same 42 exact
identities execute through Android host and `iosSimulatorArm64Test`: mapper 5, UI-model 3, click
handler 23, common handler 3, navigation handler 2, and route/state 6. The deterministic in-file
fakes preserve the route, reducer, save/back/discard, typed Boolean result, and distinct
graph/Store contracts without Android test libraries.

The exact Native identity
`io.github.stslex.workeeper.feature.plan_editor.PlanEditorFeatureSceneIosTest.resourcesBranchesAndActionsRenderAndDispatch`
composes the production screen under `AppTheme`, resolves the private CMP resource branches, and
observes real back and add-set dispatch. Together with the 42 portable cases, the target Native
XML contains exactly 43 passing cases with no skip, failure, or error. Android keeps the three
`PlanEditorExtensionIdentityTest` cases and the three editor journeys; the explicit root-factory
flow is also covered by the admitted/retired/publication-race device identities.

#### Shared image-viewer feature

`feature:image-viewer` keeps its three handler suites in `commonTest`, so the same 12 deterministic
cases run through the Android-host and `iosSimulatorArm64Test` hierarchies: Click 8, Common 2, and
Navigation 2. The navigation cases assert both the exact destination and result request name,
including `BackWithRequest`; their small in-module fakes fail immediately on unused logger,
coroutine, or navigator operations.

The exact Native identity
`io.github.stslex.workeeper.feature.image_viewer.ImageViewerSceneIosTest.resourcesBranchesAndActionsRenderAndDispatch`
composes the production screen under `AppTheme`. It proves the read-only branch has no menu, the
editable branch resolves the exact menu/replace/remove copy from private CMP resources, back and
menu actions dispatch exactly, and the production Coil request path composes through
`LocalPlatformContext`. Together with the 12 common cases, the image-viewer Native XML contains 13
passing cases with no skip, failure, or error. This is a headless Compose scene, not an iOS app,
application-window, UIKit, Metal, or XCTest claim.

Android keeps three `ImageViewerExtensionIdentityTest` cases, resolving the factory through
`appGraph.imageViewerGraphFactory`, and three `UiAdmissionRaceTest` cases: the admitted generation
resolves app-root dependencies exactly once while both rejected paths resolve them zero times.
The four existing image-viewer regression journeys remain unchanged.

#### Shared archive feature

`feature:archive` keeps its five portable suites in `commonTest`, so the same 25 exact identities
execute through Android host and `iosSimulatorArm64Test`: click handler 9, paging-handler
placeholder 1, meta line 7, list surface 4, and paging tail 4. The click-handler fake reproduces the
old relaxed store — Store-scope `launch` work is not run — and fails fast on every member the
handler must not touch. `ArchiveMetaLineTest` drives the pure `ArchiveUiMapper` seam with copy the
paging handler has already resolved, so it needs no resources. Five inherited names contain commas,
which Kotlin/Native rejects, so their three files carry
`@file:Suppress("INVALID_CHARACTERS_NATIVE_ERROR")`.

The exact Native identity
`io.github.stslex.workeeper.feature.archive.ArchiveFeatureSceneIosTest.resourcesPagingBranchesAndActionsRenderAndDispatch`
composes the production screen under `AppTheme` with deterministic `PagingData`. It resolves the
private catalog, renders both segment labels, both empty branches, the cold-open loading and
refresh-error verdicts, populated exercise and training rows, both append tails, and the
permanent-delete dialog with its plural impact copy, and observes the exact segment, Back, restore,
overflow-delete, confirm and dismiss actions. Paging hands each new list over on the main
dispatcher and the kit holds a shown loading treatment for its minimum, so the scene settles each
paging branch in bounded steps until that branch's production tag exists. Together with the 25
portable cases, the archive Native XML contains exactly 26 passing cases with no skip, failure, or
error. This is a headless Compose scene, not an iOS app, UIKit, or XCTest claim.

The 14 archive goldens moved byte-identical to `androidHostTest`. Android keeps the four
`ArchiveExtensionIdentityTest` cases, which resolve the factory through
`appGraph.archiveGraphFactory`, and `ArchiveScreenTest.pendingFeatureRewrite` in
`androidDeviceTest` as the one documented `@Smoke` skip.

#### Shared all-trainings feature

`feature:all-trainings` keeps its nine portable suites in `commonTest`, so the same 49 exact
identities execute through Android host and `iosSimulatorArm64Test`: interactor 3, click handler
18, navigation handler 2, start-blank gate 3, clearance 2, list surface 9, paging tail 4, top-bar
mode 4, and trailing slot 4. They use `kotlin.test`, `kotlinx-coroutines-test` (two suites) and
private in-file fakes. The click-handler fake reproduces the old relaxed store: it applies state
updates, records events, consumed actions and each Store-scope `launch` without running it, and
fails fast on every member the handler must not touch; only the bulk-confirm identity runs its
recorded launch, and it also asserts the semantic `ShowBulkDeleteSuccess(archivedCount = 2,
blockedNames = empty)` event. The domain suite's repository fakes import `core:data` shapes
directly; the KMP test source sets' exemption in `DomainLayerPurityRule` is what keeps them out of
the production-domain check. Two `StartBlankGateTest` names contain U+2014; the Native XML reports
both byte-exactly, the first non-ASCII test names run on Native in this repository. No suite
needs a Native test-name suppression.

The exact Native identity
`io.github.stslex.workeeper.feature.all_trainings.AllTrainingsFeatureSceneIosTest.resourcesPagingBranchesSelectionAndActionsRenderAndDispatch`
composes the production screen under `AppTheme` with deterministic `PagingData` and a fixed clock.
It resolves the whole private EN catalog, the status and relative-time copy through the production
mapper, and the bulk-archive snackbar copy through the graph's `bulkArchiveMessage`; renders the
cold-open loading and refresh-error verdicts, the first-run empty state with and without a running
session, the filtered and selection empty states, populated rows with their meta line, both append
tails, the resting and selection bars, and the archive confirm dialog; and asserts the eleven
dispatched actions as one exact ordered list. It settles each paging branch in bounded steps until
that branch's production tag exists, as archive's scene does. Together with the 49 portable cases,
the all-trainings Native XML contains exactly 50 passing cases with no skip, failure, or error. No
Android host test reaches the mapper or snackbar copy (the goldens build their rows from fixed
fixtures), so this scene is that copy's proof. It is a headless Compose scene, not an iOS app,
UIKit, or XCTest claim.

The 50 all-trainings goldens moved byte-identical to `androidHostTest`. Android keeps the two
`AllTrainingsExtensionIdentityTest` cases, which resolve the factory through
`appGraph.allTrainingsGraphFactory`; the `@Regression` `AllTrainingsExtensionDbVisibilityTest`,
which reads the factory from `MetroTestGraphHolder.graph` and composes
`allTrainingsGraph(factory = factory)`; and `AllTrainingsScreenTest.pendingFeatureRewrite` in
`androidDeviceTest` as the one documented `@Smoke` skip.

#### Shared all-exercises feature

`feature:all-exercises` keeps its ten portable suites in `commonTest`, so the same 66 exact
identities execute through Android host and `iosSimulatorArm64Test`: interactor 5, click handler
24, navigation handler 2, paging handler 1, UI mapper 11, clearance 2, list surface 9, paging tail
4, top-bar mode 4, and trailing slot 4. They use `kotlin.test`, `kotlinx-coroutines-test` and
private in-file fakes. The click-handler and paging-handler store fakes reproduce the old relaxed
store: they apply state updates, record events, consumed actions and each Store-scope `launch`
(and `Init`'s `Flow.launch`) without running it, and fail fast on every member the handler must
not touch. The three bulk-confirm identities run their recorded launch's action and then its
`onSuccess`, in that order.

Copy outside composition resolves through the injected `StringResolver`, so 14 identities run
through it: the 11 mapper identities and the three bulk-confirm ones. Each suite fakes it strictly
and in-file. The fake switches on the generated resource objects, returns the strings the old
`ResourceWrapper` stubs returned (the mapper suite's `"$count sessions"` family, the click
suite's relaxed `""`), and throws on any other resource and on any read that does not pass
exactly the arguments production passes: none for a plain string, one for `used_in_format`,
and the quantity as the only format argument of every plural. A plural read that dropped its
count would otherwise ship the raw template. The domain suite's repository fakes import
`core:data` shapes directly; `ExerciseRepository` names `core:data:database`'s `PlanSetDataModel`,
which `core:data:exercise` keeps implementation-scoped, so the module's `commonTest` carries
that one edge (decision D5 of
[kmp-phase-7-10-all-exercises-feature.md](feature-specs/kmp-phase-7-10-all-exercises-feature.md)).
One `ClickHandlerTest` name contains U+2014; the Native XML reports it byte-exactly. No suite
needs a Native test-name suppression.

The exact Native identity
`io.github.stslex.workeeper.feature.all_exercises.AllExercisesFeatureSceneIosTest.resourcesPagingBranchesSelectionAndActionsRenderAndDispatch`
composes the production screen under `AppTheme` with deterministic `PagingData` and a fixed clock.
Every read outside composition goes through one real `ComposeStringResolver`; the scene declares
no fake. It resolves the whole private EN catalog as stored and filled, the footer copy through the
production mapper and the blocked-archive copy through `toBlockedArchiveDialog`; renders the
cold-open loading and refresh-error verdicts, the first-run, filtered and selection empty states,
populated rows with their meta line, both append tails, the resting and selection bars, and the
bulk-archive, blocked-archive and permanent-delete dialogs; and asserts the fourteen dispatched
actions as one exact ordered list. Together with the 66 portable cases, the all-exercises Native
XML contains exactly 67 passing cases with no skip, failure, or error. It is a headless Compose
scene, not an iOS app, UIKit, or XCTest claim.

This scene is the only executed guard of `ComposeStringResolver.getPluralString`'s delegation:
kit's catalog has no plural, and Android host has no route to this module's plurals (a shared
feature's build may not take Robolectric, and its generated `Res` is private, so neither kit nor
app can read the catalog). On Android the delegation runs in production and in the app journeys
whenever a row has stats, with no text assertion; the RU plural forms are pinned statically by
the topology gate's catalog check only.

The 52 all-exercises goldens moved byte-identical to `androidHostTest`; their fixtures carry
fixed strings, so neither the mapper nor the resolver reaches a golden. Android keeps the two
`AllExercisesExtensionIdentityTest` cases, which resolve the factory through
`appGraph.allExercisesGraphFactory`, and the two `AllExercisesScreenTest` identities in
`androidDeviceTest`: `pendingFeatureRewrite`, the one documented `@Smoke` skip, and the
`@Regression` `blockedArchiveDialogListsBlockingTrainings`, which reads its confirm label through
`ComposeStringResolver` from the module's own Compose resources.

#### Shared past-session feature

`feature:past-session` keeps its six portable suites in `commonTest`, so the same 40 exact
identities execute through Android host and `iosSimulatorArm64Test`: interactor 4, click handler
11, common handler 6, input handler 5, navigation handler 1, and UI mapper 13. The one
overflow-gate identity, `PastSetRowOverflowGateTest.weightedRowValuesFitTheirSlots`, drives the
golden harness's layoutlib measurement, so it lives in `androidHostTest` beside the goldens and runs
on Android host only. The suites use `kotlin.test`, `kotlinx-coroutines-test` and private in-file
fakes. The three handler suites keep their deterministic `TestStore`, which applies state updates,
records events and consumed actions, and runs every launch on the test dispatcher; its `logger`
fails fast. The interactor, navigator and repository fakes answer only the members an identity
reaches and fail fast on every other one.

Copy outside composition resolves through the injected `StringResolver`, so 13 identities run
through it: the nine mapper identities that call `toUi` and four `CommonHandlerTest` identities.
The mapper and CommonHandler suites each fake it strictly and in-file. The fake switches on the
generated resource objects, returns the strings the old `ResourceWrapper` stubs returned plus the
EN separator `,`, and throws on any other resource and on any read that does not pass exactly the
arguments production passes. Both suites keep an in-file `ResourceWrapper` reduced to the session
date (`formatMediumDate`, the module's one `ResourceWrapper` read); every other member throws.
Every fixture tonnage there is under 1,000, so no host identity depends on the grouping. The module
has no `core:data:database` edge, not even in tests (decision D3 of
[kmp-phase-7-11-past-session-feature.md](feature-specs/kmp-phase-7-11-past-session-feature.md)):
the repository fakes override the one member whose return type names `PlanSetDataModel` as
`Nothing`.

The exact Native identity
`io.github.stslex.workeeper.feature.past_session.PastSessionFeatureSceneIosTest.resourcesTotalsPhasesSheetDialogsAndActionsRenderAndDispatch`
composes the production screen under `AppTheme` from a test-held State. Every read outside
composition goes through one real `ComposeStringResolver`; the scene declares no fake resolver.
`ResourceWrapper` has no iOS implementation, so a private stub answers the session date for the
five fixture timestamps and throws on everything else. The scene resolves the whole private EN
catalog as stored and filled; turns five fixtures into their totals through the production mapper,
grouped tonnage included (`4,820 kg`, `1,000 kg` across a rounding boundary, `1,234,567 kg`);
renders the loading, error and loaded phases, the session sheet and both dialogs; types into the
set fields through the `SetText` semantics action; and asserts the eleven dispatched actions as one
exact ordered list. The set fields are controlled, so the test-held State takes each typed value,
as the Store would. Together with the 40 portable cases, the past-session Native XML contains
exactly 41 passing cases with no skip, failure, or error.

What this does not cover: RU values execute in no test (the topology gate pins them statically,
and the prepared catalog's separator records were checked once); on Android host the grouping runs
only through the strict fake, so the scene is the only executed guard of `groupThousands`, and no
executed test notices a hardcoded EN separator, which the topology gate's copy contract catches by
counting the resolver reads; the overflow gate runs on Android host only; and `OnSetReorder`,
`OnDragStarted`, `OnSheetDismiss` and `OnSetTypeChange` have no scene path (`OnSetTypeChange` has
no UI path at all).

The 30 past-session goldens moved byte-identical to `androidHostTest`; their fixtures carry fixed
strings, so neither the mapper nor the resolver reaches a golden. Android keeps the four
`PastSessionExtensionIdentityTest` cases, which reach the graph through
`appGraph.pastSessionGraphFactory`. The module has no device test: its factory flow is proven on a
device by `RouteReachabilityTest.pastSessionOpensFromASeededFinishedSessionAndHomeReturns`, in the
`@Regression` suite.

#### Shared home feature

`feature:home` keeps its twelve portable suites in `commonTest`, so the same 70 exact identities
execute through Android host and `iosSimulatorArm64Test`: interactor 6, start-card readout use case
9, week readout use case 2, click handler 16, common handler 2, navigation handler 5, UI mapper 14,
mode mapper 3, start-card seed 1, list surface 5, paging tail 3, and recent meta line 4. The one
mode-label identity, `HomeStartCardModeLabelTest.theHeadNamesNoModeUntilOneIsKnown`, drives
`runComposeUiTest`; a shared feature may not take Robolectric, so it lives in `iosTest` and runs
on Native only (decision S2 of
[kmp-phase-7-12-home-feature.md](feature-specs/kmp-phase-7-12-home-feature.md)). The suites use
`kotlin.test`, `kotlinx-coroutines-test` and private in-file fakes. The click and common handler
suites declare a store fake that reproduces the relaxed MockK store: `updateState` and the
update-lambda `updateStateImmediate` apply, events and consumed actions record, and each Store-scope
`launch` is recorded and runs, on the identity's `runTest` scheduler, only in the three identities
that ran it under MockK; `Flow.launch` records without collecting. `CommonHandler`'s `Init` logs
before it subscribes, so its suite's fake returns a strict silent `Logger` (the lambda `i` is
silent, every other abstract member fails fast); the click suite's `logger` fails fast. The
interactor, use-case, repository, data-store and navigator fakes answer only what an identity
reaches and fail fast on everything else; the two readout use cases sit behind interfaces
(decision D2), so their suites construct the `…Impl` classes over strict repository fakes.

Copy outside composition resolves through the injected `StringResolver`, so 15 identities run
through it: the 14 mapper identities and `OnPickerTrainingSelected reports the persisted active
session progress`. The mapper and click suites each fake it strictly and in-file (the common suite's
fake fails fast on both members). The fake switches on the generated resource objects, returns the
strings the old `ResourceWrapper` stubs returned, and throws on any other resource and on any read
that does not pass exactly the arguments production passes: none for plain copy, two for the two
format strings, the count for the three `%1$d` plurals, and **no** format argument for the two
unit-word plurals (`start_week_sessions_count`, `start_days_count`). The mapper suite keeps an
in-file `ResourceWrapper` reduced to the relative time (`getAbbreviatedRelativeTime`, the module's
only `ResourceWrapper` read), answering the sentinel `rel:<timestamp>@<now>`; the click suite's
wrapper throws on every member. The three `@DisplayName` suites keep the function name as the
identity on both targets: each display sentence moved verbatim into a one-line KDoc directly above
`@Test` (decision S6). The module has no `core:data:database` edge, not even in tests: the
repository fakes override the members whose types name `PlanSetDataModel` without constructing it.

The exact Native identity
`io.github.stslex.workeeper.feature.home.HomeFeatureSceneIosTest.resourcesReadoutsListPickerAndActionsRenderAndDispatch`
composes the production `HomeScreen` and `TrainingPickerSheet` under `AppTheme` from test-held
state. Every read outside composition goes through one real `ComposeStringResolver`; the scene
declares no fake resolver. `ResourceWrapper` has no iOS implementation, so a private stub answers
the relative time for exactly the three timestamps the scene renders and throws on everything else.
The scene resolves the whole private EN catalog as stored and filled; maps the readouts, recent
rows, picker items and active sessions through the production mapper; renders the loading, banner,
start-card (all four modes and an empty one) and list states, the settled and failed empty region,
and the training picker with and without templates; and asserts the seven screen actions and the
three picker callbacks as exact ordered lists. Together with the 70 portable cases and the
mode-label case, the home Native XML contains exactly 72 passing cases with no skip, failure, or
error.

What this does not cover: RU values execute in no test (the topology gate pins them statically);
the hoisted active-session and picker mappings (finding F1) execute in no unit identity, because no
handler identity collects those flows — the topology gate's State-lambda check pins their shape, and
the device journeys that return to Home with a session in progress execute them; the mode sheet,
the conflict dialog, the graph's sheet and dialog wiring and the append tails have no scene path
(`HomePagingTailKindTest` covers the tails as a selector); and the relative time runs only through
stubs off-device.

The 42 home goldens moved byte-identical to `androidHostTest`; their fixtures carry fixed strings,
so neither the mapper nor the resolver reaches a golden, and the RU row golden repeats the EN one's
blobs because the rows read no catalog string. The golden test reads one catalog string itself,
`Res.string.feature_home_refresh_error` for `coldOpenError`, the first golden read of its module's
private `Res`. Android keeps the two `HomeExtensionIdentityTest` cases, which reach the graph
through `appGraph.homeGraphFactory`. The module has no device test: its factory flow is proven on a
device by `RouteReachabilityTest` (`homeIsTheColdStartDestination` and every journey that returns
to Home), in the `@Regression` suite.

#### Shared exercise-chart feature

`feature:exercise-chart` keeps its eight portable suites in `commonTest`, so the same 68 exact
identities execute through Android host and `iosSimulatorArm64Test`: chart folder 23, click handler
14, common handler 13, navigation handler 2, UI mapper 9, content resolution 4, points animation 1
and metric-tabs motion 2. `ChartFolderPrRuleParityTest`'s 4 identities run on the Android host only:
they read `PrRuleFixture` through `core:data:database-test`, an Android-only module (decision D4 of
[kmp-phase-7-13-exercise-chart-feature.md](feature-specs/kmp-phase-7-13-exercise-chart-feature.md)).
The suites use `kotlin.test`, `kotlinx-coroutines-test` and private in-file fakes. The click suite's
store fake reproduces the relaxed MockK store: `state` is the identity's flow, `updateState`
applies, events and consumed actions record, and every other member fails fast, `logger` included.
The common and content-resolution suites' store fakes also apply the update-lambda
`updateStateImmediate` and run each `launch` / `launchDefault` on the identity's `runTest` scheduler
— the action, then `onSuccess`, a throw routed to `onError`, the common suite inside
`supervisorScope` because `Init` fans out through two `async` children — and each identity advances
the scheduler until idle before its assertions; none uses `runBlocking`. The click handler reaches
the common handler only through the `ChartLoader` seam (decision D2), so the click suite records
reloads on a `ChartLoader` fake and the content-resolution suite passes the real `CommonHandler` as
the loader. The interactor and navigator fakes answer only what an identity sets up and count their
calls.

Copy outside composition resolves through the injected `StringResolver`. The common and
content-resolution suites fake it strictly and in-file, answering `""` as the relaxed mock did; the
mapper suite's fake answers in today's sentinel shapes keyed by the resource's own name. All three
throw on any resource their suite does not reach and on any read that does not pass exactly what
production passes: no format argument for the readout and footer strings, the count alone for the
two `%1$d` plurals. `ResourceWrapper` keeps only the readout's day-month date (`formatDayMonth`), so
each suite's wrapper answers that one member and throws on every other. The two parameterized
`ChartFolderTest` identities each became one `@Test` looping `ChartMetricDomain.entries` with the
metric named in every assertion (decision S2), and `MetricTabsMotionTest`'s two display sentences
moved verbatim into one-line KDoc above `@Test` (decision S7). The two DST identities pin
`America/New_York` at its 2026 transitions on kotlinx-datetime; they are the first DST identities
on Native. The module has no direct `core:data:database` edge, in production or in tests.

The exact Native identity
`io.github.stslex.workeeper.feature.exercise_chart.ExerciseChartFeatureSceneIosTest.resourcesReadoutFooterStatesPickerAndActionsRenderAndDispatch`
composes the production `ExerciseChartScreen` under `AppTheme` from test-held state. Every read
outside composition goes through one real `ComposeStringResolver`; the scene declares no fake
resolver. `ResourceWrapper` has no iOS implementation, so a private stub answers the day-month date
for exactly the four days the scene renders and throws on everything else. The scene resolves the
whole private EN catalog as stored and both plurals filled; maps the readout copy, readouts, footers
and NBSP grouping through the production mappers; renders the loading, weighted plot, weightless
plot and four empty states and the picker with and without matches; and asserts every interaction
as one exact ordered list, two presses at the canvas's edges included. Together with the 68
portable cases, the exercise-chart Native XML contains exactly 69 passing cases with no skip,
failure, or error.

What this does not cover: RU values execute in no test (the topology gate pins them statically);
the scrub that lands between a load's read of `prior` and its write (finding F1) is raced by no
identity; the haptic's delivery and the picker sheet's dismissal gestures have no scene path; the
parity identities run on the Android host only; the day-month date runs only through stubs
off-device; and the DST identities cover `America/New_York` at its 2026 transitions only.

The 30 exercise-chart goldens moved byte-identical to `androidHostTest`; their fixtures carry fixed
strings, so neither a mapper nor the resolver reaches a golden, and the chrome they compose reads
the private catalog through CMP's `stringResource`. The device test moved to `androidDeviceTest`
with its 7 `@Smoke` identities. Android keeps the five `ExerciseChartExtensionIdentityTest` cases,
which reach the graph through `appGraph.exerciseChartGraphFactory`, and the factory flow is proven
on a device by the three `@Regression` journeys that reach the `ExerciseChartGraph` tag:
`RouteReachabilityTest.exerciseChartOpensFromHomeAndHomeReturns` (a null route argument), and
`StoreRetentionTest.exerciseChartStateDoesNotLeakBetweenEntities` (a non-null one) and
`exerciseChartStoreIsDestroyedWhenItsEntryLeavesTheStack`.

#### Shared single-training feature

`feature:single-training` keeps its four portable suites in `commonTest`, so the same 62 exact
identities execute through Android host and `iosSimulatorArm64Test`: click handler 50, common
handler 5, navigation handler 5 and route argument 2. Four of them were added by Phase 7.14 to pin
decision D2, which no earlier identity observed (decision S14 of
[kmp-phase-7-14-single-training-feature.md](feature-specs/kmp-phase-7-14-single-training-feature.md)).
`the История count is the total not the visible page` is the first identity with Cyrillic letters
to run on Native; the oracle compares it from the UTF-8 XML.

The suites use `kotlin.test`, `kotlinx-coroutines-test` and private in-file fakes. The click
suite's store fake reproduces the relaxed MockK store: `state` is the identity's flow,
`updateState` and the update-lambda `updateStateImmediate` apply, events and consumed actions
record, and every other member fails fast, `logger`, `consumeOnMain` and `Flow.launch` included.
Its `launch` runs nothing unless the identity sets it up, as SPEC Section 11.1 lists: the action
then `onSuccess`, the action only, or `onError` alone, launched on the identity's `runTest`
scheduler, or captured and completed by the identity itself. A throw from a launch that runs is
routed nowhere, so it fails the identity. The common suite's store fake launches every `launch` on
the identity's scheduler inside `supervisorScope`, then `onSuccess`, routing a throw to `onError`,
and records `Flow.launch` without collecting. Every identity that launches advances the scheduler
until idle before its assertions; none uses `runBlocking`. The interactor and navigator fakes
answer only what an identity sets up, or the relaxed default, and count their calls; the history
identity's load returns a real `TrainingDomain`.

Copy outside composition resolves through the injected `StringResolver`. Both suites fake it
strictly and in-file: the click suite answers the conflict progress with its two counts, the
permanent-delete title with the training's name and the confirm's three other reads verbatim; the
common suite answers `Init`'s three edit-copy reads, each with its own string. Both throw on any
other resource and on any read that does not pass exactly what production passes. `ResourceWrapper`
keeps only the history rows' medium date (`formatMediumDate`), so the common suite's wrapper answers
that one member and throws on every other.

D2 is pinned by the four added identities. `Init` resolves the editor's constant copy into State
once per Store; the save error and both undo toasts carry it, a distinct string per field; and the
permanent-delete confirm, resolved in a captured launch, lands only on the menu it replaces, so a
menu dismissed before the copy lands stays dismissed. The module's one production
`core:data:database` edge is read by the domain mapper alone; no test source set reads
`core:data:database` or `core:data:database-test`.

The exact Native identity
`io.github.stslex.workeeper.feature.single_training.SingleTrainingFeatureSceneIosTest.resourcesDetailEditorPickerAndMenuRenderAndDispatch`
switches one content slot, from test-held state, between the production `TrainingDetailScreen`,
`TrainingEditScreen`, `ExercisePickerSheet` and `TrainingDetailMenuSheetContent` under `AppTheme`.
Every read outside composition goes through one real `ComposeStringResolver`; the scene declares no
fake resolver and needs no `ResourceWrapper`, since every date it renders is a pre-formatted label.
It resolves the whole private EN catalog as stored and the plural filled, the handlers' formatted
reads with production's arguments, renders the read screen, the editor, the picker and the menu
content, and asserts every action and picker callback as one exact ordered list. Text goes through
the `SetText` semantics action; the fields are controlled, so the test-held State takes each typed
value, as the Store would. Together with the 62 portable cases, the single-training Native XML
contains exactly 63 passing cases with no skip, failure, or error.

What this does not cover: RU values execute only in the goldens; D2's guard is raced only in
`commonTest`'s captured launch, never on a device, and no identity measures the confirm's
one-dispatch delay; the graph's wiring (snackbars, undo actions, `BackHandler`, the discard and
conflict sheets) and the reorder drag have no scene path; the history rows' medium date runs only
through `AndroidResourceWrapper` on Android; and the inherited tag race
([tech-debt.md → State Mutation Discipline](tech-debt.md#state-mutation-discipline)) is raced by
no identity.

Both golden classes, `TrainingDetailGoldenTest` and `TrainingEditGoldenTest`, moved to
`androidHostTest` with their 12 byte-identical PNGs. Every method renders `LOCALE_RU`, so they are
the first goldens to render a shared entry's own RU copy, and a RU plural (`2 СЕССИИ`, the `few`
form), through CMP. The device test moved to `androidDeviceTest` with its 3 `@Smoke` identities.
Android keeps the six `SingleTrainingExtensionIdentityTest` cases, which reach the graph through
`appGraph.singleTrainingGraphFactory`, and the factory flow is proven on a device by the two
`@Regression` journeys that reach the `SingleTrainingGraph` tag:
`RouteReachabilityTest.singleTrainingOpensForCreateFromAllTrainingsAndTheListReturns` (a null route
argument) and `singleTrainingOpensFromASeededRowAndTheListReturns` (a non-null one).

#### Shared exercise feature

`feature:exercise` keeps its ten portable suites in `commonTest`, so the same 142 exact identities
execute through Android host and `iosSimulatorArm64Test`: click handler 77, common handler 16,
interactor 10, dirty state 14, navigation handler 6, UI mapper 6, deferred delete 6, input handler
4, route argument 2 and the Russian undo label 1. Six of them were added by Phase 7.15 to pin S2,
which no earlier identity observed (S17 of
[kmp-phase-7-15-exercise-feature.md](feature-specs/kmp-phase-7-15-exercise-feature.md)).
`Init for read mode kicks off observe + load` is the first identity with a `+` to run on Native;
the oracle compares it from the UTF-8 XML.

The suites use `kotlin.test`, `kotlinx-coroutines-test` and private in-file fakes. The click
suite's store fake reproduces the relaxed MockK store: `state` is the identity's flow,
`updateState` and the update-lambda `updateStateImmediate` apply, events and consumed actions
record, launches are counted, and every other member fails fast. Its `launch` runs nothing unless
the identity sets it up, as SPEC Section B11.1 lists: the action then `onSuccess`, or the action
only, launched on the identity's `runTest` scheduler, or captured and completed by the identity
itself; a throw from a launch that runs fails the identity. The common suite's store fake launches
every `launch` on the identity's scheduler inside `supervisorScope`, then `onSuccess`, routing a
throw to `onError`, and records `Flow.launch` without collecting. Every identity that launches
advances the scheduler until idle; none uses `runBlocking`. The interactor fakes answer what an
identity sets up, or the relaxed default, and log their calls in order, so an ordered verification
reads the log; the camera-permission fake answers the identity's value and counts its reads; the
navigator fake records `navTo` and `popBack`. S8's use-case interfaces let the interactor suite
fake all four use cases, beside recording fakes of the four repositories it reaches.

D4's two suites keep their names on recording fakes. The deferred-delete witness holds the rows
in an interactor fake whose `permanentlyDelete` removes one, records each consumed action with
whether a snackbar is queued at that moment, and so still pins the window, the commit outside the
Store and Back after the queue; the Russian undo-label suite pins that the deferred delete queues
the label the State holds. Both drain `SnackbarManager` first, because one Native test binary is
one process and the manager is a process-wide object. The real delete is
`ExerciseRepositoryImplDbTest`'s (core:data:exercise) and the Russian catalog
`ComposeStringResolverRuTest`'s (core:ui:kit).

Copy outside composition resolves through the injected `StringResolver`. The click and common
suites fake it strictly and in-file: the click suite answers the conflict progress with its two
counts, the permanent-delete title with the exercise's name and the confirm's three other reads
verbatim; the common suite answers `Init`'s eight click-copy reads, each with its own string.
Both throw on any other resource and on any read that does not pass exactly what production
passes. `ResourceWrapper` keeps only the UI mapper's two dates, so its fakes answer
`formatDayMonth` and `formatMediumDate` and throw on every other member.

S2 is pinned by the six added identities: `Init` resolves the click paths' constant copy into
State once per Store; the weight-wipe confirm, the set-removed toast, both tag-limit toasts and the
deferred delete carry it, a distinct string per field; and the permanent-delete confirm, resolved
in a captured launch, lands only on the menu sheet it replaces, so a menu dismissed before the
copy lands stays dismissed. The module's one production `core:data:database` edge is read by the
domain mapper alone. The interactor suite's repository fake is the one test exception: it imports
`PlanSetDataModel` once and names it once more, in `setAdhocPlan`'s parameter, by the maintainer's
ruling in Section B19 of the specification. No test reads `core:data:database-test`.

The exact Native identity
`io.github.stslex.workeeper.feature.exercise.ExerciseFeatureSceneIosTest.resourcesDetailEditorSheetsAndActionsRenderAndDispatch`
switches one content slot, from test-held state, between the production `ExerciseDetailScreen`,
`ExerciseEditScreen`, `ExerciseDetailMenuSheetContent`, `PlanInfoSheetContent` and
`ImageSourceSheetContent` under `AppTheme`. Every read outside composition goes through one real
`ComposeStringResolver`; the scene declares no fake resolver and no `ResourceWrapper`, since every
date it renders is a pre-formatted label. It resolves the whole private EN catalog as stored and
the plural filled, row 32's `\n\n` escape as two line breaks, the handlers' copy with production's
arguments, renders the read screen, the editor and the three sheets, and asserts every action and
source callback as one exact ordered list. Text goes through the `SetText` semantics action; the
fields are controlled, so the test-held State takes each typed value, as the Store would. Together
with the 142 portable cases, the exercise Native XML contains exactly 143 passing cases with no
skip, failure, or error.

What this does not cover: RU values execute only in the Description and Edit goldens; S2's guard
is raced only in `commonTest`'s captured launch, never on a device, and no identity measures the
confirm's one-dispatch delay; the graph's wiring (the image-source launcher and its four routes,
snackbars, undo actions, `BackHandler`, the result read-back, the confirm and conflict sheets) has
no scene path; the system camera and photo picker are Part A's device check; and the history and
record dates run only through `AndroidResourceWrapper` on Android.

The three golden classes, `ExerciseDetailGoldenTest`, `ExerciseDescriptionBlockGoldenTest` and
`ExerciseEditGoldenTest`, moved to `androidHostTest` with their 48 byte-identical PNGs. The Detail
class renders the EN catalog through CMP, its `4 SESSIONS` plural included; the Description and
Edit classes render the RU one, `sheetPlanInfo` the catalog's `\n\n` escape, the first `\n` in any
Compose catalog. Both device classes moved to `androidDeviceTest` with their 5 `@Smoke`
identities; `ExerciseFormBasicsTest` is pinned by D5's check, `exercise_device_contract`. Android
keeps the six `ExerciseExtensionIdentityTest` cases, which reach the graph through
`appGraph.exerciseGraphFactory`, and the factory flow and the result read-back are proven on a
device by the app journeys: `RouteReachabilityTest`'s three exercise journeys,
`NavigationResultTest`'s `imageReplaceRequestReachesTheExerciseThatOpenedTheViewer` and
`imageRemoveRequestTurnsTheThumbnailIntoThePhotoPickerEntryPoint`, `StoreRetentionTest` and
`BackStackStateRestorationTest.editorDraftSurvivesTheImageViewerRoundTrip`.

#### Shared live-workout feature

`feature:live-workout` keeps its 18 portable suites in `commonTest`, so the same 226 exact
identities execute through Android host and `iosSimulatorArm64Test`: interactor 29, click handler
26, dialog click handler 29, external writes 24, set mutator 27, mapper 20, picker handler 12, draft
behaviour 10, visible-rows resolver 8, status mapper 7, external-write coverage 6, unfilled set 6,
mapper reload 5, common handler 4, navigation handler 4, delete-copy mapper 4, route argument 4 and
input handler 1. One of them was added by Phase 7.16 Part C to pin D8, which no earlier identity
observed: `CommonHandlerTest`'s `a load resolves every copy field from its own catalog key` loads
through a catalog resolver that answers each of the 31 copy keys with its own string and fails on
any other, and requires `State.liveCopy` to equal `NAMED_COPY`, every key read exactly once (D8 of
[kmp-phase-7-16-live-workout-feature.md](feature-specs/kmp-phase-7-16-live-workout-feature.md)).
The two identities with `—` in their names run on Native too; the oracle compares them from the
UTF-8 XML.

The suites use `kotlin.test`, `kotlinx-coroutines-test` and private in-file fakes; MockK and
Robolectric left with the port, and no shared test names `java.`, `Thread.` or `runBlocking`
outside the scene. The four click-path suites share a relaxed store fake that reproduces the relaxed
MockK store: `state` is the identity's flow and `updateState` applies to it, a launch is counted and
runs nothing, and every other member does what the relaxed mock did; where an identity runs a
launch, a capturing store fake hands it the latest one, a throw routed to `onError`. The interactor
fake logs every call in order as a member and its arguments, so a verification is a
count over the log; the string resolver answers what the identity sets up and `""` otherwise.
Where a mock was strict, its fake is strict, and a member no identity reaches fails fast.
`CommonHandlerExternalWritesTest` runs a real `BaseStore` without Robolectric: `Log.isLogging` is
off for each identity and restored after it, the manual dispatcher's queue is a lock-free deque on
`kotlin.concurrent.atomics`, and the timer identity busy-waits 2 ms on `kotlin.time.Clock`. The
interactor suite runs `LiveWorkoutInteractorImpl` on recording fakes of its seven repositories and
the real `ExternalSessionWrites`.

The module's one production `core:data:database` edge is read by the domain mapper alone. The
interactor suite's repository fakes spell `PlanSetDataModel` and `SetTypeDataModel`, its only names
from that module; `core:data:database-test` is one `androidHostTest` edge, read by
`ExerciseDoneRuleTest` alone; and no shared source set names `java.*`. The topology gate's
`live_workout_dependency_contract` pins all four.

The exact Native identity
`io.github.stslex.workeeper.feature.live_workout.LiveWorkoutFeatureSceneIosTest.resourcesScreenSheetsDialogsAndActionsRenderAndDispatch`
reads every copy outside composition through one real `ComposeStringResolver`; the scene declares no
fake resolver and names no `ResourceWrapper`. It reads the whole private EN catalog as stored and
both plurals filled; repeats `CommonHandler.resolveCopy`'s 31 reads in its order, every field its
EN value verbatim; resolves the launch reads with production's arguments and the 13 `ErrorType`
messages; and runs the production mapper on Native (`Push Day` at `12:34`, its meta line, statuses,
card labels and finish statistics). It then switches one content slot, from test-held state,
between the production `LiveWorkoutScreen` and two `LiveSetRow`s under `AppTheme`: the loaded and
the empty screen; all five sheets and all five dialogs through the screen's own routing,
plan-editor's shared picker and kit's delete dialog among them; the retired
`LiveSetRowSemanticsTest`'s two rows, each field announcing its unit; and production's 42 actions as
one exact ordered list. Text goes through the `SetText` semantics action; the fields are controlled,
so the test-held State takes each typed value, as the Store would. Settling is bounded: ten steps of
one virtual second each. Together with the 226 portable cases, the live-workout Native XML contains
exactly 227 passing cases in 19 suites with no skip, failure, or error.

What this does not cover: RU values render only in `SessionStateGoldenTest.exerciseBodyweightRu`,
and the gate pins the RU catalog statically; the undo toast's five-second window has no scene path
(the toast itself is `SessionSheetsGoldenTest.undoToast`); the graph's wiring (the plan editor's
result read-back, haptics, snackbars, `BackHandler`, the load-failure exit and `AppLoadedContent`)
has no scene path, and the app journeys below cover it on a device; no UI dispatches
`OnDeleteSessionMenuClick`, so the delete dialog renders from test-held state only
([tech-debt.md → Dead delete-session surface](tech-debt.md#dead-delete-session-surface-in-featurelive-workout--actionclickondeletesessionmenuclick-has-no-emitter-phase-716-part-c-2026-10-26));
and the iosArm64 test binaries are linked, never run.

The four golden classes, `SessionStateGoldenTest`, `SessionHeaderGoldenTest`,
`SessionScreenGoldenTest` and `SessionSheetsGoldenTest`, moved to `androidHostTest` with their 60
byte-identical PNGs; they render the catalogs through CMP now, and `exerciseBodyweightRu` the RU
one. `LiveSetRowOverflowGateTest`, `SetColumnAlignmentGateTest` and `ExerciseDoneRuleTest` moved
beside them and run in every host test task, so `testAndroidHostTest` runs 21 suites and 235 cases.
`LiveWorkoutScreenTest` moved to `androidDeviceTest` with its 6 `@Smoke` identities. Android keeps
the seven `LiveWorkoutExtensionIdentityTest` cases, which reach the graph through
`AppGraph.liveWorkoutGraphFactory`, and the factory flow and the result read-back are proven on a
device by the app journeys: `RouteReachabilityTest`'s `liveWorkoutOpensBlankFromHomeAndHomeReturns`,
`liveWorkoutIsReEnteredThroughTheActiveSessionBanner` and
`planEditorOpensFromALiveSessionExerciseAndTheSessionReturns`,
`NavigationResultTest.planEditorSaveReachesTheLiveSessionThatOpenedIt` and
`StoreRetentionTest.liveWorkoutStoreSurvivesThePlanEditorRoundTrip`.

#### Shared app-dialogs feature

`feature:app-dialogs:impl` keeps its six portable suites in `commonTest`, so the same 43 exact
identities execute through Android host and `iosSimulatorArm64Test`: repository 16, resolver 12,
repo handler 5, observer 5, date mapper 3 and choose handler 2. Four of them were added by Phase
7.17 to pin D3, which moved the dialogs' medium date out of composition and into
`State.dateLabel`: `AppDialogRepoHandlerTest`'s `Observe writes the dialog's formatted date beside
it` and `AppDialogDateMapperTest`'s three
([kmp-phase-7-17-app-dialogs-feature.md](feature-specs/kmp-phase-7-17-app-dialogs-feature.md)).
Two identities were renamed for Native (S2), and the two with `—` in their names run on Native too.

The suites use `kotlin.test`, `kotlinx-coroutines-test` and private nested fakes; MockK left with
the port. `AppDialogRepositoryTest` runs the real file-backed store (D5): each identity opens
`PreferenceDataStoreFactory.createWithPath` over its own file in okio's
`FileSystem.SYSTEM_TEMPORARY_DIRECTORY`, on `Dispatchers.Default`, and deletes it afterwards through
`deleteStoreFile`, an `expect` with one `actual` per platform. The handler and observer suites run
the real `AppDialogRepository` and `AppDialogObserverImpl` over an in-memory `DataStore<Preferences>`
nested in each suite, whose edits complete on the test scheduler, so every claim is an effect on
the store (S3): a publish is read back, a dismiss reads back nothing, and a choice reaches a live
subscriber.

The exact Native identity
`io.github.stslex.workeeper.feature.app_dialogs.impl.AppDialogFeatureSceneIosTest.resourcesDatesVariantsAndChoicesRenderAndDispatch`
reads the whole private EN catalog as stored and its four placeholder entries filled through one
real `ComposeStringResolver`; runs the production date mapper over all four variants with a stubbed
`ResourceWrapper.formatMediumDate`, the scene's only test double; then renders the production
`AppDialogHostContent` under `AppTheme` from test-held State: nothing, both restore successes, the
failure body for each of the twelve `BackupErrorCode`s, the undo confirmation and the undo success,
and requires the nine choices as one exact ordered list. Settling is bounded: ten steps of one
virtual second each. Together with the 43 portable cases, the app-dialogs Native XML contains
exactly 44 passing cases in 7 suites with no skip, failure, or error.

What this does not cover: RU values, which the gate pins statically; `AppDialogHost` itself, whose
two State reads the gate pins (`app_dialogs_contract`); the Store and its graph, whose wiring
`AppDialogExtensionIdentityTest` (4 cases, through `AppGraph.appDialogGraphFactory`) and
`DialogStateAcrossGenerationsTest` cover on Android; and the iosArm64 test binaries, which are
linked, never run. `AppDialogHostContentTest` moved to `androidDeviceTest` with its 10 `@Smoke`
identities.

#### Shared string resolver

`core:ui:kit`'s `ComposeStringResolver` is proven against kit's real catalog, outside
composition and with no fake. On the Android host, under Robolectric,
`ComposeStringResolverTest.resolvesTheEnglishCatalog` reads the EN catalog and
`ComposeStringResolverRuTest.resolvesTheRussianCatalog` the RU one; the Russian half is a second
class in the same file with `@Config(sdk = [33], qualifiers = "ru")`, as
`CommonHandlerComposeResourceTest.kt` does, because the Robolectric JUnit 5 extension forbids
method-level `@Config`. Each method reads
four rows in one `runBlocking` (a plain string, a one-argument string, a two-argument string in
argument order, and a placeholder string read without arguments) and asserts them with
`assertAll`, so every failing row is reported. On Native, the exact identity
`io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolverIosTest.resolvesTheEnglishCatalog`
reads the same four EN rows, and the kit validator pins it.

Kit's catalog has no plural, so neither kit test reaches `getPluralString`. Its delegation first
runs on Native, in `feature:all-exercises`' production scene, which reads all nine of that
module's plurals through the real resolver; Android host has no route to it (see
[Shared all-exercises feature](#shared-all-exercises-feature)). The mapper identities use a fake,
so they do not execute it. Feature tests fake the interface in-file, switching on the generated
resource objects; there is no shared fake or fixtures module.

#### Image sources

`core:ui:kit`'s `ImageSourceLauncherTest.launchesEachContractAndRoutesItsResult` drives the
Android actual of `rememberImageSourceLauncher` in one Robolectric composition, under a recording
`ActivityResultRegistry` provided through `LocalActivityResultRegistryOwner` and a recording
`Context`: each verb's contract and input, each result's callback, and the app-details settings
intent. The system camera and photo-picker apps, and the exercise graph's mapping of the
permission result to `RequestCameraCapture` / `OnCameraPermissionDenied`, are the maintainer's
device check (kmp-phase-7-15-exercise-feature.md Section 12.2). The iOS actual is compiled, never
executed.

#### Shared MVI runtime

`core:ui:mvi` uses `commonTest` for Store lifetime, disposal, navigation-result, event-pressure and
launch-failure recording contracts; `androidHostTest` for the clean JVM ABI, the real Android
Firebase-provider seam and the Store-tag wiring of a launch failure;
`iosTest` for the production Compose processor scene; and `androidDeviceTest` for the two
`@Smoke` lifetime/retention cases. The legacy `src/test` and `src/androidTest` directories must
remain empty.

The identity gates parse JUnit XML structurally rather than accepting a Gradle exit code or module
total. They require twenty exact Android-host identities, fourteen exact MVI Native identities,
and both Android-device identities. Native results live under
`build/test-results/iosSimulatorArm64Test`; the AGP-KMP connected result path is
`core/ui/mvi/build/outputs/androidTest-results/connected/androidMain`.

- `NavResults.OnResult` is deliberately not unit-tested: it is a `@Composable` and needs a
  composition, which `core:ui:mvi`'s unit tests have no host for. Its two behaviours — deliver
  only on a non-null result, and clear after delivering — are covered instead as the composition
  of `NavResults.result` and `NavResults.clear`, including the full re-arm cycle, in
  `NavigationResultContractTest`.
- DataStore-backed repositories get no "process restart" simulation. Preferences DataStore
  enforces singleton-per-file at runtime, so recreating the store over the same temp file would
  first require cancelling the DataStore's internal `CoroutineScope`. Cross-restart persistence is
  treated as the library's responsibility; the publish-then-read tests exercise the same
  persistence path through the file storage layer (`AppDialogRepositoryTest`).
- `PastExerciseUiModel.setSummary` has exactly **one** test reader in the whole suite:
  `PastSessionUiMapperTest`'s "mapper builds the collapsed summary per exercise type" and its
  three siblings. Every golden fixture hand-writes the summary string into its own data and never
  calls `toUi`, so before those tests a `return ""` in `PastSessionUiMapper.setSummary()` kept
  everything green while collapsed cards silently lost their plan line. Pinned there: weighted
  joins as `{weight}×{reps}` with `×` = U+00D7 (not the Latin letter `x`) separated by `" · "`;
  weightless collapses to bare rep counts; a WEIGHTED set with `weight = null` keeps the `×` shape
  with the em dash in the weight position (`"49×15 · —×15"`) so 15 reps are never re-read as 15 kg
  among weight-leading neighbours; sets with `reps == 0` are excluded.
- `AppIconsMirroringTest`'s `NON_DIRECTIONAL` half is a **negative control**, not padding.
  `autoMirror` is a property of the `ImageVector`, not a pixel, so LTR goldens are byte-identical
  whichever way it is set and a semantics test never reads it — asserting only that `ChevronLeft`
  / `ChevronRight` mirror would pass just as happily if `strokeIcon` set `autoMirror = true` for
  EVERY glyph in the file, a worse defect than the one being fixed (the manifest sets
  `android:supportsRtl="true"`, so a fixed-path directional glyph points the wrong way in an RTL
  locale). `AppIcons.Skip` sits in `NON_DIRECTIONAL` deliberately rather than with the directional
  pair: it is a media-transport glyph, and a transport timeline reads left-to-right in every
  locale, so mirroring it would point the control at the wrong end of the track.
- `ColorFieldScanner.COLOR_GETTER` matches `^get([A-Z][A-Za-z0-9]*)(?:-.+)?$` over zero-arg
  `long`-returning methods, with the suffix optional, because
  `androidx.compose.ui.graphics.Color` is a `@JvmInline value class` over `ULong`: its property
  getters compile to no-arg methods returning primitive `long`, usually with a mangled name suffix
  (e.g. `getPrimary-0d7_KjU`). A plain non-colour `Long` property also matches. The over-reporting
  is deliberate and safe — `PaletteContrastReportTest` asserts the scanned name set against an
  explicit expected set, so a false positive fails loudly and a false negative (a colour hidden
  from the contrast report) cannot happen.
- `NavTransitionsTest` can assert the predictive transitions on the JVM ONLY because the design
  carries no `slide` and no `changeSize` channel. `AnimatedContentTransitionScope` is a sealed
  interface with only `internal` implementations, so the receiver `NavDisplay` supplies cannot be
  faked; every builder in `NavTransitions.kt` therefore takes `AppMotion` + `Int` and touches no
  receiver member, and the host's lambdas only delegate. `EnterTransition` / `ExitTransition` then
  compare structurally through `TransitionData`, whose `Fade` / `Scale` / `Veil` leaves are data
  classes and whose `TweenSpec` compares duration, delay and easing. `slide` and `changeSize` both
  store a lambda that is reference-compared, and `slideOutHorizontally` re-wraps its argument, so
  even a shared production lambda would not survive the wrap — adding either channel silently
  removes this whole file's ability to assert.

### Screen serialization registry test

`ScreenSerializationTest` round-trips every sealed `Screen` leaf through kotlinx.serialization
`Json` configured with `screenSavedStateConfiguration.serializersModule` — **not** through
`encodeToSavedState` / `decodeFromSavedState`. The SavedState encoder is `Bundle`-backed and needs
a device or Robolectric, while the defect being guarded — a leaf missing from
`screenSerializersModule` — throws in the polymorphic serializer LOOKUP, which is
format-independent. The module under test is read off `screenSavedStateConfiguration`, the exact
object production hands to `rememberNavBackStack`. (`documentation/feature-specs/nav3-stage-1-3.md`
still names the SavedState vehicle; the JSON one is what the test does, and "correcting" the test
to match that spec would break the JVM run.)

`sampleValue` returns non-null samples (`"sample"` for `String`, `true` for `Boolean`) even for
**nullable** constructor parameters, because a null field encodes as an absent key in most formats
and would let an asymmetric field slip through the round trip undetected. Seeding null for the
nullable uuid params — `Screen.Training.uuid`, `Screen.Exercise.uuid`, `Screen.LiveWorkout`'s two,
`Screen.ExerciseChart.exerciseUuid`, `Screen.PlanEditor.Existing`'s three — would weaken the test
rather than broaden it.

Since the module's KMP conversion the reflection test lives in `androidHostTest`, guards the
leaf count exactly (12 at the current baseline — a route-set change must deliberately update
it), and additionally pins the module's no-compatibility JVM interface ABI: both `isSingleTop`
getters are Java default methods and no `DefaultImpls` bridge class is loadable. Its
Kotlin/Native sibling, `ScreenSerializationIosTest.allCurrentRoutesRoundTripThroughProductionRegistry`
(`src/iosTest`), round-trips the same routes through the production registry and pins the exact
`nav-result` key strings; it proves the registry executes under Kotlin/Native, while the JVM test
stays the hierarchy-change detector.

**The two oracles share one fixture, and the JVM one pins it to the hierarchy.** Kotlin/Native
has no sealed-subclass reflection, so the Native test cannot discover routes — it reads
`screenSampleCatalog` and `SCREEN_ROUTE_BASELINE` from
`core/ui/navigation/src/commonTest/.../ScreenSampleCatalog.kt`, a plain fixture with no test-framework
dependency, visible to both `androidHostTest` and `iosTest` through the default KMP source-set
hierarchy. Left alone that catalog would be a second hand-maintained list free to drift from the
routes that actually exist, so the host test asserts `sealedLeaves(Screen::class).toSet()` equals
`screenSampleCatalog.map { it::class }.toSet()`, plus both sizes against the shared baseline and
one sample per class. A route added to `Screen.kt` but missing from the catalog — or a catalog
entry duplicated — reds the host test: a duplicated or swapped entry is reported by name, a count
drift by count.

The host test deliberately keeps constructing its round-trip subjects **by reflection** from the
discovered hierarchy rather than from the catalog. Round-tripping the catalog on both platforms
would only re-read the same list twice; generating subjects from the hierarchy is what makes the
JVM run a hierarchy-change detector. Exhaustiveness remains limited to the current direct and
sealed-reachable hierarchy — a route implementing only the non-sealed `ScreenWithResult` marker
would escape discovery, which is why new result routes keep the explicit
`: Screen, ScreenWithResult<R>` shape. There is no classpath scanning.

Adding a route therefore costs four edits — register the serializer, add one non-default sample,
increment the baseline, re-run both gates — as listed in the
[`add-feature`](../.claude/skills/add-feature.md) route step.


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

#### Instrumented-suite hazards

- **`pending_*` dialog flags outlive both the graph and the test.** They live in the
  process-lifetime `app_dialogs_prefs` DataStore, shared by every androidTest in the `:app:app`
  instrumentation process no matter how many `AppGraph` generations a test builds —
  `MetroTestRule`'s per-test in-memory DB teardown does not touch them. A test that publishes an
  `AppDialog` and does not acknowledge it leaves the flag set for every later test in the run.
  `DialogStateAcrossGenerationsTest.tearDown` therefore builds one extra generation and calls
  `appDialogObserver.acknowledgeReaction(...)` on whatever `appDialogRepository.currentDialog`
  still holds; it is not redundant cleanup.
- **Injected fling gestures do not scroll the AllExercises list on CI's x86_64 emulator.** Two
  prior versions of `scrollListUntilComposed` used `performTouchInput { swipeUp() }` and both went
  red on that profile while green on arm64 (runs 31884113468, 31885121564). The second falsified
  the pacing theory: 24 progress-checked swipes moved the composed row window ZERO times. Use
  `performScrollToNode`, which drives the lazy list's own scroll semantics — no gesture, no
  viewport math, no gesture-navigation interference. Its one precondition, that the item be
  resolvable by the lazy layout, holds by construction in `BackStackStateRestorationTest`: Paging's
  default initial load (3 × pageSize = 30) covers all `SEEDED_ROWS = 14` rows up front, so the
  journey contains no paging append.
- **Never assert on a relative-time meta string.** `NavSeed`'s
  `FIXED_CREATED_AT = 1_700_000_000_000L`, `FIXED_STARTED_AT = 1_700_000_100_000L` and
  `FIXED_FINISHED_AT = 1_700_000_200_000L` stop drift WITHIN a run, not across the calendar: they
  are a moment in 2023, so a relative-time label rendered from them already reads "years ago" and
  changes again every month with no code change. Assert on seeded names (unique per test) or on
  absolute values the seed controls. Nothing breaks today only because every selector in the suite
  is name-based.
- **`app/app`'s androidTest source set cannot see a feature module's internal Compose `Res`.**
  `NavigationResultTest.NO_PLAN_LABEL = "no plan"` is live-workout's
  `Res.string.feature_live_workout_status_no_plan` inlined by hand; the generated `Res` is internal
  to the module, and an edge to reach one string would give the navigation suite a compile-time
  dependency on a feature it is otherwise independent of. Accepted cost: re-wording the string
  turns the "before" assertion into a comparison against a stale literal — which fails loudly
  rather than passing quietly, so the failure mode is the safe one.
- **`BottomBarItem.testTag` (`"BottomAppBarItem_$name"`) is a test contract, not a private
  detail.** `ApplicationBottomBarTest` looks items up by it directly, and `harness/NavPaths.kt`
  pins `BOTTOM_BAR_HOME` / `BOTTOM_BAR_TRAININGS` / `BOTTOM_BAR_EXERCISES` from it. Those tests
  are about navigation lifecycle, so renaming the tag mixes a chrome change into a test change.

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
| `feature/all-trainings` | `feature/all-trainings/src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt` |
| `feature/all-exercises` | `feature/all-exercises/src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenTest.kt` |
| `feature/single-training` | `feature/single-training/src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingScreenTest.kt` |
| `feature/exercise` | `feature/exercise/src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseScreenTest.kt`, `…/ExerciseFormBasicsTest.kt` |
| `feature/settings` | `feature/settings/src/androidDeviceTest/kotlin/io/github/stslex/workeeper/feature/settings/SettingsScreenTest.kt` |
| `app/app` (`@Regression`, real graph) | `app/app/src/androidTest/.../app/ApplicationBottomBarTest.kt`, `ExerciseCreatePersistenceTest.kt`, `NavigationLifecycleRegressionTest.kt`, `RecoveryActivityDbFreeTest.kt`, `AllTrainingsExtensionDbVisibilityTest.kt` |

## Visual gate — Paparazzi screenshot goldens

Applies to every module that records goldens: `:core:ui:kit` (KMP), `:core:ui:plan-editor` (KMP),
`:core:ui:start-mode` (KMP), `:feature:archive` (KMP), `:feature:all-trainings` (KMP),
`:feature:all-exercises` (KMP), `:feature:past-session` (KMP), `:feature:home` (KMP),
`:feature:exercise-chart` (KMP), `:feature:single-training` (KMP), `:feature:exercise` (KMP),
`:feature:live-workout` (KMP), and `:feature:settings` (KMP). Goldens
live in
`src/test/snapshots/images/` in classic Android modules and in
`src/androidHostTest/snapshots/images/` in KMP modules (`:core:ui:kit`, `:core:ui:plan-editor`,
`:core:ui:start-mode`, `:feature:archive`, `:feature:all-trainings`, `:feature:all-exercises`,
`:feature:past-session`, `:feature:home`, `:feature:exercise-chart`,
`:feature:single-training`, `:feature:exercise`, `:feature:live-workout`, `:feature:settings`), and
are committed. The shared harness
(`golden`, `goldenSubject`, `GOLDEN_DEVICE`, `GoldenTheme`) is `core:ui:golden-harness`; the
shared liveness gate is `gradle/golden-gate.gradle.kts`, applied with
`apply(from = "$rootDir/gradle/golden-gate.gradle.kts")`.

```bash
# Verify the committed goldens (what CI runs, before detekt)
./gradlew verifyPaparazziDebug

# Re-record after an intentional visual change, then commit the PNGs
./gradlew :core:ui:kit:recordPaparazziDebug
```

Both spellings cover the KMP-shaped `:core:ui:kit`, `:core:ui:plan-editor`,
`:core:ui:start-mode`, `:feature:archive`, `:feature:all-trainings`, `:feature:all-exercises`,
`:feature:past-session`, `:feature:home`, `:feature:exercise-chart`,
`:feature:single-training`, `:feature:exercise`, and `:feature:live-workout`
too: on a KMP module Paparazzi registers
`verifyPaparazziAndroidMain` / `recordPaparazziAndroidMain`, and the KMP compose convention
registers `verifyPaparazziDebug` / `recordPaparazziDebug` as lifecycle aliases onto them, so the
repo-wide commands above keep reaching every golden module.

**Re-record workflow.** `recordPaparazziDebug` regenerates every golden, the PNGs are
committed alongside the code change, and the reviewer reads the image diff. During the v3
redesign the goldens are re-recorded at every visual step — they are the *before* picture for
the next one, and are disposable by design.

**Rule for the redesign branch: a golden change must be intentional and explained in the
commit body. An unexplained golden delta is a review stop.** A commit that touches a module's
`snapshots/` directory (`src/test/snapshots/` or `src/androidHostTest/snapshots/`) without
saying why in its message should not be approved.

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
and `recordPaparazziDebug` are therefore finalized by each golden-holding module's
`assertGoldenLiveness`, which fails the build unless at least as many golden test cases
executed as there are committed golden images.

That assertion reads the JUnit XML — a cacheable *output* of the test task. So with the build
cache warm it would read restored XML and vouch for a run that never happened: measured, a
wiped `core/ui/kit/build` plus a warm cache printed *"Visual gate live: 10 golden test case(s)
executed"* in a 565 ms build that executed none. The goldens themselves are declared inputs, so
a *changed* golden always misses the cache and is still caught — only the liveness claim was
hollow. The host test task (`testDebugUnitTest` on classic modules, `testAndroidHostTest` on KMP
modules — the KMP `testDebugUnitTest` is a plain lifecycle alias, never cast to `Test`) is
therefore marked `doNotCacheIf` + `upToDateWhen { false }` when a Paparazzi task was requested,
so the gate executes on every invocation, CI or local.

**The subject-sized canvas crops what leaves the bounds.** `goldenSubject` renders with
`SessionParams.RenderingMode.SHRINK`, which sizes the image to the content, so anything drawn
outside the specimen's own bounds is cropped away. Padding in these fixtures is load-bearing, not
layout taste: `LiftSpecimen` pads all sides with `AppDimension.Space.xl` because the light theme's
cast shadow is half of what `surfaceLifted` vs `surfaceResting` asserts, and
`railWithOneOffUnderline` reserves `Modifier.padding(bottom = 8.dp)` because the `.grp.temp::after`
dashed `dim` underline beneath a one-off group draws 4dp BELOW the band, outside
`AppProgressRail`'s own bounds. For the same reason golden fixtures compose `PlanEditorBody` with
`scrollable = false`: a capped inner scroller inside a SHRINK canvas photographs the cap rather
than the list (the host owns the scroll on both surfaces the body ships on).

**Every golden paints `AppUi.colors.surfaceTier0` across the whole frame.** Without that paint the
visible background comes from Paparazzi's `theme` parameter, not from `AppTheme` — a dark and a
light golden would then share a background, leaving the actual theme difference unverified.
`GoldenTheme.windowTheme` (`android:Theme.Material.Light.NoActionBar` for LIGHT,
`android:Theme.Material.NoActionBar` for DARK) should therefore never actually be visible; it is
still set per variant so that a golden which forgets to paint its background produces an obviously
wrong image rather than a plausible one. Sheet *content* is the deliberate exception — it renders
on `surfaceTier3`, because `AppBottomSheet`'s `containerColor` is `surfaceTier3` and
`AppSheetLayout` paints no background of its own (`ExerciseChartGoldenTest.pickerSheetContent`,
`SessionSheetsGoldenTest`).

**Native resolution is measured, not preference.** `useDeviceResolution = true`. Scaled, a `1.dp`
rule landed across 2–4 rows at four different intensities (`#E3E3DF`, `#EFEFEC`, …) — resampling
blur, not rendering. At device resolution the same rule is 3 rows of one flat `#E0E0DC` and the
`0.5.dp` rule is exactly 1 row: layoutlib snaps hairlines to whole pixels, so the blur was
entirely a downscale artefact. Cost is 216 KB for the six goldens involved. Never re-record a
golden scaled — that reintroduces exactly this blur.

**`HairlineCanaryGoldenTest` is why that is known.** 440 dpi is 2.75 physical pixels per dp, so a
`1.dp` rule is 2.75 px and a `0.5.dp` rule is 1.375 px — neither lands on a pixel boundary, and
the redesign leaves the hairline as the only section separator in the app. The canary probes phase
deliberately: the spacer heights `7.dp`, `10.5.dp`, `13.dp`, `0.5.dp` accumulate fractional
offsets so successive rules start at different sub-pixel phases down the frame. Measured answer:
no partial-alpha edge at any phase, identically in both themes, so hairlines are crisp and stable
at `maxPercentDifference = 0.0`. A failure here is a NO-GO finding for the hairline golden
category, to be reported.

**Paparazzi decodes no image.** `ExerciseDescriptionBlockGoldenTest.readWithImage` passes
`ImageDisplay.FromPath(path = "/exercise/preview.jpg", lastModified = 1L)` into an `AsyncImage`
and photographs the FILLED box's own treatment — solid-bordered gradient, no glyph — rather than a
photograph. That is the assertion by design: the has-picture / has-no-picture signal under test is
the border and the fill, not the picture. `ExerciseThumbGoldenTest` photographs the same
distinction using a flat `Box` stand-in for the same reason.

**One frame, and the clock is never advanced.** Anything seeded or left mid-flight is captured
mid-flight. `ChartPointsAnimator` therefore seeds its `entries` / `live` lists synchronously in
its constructor at `presence = 1f`, and `retarget(animate = false)` snaps everything — that, plus
the draw phase never seeing an unpopulated collection, is why the seeding is synchronous. In
`ChartCanvas` the animator is held by `remember` with NO keys deliberately: it must outlive every
dataset, since a new dataset retargets the existing points rather than replacing the collection.

**Localisation coverage.** Only a `LOCALE_RU` frame can fail on a hardcoded set-type letter: at
the harness's default locale a hardcoded literal in `AppSetTypeChip` and a `stringResource` render
identically, so no `en`-only frame can see `W` drawn over the Russian warmup label.
`SetTypeMarkGoldenTest.setTypeMarksRu` is therefore not duplicate coverage of the `en` frame, and
`AppSetBar` shares those frames both because its labels localise (the Russian and English add-set labels) and
because the drawn `opacity:.35` disabled half has no other instrument — a handler test cannot see
an alpha. Conversely `NavBarGoldenTest` deliberately ships **no** `values-ru` variant: Cyrillic
reaches `AppNavBar` only through `contentDescription`, which is invisible in a picture. That is
safe only while the chosen `#s-nav` variant draws no captions at all; restoring nav captions
restores the gap.

**A golden fixture can be mutation-blind.** `PastSessionGoldenTest.cardSkippedEmpty` zeroes
`setSummary` and `sets` together, so swapping the `skipped` and `setSummary.isNotEmpty()` branches
of `CardHeader`'s plan-line `when` produces BYTE-IDENTICAL pixels there. `cardSkippedWithSets`
(skipped with a non-empty summary and real sets) is the fixture that actually pins the precedence,
and that state is reachable in production — the live session preserves performed sets across a
skip toggle, and `PastSessionUiMapper` fills both `setSummary` and `sets` regardless of `skipped`.
The two are not redundant; deleting the second removes the only coverage of the branch order.

**Jupiter drives Paparazzi, with no `junit-vintage-engine` on the classpath.** Paparazzi
2.0.0-alpha05 exposes `setup(TestName)` / `teardown()` as public members that touch no JUnit 4
type — `setup` builds the `PaparazziSdk`, calls its `setup()` and `prepare()` and stores the test
name; `teardown()` tears the SDK down and closes the snapshot handler; the `TestRule`
implementation on the same class is just another caller of those two. That matters beyond
tidiness: on the JUnit 4 path a missing Vintage engine let `verifyPaparazziDebug` exit 0 having
executed zero screenshot tests, whereas with Jupiter as the only engine present, dropping it fails
loudly ("Cannot create Launcher without at least one TestEngine"). It does not close the hole in
general — a task-level test filter still produces a silent zero-test pass — which is what
`assertGoldenLiveness` is for.

**Goldens are excluded from a plain `testDebugUnitTest`.** Paparazzi decides verify-vs-record from
the `paparazzi.test.verify` system property, and the plugin injects it at *execution* time on its
own tasks only — it is absent from the test task's configured `systemProperties` under either
invocation. So under a plain `testDebugUnitTest` the goldens render into the build-dir report and
always pass, costing ~6 s per CI build for something that only looked like a second safety net.
Hence `excludeTestsMatching("*.golden.*")` when no Paparazzi task was requested — applied to the
classic `testDebugUnitTest` or, on a KMP module, to the real host `Test` tasks, where the filter
also sets `isFailOnNoMatchingTests = false` so a golden-only KMP module's plain run cannot die on
its own filter. "Paparazzi mode" is guessed from
`gradle.startParameter.taskNames.any { "Paparazzi" in it }` — true for both the classic and the
`*AndroidMain` spellings — which could in principle be wrong for a lifecycle task that pulls in a
verify task without naming it; `assertGoldenLiveness` is what catches that case.

### Settings shared-boundary coverage

Settings preserves 120 original non-golden test identities: 22 run from `commonTest`, and 98
remain Android-host tests. Two real-Store availability cases and four Android adapter cases
bring its Android-host non-golden inventory to 126. Native runs the 22 ports, two availability
cases, three unavailable-backup/production-graph cases and four production-scene cases: 31 exact
identities. No mock backup backend is supplied to the Native parent. Its local dispatcher fixture
uses the existing plain iOS annotations; Android retains both qualified identity accessors.
The 12 golden PNGs move byte-identically to `androidHostTest`; two Settings Smoke identities and
four app extension identity meanings remain. The app's 12 generation/dialog device identities
are verified separately from these host and Native inventories.

The Native oracle registers literal identities before XML is observed. Settings results must be
newer than `feature/settings/build/native-test-start`; the existing job removes Settings results
and creates that marker before execution, then uploads both. Local full Native runs must do the
same preparation. Missing, stale, empty, skipped-only, duplicate or incomplete XML is a failure.
Linking `iosArm64` tests is binary evidence, never device test execution. See
[Phase 7.18 evidence](feature-specs/kmp-phase-7-18-settings-feature.md#12-exit-evidence-ledger)
for executed gates, controls, review and remaining limits.

## Running tests

From the project root:

```bash
# Unit tests (JVM, fast; on KMP modules the alias fans out to testAndroidHostTest).
# Needs python3 on PATH: the Wear alias also runs :app:wear:verifyEmulatorAcceptanceRunner, the
# acceptance parser suite with its exact 65-test identity inventory (run_parser_tests.py).
./gradlew testDebugUnitTest

# Shared MVI Android-host tests plus exact JUnit identities
./gradlew :core:ui:mvi:testAndroidHostTest --rerun-tasks --no-build-cache --no-configuration-cache
python3 .github/scripts/assert_mvi_host_identities.py

# Native Phase-7 tests of the 18 modules (macOS + Xcode), kept in one --continue invocation
mkdir -p feature/settings/build
rm -rf feature/settings/build/test-results/iosSimulatorArm64Test
touch feature/settings/build/native-test-start
./gradlew :core:ui:kit:iosSimulatorArm64Test \
  :core:ui:navigation:iosSimulatorArm64Test \
  :core:ui:mvi:iosSimulatorArm64Test \
  :core:ui:start-mode:iosSimulatorArm64Test \
  :core:ui:plan-editor:iosSimulatorArm64Test \
  :feature:image-viewer:iosSimulatorArm64Test \
  :feature:plan-editor:iosSimulatorArm64Test \
  :feature:archive:iosSimulatorArm64Test \
  :feature:all-trainings:iosSimulatorArm64Test \
  :feature:all-exercises:iosSimulatorArm64Test \
  :feature:past-session:iosSimulatorArm64Test \
  :feature:home:iosSimulatorArm64Test \
  :feature:exercise-chart:iosSimulatorArm64Test \
  :feature:single-training:iosSimulatorArm64Test \
  :feature:exercise:iosSimulatorArm64Test \
  :feature:live-workout:iosSimulatorArm64Test \
  :feature:app-dialogs:impl:iosSimulatorArm64Test \
  :feature:settings:iosSimulatorArm64Test \
  --rerun-tasks --no-build-cache --no-configuration-cache --console=plain --continue
python3 .github/scripts/assert_kmp_ios_smoke.py

# The same modules' iosArm64 (device) test binaries (macOS): compiled and linked, never run.
# Each lands at <module>/build/bin/iosArm64/debugTest/test.kexe, which CI requires 18/18.
./gradlew :core:ui:kit:linkDebugTestIosArm64 \
  :core:ui:navigation:linkDebugTestIosArm64 \
  :core:ui:mvi:linkDebugTestIosArm64 \
  :core:ui:start-mode:linkDebugTestIosArm64 \
  :core:ui:plan-editor:linkDebugTestIosArm64 \
  :feature:image-viewer:linkDebugTestIosArm64 \
  :feature:plan-editor:linkDebugTestIosArm64 \
  :feature:archive:linkDebugTestIosArm64 \
  :feature:all-trainings:linkDebugTestIosArm64 \
  :feature:all-exercises:linkDebugTestIosArm64 \
  :feature:past-session:linkDebugTestIosArm64 \
  :feature:home:linkDebugTestIosArm64 \
  :feature:exercise-chart:linkDebugTestIosArm64 \
  :feature:single-training:linkDebugTestIosArm64 \
  :feature:exercise:linkDebugTestIosArm64 \
  :feature:live-workout:linkDebugTestIosArm64 \
  :feature:app-dialogs:impl:linkDebugTestIosArm64 \
  :feature:settings:linkDebugTestIosArm64 \
  --rerun-tasks --no-build-cache --no-configuration-cache --console=plain --continue

# Focused MVI device cases plus exact JUnit identities
./gradlew :core:ui:mvi:connectedAndroidDeviceTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=io.github.stslex.workeeper.core.ui.test.annotations.Smoke \
  --rerun-tasks --no-build-cache --no-configuration-cache
python3 .github/scripts/assert_mvi_device_identities.py

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

- **Unit tests run on every PR and on pushes to `master` and `dev`** as part of
  `android_build_unified.yml`; a push whose changed files are all Markdown skips the workflow.
- **UI tests do not gate PRs.** The `ui_tests.yml` workflow runs weekly (Mondays
  05:00 UTC, against `dev`; the cron only evaluates from the default branch, so it
  activates once the workflow reaches `master` with a release) and on manual dispatch
  with a `smoke` / `regression` / `all` selector. They do not block PR merges. Run them
  locally before opening a PR that touches Compose code.

See [ci-cd.md](ci-cd.md) for the full pipeline.
