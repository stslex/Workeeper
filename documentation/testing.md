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

#### Shared MVI runtime

`core:ui:mvi` uses `commonTest` for Store lifetime, disposal, navigation-result and event-pressure
contracts; `androidHostTest` for the clean JVM ABI and real Android Firebase-provider seam;
`iosTest` for the production Compose processor scene; and `androidDeviceTest` for the two
`@Smoke` lifetime/retention cases. The legacy `src/test` and `src/androidTest` directories must
remain empty.

The identity gates parse JUnit XML structurally rather than accepting a Gradle exit code or module
total. They require seven exact Android-host identities, five exact MVI Native identities, and both
Android-device identities. Native results live under `build/test-results/iosSimulatorArm64Test`;
the AGP-KMP connected result path is
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
- **`app/app`'s androidTest source set cannot see a feature module's `R`.**
  `NavigationResultTest.NO_PLAN_LABEL = "no plan"` is
  `R.string.feature_live_workout_status_no_plan` inlined by hand; adding that dependency to reach
  one string would give the navigation suite a compile-time edge into a feature it is otherwise
  independent of. Accepted cost: re-wording the string turns the "before" assertion into a
  comparison against a stale literal — which fails loudly rather than passing quietly, so the
  failure mode is the safe one.
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
| `feature/all-trainings` | `feature/all-trainings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt` |
| `feature/all-exercises` | `feature/all-exercises/src/androidTest/.../AllExercisesScreenTest.kt`, `AllExercisesScreenAccessibilityTest.kt`, `AllExercisesScreenEdgeCasesTest.kt` |
| `feature/single-training` | `feature/single-training/src/androidTest/.../SingleTrainingScreenTest.kt` |
| `feature/exercise` | `feature/exercise/src/androidTest/.../ExerciseScreenTest.kt`, `ExerciseFormBasicsTest.kt` |
| `feature/settings` | `feature/settings/src/androidTest/.../SettingsScreenTest.kt` |
| `app/app` (`@Regression`, real graph) | `app/app/src/androidTest/.../app/ApplicationBottomBarTest.kt`, `ExerciseCreatePersistenceTest.kt`, `NavigationLifecycleRegressionTest.kt`, `RecoveryActivityDbFreeTest.kt`, `AllTrainingsExtensionDbVisibilityTest.kt` |

## Visual gate — Paparazzi screenshot goldens

Applies to every module that records goldens: `:core:ui:kit` (KMP), `:core:ui:plan-editor`,
`:core:ui:start-mode` (KMP), and `feature/`{`all-exercises`, `all-trainings`, `archive`, `exercise`,
`exercise-chart`, `home`, `live-workout`, `past-session`, `settings`, `single-training`}. Goldens
live in `src/test/snapshots/images/` in classic Android modules and in
`src/androidHostTest/snapshots/images/` in KMP modules (`:core:ui:kit`,
`:core:ui:start-mode`), and are committed. The shared harness
(`golden`, `goldenSubject`, `GOLDEN_DEVICE`, `GoldenTheme`) is `core:ui:golden-harness`; the
shared liveness gate is `gradle/golden-gate.gradle.kts`, applied with
`apply(from = "$rootDir/gradle/golden-gate.gradle.kts")`.

```bash
# Verify the committed goldens (what CI runs, before detekt)
./gradlew verifyPaparazziDebug

# Re-record after an intentional visual change, then commit the PNGs
./gradlew :core:ui:kit:recordPaparazziDebug
```

Both spellings cover the KMP-shaped `:core:ui:kit` and `:core:ui:start-mode` too: on a KMP module Paparazzi registers
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

## Running tests

From the project root:

```bash
# Unit tests (JVM, fast; on KMP modules the alias fans out to testAndroidHostTest)
./gradlew testDebugUnitTest

# Shared MVI Android-host tests plus exact JUnit identities
./gradlew :core:ui:mvi:testAndroidHostTest --rerun-tasks --no-build-cache --no-configuration-cache
python3 .github/scripts/assert_mvi_host_identities.py

# Native Phase-7 tests (macOS + Xcode), kept in one --continue invocation
./gradlew :core:ui:kit:iosSimulatorArm64Test \
  :core:ui:navigation:iosSimulatorArm64Test \
  :core:ui:mvi:iosSimulatorArm64Test \
  :core:ui:start-mode:iosSimulatorArm64Test \
  --rerun-tasks --no-build-cache --no-configuration-cache --continue
python3 .github/scripts/assert_kmp_ios_smoke.py

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

- **Unit tests run on every PR and on pushes to `master`** as part of `android_build_unified.yml`.
- **UI tests do not gate PRs.** The `ui_tests.yml` workflow runs weekly (Mondays
  05:00 UTC, against `dev`; the cron only evaluates from the default branch, so it
  activates once the workflow reaches `master` with a release) and on manual dispatch
  with a `smoke` / `regression` / `all` selector. They do not block PR merges. Run them
  locally before opening a PR that touches Compose code.

See [ci-cd.md](ci-cd.md) for the full pipeline.
