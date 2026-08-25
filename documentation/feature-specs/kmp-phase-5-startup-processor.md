# KMP Phase 5 — startup processor, runtime generations, restart-free reinitialization

| | |
|---|---|
| Status | SPEC v2 (R2) — maintainer decision **R2** recorded 2026-08-22; §7.1 gate evidence stands; implementation proceeds on this branch under §17's review |
| Baseline | `dev` @ `c935227df22020f394f1ea0748fdeba7b7a67fd5` (2026-08-22, "Fix ad-hoc exercise removal copy (#250)") |
| Branch | `feature/kmp-phase-5-startup-processor`, draft PR #252 |
| Upstream phases | 0–4 complete; Phase 6 data layer complete (#239/#240/#241 merged); Phase 7 (CMP UI, `iosApp`, iOS composition root) strictly downstream |
| Supersedes | the Phase-4 startup inventory (predates `warmQueryPlanner()`); `kmp-migration-assessment.md` §"restart-free actual" reinit order (Nav2-era, Room 2.8.4-era — §7.1 measured it false); **spec v1's same-database architecture and its §16 review (see §16)** |

Every code claim below was measured on the baseline SHA (file:line cites). Where a document
contradicts the code, the code is authoritative and the contradiction is listed in §15.

## 0. Decision record

Stable decision IDs (the PR history previously used "Option A/B" ambiguously — those labels are
retired):

- **R1 — descope file-swap reinitialization.** In-process reinit covers only the no-DB-swap class;
  restore/rollback/undo keep process restart everywhere; iOS restore deferred as an open Phase 7
  decision. *Not chosen.*
- **R2 — DB generation for file-swap operations.** The same-`AppDatabase` invariant is relaxed
  **only** for restore/rollback/undo operations that replace the live database file. Graph-only
  lifecycle work continues reusing the current database instance. Android production retains its
  existing process-restart behavior. Restart-free iOS restore is a **required migration
  capability**: Phase 5 implements and proves the lifecycle mechanism on Android; Phase 7 remains
  responsible for the actual iOS database factory, filesystem behavior, composition root, and
  runtime wiring. **Maintainer decision, 2026-08-22. This spec implements R2.**
- **H1 — application-owned runtime host outside the Metro graph** (spec v1 "Option A"). The host
  owns process roots and publishes generations; the swap authority sits outside the thing being
  swapped. *Chosen ownership model; carried into R2.*
- **H2 — graph-owned self-replacement** (spec v1 "Option B"): a `@SingleIn(AppScope)` manager
  inside the graph builds its successor. *Rejected* — self-referential lifetime, in-graph readers
  capture their own generation, test-swapping needs the graph told about itself.

**The replacement invariant (R2, verbatim):**

> No database-bound object may outlive its `RuntimeGeneration`, and the database, Metro graph,
> lifetime, ViewModel/navigation ownership, and generation ID must be handed over as one coherent
> unit.

## 1. Objective

Deliver an explicitly owned startup and runtime-generation lifecycle that:

- preserves current Android production restart behavior (process relaunch stays the shipping
  mechanism for every restore/rollback/undo path);
- makes safe restart-free reinitialization possible within one process, proven on Android
  instrumentation — **including the file-swap class under R2's DB-generation model**;
- proves graph replacement reusing the same `AppDatabase` for graph-only work, and coherent
  DB+graph+UI generation replacement for file-swap work;
- resets Nav3 and UI/store ownership on a generation boundary;
- removes the anonymous, unowned `CoroutineScope(SupervisorJob() + dispatcher)` instances and
  gives every app-scoped job/collector a deterministic owner;
- establishes an honest platform contract for the future iOS host **without** claiming a runnable
  iOS restore flow exists before Phase 7's iOS database/application composition root.

Non-goals: Phase 7 UI/CMP work, `iosApp`, KMP conversion of `app:common`, Room schema/dependency/
toolchain changes, `File`→okio, Metro/Nav3/Room replacement, DAO/repository proxies or graph-wide
swappable indirection, unrelated bug fixes, golden re-recording, new suppressions/baselines.
(The inert `SupervisorJob()` in `AppCoroutineScopeImpl` was scoped out here and then became
in-scope at R3: the generation join requires the supervisor on the winning side of the `plus`
— §8.4, §22.3.)

## 2. Measured startup-stage matrix

`BaseApplication` is abstract; manifest apps are `DevMobileApp`/`StoreMobileApp`. All stages run
synchronously on the main thread unless noted. Graph construction happens inside stage 5a — the
first `appGraph` read forces the `by lazy` build (`BaseApplication.kt:62-68`), which also forces
the lazy cold `buildAppDatabase` (`:66`, opens no SQLite file — `AppDatabaseFactory.kt:15`).

| # | Stage | Sync/async | Dispatcher | Graph | DB | Predecessor / deadline | Owner & cancellation | Failure policy | Test seam |
|---|-------|-----------|-----------|-------|----|------------------------|----------------------|----------------|-----------|
| 1 | `super.onCreate` + Crashlytics init + `Log.isLogging` + trace flag (`BaseApplication.kt:93`) | sync | main | no | no | first | n/a | crashlytics null-guarded | none needed |
| 2 | `handleRecoveryPreflightChain()` (`:158-178`) — Scenario 1 then Scenario 2 | **sync, `runBlocking` ×2** (`:159,:173`) | main blocks; inner hops to IO (`DatabaseSnapshotProviderImpl.kt:33,57,65`) | **first graph read** | S1: one DataStore read; SQLite open only if `restore_in_progress`. S2: Room-free framework-SQLite peek | must complete before any Activity (async would flash MainActivity — KDoc `:155-157`) | `runBlocking` — uncancellable by design | S1 peek `runCatching`→rollback→`restartApp()` (process exit, `:162-165`); S2 failure → `RouteToRecovery` decision, not a crash | `TestApplication.onCreateGraphBootstrap` no-op (`harness/TestApplication.kt:19-20`) |
| 3 | `cleanupOrphanedImageTempFiles()` (`:180-187`) | fire-and-forget launch | **anonymous** `CoroutineScope(SupervisorJob()+Dispatchers.IO)` (`:184`) | reads `appGraph.imageStorage` | no | after preflight; no deadline | **UNOWNED — never cancelled** | none at launch site; body deletes `files/images/temp/*` (`ImageStorageImpl.kt:93-97`) | same no-op seam |
| 4 | `warmQueryPlanner()` (`:222-229`) | 2 sync guards, then fire-and-forget | **anonymous** `CoroutineScope(SupervisorJob()+Dispatchers.IO)` (`:225`) | reads `lastDecision` | **yes — `ANALYZE` opens the DB** (`QueryPlannerStatistics.kt:15-19`) | strictly after preflight (decision cached); never on `RouteToRecovery` (`:223`); skipped on `isLowRamDevice` (`:224`); no reader waits on it | **UNOWNED — never cancelled** | `runCatching` + `Log.e` (`:226-227`) | same |
| 5 | `bootstrapAppDialogObserver()` (`:237-239`) — eager `appGraph.recoveryBootstrap` read | sync construction; collector async | observer's own `CoroutineScope(SupervisorJob()+@DefaultDispatcher)` (`RestoreDialogChoiceObserver.kt:51`) | yes | no | **must subscribe before first `MainActivity.onCreate`** — choice bus is `MutableSharedFlow(replay=0)`, pre-subscription emits lost (`AppDialogObserverImpl.kt:28-32`) | scope owned by the `@SingleIn(AppScope)` singleton — **never cancelled** | per-choice `runCatching` (`:100-103`) | same |
| 6 | `PerformanceMetricsRecorder.process(AppCreated)` (`:124`) | sync | main | no | no | last in `onCreate` | n/a | — | none |
| 7 | `MainActivity.onCreate`: S2 routing via cached `lastDecision` → direct `Intent(RecoveryActivity)`+`finish()` (`MainActivity.kt:34-38`); else metrics + `activityProducer.produce(this)` + `setContent { App() }` (`:59-64`) | sync | main | via `get()` accessors — **no capture** (`:23-25`) | no | after stage 2's cached decision | Activity | — | instrumented tests use the same activity |

Measured edge (recorded, behavior preserved): on the Scenario-1 `RestoreSucceeded` path Scenario 2
never runs, so `lastDecision == null` and stage 4's guard passes — the `ANALYZE` is then the first
open of the freshly-restored file, and any pending restore migration runs on that IO coroutine
inside `runCatching` (`BaseApplication.kt:124-128` + `:223`).

WorkManager: default initializer removed in manifest (`AndroidManifest.xml:72-81`); on-demand init
via `Configuration.Provider` building `MetroWorkerFactory(this)` per read (`BaseApplication.kt:90`);
the factory `by lazy`-captures `BackupWorkerDeps` (= the graph) for its own lifetime
(`MetroWorkerFactory.kt:10-12`) — a production graph **capture** (§4).

## 3. Lifetime matrix

| Object | Classification (R2) | Evidence / consequence |
|---|---|---|
| `AppDatabase` | **DB-generation** — same object across graph-only handovers; a NEW object after every file-swap replacement | the only `close()` sites are the restore/rollback swaps (`DatabaseSnapshotProviderImpl.kt:95,196`); §7.1 measured close() terminal. Under R2 the runtime owns close+swap and mints the next DB generation via the full production factory (`buildAppDatabase`). |
| `ImageStorage` | **process** — preserve | stateless dir wrapper, `create()` root (`BaseApplication.kt:66`); reused across generations. |
| DataStore instances (5 files: `common_prefs`, `backup_scheduling_prefs`, `restore_state_prefs`, `app_dialogs_prefs`, `backup_account_prefs`) | **process** — preserve | `DataStoreProvider`'s companion CAS memo is explicitly process-lifetime (`DataStoreProvider.kt:26-43`); pinned by `AppScopeDataStoreSingletonTest`. |
| Dialog state (`pending_*` flags) | **process (persisted)** — preserve | `AppDialogRepository` derives state from DataStore on every read (`AppDialogRepository.kt:25,61-63`). Survives replacement; exactly-once consumption via dismiss-after acknowledge. |
| `AppReinitializer`, Firebase holders, `PerformanceMetricsRecorder` | **process** | stateless / static singletons. |
| `AppGraph` + every `@SingleIn(AppScope)` binding (61 sites — scope-sweep) | **runtime-generation** — rebuild & dispose as one unit with the DB it binds | includes `NavigatorEventBus`, `AppDialogObserverImpl`, the 9 repositories, DAO providers (derive from the generation's DB root), gd auth chain, coordinators, `DatabaseSnapshotProviderImpl` (holds the generation's `AppDatabase`). |
| `RestoreDialogChoiceObserver` (+ collector), `DriveBackupAuth.authScope` (+ collector), `SnapshotExportRunnerImpl.scope`, both startup chores | **runtime-generation, lifetime-owned** | today process-lifetime, never cancelled (`RestoreDialogChoiceObserver.kt:51-53`, `DriveBackupAuth.kt:56-63`, `SnapshotExportRunnerImpl.kt:48`, `BaseApplication.kt:135,225`); §8.2 makes them children of the generation's `AppScopeLifetime`, cancelled-and-joined during Quiescing. |
| `MetroWorkerFactory` captured deps | **RESOLVED — the factory captures nothing** | §8.6: construction is dependency-free; a run binds deps+lease atomically at `doWork`'s first operation. |
| **In-flight `BackupWorker`** | **runtime-generation live capture** | a RUN (not a constructed worker) holds the six deps bound atomically into its admission lease at `doWork`'s first operation (§8.6); §8.4's Quiescing closes admission and awaits every outstanding lease before PONR. |
| Nav3 back stack, `NavigatorHolder`, per-entry Stores, `AppRootViewModel`, `AppDialogStoreImpl`, `NavigationEventBusSetup` collector | **UI/navigation-generation** — recreate/reset with the runtime generation | back stack `rememberNavBackStack(screenSavedStateConfiguration, Home)` (`App.kt:70`); `AppRootViewModel` ctor-captures `commonDataStore`+`navigatorEventBus` (`App.kt:64-70`); `AppDialogStoreImpl` Activity-store-scoped today (`AppDialogFeature.kt:23-34`); command collection is composition-side (`NavigatorExt.kt:32-41`). |
| `ActivityHolderImpl` content | **UI-generation adjacent** | re-registered only on Activity lifecycle events (`MainActivity.kt:41,69`); after a mid-Activity replacement the new generation's holder is empty until the next event — zero production readers today (measured); recorded property. |
| `iosApp` host, iOS DB factory, iOS composition root | **platform-host — Phase 7** | do not exist; nothing here may pretend they do. |

## 4. Graph-reader / capture inventory — including live captures

Five publication seams, all on `BaseApplication` (`:49-56`): `AppGraphOwner` (MainActivity `:23`,
non-capturing `get()`), `AppDepsHolder.appDeps()` (13 feature readers — acquire-and-drop inside
`rememberMetroStoreProcessor` factories), `RecoveryDepsHolder` (RecoveryActivity, activity-scoped),
`BackupWorkerDepsHolder` (**captures** via `by lazy`, `MetroWorkerFactory.kt:10-12`),
`AppRootDepsHolder` (**captures** into `AppRootViewModel`, `App.kt:64-70`).

`App()` is the ONLY caller of `AppRootDepsHolder.appRootDeps()` in the app (measured; the single
call site is `app/common/.../App.kt:143`), once per composed generation region. That is what makes
`MetroTestGraphHolder.appRootDepsResolutions == 0` a literal "this region reached the graph zero
times" in `UiAdmissionRaceTest`.

**Live-capture audit** (objects holding old-generation dependencies while work is in flight —
graph-access-site analysis alone is insufficient):

| Live capture | Held for | Quiesce treatment (§8.4) |
|---|---|---|
| `BackupWorker` in `doWork()` — six lease-bound deps incl. DB-bound provider | the admitted run (lease acquired at the first op, released in the finally) | close admission + await outstanding leases, bounded; timeout → abort pre-PONR |
| `SnapshotExportRunnerImpl` in-flight `runExport()` (DB JSON export, `SnapshotExportRunnerImpl.kt:52-54`) | export duration | lifetime child → `cancelAndJoin` |
| `RestoreDialogChoiceObserver` collector; `DriveBackupAuth` collector | infinite | lifetime children → `cancelAndJoin` (last quiesce step, §8.4) |
| Per-entry Stores / `AppRootViewModel` / `AppDialogStoreImpl` (repos, bus captured at ctor) | entry / generation-VM-store lifetime | UI region disposal + generation `ViewModelStore.clear()` |
| Startup chores (cleanup, `ANALYZE`) | one-shot | lifetime children → `cancelAndJoin` |
| `MetroWorkerFactory` lazy deps | ~~process (defect)~~ RESOLVED | §8.6: the factory captures NOTHING; admission moved to doWork's first operation |
| **Snackbar deferred-commit path** — `AppSnackbarModel`'s `onDismissed` runs under `withContext(NonCancellable)`; the ED11 deferred permanent-delete commits DB work there via a closure capturing gen-N repositories; `SnackbarManager` is a process-level object with an unlimited queue whose requeue path can carry gen-N closures into the gen-N+1 collector (review v2 finding 1) | until the resolve completes / queue drained | Quiescing sub-step: await the in-flight resolve before PONR; queued models are generation-tagged at enqueue — discarded at delivery after a COMMITTED handover (epoch advance), preserved on abort (§8.4 step 3) |
| JVM identity tests + androidTest singleton tests build graphs (`buildAppGraph` — 14 JVM files + `AccountDataStoreSingletonTest.kt:34,54`, `AppScopeDataStoreSingletonTest.kt:71,94`) | test | reach the 4th root via `buildAppGraph`'s defaulted parameter (the default = pre-Phase-5 anonymous-scope behavior) |

## 5. Restore / rollback / undo call paths (measured, pre-R2)

- **Restore** (Settings): `BackupInteractorImpl.restoreLatest` → version gates → `preserveCurrentDb()`
  → `markRestoreInProgress` → download → `restoreFromSnapshot(temp)` = magic check → source-version
  peek → **`appDatabase.close()`** → delete `-wal`/`-shm` → copy → `app.db.tmp` → `renameTo`
  (`DatabaseSnapshotProviderImpl.kt:151-185`) → `NavigatorEventBus.restartApp()` → process exit.
  Verification happens in the **new** process's Scenario 1 (`RestoreRecoveryCoordinator.kt:42-45`).
- **Rollback** (Scenario-1 failure and undo): `rollbackToPreRestoreBackup()` — same swap core from
  `cache/pre_restore_backup.db` + `source.delete()` (`:95-121`). Restore has its own structurally
  identical copy of the swap core.
- **Undo**: `performUndoRestore` three-way outcome, dismiss-after discipline, restart on `Succeeded`
  (`RestoreDialogChoiceObserver.kt:108-125`).
- Every replacement ends in process exit today; **no code path reuses a closed `AppDatabase`**.
- **R2 changes the mechanics ownership (§8.5): the provider stops closing/swapping independently;
  callers route through the runtime-owned replacement transaction.** Android production keeps the
  restart ending.

## 6. The Room 3 feasibility fact (source-measured; the gate measured it)

`kmp-migration-assessment.md:546` ("captured DAOs follow the reopen for free") and `:552-553` were
written against **Room 2.8.4**. Phase 6 (#240) moved production to **Room 3.0.0 driver mode**.
From `room3-runtime-android-3.0.0-sources.jar`: `RoomDatabase.close()` → `closeBarrier.close()` →
`onClosed()` (cancels the DB scope, stops the `InvalidationTracker`, closes the connection manager,
`RoomDatabase.android.kt:398-406`); `CloseBarrier.closeInitiated` is a one-way CAS
(`CloseBarrier.kt:82-88`); `connectionManager` is assigned once (`:96,202`);
`ConnectionPoolImpl.useConnection` on a closed pool throws
`SQLiteException(SQLITE_MISUSE, "Connection pool is closed")` (`ConnectionPoolImpl.kt:117-119`).
**Room 3 `close()` is terminal for the object.** §7.1 confirmed this on device. R2 is designed on
this fact: a closed generation is terminal (§8.5) and the next DB generation is a fresh object
from the full production factory.

## 7. Room entry gate (device, production driver) — protocol and result

Protocol (run 2026-08-22): production-shaped file-backed DB (`BundledSQLiteDriver`, live file at
`getDatabasePath(AppDatabase.NAME)`), retained `AppDatabase` + DAO, two-sentinel discipline
(snapshot sentinel vs live-only sentinel), BOTH production replacement paths, disk-truth via fresh
framework handle + inode capture, known-negative swap-bypass mutation.

### 7.1 GATE RESULT — RED for same-object reuse (measured 2026-08-22)

Device: `sdk_gphone64_arm64` emulator (Pixel 6 AVD), API 34, arm64-v8a; Room 3.0.0 +
`BundledSQLiteDriver`. Command:
`ANDROID_SERIAL=emulator-5554 ./gradlew :core:data:database:connectedDebugAndroidTest
-Pandroid.testInstrumentationRunnerArguments.class=…SameInstanceReopenAfterSwapDeviceTest`.

- **Both** production replacement paths succeed on disk: fresh-handle read shows
  snapshot-sentinel-present / live-sentinel-absent, and the live file's inode changes across the
  atomic rename (`Os.stat` evidence).
- The read through the **retained** DAO on the **retained** `AppDatabase` throws
  `android.database.SQLException: Error code: 21, message: Connection pool is closed` —
  deterministically, on both paths. It does **not** read OLD data: the stale-inode
  silent-corruption outcome does not occur; the failure is loud.
- Known-negative: bypassing the swap turns both tests red at the file-identity assertion (inode
  unchanged). Mutation reverted.
- The committed `SameInstanceReopenAfterSwapDeviceTest` is a **permanent characterization /
  negative-constraint test** (kept under R2): it pins that (a) the production swap is real,
  (b) Room 3's old DB and retained DAO fail **loudly** after replacement — never silently serving
  stale data — which is exactly the property R2's Quiescing and terminal-generation rules stand
  on, and (c) any future Room that changes this behavior surfaces as a loud test failure that
  re-opens the R2 design assumptions. Note: this test drives the production driver and the
  production swap paths but constructs Room directly (`Room.databaseBuilder` + driver, no
  migrations chain); the §11.2 GREEN gate uses the complete production factory.

### 7.2 Decision consequence

R2 (see §0): the same-object invariant is relaxed **for file-swap operations only**; the runtime
owns the replacement transaction and mints a fresh DB generation from the full production factory.
Graph-only reinitialization continues reusing the live `AppDatabase` object.

## 8. Architecture (R2, ownership model H1)

### 8.1 `AppRuntime` and `RuntimeGeneration` (new, `app/app` `runtime/`, internal)

```kotlin
internal class RuntimeGeneration(
    val id: Int,                       // runtime + UI/navigation generation id
    val dbGeneration: Int,             // increments ONLY on file-swap replacement
    val database: AppDatabase,
    val graph: AppGraph,
    val lifetime: AppScopeLifetime,
    val viewModelStore: ViewModelStore,
)
```

One **immutable** value; published atomically; readers observe generation N or N+1, never a
mixture. `AppRuntime` owns: `applicationContext`; the factories (`dbFactory` — production
`::buildAppDatabase`; `imageStorageFactory`; `graphFactory` — `buildAppGraph`); the process
`ImageStorage`; the generation counters; a `Mutex` single-flighting every lifecycle transition
(graph-only reinit AND replacement); and the published phase:

```kotlin
internal sealed interface RuntimePhase {
    data class Serving(val generation: RuntimeGeneration) : RuntimePhase
    data object Transitioning : RuntimePhase       // no generation is published to NEW UI work
}
```

Construction is factory-parameterized so the androidTest harness installs a runtime over test
roots; the §11.2 gate installs one over the **production** DB factory. `AppRuntime` implements
`AppReinitializationHost` (§8.8). The close is a constructor seam too —
`closeDatabase: (AppDatabase) -> Unit = ::closeAppDatabase` — for two reasons: `app:app` must stay
Room-free (its build script names no `androidx.room3` artifact, only `:core:data:database` and
`:core:data:database-test` for androidTest), and the JVM suites record close ordering.

### 8.2 `AppScopeLifetime` (new, `core:core` commonMain)

```kotlin
class AppScopeLifetime(parent: Job? = null) {
    val job: CompletableJob = SupervisorJob(parent)
    fun childScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob(job) + dispatcher)
    suspend fun cancelAndJoin()
}
```

4th `create()` bound-instance root (`AppGraph.Factory.create` gains `appScopeLifetime`; threaded
through `buildAppGraph`; `MetroTestRule` passes a per-test lifetime cancelled in `after()`; the 14
JVM identity tests + the two androidTest singleton tests pass one explicitly). Consumers replace
their self-created scopes: `RestoreDialogChoiceObserver.kt:51` → `lifetime.childScope(default)`;
`DriveBackupAuth.kt:56` and `SnapshotExportRunnerImpl.kt:48` → `lifetime.childScope(io)`; the two
`BaseApplication` chores run on the generation lifetime via the startup processor. Per-consumer
`SupervisorJob(parent)` children preserve today's isolation exactly while making
`cancelAndJoin()` deterministically END every generation-owned job and collector. No anonymous
scope remains in production (`git grep "CoroutineScope("` exit criterion).

### 8.3 `StartupProcessor` (new, `app/app` `runtime/`, internal)

The extracted, typed form of `onCreateGraphBootstrap`:

```kotlin
sealed interface StartupOutcome {
    data object Proceed : StartupOutcome
    data object RouteToRecovery : StartupOutcome     // decision cached on the coordinator, as today
    data object RestartRequired : StartupOutcome     // Scenario-1 RestoreRolledBack (cold start)
    data object FinalizationPending : StartupOutcome // mandatory state/UI handoff incomplete
}
```

Stages, order-preserving: Scenario 1 → (short-circuits) → Scenario 2 → chores (image cleanup;
planner warm-up guarded by `RouteToRecovery` + injected `isLowRamDevice`) → dialog-observer arming
(eager `recoveryBootstrap` read). Chores launch on the **generation lifetime**, fire-and-forget,
no reader waits. A newly finalized restore adds a policy-specific terminal barrier: cold start
publishes the outbox before chores, while an in-process candidate arms chores and the observer
before publication. App-dialog write failure routes cold start to sealed recovery or returns
candidate `FinalizationPending`; acknowledgement failure after that durable write is replay cleanup.

- **Cold start**: generation 1 published on build (the graph exists before preflight because
  preflight *uses* it; no UI can observe it before `onCreate` returns — manifest has no
  graph-reading providers), preflight under the two existing `runBlocking` boundaries,
  `RestartRequired` → `restartApp()` (process exit). Byte-equivalent behavior. Scenario-1's
  cold-start rollback now routes its swap through the runtime (§8.5, `RestartProcess` policy) —
  same file mechanics, same restart ending. **Mutex rule (review v2 condition 6): generation 1's
  build+publish completes and RELEASES the transition mutex before the preflight runs, and the
  cold-start rollback takes the plain `RestartProcess` path, never the full machine — the
  alternative deadlocks the main thread on the runtime's own mutex.**
- **Replacement preflight** (§8.5 `Preflight` state): the machine drives the *same* Scenario-1
  verification semantics against the candidate generation via its own coordinator instance.

### 8.4 The replacement state machine (runtime-owned; round-2 protocol)

```
Running → Quiescing(abortable) →‖PONR‖→ Teardown → Close → ReplacingFile
        → BuildingGeneration → Preflight → Publishing → Running
                                   ↘ ladder: RecoveredByRollback | Fatal
```

All transitions run under the runtime `Mutex`. Every database mutation has one validated,
runtime-owned `RestoreOwnerId`; callers never share a transaction or callbacks across owners.
The former same-operation coalescing is removed because its key did not include the second
caller's owner or effects. Concurrent calls serialize, but each keeps its own source ref, undo
ref, journal owner and terminal callback. Runtime liveness is rechecked inside the mutex: a
transaction queued behind one that reached Fatal performs no validation, close, swap or
publication and completes with Fatal.

**Submission, source ownership, effects.** Callers submit and await; the body runs on the
runtime's never-cancelled host scope. The downloaded caller temp may start in `cacheDir`, but the
runtime copies it to the dedicated `noBackupFilesDir/restore-recovery` root under its opaque
`RestoreSourceRef` before the first caller-cancellable suspension, journal claim or PONR. The
handoff and host-transaction submission run together under `NonCancellable`, so caller cleanup
cannot remove the source between ownership transfer and submission. Persisted-state garbage
collection, not a terminal-frame path guess, owns final deletion. All caller bookkeeping is one
typed `DatabaseReplacementEffects` object: `onBeforeMutation` runs inside the mutex after validation
and before anything irreversible; exactly one terminal method
(`onRejectedBeforeMutation` / `onCommitted` / `onRecoveredByRollback` / `onFailedAfterMutation`
/ `onFatal`) runs per transaction, on the transaction's coroutine, for every outcome including
internal escapes. Terminal selection is durable-phase-aware: `onCommitted` is legal only after
the `Committed` journal exists and the shared verified-attempt finalizer has made the pointer and
terminal outbox durable. A failure before that atomic transition preserves the `Committed`
attempt and its exact files and cannot report a clean success. Failure of the later app-dialog
write keeps the finalized pointer/outbox and also cannot report clean success. Publication is
idempotent; its owner-checked acknowledgement clears the outbox only after dialog publication
succeeds, but acknowledgement failure does not revoke that already-durable dialog.

**The point of no return is the START of the first irreversible action** — the outgoing
teardown, the `close()` INVOCATION, or the file mutation — never its completion. Before it,
every failure unwinds to `Serving(genN)` with the generation fully intact. After it, generation
N is never republished.

Two policies select the ending:

- **`RestartProcess` (Android production)** — uses the reversible Quiescing admission fence, then
  closes and replaces the live file without building a candidate generation. After every
  post-PONR outcome, the runtime invokes its host-owned `AppReinitializer` before completing the
  submitting deferred; restart no longer depends on a Settings/UI coroutine surviving the
  transition. The recovery ladder still applies to `RebuildInProcess` only. A restart-policy
  close or replacement failure is `FailedAfterMutation`, preserves the journal and exact recovery
  assets, keeps UI and DB-bound admission closed, and restarts into cold-start recovery. A restart
  hook that returns is a test seam; a hook failure is surfaced as `effectsError`, never clean
  success.
- **`RebuildInProcess` (Android instrumentation now; iOS Phase 7)** — the full machine:

**Quiescing** (ABORTABLE: every step is reversible; nothing of generation N is torn down):
1. Publish `Transitioning` — new UI work sees no generation; the old `key(id)` region leaves
   composition; per-entry Stores and root VMs run their `dispose()` paths. Await the region's
   departure through the **token** gate, and RETIRE the outgoing id **in the same atomic
   compare-and-set that observes zero outstanding tokens** — a late admission can never land
   after that observation: it is REFUSED, and a refused region composes nothing and resolves
   nothing (R3). Admission is taken during COMPOSITION, not from an effect, because effects run
   at apply time — after the region's children have already resolved their Stores and
   ViewModels. Grants are identified by token, not counted, so a release arriving after the id
   was retired and later reopened cancels out nothing (the ABA a counter could not see). Aborts
   un-retire the id; commits leave it retired forever. Bounded; timeout → abort.
2. Close worker admission and await the outstanding **leases**. DB-bound work binds through a
   lease acquired atomically with the generation deps at the work's FIRST operation inside
   `doWork` (§8.6) — a worker constructed but never started holds nothing. Blocked acquirers
   suspend through the bounded window and bind to whatever generation is published when
   admission reopens. Timeout → abort (pre-PONR); the periodic schedule is never cancelled.
3. FENCE the snackbar deferred-commit path: await any in-flight `resolveSnackbarOutcome` — its
   `NonCancellable` commit survives step 1's collector cancellation by design — AND close
   admission for new routings **in the same atomic step**, so the zero observation cannot be
   invalidated a moment later by a collector that was about to begin one (R3). A refused routing
   requeues its model rather than running it. Queued models are **generation-tagged at enqueue**
   and the tag TRAVELS with the model through delivery, cancellation and requeue: a requeue
   re-enqueues the ORIGINAL epoch, so a model the host died holding can never be re-stamped as
   N+1. After a COMMITTED handover the epoch advances — **before the successor is published**,
   which is what closes the window where N+1's collector is live while N's models still pass the
   delivery filter — and gen-N models are discarded at delivery (their closures never execute
   inside gen N+1: the ED11 / D-OPEN-10 interruption semantics, logged). After an ABORT the
   epoch does not advance, the fence lifts, and the models deliver normally when gen N resumes.

**‖ PONR — Teardown begins ‖**

4. **Teardown** (post-PONR; failures are Fatal, never an abort): clear the generation's
   runtime-owned `ViewModelStore` — `BaseStore.onCleared` now actually ENDS the Store's work
   (R3) — then `lifetime.cancelAndJoin()` (bounded). The clear must run on the MAIN thread
   (`GenerationQuiescer.clearStoreBounded` dispatches it on `policy.mainDispatcher`; the
   androidTest harness mirrors it with `runOnMainSync`): since R3 a cleared Store actually
   disposes, disposal detaches a `LifecycleRegistry` observer, and Lifecycle enforces observer
   removal on the main thread — an off-main clear throws. Store jobs are descendants of that
   lifetime: `AppCoroutineScopeImpl` takes the generation's job and puts its supervisor on the
   winning side of the context `plus`, so the join awaits every Store `finally` — including one
   that touches the database — BEFORE the close. Every graph-owned DB-bound job must be
   joinable (or covered by a lease); an unjoinable job after teardown began is a protocol
   violation → the machine goes Fatal WITHOUT closing — never a close under an unjoined job,
   never a republished half-torn generation.
5. **Close**: `generation.database.close()` — the invocation is post-PONR by definition. A
   throwing close is an unknown handle state → **Fatal, never `RejectedBeforeMutation`, never a
   republished outgoing generation, never a rename.**

New DB work during the window is excluded structurally: the UI admission gate is retired, worker
admission is closed (leases drained), snackbar resolves drained, lifetime joined; the transaction
itself is the only actor. No DAO/repository proxying, no graph-wide gate.

**ReplacingFile**: generation N is terminal — never republished, never a fallback. The provider's
pure file mechanics run (§8.5): delete sidecars → copy the exact restore or undo ref to
`${AppDatabase.NAME}.tmp` → atomic rename. Source deletion is deferred until durable
finalization and owner-aware sweeping. Validation ran in Running against the exact
runtime-owned file. The `.tmp` copy means the successful restore peak remains approximately five
DB-sized files; immutable undo removes positional rewrites and crash states, not that peak.

**BuildingGeneration**: `dbFactory()` — the **complete production factory** (`buildAppDatabase`:
driver + full `MIGRATIONS` chain, cold) — then a fresh `AppScopeLifetime`, fresh `ViewModelStore`,
new graph via `graphFactory(context, newDb, imageStorage, newLifetime, runtime)`. Allocation is
STAGED: a candidate orphaned by a later failure has its jobs cancelled-and-JOINED before its
database closes; a candidate/orphan close that itself throws STOPS the ladder (Fatal, no further
rename). Nothing published yet.

**Preflight**: the candidate's own coordinator verifies (Scenario-1 semantics:
`currentSchemaVersion()` through the candidate — the open is the verification and migrations run
here), then invokes the same atomic attempt finalizer used by cold-start preflight. The candidate
then completes chores and dialog-observer arming before mandatory outbox publication. A finalizer
write or app-dialog publication failure returns `FinalizationPending`; the candidate is torn down
and must not publish. Pointer activation is therefore not a policy callback and cannot be skipped
by `RebuildInProcess`.

**Publishing**: single atomic `Serving(genN+1)` write (one immutable value behind both phase
faces); the snackbar epoch advances (commit only); worker admission reopens; UI re-keys onto the
new generation; old saved-state slot dropped (`SaveableStateHolder.removeState(oldId)`). Candidate
chores and observer arming have already crossed the Preflight barrier before this state.

**Failure semantics + result truth** (locked):
- Pre-PONR: abort → generation N keeps serving, fully intact (ViewModelStore included);
  admission gates reopen; `onRejectedBeforeMutation` compensates.
- Post-PONR: the ladder must end in a published successor or the explicit Fatal. **`Completed`
  means the REQUESTED operation committed — nothing else.** A restore whose data ended up rolled
  back (by the ladder, or by the preflight's inline Scenario-1 rollback) reports
  **`RecoveredByRollback`** even when a successor generation published successfully: the serving
  data is the PRE-operation data, and callers must produce restore-FAILURE semantics — never a
  success dialog, never an undo offer. An explicitly REQUESTED rollback that commits reports
  `Committed`. Exactly one bounded rollback recovery per transaction; if construction and
  rollback recovery both fail — or any close throws — the machine emits the terminal
  `ReplacementOutcome.Fatal`: the runtime publishes no generation, `Transitioning` is never
  overwritten onto Fatal, holders and lease acquisition throw, and no path converts Fatal back
  to Serving. Recovery assets/markers are PRESERVED on `FailedAfterMutation` and Fatal — they
  belong to the runtime/journal protocol (§8.5a), and callers never delete them. (Routing a
  `RebuildInProcess` Fatal to a recovery surface is the calling HOST's wiring — the Phase 7 iOS
  root's, or an instrumented test's; the runtime's contract ends at the typed outcome.)

**Graph-only transitions** share the machine's shape with a stricter publication rule: quiesce
(abortable, N intact) → candidate build + preflight while N is FULLY intact (still abortable;
an abort re-enters N with its ViewModelStore untouched) → **the committed safe boundary (PONR):
N's teardown — VM store clear + lifetime cancel-and-join — COMPLETES before N+1 is exposed**.
Post-PONR failures never resurrect the partially disposed N: an escape after teardown began
goes Fatal. Publication order at the boundary is fixed (R3): advance the snackbar epoch →
publish the successor → reopen worker admission → lift the snackbar fence.

`FinalizationPending` is the one pre-PONR preflight verdict that cannot abort back to N: mandatory
restore terminal publication is still missing. The candidate is torn down without closing the
shared database, the runtime goes Fatal, and N is not republished or re-admitted.

An incomplete teardown is TERMINAL, never publish-anyway (R4): a post-PONR teardown failure
(VM-clear throw, or an unjoinable N job) best-efforts the candidate's own release, aggregates,
and goes Fatal — no epoch advance (N's producers may still be live), no publication, the UI gate
stays retired; `publishFatal`'s worker-gate reopen exists solely to wake parked acquirers into
the Fatal check, and no lease is ever granted. The same terminality holds pre-PONR wherever an
abort would leave candidate work live beside a republished N: a rejected preflight whose
candidate teardown FAILS (its unjoinable jobs share the LIVE database, and a later replacement's
teardown joins only the OUTGOING lifetime — the orphan would see the shared handle closed under
it) goes Fatal instead of aborting, and a partial construction whose unwind could not join its
child surfaces the distinct `PartialCandidateUnwindException`, which the graph-only caller maps
to Fatal rather than treating as an ordinary construction abort.

**Candidate teardown** is ONE path for every candidate that must not be published (preflight
failure, inline-rollback invalidation, partial construction): prevent publication → clear
ViewModel ownership → cancel AND bounded-JOIN the lifetime → close the database only after the
join → any failure stops the ladder (Fatal) with no later rename, because an unjoined job or an
unknown-state handle may still hold the file. The same rule covers a generation whose graph
construction threw: its lifetime is joined before the orphan database is closed.

**Re-entrancy** (review v2 condition 4): a rollback request arriving from inside the
transaction's own Preflight (the coordinator's failure path) is executed as the current
transaction's rollback branch via a **CoroutineContext transaction marker** — the machine installs
a per-transaction context element around the Preflight invocation, and the `DatabaseReplacement`
implementation inlines the file mechanics only when `coroutineContext[marker]` matches the current
transaction (kotlinx `Mutex` is non-reentrant and holder-anonymous, so mutex introspection cannot
implement this). It never starts a nested transaction; the inline branch closes the candidate
(invalidating it for publication), and a failed inline close stops the ladder Fatal. The inline
caller's own result is `Committed` (it requested the rollback); the outer restore's is
`RecoveredByRollback`.

### 8.5 Replacement-mechanics ownership — provider split and the caller seam

`DatabaseSnapshotProviderImpl` **stops closing or swapping the published database independently**:

- Restore mechanics accept only `RestoreSourceRef`; rollback mechanics accept only `UndoRef`.
  Paths are derived below `noBackupFilesDir/restore-recovery`, never persisted or accepted from a
  caller. `createUndo(ref)` copies into a unique same-directory
  `undo_<owner>.db.<nonce>.creating` file, syncs it, then atomically publishes the final name with
  a permanent cross-process file lock, no-follow absence check and same-directory atomic move. An
  existing immutable final is never overwritten.
- Validation, advisory capacity checks and immutable undo creation run while the existing
  generation is still serving. Only the runtime invokes the pure sidecar-delete + copy-to-live
  `.tmp` + rename mechanics after close.
- `preserveDbBeforeMigration`, staged restore sources and recovery exports are durable no-backup
  assets. Sharing creates a copy in one narrowly exposed cache share directory on demand; the
  no-backup root is not a `FileProvider` root.
- Callers reroute to a narrow seam, `DatabaseReplacement` (interface in `core:data:backup:api`
  **androidMain** — the module is KMP and the signature uses `java.io.File`; precedent:
  `BackupStorage` sits there for the same reason):
  `suspend fun restoreFromSnapshot(source: File, effects: DatabaseReplacementEffects)` and
  `suspend fun rollbackFromUndo(sourceRef: UndoRef, effects: DatabaseReplacementEffects)`.
  There is no production default effects owner. `DatabaseReplacementResult` is the phase-aware
  sealed type
  `Committed(effectsError?)` / `RejectedBeforeMutation(error)` / `RecoveredByRollback(error)` /
  `FailedAfterMutation(error)` / `FatalNoGeneration`, and `DatabaseReplacementEffects` is the
  typed per-phase contract, including exact-ref compensation ownership (§8.4). The runtime
  implements the seam and enters the graph as a `create()` bound instance (5th root). Rerouted callers:
  `RestoreLatestBackupUseCase` (feature/settings), `RestoreRecoveryCoordinator
  .handleRestoreFailure` and `performUndoRestore` (feature/recovery) — each supplies its
  effects object; caller code after the await only MAPS results, it never compensates.
  Recovery layering is preserved: coordinators keep owning *semantics* (outcome mapping and
  dialogs) while their flag/dialog WRITES ride the effects on the transaction's
  coroutine; the runtime owns *mechanics* (staging, quiesce, teardown, close, file swap,
  generation build, durable finalization, owner-aware cleanup and post-PONR restart). On Android
  production the `RestartProcess` policy invokes restart before the caller can observe a result.

### 8.5a Installation-scoped attempt journal and immutable assets

The current persisted model is one installation-scoped envelope:

```kotlin
RestoreProtocolState(
    installEpoch: InstallEpoch,
    attempt: RestoreAttempt?,
    activeUndo: ActiveUndo?,
    terminalOutbox: RestoreTerminal?,
)
```

`RestoreAttempt` is sealed: `Restore(id, phase, context, undoRef, sourceRef)` or
`Rollback(id, phase, sourceRef, origin)`. Refs are opaque owner identities, not paths. The
repository stores the epoch with the envelope and separately with every attempt, pointer and
outbox record, then reconciles ownership before decoding any one of them. At most one unresolved
attempt exists. `Prepared` means the live-file outcome is unknown; `Committed` proves only that
the exact mutation returned success and still requires verified finalization.

An epoch mismatch is foreign transferred state. One atomic edit clears the foreign attempt,
pointer, outbox and protocol keys without invoking owner callbacks, dereferencing a persisted
path, deleting a same-named local file or touching the live database. Startup then continues
through ordinary schema and migration preflight. Same epoch plus a missing referenced file is a
real local recovery failure and must never be downgraded to "ignore".

All authoritative files live under `noBackupFilesDir/restore-recovery`: the stable random install
epoch, immutable undo files, staged restore sources, durable raw recovery export and protocol
partials. Each immutable publication uses a unique `<final>.<nonce>.creating` file so concurrent
processes never write the same inode. The complete synced partial is exposed under the final name
by a same-directory atomic move while `.publication.lock` is held across a no-follow absence
check; an existing final name is never overwritten. The permanent lock inode is never swept, and
process death releases its kernel lock. Protocol state persists only owner and ref values. It
never persists an arbitrary absolute path.

**Exact ownership.** A restore owner N creates immutable `UndoRef(N)` while any previously active
pointer P remains untouched. The journal records both `UndoRef(N)` and `RestoreSourceRef(N)` in
its `Prepared` claim. A user undo applies `activeUndo.ref`; compensation applies the failed
restore's `UndoRef(N)`. Each rollback receives a fresh owner and persists the exact applied ref
plus `UserUndo` or `ScenarioOneRecovery`. Compensation N clears only N if N is active; it cannot
clear unrelated P. A source is deleted only after its rollback finalization is durable.

**Restore ordering.** While the old generation still serves:

```text
validate exact staged source
→ advisory capacity check
→ create immutable UndoRef(N)
→ persist Prepared(N), active=P
→ close and swap live DB
→ persist Committed(N), active=P
→ verify candidate/cold-start generation
→ atomically finalize active=N-or-null + terminal outbox + remove N
→ mandatory publish; owner-check acknowledgement as replay cleanup
→ sweep unreferenced P and protocol debris
```

`Committed(N), active=P` is valid only while UI and DB-bound admission remain closed. If the live
database verifies but N is missing, the restore is proven and its undo is unavailable: the
atomic finalizer writes `activeUndo=null` and `previousVersionAvailable=false`. It must never
advertise P as undo of N. If the finalizer's atomic edit fails, the `Committed` attempt, P and N
stay intact and no clean success or candidate publication is legal.

**Capacity admission.** After restore-source ownership transfer and validation, but before undo
creation, journal claim, generation teardown, close or swap, query injectable
`StorageManager.getAllocatableBytes()` without allocating. Restore requires the post-checkpoint
live size for N, staged-source size for the live `.tmp`, and an explicit margin; rollback requires
the exact source size for `.tmp` plus margin. Arithmetic is overflow-safe, equality passes, and
one byte short, overflow or query failure returns typed `RejectedBeforeMutation` with the serving
generation untouched. This is advisory: all writes must still map ENOSPC conservatively.

**Validation boundary.** `SqliteHeaderCheck` proves only header-format and file-length
consistency. Its SQLite change-counter/version-valid-for mismatch behavior follows file-format
semantics and must be characterized on matching, mismatched, zero-page-count and tail-truncated
real snapshots. Recovery Continue separately consumes every row of framework SQLite
`PRAGMA integrity_check` and accepts only `ok`.

**Owner-aware sweeping.** Cleanup is derived only from the epoch-reconciled persisted state under
startup/transition serialization. It preserves the install token, unresolved attempts, active
undo, staged restore refs and outbox/finalization assets. It deletes only strict protocol-owned
names beneath the dedicated recovery root, including orphan `.creating` files, staged sources
and obsolete undo files. It never follows a persisted path. Delete failure is retryable garbage,
not permission to resolve state or drop an owner. Cache deletion therefore cannot remove an
authoritative same-install recovery asset.

### 8.5b Terminal recovery classification and shared finalization

`PreflightOutcome` distinguishes what the launch may still do:

| Outcome | Condition | What the launch does |
|---|---|---|
| `RestoreSucceeded` | exact `Restore/Committed` + verified live generation + atomic pointer/outbox finalization | cold: publish before chores; candidate: arm then publish; continue only after the app-dialog write succeeds; acknowledgement retry is idempotent cleanup |
| `RestoreRolledBack` | exact compensation rollback committed and finalized | restart last; the in-process Room handle may be stale |
| `RecoveryCompleted` | an interrupted rollback is already `Committed`, finalizes by descriptor identity, and its origin-aware terminal is durably published | continue through ordinary schema preflight; retained outbox acknowledgement is replay cleanup |
| `FinalizationPending` | verification succeeded but the atomic state transition, exact post-arming proof, or app-dialog publication did not complete | keep UI/DB-bound admission closed and never publish the candidate or report clean success; pre-edit failure preserves the attempt/assets, post-edit failure preserves the finalized pointer/outbox |
| `InterruptedRestore` | same-install outcome-unknown restore with a healthy compatible live file, including the explicit legacy missing-C case | route to the DB-free recovery surface; automatic acceptance is forbidden, but user Continue is available (§27) |
| `RecoveryRequired` | same-install referenced asset missing/unusable, live DB unusable, or rollback cannot become durably provable | arm zero DB-bound work and route to the DB-free recovery surface with assets preserved |

Foreign-epoch state is reconciled before this classification and therefore never reaches
`RecoveryActivity`. A missing same-install ref does reach `RecoveryRequired`; file absence is not
evidence of transfer or foreign ownership.

**Replay-safe finalization.** Cold-start `RestartProcess` preflight and candidate
`RebuildInProcess` preflight call one finalizer after the same verification boundary. It performs
one owner-checked DataStore edit that changes or clears `activeUndo`, writes the terminal outbox
and removes the owned attempt. The pointer transition and terminal must agree with the attempt:

- verified restore N: `Replace(ActiveUndo(N, date))`, or `Replace(null)` when N is missing after
  the restore itself was proven; the outbox carries the same `previousVersionAvailable` verdict;
- user rollback of A: `ClearIf(A)` plus `UndoSucceeded`;
- compensation rollback of N: `ClearIf(N)` plus `RestoreFailed`; unrelated active P survives.

The outbox is published idempotently through `AppDialogPublisher` and acknowledged only after
publication returns successfully. App-dialog write failure remains `FinalizationPending`;
restore-state acknowledgement failure after that write is replay cleanup. Asset deletion follows
durable finalization, not UI acknowledgement. A committed rollback may finalize from descriptor
identity even if an earlier best-effort deletion already removed its source. Forbidden states
include a resolved attempt with an old pointer, a success dialog before compensation, and candidate
publication while mandatory restore state is pending.

**Origin-aware replay.** Both rollback descriptors carry their origin in the same atomic claim:
`UserUndo` owes `UndoRestoreSuccess`; `ScenarioOneRecovery` owes `RestoreFailure`. Unknown or
unparsable new-format owner/origin state is corrupt same-install state, not permission to invent
a success terminal. Legacy interpretation is handled only by the explicit rollout table in §27.

`InterruptedRestore` and `RecoveryRequired` are cached on the coordinator exactly as the
migration decision is, and `MainActivity` routes on either. There is no automatic restart loop.
Worker admission remains sealed until an explicit recovery action completes and restart occurs.

### 8.6 `MetroWorkerFactory` — no generation capture; first-operation admission

The factory holds NOTHING generation-scoped: `createWorker` constructs
`BackupWorker(appContext, params)` dependency-free (WorkManager caches the factory for the
process AND may construct workers it never starts — cancelled before dispatch, constraint
races). Admission is the FIRST operation inside `doWork`:
`(applicationContext as BackupWorkerDepsHolder).awaitBackupWorkLease()` suspends through a
bounded transition window and returns the CURRENT generation's deps bound atomically with the
lease the quiesce drain awaits; `release()` runs in the worker's `finally`. A
constructed-but-never-started worker therefore holds no lease (nothing leaks, nothing blocks a
transition), and a run can never tear across two generations. Throws loudly when the runtime is
Fatal.

A `createWorker` returning **null** is DELEGATION, not failure: WorkManager's inherited
`createWorkerWithDefaultFallback` then builds the unknown worker through the default reflection
factory. That path must never enter the admission gate — a foreign worker holding a
`BackupWorkLease` would hold up a replacement transition's lease drain.

### 8.7 UI generation boundary (`app:common`, narrowest seam)

```kotlin
sealed interface AppUiPhase {
    data class Generation(val id: Int, val viewModelStoreOwner: ViewModelStoreOwner) : AppUiPhase
    data object Transitioning : AppUiPhase
}
interface AppUiGenerationsHolder { val appUiPhases: StateFlow<AppUiPhase> }
```

Unlike `AppRootDeps`, `AppUiGenerationsHolder` may NOT be a member of the graph's contract: the
phase stream must OUTLIVE every graph, because it is what announces graph replacement. The process
`Application` satisfies it instead, from below both the graph and the runtime.

`BaseApplication` implements the holder from the runtime; `TestApplication` inherits a
harness-controlled source (`MetroTestRule` installs a static `Generation(1, testOwner)` for
existing tests; runtime-driven tests install a test runtime) — **no instrumented test composes
`App()` without a published phase**. `App()`:

```kotlin
val phase by holder.appUiPhases.collectAsState()
val saveableStateHolder = rememberSaveableStateHolder()
when (phase) {
    is Generation -> saveableStateHolder.SaveableStateProvider(phase.id) {
        key(phase.id) {
            CompositionLocalProvider(LocalViewModelStoreOwner provides phase.viewModelStoreOwner) {
                …the ENTIRE existing body, INCLUDING the AppRootViewModel resolution at its top —
                a wrapper enclosing only the visual tree would land the VM in the Activity store
                and let its ctor-captured gen-N navigatorEventBus cross generations…
                DisposableEffect(phase.id) { onDispose { runtime.onUiRegionDisposed(phase.id) } }
            }
        }
    }
    // Theme-independent background: the theme flows from the generation's AppRootViewModel,
    // which does not exist in this branch.
    Transitioning -> Box(Modifier.fillMaxSize().background(themeNeutralBackground))
}
```

Pinned consequences: fresh `rememberNavBackStack` per generation → new generation starts at Home;
Back cannot reach the old stack; gen-N saved entries live only under gen-N's
`SaveableStateProvider` slot (dropped via `removeState(oldId)` on publish — no resurrection);
an *aborted* replacement republishes `Serving(genN)` and the same slot restores the old back
stack; `AppRootViewModel`/`AppDialogStoreImpl` resolve from the generation's `ViewModelStore`
(new instances from the new graph's deps; old ones cleared deterministically); per-entry Stores
unaffected mechanically (NavDisplay's decorators re-provide the per-entry owner underneath);
ordinary Activity recreation mid-generation: runtime survives, same id, same VM store — retention
identical to today, because the host-teardown clear (below) reads `Activity.isChangingConfigurations`,
`ComponentActivity`'s own predicate; process death: counters restart at 1, `key(1)`+slot(1) matches what gen-1
saved → the existing restoration oracle (`BackStackStateRestorationTest`) is unchanged. Saved
state from a >1 generation is intentionally not restored after process death
(production-unreachable on Android; recorded for the Phase 7 host). No new CompositionLocal is
introduced — `LocalViewModelStoreOwner` is the standard androidx local (same pattern NavDisplay
uses per entry); `AppUiGenerationsHolder` carries no graph and no navigator.

**Host teardown (R5).** Re-parenting the owner removed the ONLY production clear of the Store
tree: `ComponentActivity` ran `viewModelStore.clear()` on its own store at `ON_DESTROY`, and the
generation's store is cleared only by `GenerationQuiescer.tearDown`, which no Android production
path reaches (`RestartProcess` is the policy; `AppReinitializer` exits the process). Worse than
retention: `rememberViewModelStoreNavEntryDecorator` deliberately SKIPS its own `clearAllKeys()`
when the lifecycle is already `DESTROYED`, on the documented assumption that the parent store is
about to be cleared — and its `parentKey` is a call-site composite hash, identical across
launches, so the next `MainActivity` in the same process resurrects the previous one's per-entry
stores with their stale state. `BaseApplication` therefore registers a
`UiHostLifecycleTracker`: on `onActivityDestroyed` it clears the serving generation's store when
`!isChangingConfigurations` AND no other host Activity is live. Attachments are tracked by
IDENTITY, not by a counter, so a missed attach degrades to a no-op remove rather than biasing the
baseline into clearing while a host is still composing. The two-live-hosts guard is also what
keeps the `MainActivity → RecoveryActivity` hand-off from clearing, since the successor is
created before the source is destroyed. The androidTest `TestApplication` overrides the hook and
routes it to `MetroTestGraphHolder`, which owns the harness's generation source.

### 8.8 `AppReinitializer` boundary — honest resolution (unchanged from v1)

commonMain gains `interface AppReinitializationHost { fun requestReinitialize() }`; androidMain
actual **unchanged process restart**; iosMain actual becomes
`actual class AppReinitializer(private val host: AppReinitializationHost)` delegating — the
unconditional `TODO()` is eliminated without a silent no-op (ctor-required host; nothing
constructs the iOS actual in Phase 5; the androidMain actual already carries an
expect-undeclared ctor, so this compiles). `AppRuntime : AppReinitializationHost` proves the
contract on Android. **Phase 7 handoff**: restart-free iOS restore is a required capability whose
lifecycle mechanism Phase 5 proves on Android; Phase 7 owns the iOS database factory, filesystem
behavior, composition root, and binding the iOS runtime host.

## 9. Concurrency and failure semantics

- Publication: one atomic write of an immutable `RuntimePhase` — no mixed-generation reads; one
  immutable value backs both the runtime and UI phase faces.
- Serialization: one `Mutex` over all transitions; each caller retains a distinct validated owner
  and transaction; re-entrant exact-ref rollback inlines into the running transaction (§8.4).
  Fatal is rechecked inside the mutex — queued transactions behind a Fatal one do nothing.
- Failure ordering: every fallible caller-visible step (UI retire, lease drain, resolve drain,
  validation, beforeMutation) precedes PONR; aborts republish `Serving(genN)` with saved state
  AND ViewModelStore intact — unless the last Android host was permanently destroyed during the
  transition, in which case §8.7's host-teardown clear has already emptied it and there is no UI
  left to observe the loss. PONR = the START of the first irreversible action (teardown / close
  invocation / rename); post-PONR failures end in the requested commit, `RecoveredByRollback`,
  `FailedAfterMutation` (assets preserved + journaled), or the explicit `Fatal`
  — a throwing close is Fatal, never a republished generation, never a rename.
- Terminal generations: closed ⇒ never republished, never a fallback.
- Worker admission (round-2 supersession of the round-1 factory-lease model): a run binds
  deps+lease atomically at its FIRST operation inside `doWork` (§8.6). A worker constructed
  during — or before — a transition holds nothing until that point; blocked acquirers SUSPEND
  (no thread parking) through the bounded window and bind to the published successor; an
  unreleased lease aborts the transition pre-PONR (loud, never corrupting). The §7.1
  pool-closed loud-failure pin remains as defence-in-depth, no longer as the design's race
  answer.
- Cold-start `runBlocking` boundaries untouched. Chore failure policy unchanged.

## 10. Locked invariants — preservation map (R2)

| Invariant | How preserved |
|---|---|
| Android prod `AppReinitializer` = process restart; no silent switch | androidMain actual untouched; production replacement policy is `RestartProcess`; `RebuildInProcess` has zero production callers (grep-pinned) |
| **R2 replacement invariant** (§0) | `RuntimeGeneration` is the single published unit; DB-bound objects are graph-owned and die with their generation (teardown) or are lease-admitted at their first operation and awaited (workers); DB generation increments only on file swaps |
| Graph-only work reuses the live `AppDatabase` | graph-only reinit hands the same `database` object into the next generation |
| No DAO/repository proxies, no graph-wide swappable indirection | replacement rebuilds the graph; nothing is proxied — the only new indirection is the narrow `DatabaseReplacement` caller seam |
| Restore state + dialog exactly-once | installation-scoped attempt/pointer/outbox live in one owner-checked edit domain; outbox publication is replayable and acknowledged only after publication; process-lifetime memoization for unrelated DataStores is unchanged |
| Recovery ordering (S1→S2, short-circuits, blocking boundaries, no Room on `RouteToRecovery`, observer-before-Activity, TestApplication bypass) | `StartupProcessor` is an order-preserving extraction; coordinators keep semantics; `TestApplication.onCreateGraphBootstrap` no-op seam retained; harness publishes phases explicitly |
| Startup jobs owned; planner semantics | §8.2/§8.3 |
| Nav3 canonical; root reset; no resurrection; restoration oracle unchanged; no graph/navigator CompositionLocals | §8.7 (measured: `NavCommand` has 5 variants, no `ResetToRoot`) |
| Metro-only DI; module boundaries; `core:core` ↛ app | §8.2 root + §8.5 seam placement + §8.8 direction |
| Schema, migrations, driver unchanged | new DB generations use `buildAppDatabase` verbatim; no builder changes |
| **The snackbar queue stays unbounded** — `SnackbarManager.queue` is `Channel<Queued>(capacity = Channel.UNLIMITED)`; never cap it, never give it an overflow policy | `AppSnackbarModel.onDismissed` carries a deferred delete's COMMIT (ED11), not just feedback, so an evicted entry is a confirmed delete that silently never runs after the screen that promised it popped. `SnackbarManagerTest`'s `a commit queued behind a burst is delivered, never evicted` reds on any bound. Entries are tiny, every producer is a user gesture, and the single collector (`App.kt`) drains one per toast lifetime; process death cancelling everything queued is D-OPEN-10's recorded shape, unchanged |

## 11. Gates

### 11.1 Characterization gate (committed, permanent)

`SameInstanceReopenAfterSwapDeviceTest` — §7.1. Pins swap-is-real + old-generation-fails-loud;
red if Room's close semantics ever change (re-opens R2's design assumptions).

### 11.2 Per-generation GREEN device gate (new)

`RuntimeGenerationSwapDeviceTest` (`:app:app` androidTest, `@Regression`, Metro harness with a
runtime over the **complete production factory** `buildAppDatabase` + production `buildAppGraph`
+ real files in the instrumented sandbox). For **both** restore and rollback it must prove:
1. a real inode-changing production swap;
2. the old DB/DAO are unusable after close (loud pool-closed);
3. a newly built `AppDatabase` + newly resolved DAO see NEW and not OLD;
4. graph dependencies resolve from the new DB generation (a Room-backed repository read through
   the new generation's graph serves the swapped data; the new graph's
   `databaseSnapshotProvider` operates on the new DB);
5. repeated replacement cycles work (≥2 consecutive swaps);
6. a known-negative mutation (swap bypass) makes it red (run + reverted, per protocol).

### 11.3 Failure-injection matrix (required coverage)

Injection seam: internal transaction hooks on `AppRuntime` (test-only interceptors per state —
not DAO proxies). Each point: inject → assert the locked outcome.

| Injection point | Locked outcome (round-2) |
|---|---|
| Quiesce (UI await / lease drain / resolve drain timeout) | abort pre-PONR; gen N serving; saved state AND ViewModelStore intact (§8.7 host-teardown caveat); admission gates reopen; `onRejectedBeforeMutation` compensates |
| Concurrent request for a DIFFERENT operation mid-transaction | its own serialized transaction — never the in-flight operation's result |
| Concurrent requests, including byte-identical sources | serialize as distinct owners; each has its own staged source, immutable undo, journal and callbacks; no transaction coalescing |
| Worker admission during the closed window | the run SUSPENDS at its first-op lease acquisition and binds to the published successor; a worker constructed but never started holds no lease |
| Late UI attach after the zero observation | refused by the atomic retire CAS — never passes, never blocks the machine; attach BEFORE zero blocks the machine until disposed |
| Snackbar deferred-commit in flight at quiesce | awaited before PONR; queued models generation-tagged — discarded at delivery on commit, preserved on abort; a gen-N callback never executes in gen N+1 |
| Restore source ownership-transfer failure / caller cancellation | no-backup staging plus host submission completes under the non-cancellable handoff; a cancelled caller's cache cleanup is a no-op; staging failure → `RejectedBeforeMutation` before validation; persisted-state sweep owns final deletion |
| Unjoinable outgoing job after teardown began | Fatal WITHOUT closing — never a close under an unjoined job, never a republish |
| Outgoing DB `close()` throw | **Fatal** (post-PONR unknown state); no rename; never `RejectedBeforeMutation` (RestartProcess: `FailedAfterMutation`, assets preserved + journaled) |
| Candidate/orphan `close()` throw (dispose, orphan, inline rollback) | ladder STOPS: Fatal, no further rename |
| File replacement (rename fails) | rollback mechanics + fresh generation → **`RecoveredByRollback`** (restore-failure semantics), else Fatal |
| New DB construction / graphFactory throw | staged unwind (jobs joined, orphan closed) then rollback recovery → `RecoveredByRollback`, else Fatal |
| Migration/preflight failure (incl. inline S1 rollback) | outer restore → `RecoveredByRollback` after the bounded retry over the rolled-back file; the inline caller's own result is `Committed`; exactly one bounded recovery |
| Atomic verified-finalizer write failure | `FinalizationPending`; keep `Committed` attempt and exact assets; no clean success, outbox or candidate publication |
| Mandatory app-dialog publication/read/proof failure after finalization | `FinalizationPending`; finalized pointer/outbox remain durable; no clean success or candidate publication; rollback source may be collected because rollback finalization is already durable |
| Restore-state acknowledgement failure after durable app-dialog publication | proceed; retained outbox is replay cleanup and deduplicated publication is safe |
| Capacity query insufficient/throws/overflows | typed pre-PONR rejection; zero undo creation, journal, close or swap; equality passes |
| Rollback mechanics failure | `ReplacementOutcome.Fatal`; no closed generation serving; `onFatal` effects journal durably |
| Post-rollback generation construction failure | `Fatal`, same |
| Transaction escape / internal `CancellationException` | the deferred completes exactly once: pre-PONR → `Serving(genN)` + rejection; post-PONR → Fatal; on a Fatal runtime → Fatal with no published-state touch |
| Operation submitted after (or queued behind) a Fatal transaction | Fatal — no validation, no close, no swap, no publication; `publishTransitioning` never overwrites Fatal |

### 11.4 Exit gates

`assembleDebug`, `testDebugUnitTest`, `verifyPaparazziDebug` (zero movers), `lintDebug`,
`assembleDebugAndroidTest` under `--rerun-tasks --no-build-cache --no-configuration-cache`; detekt
separate forced run; `:lint-rules:test`; connected **Regression** + **Smoke** via the
`ui_tests.yml` invocation; affected `iosSimulatorArm64` compile+KSP (`core:core` at minimum); iOS
link/runtime **UNVERIFIED** (no host); CI green on PR #252; raw instrumentation XML attached to
the PR evidence (committed under `documentation/feature-specs/kmp-phase-5-evidence/`).
Task-execution counts reported; UP-TO-DATE/FROM-CACHE runs are not evidence.

## 12. Test plan (delta over §11)

JVM (`app/app` test): runtime generation identity (two generations, same-DB handover for
graph-only; distinct DB for replacement via fake factories), distinct graph/navigator identities,
teardown-before-close and teardown-before-publish ordering, concurrent transitions serialized
with distinct owners, candidate-not-published-before-preflight/finalization, state-machine ordering + the
FULL §11.3 matrix, PLUS the composed seam gate (`RestoreTransactionIntegrationTest`): the REAL
`RestoreLatestBackupUseCase` driving the REAL `AppRuntime` over actual temp files (staged
ownership vs the caller's genuine finally-delete, marker-inside-txn, dead-initiator
compensation, RecoveredByRollback mapping, journal-on-Fatal). JVM (`core:data:backup:worker`):
first-op lease acquisition/release, constructed-never-started holds no lease, doWork-time
binding. JVM (`feature/recovery`): observer on lifetime scope; the process-restart gate
(FailedAfterMutation → assets survive → a second coordinator instance completes the rollback);
the journal route (no schema peek, never RestoreSuccess). Instrumented (`app/app`): §11.2 gate;
dialog `pending_*` flags
set → replacement → dialog shown and consumed exactly once; DataStore memoization across
generations (existing singleton tests + runtime variant); Nav3 root reset + old-stack removal +
Store disposal + no resurrection after Activity recreation; abort-path saved-state restoration;
existing `BackStackStateRestorationTest` untouched as the restoration oracle. Instrumented
(`core:data:database`): §11.1 characterization (already committed).

## 13. Commit decomposition (single draft PR #252, continuing)

Landed: 1. spec v1 · 2. characterization gate (RED evidence) · 3. review v1 record.
Next: 4. `docs(kmp): revise phase 5 spec for the R2 db-generation decision` (this file) ·
5. `refactor(runtime): introduce app-scope lifetimes as a graph root` ·
6. `refactor(startup): extract the startup processor` ·
7. `refactor(runtime): introduce the runtime host and generation-aware ui` ·
8. `feat(runtime): runtime-owned database replacement transaction` (seam, provider split, state
machine, policies, caller rerouting, failure injection + JVM coverage) ·
9. `test(app): per-generation green device gate and replacement instrumented suite` ·
10. `docs(kmp): record phase 5 evidence and phase 7 handoff` (stale-claim register §15 + raw XML
evidence + assessment supersession).
Round-1 rework (§20): 11. `95458856` fix(runtime) · 12. `366997da` test(app) composed
handshake · 13. `52429c8c` docs.
Round-3 rework (§22): 19. `db1ae8a4` `feat(backup): replace the restore booleans with a
crash-durable attempt journal` (blockers 1+2+3+6 — the journal, the in-transaction rollback
reservation, phase-aware recovery outcomes and the unified candidate teardown interlock through
one transaction) · 20. `66cc5cc7` `fix(runtime): tokenized UI admission and a linearized
snackbar handover` (blockers 4+5) · 21. docs closeout (§8.4/§8.5a/§8.5b rewritten in place +
§22 + evidence).
Round-2 rework (§21): 14. `6d26fbdd` `feat(ui-kit): generation-tag the snackbar queue` ·
15. `822c8d0d` `feat(backup): add the durable restore-mutation journal` ·
16. `d02123cd` `feat(runtime): unify the replacement transaction protocol` (the interlocked
core: staging, typed effects, result truth, PONR-at-start, teardown boundary, first-op leases,
atomic UI retire, Fatal-under-mutex — one coherent cut) · 17. `e9886497` `fix(runtime): harden
the protocol from the adversarial verification round` · 18. docs closeout (§8 rewrite in place
+ §21 + evidence).
Each commit bisect-green; behavior-specific tests ride with their behavior commit.

## 14. Rollback / reversibility

Commits 5–9 layer on existing seams (five holders, `create()` factory, `protected open` bootstrap
seam) without deleting any; reverting 9..5 in order restores today's behavior verbatim (the
extraction is order-preserving; the provider split is re-inlinable). No migration, no
persisted-format change, no golden change anywhere in the plan.

## 15. Stale-claim register (docs closeout, commit 10)

1. `kmp-migration-assessment.md:546-553` — Room 2.8.4 reopen claims + Nav2 `ResetToRoot`/
   `NavController` reinit order → dated supersession note pointing at §7.1 + this spec.
2. `core/core` iosMain `AppReinitializer` KDoc "three DataStore-memoization bypasses" (fixed in
   Phase 6) → reword with the §8.8 contract; commonMain KDoc's "reopen Room" wording → R2 model.
3. Phase 4 spec startup inventory — dated supersession note (predates `warmQueryPlanner`).
4. `app/app/src/main/AndroidManifest.xml:54-62` — Hilt/`HiltWorkerFactory`/`@AssistedInject`
   comment vs Metro reality.
5. `DatabaseSnapshotProvider.kt:20` ("caller MUST tear down every consumer"), `:52-54` ("via
   Room's open helper"), `:106-109` + `UndoRestoreOutcome.kt:11` ("atomic rename" — actually
   copy+rename+delete), `:150-151` (pre-migration WAL-checkpoint claim) — all superseded by the
   §8.5 split anyway.
6. `documentation/ci-cd.md:342-345` + `lint-rules.md:724-731` — pre-commit hook described as
   disabled/copied; measured: `core.hooksPath`, detekt runs on staged `.kt`.
7. Nav2-era wording: `AppFeature.kt:12,18-23`, `Feature.kt:13`, `FeatureAssisted.kt:13`,
   `MetroStoreProcessor.kt:14`; `NavigatorEventBus.kt:30` ("injects"); `AppDialogHost.kt:14`
   ("@Singleton"); coordinator/repository KDoc caller claims (`RestoreRecoveryCoordinator.kt:31`,
   `AppDialogRepository.kt:48`); post-§8.7: `AppFeature.kt:12` + `AppDialogFeature.kt:23`
   ("LocalViewModelStoreOwner is the host ComponentActivity").
8. `documentation/performance.md` `AppCreated` boundary — verify against §2 stage 6.
9. `android_build_unified.yml:146` "13 golden-holding modules" — 14 apply Paparazzi (recorded, not
   edited — CI text change out of scope).
9b. `MetroWorkerFactory.kt:10` KDoc cites work-runtime 2.10.0; the catalog ships 2.11.2.
10. Unverified-claims register at implementation end: iOS link/runtime behavior of the reworked
    iosMain actual (compile-verified only); `key()`/SaveableStateHolder interaction with
    saved-state beyond the tested API-34 emulator.

## 16. Independent architecture review v1 — SUPERSEDED

The 2026-08-22 review (CONFIRM + three conditions) evaluated **spec v1's same-database design**
(graph generations that never close the DB, restore descoped). The R2 decision replaces that
architecture's replacement story, so review v1 is **no longer authoritative** for this spec.
What carries forward from it as still-applicable findings: the disposal-ordering condition (now
§8.4 Quiescing step 1 + §8.7's `DisposableEffect` signal), the harness seam condition (now §8.7's
`AppUiGenerationsHolder` harness contract), the `create()`-root fan-out correction (now §4's test
inventory), the worker single-read-per-invocation pin (§8.6), the `ActivityHolder` empty-after-
replacement property (§3), and the stale-doc additions (§15.7).

## 17. Independent architecture review v2 (R2) — CONFIRM, with binding conditions

> **Round-2 supersession notes (2026-08-23, §21):** condition 1's "drain-or-record the queue"
> resolution is superseded by generation-tagged models (drop-on-commit / preserve-on-abort,
> §8.4 step 3); condition 2's drain-to-close construction race is DISSOLVED by first-operation
> lease admission (§8.6) — no worker binds deps before `doWork`, so the bounded-loud-failure
> classification no longer describes the design (it survives only as the §7.1 defence-in-depth
> pin); condition 5 gains the round-2 scoping that a `RestartProcess` close-throw is
> `FailedAfterMutation` (post-PONR), never a cleanup-safe rejection, while the
> already-terminal-generation restartability stays. Where this section conflicts with §8/§21,
> those win.

Fresh-context adversarial review, 2026-08-22, of this spec version: **CONFIRM — no binding
condition violated, no STOP condition fires.** STOP-sweep results: quiescence needs no DAO
proxying; the swap moves under runtime ownership without breaking recovery layering (module
directions verified; the swap callers are exactly the three named sites); no path re-serves a
closed generation (the seam-read race is a bounded loud failure — `RuntimePhase` stays
`Transitioning`, nothing republishes); no schema/migration/driver/dependency/Phase-7 work is
forced (WorkManager 2.11.2 already ships the awaitable unique-work API); dialog exactly-once
holds end-to-end (dedup at publish, transition window structurally choice-free, `BaseStore`
re-arms `initialActions` on re-init); `BackStackStateRestorationTest` remains a valid oracle
under the §8.7 wrapper. Binding conditions, all folded into §4/§8/§9/§11 above:

1. **Snackbar deferred-commit live capture (biggest find).** `AppSnackbarModel`'s `onDismissed`
   runs under `withContext(NonCancellable)` and the ED11 deferred permanent-delete hands a
   DB commit there through a closure capturing gen-N repositories; `SnackbarManager` is a
   process-level object with an unlimited queue whose requeue path can carry gen-N closures into
   the gen-N+1 collector. Quiescing gains a sub-step (await the in-flight resolve; drain-or-record
   the queue before close), the §4 audit gains the row, and the queued-model cross-generation
   property is recorded.
2. **Worker failure wording corrected in §9**: a pool-closed `SQLiteException` propagates uncaught
   out of `BackupWorker.doWork` → FAILED, not retried (`Result.retry()` exists only on caught
   `BackupResult` branches). Periodic work survives via its chain; one-time requires a re-trigger.
   The drain-to-close construction race (a worker built after the drain, before close) is in the
   §11.3 matrix with the same bounded-loud-failure classification.
3. **Coalescing is operation-identity-scoped** (§8.4): a concurrent request for a DIFFERENT
   operation never receives the in-flight transaction's result — it queues behind it or returns an
   explicit busy outcome. (An undo coalescing onto a restore's Success would clear the undo slot
   and publish `UndoRestoreSuccess` for a rollback that never ran.)
4. **Re-entrancy mechanism is a CoroutineContext transaction marker** (§8.4): kotlinx `Mutex` is
   non-reentrant and holder-anonymous; the machine installs a per-transaction context element
   around Preflight, and the `DatabaseReplacement` impl inlines the rollback branch only when the
   marker matches the current transaction.
5. **`RestartProcess` scoping** (§8.4): the post-close recovery ladder applies to
   `RebuildInProcess` only. Production uses the reversible admission fence, performs close and
   replace once, preserves any post-PONR failure journal/assets, and invokes the runtime-owned
   restart for every post-PONR outcome so cold-start recovery decides the next step.
6. **Cold-start mutex rule** (§8.3): generation 1's build+publish completes and releases the
   transition mutex BEFORE the startup preflight runs; a cold-start Scenario-1 rollback takes the
   plain `RestartProcess` path, never the full machine — otherwise the first cold-start rollback
   deadlocks the main thread on the runtime's own mutex.

Notes folded: the §8.7 wrapper must enclose App()'s `AppRootViewModel` resolution (top of the
body), and the Transitioning interstitial uses a theme-independent background (the theme flows
from that VM); §10's DataStore count corrected to 5 files; the identity tests reach the 4th root
through `buildAppGraph`'s defaulted parameter (reconciled — the default IS the pre-Phase-5
anonymous-scope behavior); `DatabaseReplacement` lives in `core:data:backup:api` **androidMain**
(the module is KMP; `java.io.File` precedent: `BackupStorage`); `MetroWorkerFactory.kt:10`'s
work-runtime 2.10.0 KDoc cite is stale (catalog: 2.11.2) → §15; `LargeClass` (600) is the one
detekt ceiling to watch for the state machine.

## 18. Final verification record (2026-08-22)

Raw artifacts + exact commands: `kmp-phase-5-evidence/README.md` (+ the final Regression run's
instrumentation XMLs beside it). Summary — every gate GREEN:

- **Forced host battery** (`assembleDebug testDebugUnitTest verifyPaparazziDebug lintDebug
  assembleDebugAndroidTest --rerun-tasks --no-build-cache --no-configuration-cache`): exit 0 in
  7m48s, 4030 executed task lines, **2461 unit/host tests, 0 failures**; `verifyPaparazziDebug`
  ran in all **13** golden-holding modules with **zero movers** (correction to §15.9's "14":
  `core:ui:golden-harness` references golden-gate in a comment only and hosts no goldens — the
  workflow comment's 13 is right).
- **detekt** and **`:lint-rules:test`**: separate forced runs, exit 0.
- **iOS**: `compileKotlinIosSimulatorArm64` for all five KMP modules + `:core:data:database:
  kspKotlinIosSimulatorArm64`, forced, exit 0. Link/runtime: **UNVERIFIED** (no host before
  Phase 7 — reported as such, never as passed).
- **Connected suites** (the `ui_tests.yml` invocations, emulator API 34 arm64): Smoke **43/43**,
  Regression **77/77** (45 `app:app` + 30 `core:data:database` + 2 others), both exit 0.
- **Device gates**: §7.1 characterization pin 2/2 (RED gate-form measured first, known-negative
  red then reverted); §11.2 per-generation GREEN gate 1/1 over the complete production factory +
  real graph + real preflight, known-negative (swap bypass) red at the inode assertion then
  reverted.
- **Commit map (landed)**: spec v1 `51e9b2af` · characterization gate `dcb8dbd9` · review v1
  record `787a49b9` · spec v2 (R2) `1845d7c9` · review v2 record `8157005e` · lifetimes +
  startup processor `441f56f8` · runtime host + generation-aware UI `68f3ac18` · replacement
  transaction `fdbf010e` · device green gate + instrumented suite `e10280b1` · worker de-capture
  `fa8bc358` · docs closeout + this record (final commit).
- **Implementation-note delta vs §8**: ~~graph-only transitions use the RELAXED quiesce order
  (publish before the outgoing lifetime ends)~~ **SUPERSEDED (round-2, §21)**: graph-only
  publication now happens only AFTER the outgoing teardown reaches its committed boundary
  (§8.4); the abort window ends at PONR (teardown start), where it used to extend to close.
  The §11.3 injections are driven through the runtime's factory/policy/provider seams — no hook
  mechanism was needed (nothing weaker: every matrix row has a test in
  `AppRuntimeTest`/`AppRuntimeReplacementTest`/`RestoreTransactionIntegrationTest`).

- **Recorded residual properties** (final-review findings, classified; (b) and (c) since FIXED —
  see §20/§21): (a) a process death in the ONE frame between a `RebuildInProcess` publish and
  the old saveable slot's removal can leave the old slot in saved state; ids restart at 1 next
  process, so a Phase 7 host that persists generation counters must drop stale slots at cold
  start (the recreation half of the window is closed — `previousGenerationId` is saveable).
  This remains the one deliberately deferred window (deferred, not closed — closing it needs a
  removal protocol that survives process death, owned by the Phase 7 host design).
  (b) ~~coalescing check outside the mutex~~ FIXED: same-operation registration is atomic under
  the submission lock. (c) ~~a throw inside `RebuildInProcess` can strand `Transitioning`~~
  FIXED: every transaction escape resolves deterministically — pre-PONR to `Serving(outgoing)`
  + `RejectedBeforeMutation`, post-PONR to the explicit `Fatal` (PONR = the START of the first
  irreversible action, §8.4). (d) On the cold-start S1 rollback path the terminal `close()` now
  runs on the (already-blocked) main thread instead of the provider's IO hop — functionally
  identical under `runBlocking`, recorded as the one byte-equivalence residue.
  (e) ACCEPTED (recorded 2026-08-25, from the PR #252 bot review): a graph-holder read that passes
  through NEITHER admission gate — `BaseApplication.appGraph` → `AppRuntime.currentGeneration` —
  still answers with the OUTGOING generation between that generation's database close and the
  transition's terminal publication, so such a reader could resolve repositories over a closed
  Room handle. The accessor's own KDoc states the behaviour ("Reads during a transition answer
  with the outgoing generation"). The window is bounded by construction on BOTH policies: it lies
  inside one `transitionMutex` hold, the phase is already `Transitioning`
  (`GenerationQuiescer.quiesce` has retired UI admission and closed and drained worker admission
  first, and the UI region renders an empty box), and `currentGeneration` THROWS the moment the
  same hold sets a terminal flag — `restartTerminal` for `RestartProcess`, `isFatal` for a `Fatal`
  — while on an ordinary `RebuildInProcess` success the window simply ends at the successor's
  publication, with no flag and no throw. The composition-side readers and the worker lease are
  gated, and `servingViewModelStore` reads `currentOrNull` so a terminal runtime's store stays
  releasable. What is NOT gated, and is therefore the honest boundary of this residual: the
  Activity-lifecycle reads — `MainActivity` dereferences `appGraph` in `onCreate` before
  `setContent`, and `RecoveryActivity`'s `recoveryDeps()` resolves the same graph — plus the
  runtime's own pre-mutex reads in `replace`. Those resolve graph objects during creation rather
  than issuing queries there, and §4's reader inventory already lists them as publication seams,
  but nothing stops them landing in the window; past the terminal flag they throw rather than hand
  back a stale handle, which on the Activity path is a fail-fast, not a handled outcome. Recorded,
  not closed — closing it means making the accessor phase-aware, which is the compile-time
  typestate item in §27.10, and today's protection is the gates plus that audit, not the accessor.
  Round-4's wider claim about this window (a ~2 s live-UI exposure under `RestartProcess`, from a
  `RESTART_DELAY_MS` restart hop and a never-quiescing restart path) does NOT describe the code
  from `d869e113` onward: it now publishes `Transitioning` and quiesces before its swap, and the
  delay and its scheduler were deleted.

## 19. Independent review artifact (2026-08-22): CONFIRM — superseded in part by §20

Status honesty: this and the §16/§17 records are **in-repo review artifacts** (fresh-context
adversarial passes run as part of this work), NOT GitHub PR approvals. The maintainer's actual
GitHub review of PR #252 (2026-08-22) returned **REQUEST_CHANGES** with six findings this
artifact's pass did not catch; §20 records them and their fixes. Where this section conflicts
with §20, §20 wins.

Fresh-context adversarial review of the implementation as of `58bde10f` (working tree = final
state at that time):
**CONFIRM — no shipped-behavior break, no replacement-invariant violation, no vacuous
load-bearing test.** Attack summary: cold-start mutex deadlock disproven by trace (gen-1 build
holds only its monitor; nothing holds the transition mutex at cold start); no mixed-pair reader
(production acts on single atomic reads); scope sweep clean (the host scope is the one
documented process scope); restore layering byte-equivalent (use-case body diffed against the
pre-split interactor); DB-identity discipline verified at every `publishPhase(Serving(...))`
argument; zero production `reinitialize`/`replace` callers; layering directions verified; the
Fatal JVM test genuinely post-close; the device gate cannot pass on a bypassed swap; every
docs-closeout edit verified line-by-line against the code, and no doc implies a runnable iOS
restore. Both MUST-FIX findings are applied on the branch: `AppScopeLifetime.childScope` puts
the lifetime-parented supervisor on the winning side of the context `plus` (a caller-supplied
Job can no longer detach a scope from its generation), and `previousGenerationId` is
`rememberSaveable` (slot removal survives Activity recreation). Note-level findings are folded
into §18's residual register, the softened §8.4 Fatal wording, the suspend-path
`StartupProcessorTest` pins (9 tests), and the hardened `UiGenerationSwapTest` absence
assertions (re-run green on device after hardening).

## 20. Round-1 REQUEST_CHANGES rework record (2026-08-23) — SUPERSEDED IN PART by §21

> The round-1 rework itself received a second REQUEST_CHANGES; §21 records the round-2
> protocol that replaced several of this section's mechanisms. Now-stale claims here:
> finding 2's factory-time lease acquisition (→ first-operation admission inside `doWork`,
> §8.6); finding 5's "failed close → `RejectedBeforeMutation`, no rename" (→ close-throw is
> **Fatal** under `RebuildInProcess`, `FailedAfterMutation` under `RestartProcess` — §8.4);
> finding 6's "ViewModelStore intact until AFTER publish" for graph-only (→ teardown completes
> BEFORE publish, §8.4); §20.2's per-suite claim lists (→ §21.2); §20.4(iii) (→ the lease is
> acquired at the work's first operation, not at construction). Where this section conflicts
> with §8/§21, those win.

The maintainer's GitHub review of PR #252 (2026-08-22) returned REQUEST_CHANGES with six
findings against the transaction/quiescence guarantees. All are fixed on the branch; this
section is the findings→fixes→proofs map and supersedes anything above that conflicts with it.

### 20.1 Findings → fixes (commits `95458856` + `366997da`)

1. **Self-cancelling transaction** → submission ownership. Every transition (replacement AND
   graph-only) runs on `AppRuntime.hostScope` (never-cancelled; injectable `hostDispatcher`);
   callers submit and await a `CompletableDeferred`. Caller death abandons only the await.
   Post-commit state/dialog effects moved into caller-supplied hooks
   (`ReplacementHooks.beforeMutation` / `onCommitted`) executed ON the transaction's coroutine:
   the restore marker write now happens inside the mutex (spurious-Scenario-1 interleave closed
   by ordering), and the undo/S1 flag-clears + dialog publishes survive the initiator's death.
   Every transaction escape resolves deterministically via a per-transaction `PonrTracker`:
   pre-PONR → republish `Serving(outgoing)` + `RejectedBeforeMutation`; post-PONR → the explicit
   `Fatal`. A stranded `Transitioning` is no longer reachable (fixes §18 residual (c)).
2. **Quiesce was not a closed barrier** → leased admission. `BackupWorkLease` +
   `BackupWorkerDepsHolder.acquireBackupWorkLease()`: deps and lease acquired atomically under
   `admissionLock`; quiesce closes admission then awaits `activeWorkerLeases == 0` (real join —
   constructed-but-not-RUNNING workers included); timeout aborts BEFORE close; abort/publish/
   Fatal reopen admission so parked acquirers resume against the published state (Fatal makes
   them throw loudly). The snapshot-style `BackupWorkDrain` helper is deleted. §9's racing-worker
   bullet is superseded accordingly.
3. **UI gate accepted foreign disposes** → generation-id-bound counted gate. Per-id attachment
   counts; a transition awaits ITS outgoing id reaching zero; a wrong/stale id only ever moves
   its own key; overlapping attachments (recreation) must all detach. The
   `AppUiGenerationsHolder` callbacks are now abstract — no silent no-op defaults.
4. **Failure seam conflated pre/post-PONR** → phase-aware `DatabaseReplacementResult`
   (`Committed` / `RejectedBeforeMutation` / `FailedAfterMutation` / `FatalNoGeneration`).
   Callers delete the rollback file / clear markers ONLY on `RejectedBeforeMutation`; on
   `FailedAfterMutation` and `FatalNoGeneration` the recovery assets belong to the runtime's
   ladder (the S1 coordinator keeps its shipped defensive cleanup only for a genuinely-failed
   post-mutation rollback attempt).
5. **Ladder leaks** → staged construction + close discipline. `buildGeneration` closes an
   OWNED database when the graph factory throws (a graph-only candidate shares the outgoing DB
   and never closes it) and cancels the fresh lifetime; a failed/unknown `close()` →
   `RejectedBeforeMutation` with NO rename ever attempted; `rolledBack` is set BEFORE consuming
   the source in both the primary rollback operation and recover-via-rollback (a rolled-back
   file admits a follow-up attempt); `Fatal` is a published terminal state — `currentGeneration`
   and lease acquisition throw, and no path converts Fatal back to Serving.
6. **State-machine defects** → same-operation single-flight registration is atomic under
   `submissionLock` (fixes §18 residual (b)); graph-only transitions keep the outgoing
   ViewModelStore INTACT until publish (clear + lifetime join run AFTER `publishServing`),
   unwind construction/preflight throws to `Serving`, and a nested rollback inside a graph-only
   preflight is REJECTED pre-mutation via the `GraphOnlyTransition` context marker (deadlock-
   free; persisted S1 state stays intact for a cold start); one immutable `Published` value
   backs both `phases` and `uiPhases` — the two faces cannot disagree.

### 20.2 What each suite proves now (exact claims)

- `AppRuntimeReplacementTest` (JVM, 20 tests): submission survival for BOTH real initiator
  shapes (Settings-Store cancel mid-await; undo initiator inside the outgoing lifetime — hook
  runs, no deadlock); atomic same-op coalescing under concurrency; different-op isolation
  (never each other's results); lease-drain abort pre-close + admission reopen + a REAL parked
  thread binding to the successor generation; wrong-id / stale-id / multi-attachment UI-gate
  negatives; the RestartProcess transaction shapes; close-throw → `RejectedBeforeMutation`
  with zero renames; the full post-close ladder including graphFactory-failure closing the
  orphan candidate DB and the rolled-back-file retry; inline rollback inside a running
  transaction; Fatal throwing from every holder; hook-failure containment.
- `AppRuntimeTest` (JVM, 12 tests): gen-1 lazy single-build with the unified published value;
  graph-only same-DB handover (db factory runs once); outgoing lifetime joined only after
  publish; candidate never published before preflight; aborts leave the outgoing
  ViewModelStore's ViewModels INTACT (asserted via a probe ViewModel surviving); construction/
  preflight throws unwind to Serving with the SHARED db never closed; nested rollback in a
  graph-only preflight observed as `RejectedBeforeMutation` inside the preflight with zero file
  operations; unreleased lease aborts graph-only too; stale-expected coalescing; caller
  cancellation abandons only the await.
- Caller suites: `RestoreRecoveryCoordinatorTest` pins the phase-aware branches (post-mutation
  failure keeps shipped defensive cleanup; PRE-mutation rejection and Fatal preserve every
  asset/marker and publish nothing; undo state+dialog writes precede the caller hook);
  `RestoreDialogChoiceObserverTest` pins that the success acknowledge is passed AS the
  onCommitted hook; `BackupInteractorImplTest` pins marker-write-inside-beforeMutation ordering
  and that only `RejectedBeforeMutation` triggers pre-swap cleanup; `BackupWorkerTest` pins
  lease release exactly-once (success AND body-throw); `MetroWorkerFactoryTest` pins one-lease-
  per-admission with deps bound at admission time and zero admissions on the negative path.
- `AppRuntimeUiHandshakeDeviceTest` (device, composed): a REAL `AppRuntime` behind the whole
  app shell (`MetroTestGraphHolder.runtimeDelegate`); one graph-only `reinitialize()` against
  live composition proves the quiesce awaits the UI region's ACTUAL disposal (production
  callbacks, production stream), the successor re-keys `App()` at the root, the same database
  object crosses the handover, and post-swap recreation restores only the new generation.
  Known-negative (executed+reverted): severing `TestApplication`'s dispose callback turns this
  exact test red with the bounded `Aborted("ui region did not dispose in time")` and the
  outgoing generation kept serving — raw XML committed
  (`known-negative-ui-handshake-severed-dispose.xml`). The `reinitialize(expected = genOne)`
  submission runs on a background `thread(name = "handshake-submitter")` while the TEST thread
  pumps composition (`composeRule.waitUntil(timeoutMillis = 15_000)`): the compose rule owns the
  frame clock, so blocking the test thread on the transition instead would starve recomposition,
  the live region would never dispose, and the run would report a FAKE `Aborted` disposal timeout
  instead of exercising the handshake.
- `RuntimeGenerationSwapDeviceTest`: claims NARROWED. The runtime host is built directly — no
  Activity, no composition, no WorkManager, no `MetroTestRule` — so the quiesce stages run over
  EMPTY populations (zero UI attachments, zero admitted leases): it proves the inode swap, the
  terminal close and the fresh-Room generation's coherence, and claims neither the UI handshake
  (`AppRuntimeUiHandshakeDeviceTest`) nor the live lease drain (the JVM gate/lease suites).

### 20.3 Re-verification (2026-08-23, all forced)

- Host battery (`assembleDebug testDebugUnitTest verifyPaparazziDebug lintDebug
  assembleDebugAndroidTest --rerun-tasks --no-build-cache --no-configuration-cache`): exit 0,
  9m06s, 3265/3265 tasks executed, **2609 host tests / 0 failures** (up from §18's 2461 — the
  rework's new and rewritten suites).
- detekt: forced, exit 0 (zero suppressions added — the two TooGenericExceptionCaught hits were
  refactored to `runCatching`, blank-line/import findings fixed). `:lint-rules:test`: exit 0.
- Affected-KMP iOS compile: `:core:data:backup:api:compileKotlinIosSimulatorArm64` forced,
  exit 0 (the seam change is androidMain; iOS link/runtime remains UNVERIFIED — no host).
- Connected (API-34 arm64 emulator): full `app:app` 46/0; `core:data:database` 30/0;
  ui_tests.yml-form Regression **78/0** (46+30+2), Smoke **43/0**. Raw XMLs refreshed beside
  the evidence README.
- The saveable-slot one-frame death window (§18 residual (a)) remains DEFERRED, unchanged —
  closing it requires a Phase-7-owned removal protocol; no safe closure existed inside this
  rework's scope.

### 20.4 Phase 7 obligations (delta)

Unchanged from §18 plus: the iOS host must (i) call `reinitialize`/`replace` through the
submission API only (never wrap them in its own cancellable scope — the submission API already
guarantees completion), (ii) implement the platform analogue of the UI attach/dispose gate for
its composition root, (iii) route any DB-bound background work through an admission lease
equivalent, and (iv) drop stale saveable slots at cold start if it persists generation counters.

## 21. Round-2 REQUEST_CHANGES rework record (2026-08-23) — the unified transaction protocol

The round-1 rework was rejected for patching symptoms with unstructured callbacks. Round 2
consolidates ownership and terminal semantics into ONE transaction protocol (§8.4 rewritten in
place — the governing state machine now IS the round-2 machine). This section maps the eight
mandatory corrections to their mechanisms and pins.

Commits: `6d26fbdd` (snackbar epoch) · `822c8d0d` (restore journal) · `d02123cd` (the unified
protocol) · `e9886497` (adversarial-round hardening) · docs closeout (this section).

### 21.1 Corrections → mechanisms

1. **Source ownership at submission** — `ReplacementOperation.RestoreFromSnapshot` is
   identity-keyed on the ORIGINAL path (coalescing before staging); the submission frame
   (non-suspending — no cancellation point between registration and transfer) STAGES the file
   into a runtime-owned copy (`stageRestoreSource`: rename, copy fallback); the runtime deletes
   the staged copy on EVERY terminal outcome. No NonCancellable-wrapped caller await anywhere.
2. **Runtime-owned compensation** — the `ReplacementHooks` lambdas are DELETED. All caller
   compensation is the typed `DatabaseReplacementEffects` object (api module):
   `onBeforeMutation` (preparation, inside the mutex) + exactly one terminal method per
   transaction (`onRejectedBeforeMutation` / `onCommitted` / `onRecoveredByRollback` /
   `onFailedAfterMutation` / `onFatal`), executed by the runtime on the transaction coroutine
   for every outcome INCLUDING internal escapes. A failing `onCommitted` surfaces as
   `Committed(effectsError)` — never swallowed. One durable journal entry backs the
   RestartProcess path (§8.5a: `restore_mutation_interrupted`).
3. **Result truth** — `Committed` = the REQUESTED operation committed. New
   `RecoveredByRollback(error)` for a restore that failed post-PONR and was recovered onto
   PRE-operation data (restore-FAILURE semantics; the inline-S1-rollback restore path now
   reports it too, where round 1 falsely reported Completed). An explicitly requested rollback
   that commits reports Committed.
4. **Asset preservation** — the coordinator's delete-on-FailedAfterMutation branch and its test
   are REMOVED; every non-commit S1 rollback outcome preserves every file and marker, and the
   next launch retries (pinned by a two-coordinator process-restart test). The journal (§8.5a)
   keeps a preserved-assets restart from producing a false RestoreSuccess.
5. **PONR = the start of every irreversible action** — teardown start / close INVOCATION /
   rename (per-transaction `PonrTracker` crossed before, not after). Outgoing close-throw →
   Fatal (RebuildInProcess) / FailedAfterMutation (RestartProcess) — never
   `RejectedBeforeMutation`, never a republish, never a rename. Candidate/orphan/inline close
   throws stop the ladder Fatal with no further rename; candidate jobs are cancelled-and-JOINED
   before their DB closes.
6. **Complete teardown** — strict replacement clears the outgoing runtime-owned ViewModelStore
   and joins the lifetime BEFORE close (an unjoinable job → Fatal without closing); graph-only
   publishes N+1 only after N's teardown reaches the committed boundary; post-PONR failures
   never resurrect a partially disposed N.
7. **Admission repair** — worker lease acquisition moved from the factory to the FIRST
   operation inside `doWork` (suspending; the factory captures nothing; constructed-but-never-
   started workers hold no lease). UI attachment admission closes ATOMICALLY with the zero
   observation (the retire CAS — a late attach is refused; aborts un-retire; commits retire
   forever). Snackbar models are generation-tagged at enqueue; a committed handover advances
   the epoch (gen-N callbacks discarded at delivery, never executed in N+1); aborts preserve.
8. **Fatal terminal under concurrency** — liveness rechecked INSIDE `transitionMutex` (a queued
   B after A's Fatal performs no validation/close/swap/publication); `replace()`/`reinitialize`
   after Fatal return Fatal (never a cleanup-safe rejection); `publishTransitioning` cannot
   overwrite Fatal; every submitted deferred completes exactly once, internal
   `CancellationException` included (the round-1 graph-only CE-rethrow that stranded the
   deferred is fixed and pinned).

### 21.2 What each suite proves (exact claims)

- `AppRuntimeReplacementTest` (JVM, 35): staging-at-submission + terminal staged cleanup +
  cancelled-caller-cannot-strand (original path deleted mid-transaction); marker-then-caller-
  killed-then-lease-timeout → transaction-owned compensation; effects exactly-once for every
  terminal (order-recorded); `Committed(effectsError)` surfacing; swap-fail→rollback →
  `RecoveredByRollback` (never Completed) with recovered-effects; inline-S1-rollback → inline
  caller Committed / outer restore RecoveredByRollback; requested-rollback → Committed;
  RestartProcess swap-fail AND close-throw → FailedAfterMutation with journal effects + assets
  preserved; outgoing close-throw → Fatal + no rename + throwing holders; probe-ViewModel
  cleared + DB job joined BEFORE close (order-recorded); unjoinable job → Fatal without close;
  candidate-dispose close-throw and orphan close-throw → Fatal with exactly one swap (no
  rollback rename); clean orphan close → RecoveredByRollback; lease abort pre-PONR + admission
  reopen + retry; suspended acquirer binds the successor atomically; late attach after the
  zero observation refused (count stays 0) while attach-before-zero blocks the machine;
  abort un-retires the outgoing id; epoch advances on commit only; atomic same-op coalescing
  (one staging, one preflight); different-op isolation; A-Fatal-while-B-queued → B does
  nothing (no validation read, no swap, Fatal result, Fatal never overwritten);
  replace/reinitialize after Fatal → Fatal with only `onFatal` effects; Fatal admission throws
  loudly instead of parking.
- `AppRuntimeTest` (JVM, 14): gen-1 lazy single-build + one immutable published value behind
  both faces; same-DB handover; **teardown-completes-before-publish** (VM clear and job-end
  both observe `Transitioning`, never gen N+1); candidate-unpublished-before-preflight;
  pre-PONR aborts leave serving generation + ViewModelStore INTACT; construction/preflight
  throws unwind; **preflight `CancellationException` → the deferred still resolves (Aborted)**;
  nested rollback in a graph-only preflight → deterministic `RejectedBeforeMutation`, zero file
  ops; lease abort + retry; stale-expected coalescing; caller-cancel submission ownership;
  id-bound UI gating (wrong id never releases); epoch commit/abort discipline.
- `RestoreTransactionIntegrationTest` (JVM, 5 — the composed seam gate): the REAL
  `RestoreLatestBackupUseCase` + REAL `AppRuntime` + ACTUAL temp files: commit path (marker via
  effects, staged file swapped, both file families cleaned); caller cancelled mid-transaction
  with its GENUINE `finally { tempFile.delete() }` running — the staged copy survives and the
  restore commits; caller-killed + lease-timeout → the real effects compensate marker+preserved
  snapshot on the transaction; swap-fail+rollback → `Failure` result, marker compensated, no
  fake undo; ladder-exhausted Fatal → `restore_mutation_interrupted` journaled with the marker
  preserved.
- `RestoreRecoveryCoordinatorTest` (JVM): journal-first pre-flight (no schema peek, never
  RestoreSuccess); FailedAfterMutation preserves EVERY asset (the old defensive-delete test is
  gone); the two-coordinator PROCESS-RESTART gate (fail → assets survive → second instance
  completes the rollback + truthful RestoreFailure exactly once); undo effects ordering
  (state+dialog before the caller's acknowledge); `Committed(effectsError)` logged not lied.
- `BackupInteractorImplTest` (JVM): marker-inside-beforeMutation ordering; per-result caller
  mapping incl. `RecoveredByRollback` → Failure + marker compensation, FailedAfterMutation/
  Fatal → journal write with nothing deleted.
- `BackupWorkerTest` / `MetroWorkerFactoryTest` (JVM/Robolectric): first-op lease acquisition
  and exactly-once release (success AND body-throw); **constructed-but-never-started worker
  acquires NO lease** (builder- and factory-constructed); the factory touches no admission on
  either dispatch path; the run binds the deps CURRENT at doWork time, not construction time.
- `SnackbarManagerTest` (JVM, kit): a pre-advance model is discarded at delivery with its
  callbacks never executed; no advance → preserved and delivered; delivery keeps exactly the
  current-epoch models.
- `RestoreStateRepositoryImplTest` (JVM): the journal entry round-trips, is idempotent, and is
  cleared by `clearRestoreInProgress`.
- Device: `RuntimeGenerationSwapDeviceTest` (claims per §20.2 narrowing — inode/close/fresh-
  Room over empty quiesce populations) and `AppRuntimeUiHandshakeDeviceTest` (the composed
  real-handshake proof; its known-negative XML stands) — re-run green on the round-2 protocol.

### 21.2a Adversarial verification round (pre-submission, 4 independent lenses)

A fresh-context adversarial pass (protocol-interleaving, mandate-compliance, test-vacuity,
caller-semantics lenses) ran against the landed protocol; every confirmed finding was fixed on
the branch before submission:

- **Terminal effects now hold the transition mutex on EVERY path** (they ran post-unlock,
  letting a successor transaction's `onBeforeMutation` interleave with the predecessor's
  pending compensation — which could erase the successor's crash-safety marker or delete the
  shared preserved snapshot mid-swap). Pinned by `terminal effects hold the transition mutex —
  a successor cannot interleave them` (T2 provably blocked behind T1's suspended compensation).
- **Phase-aware Fatal dispatch**: a transaction that resolves Fatal WITHOUT crossing its own
  PONR (queued behind another's Fatal; submitted onto a Fatal runtime) performed nothing —
  dispatching `onFatal` would journal a mutation that never happened (and later force a
  rollback of a committed restore); dispatching the rejection-compensation would delete the
  fatal transaction's recovery assets. Neither runs; the caller learns from the result alone.
- **No silent boot loop**: an UNCOMMITTED failure-path rollback now returns
  `PreflightOutcome.RecoveryRetryPending` — the launch CONTINUES (no `RestartRequired`: with
  the rollback pending, restart→retry→fail→restart would loop forever with zero feedback), a
  truthful `RestoreFailure` dialog is published WITHOUT touching any recovery asset, and the
  next launch retries. Pinned in the coordinator suite (incl. the two-launch process-restart
  gate) and `StartupProcessorTest`.
- **Staging debris**: a mid-copy staging failure now deletes its own partial file (the terminal
  cleanup only tracks a successfully staged copy).
- **Vacuity hardening** (each previously-green-under-regression hole now discriminating):
  packaged-effects discriminator tests capture the effects object WITHOUT invoking it and
  assert nothing runs post-await, then invoke it manually (the effects-invoking stub alone
  could not distinguish effects-packaging from post-await code); the successor-binding lease
  test uses an UNCONFINED acquirer that resumes inside `reopen()` (order-sensitive:
  reopen-before-publish would bind the closing generation and fail); the submission-frame
  staging pin runs with a STANDARD host dispatcher + `UNDISPATCHED` caller (staging must be
  observable before the host coroutine ever runs); `UiAdmissionGateTest` adds a
  4000-iteration multi-threaded hammer for the retire CAS (a two-step observe-then-retire gate
  ends retired WITH a counted attachment and fails); the candidate-jobs-joined-before-close
  clause gained its missing pin; the undo ordering pin now records clear → publish →
  acknowledge as one ordered list. Post-hardening counts: `AppRuntimeReplacementTest` 35,
  `AppRuntimeTest` 14, `RestoreTransactionIntegrationTest` 5, `UiAdmissionGateTest` 4,
  `StartupProcessorTest` 10, coordinator suite 15 — all green.

**Recorded residual (deliberate, documented — not silent):** under `RestartProcess`, an
outgoing `close()` that THROWS maps to `FailedAfterMutation` + the §8.5a journal (mandate 5
forbids the cleanup-safe rejection; skipping the journal would reintroduce the banned false
"restore succeeded" on the next launch). If the app keeps running usable in that anomalous
window (a throwing close is a driver-level fault — Room 3's close is graceful-blocking by
design) and the user writes data before the next launch, the journal-forced rollback reverts
those writes to the pre-restore snapshot. Accepted: the branch is anomalous-by-construction,
both truthful alternatives are worse (a lying success dialog, or a permanently stuck marker),
and the window closes at the next launch.

### 21.3 Phase 7 obligations (current)

Supersedes §20.4: the iOS host must (i) call `reinitialize`/`replace` through the submission
API only; (ii) implement the platform analogue of the UI attach/dispose gate INCLUDING the
atomic retire semantics for its composition root; (iii) route DB-bound background work through
a first-operation admission lease equivalent (never construction-time binding); (iv) supply a
`DatabaseReplacementEffects` implementation for its restore flow and honor the
`RecoveredByRollback` / `Committed(effectsError)` result semantics; (v) read the
`restore_mutation_interrupted` journal in its cold-start pre-flight; (vi) drop stale saveable
slots at cold start if it persists generation counters. The saveable-slot one-frame death
window (§18(a)) remains deliberately deferred to the Phase 7 host design.

## 22. Round-3 REQUEST_CHANGES rework record (2026-08-23)

Round 2 was rejected for the crash gap between its two restore booleans and for gates that were
counted rather than closed. Round 3 makes the durable record attempt-scoped and turns each gate
into a real barrier. §8.4 / §8.5a / §8.5b above are rewritten in place as the governing text.

### 22.1 Blockers → fixes → the exact test that pins each

| # | Blocker | Fix | Proof (and its exact boundary) |
|---|---|---|---|
| 1 | `restore_mutation_interrupted` written only by terminal effects → a death after close-start left the OLD valid DB with no marker → cold start peeked it and published a false `RestoreSuccess` | Attempt journal (§8.5a): `Prepared` persisted atomically pre-PONR with identity + context + reserved rollback path; `Committed` recorded only after the rename returned success and the reservation was staged (R5; pre-R5, promoted); owner-only advance/resolve; legacy and unparsable states read as `Prepared` | `RestoreRecoveryCoordinatorTest`: `a PREPARED attempt never peeks the schema and never claims success` (asserts ZERO `currentSchemaVersion()` calls and no `RestoreSuccess` publish) + `a ROLLBACK-kind attempt never peeks the schema either` + `a COMMITTED attempt verifies by peek and succeeds`. `RestoreStateRepositoryImplTest` (18) pins ownership, idempotence, legacy migration and the unparsable-phase reading. **Boundary:** these prove the BRANCHING and the persisted round-trip; no test kills the process, so single-`edit` atomicity is DataStore's guarantee, not ours |
| 2 | every non-commit rollback mapped to one `RecoveryRetryPending`, after which chores armed and the main UI showed over an unknown database | `RetrySafe` (proven pre-PONR, DB intact and open → continue, assets preserved) vs `RecoveryRequired` (post-PONR / closed / fatal / commit-without-durable-record → `RouteToRecovery`, arm NOTHING) | `StartupProcessorTest`: `RetrySafe continues the launch and arms normally` and `RecoveryRequired routes to recovery and arms ZERO db-bound work` (planner, `recoveryBootstrap` and `cleanupTempFiles` each asserted `exactly = 0`). `RestoreRecoveryCoordinatorTest` maps every result to its verdict incl. the parameterized post-mutation trio |
| 3 | `preserveCurrentDb()` ran pre-submission, outside the mutex, onto ONE canonical path | reservation moved inside the transaction (after validation, before PONR), per-attempt file recorded in the journal, promoted on commit, discarded only by its owner; `preserveCurrentDb` deleted | `AppRuntimeReplacementTest`: reserved-inside-the-transaction (not called when validation fails; called once with the attempt id; its path reaches `onBeforeMutation`), rejected-attempt-keeps-the-previous-slot (sentinel bytes), promote-on-commit, and `BackupInteractorImplTest` asserting the use case no longer reserves anything |
| 4 | UI gate counted attachments (ABA-prone) and admitted from an EFFECT, i.e. after the region's children had already resolved | token gate with atomic retire; admission during composition via `RememberObserver`, content gated on the grant; Store jobs parented to the generation lifetime; `dispose()` idempotent; `onCleared` ends the Store | `UiAdmissionGateTest` (ABA test that a counter cannot pass, 3000-iteration retire-CAS hammer, idempotent-release, refusal); `StoreGenerationJoinTest` (a real `BaseStore.launchDefault` whose `finally` touches the DB completes BEFORE `cancelAndJoin` returns — killed by reversing the `plus` operands); `UiAdmissionRaceTest` (composed; a refused region resolves zero dependencies). **Boundary:** the composed test drives the frame clock manually, but it proves "refused ⇒ nothing resolved", not every possible interleaving of the real Compose applier |
| 5 | epoch advanced AFTER publish; requeue re-stamped the current epoch; resolve accounting was a non-atomic RMW with no fence | advance before publish; the epoch travels with the model (`DeliveredSnackbar`) and requeue preserves it; one linearizable gate value; fence closes admission atomically with the zero observation | `SnackbarManagerTest`: requeue-keeps-its-epoch, abort-preserves, no-resolve-after-the-fence, fence-waits-for-an-in-flight-`NonCancellable`-commit, two-overlapping-collectors; `AppRuntimeTest`: `the snackbar epoch advances BEFORE the successor is published` (records the published phase observed at advance time) |
| 6 | `buildGeneration` cancelled a partial candidate's lifetime and closed its DB immediately | one `tearDownCandidate` path — clear → cancel + bounded JOIN → close only after the join → any failure stops the ladder with no later rename; the same rule for partially constructed generations | `AppRuntimeReplacementTest`: candidate-jobs-joined-before-close and partial-construction-joins-before-closing-the-orphan, both order-recorded with a DB-touching `finally` |

### 22.2 Durable journal — transitions and crash outcomes

| Crash point | Journal reads | Files on disk | Next launch concludes |
|---|---|---|---|
| before `beginAttempt` | no attempt | live DB untouched, previous undo slot intact | normal launch (`NoOp`) |
| after `Prepared`, before teardown | `Prepared` + reservation path | live DB untouched; reservation == live DB | recovery: rolls back onto the reservation (a no-op restore of identical bytes) |
| during teardown / after close begins, before rename | `Prepared` | live DB still the OLD file | recovery: rolls back onto the reservation — **never a peek-driven success**, which is the round-2 hole |
| after rename, before promote | `Prepared` | live DB is NEW; reservation holds the true pre-attempt DB; canonical slot still the PREVIOUS restore's | recovery: rolls back onto the **reservation** (the journal names it), i.e. the correct pre-attempt data |
| after promote, before `Committed` | `Prepared` | live DB is NEW; canonical slot now holds the true pre-attempt DB; reservation consumed by the rename | recovery: reservation path is gone → falls back to the canonical slot, which the promotion just made correct |
| after `Committed`, before terminal effects | `Committed` | live DB is NEW; undo slot correct | success path: peek verifies the NEW file, publishes `RestoreSuccess`, resolves the attempt |
| S1 recovery rollback, any of its own equivalents | the SAME attempt id, `Prepared` → `Committed` | as above with the roles swapped | the next launch re-enters the pre-flight and retries; a committed recovery resolves the attempt and publishes `RestoreFailure` |

A crash between the file commit and the durable record therefore rolls back conservatively — permitted explicitly — and never claims success.

### 22.3 Known-negatives (executed, red, reverted; XMLs committed)

| Mutation | Result | Evidence |
|---|---|---|
| R3-A — the cold-start branch ignores `phase`, so a `Prepared` attempt takes the peek path | 7 coordinator tests red, incl. `a PREPARED attempt never peeks the schema…` failing on `currentSchemaVersion() should not be called` — the false-success reproduced | `known-negative-r3a-prepared-bypass.xml` |
| R3-B — publish the successor BEFORE advancing the snackbar epoch | `the snackbar epoch advances BEFORE the successor is published` red | `known-negative-r3b-publish-before-epoch.xml` |
| R3-C — retire the outgoing id in a step SEPARATE from the zero observation | the rewritten hammer red at iteration 29: *"a token was granted for a generation the gate reported clear"*. The first hammer could NOT see this (its racer released its own grant before the count was read) — the adversarial review caught that, and the test now keeps the grant so a token and a "clear" verdict become mutually exclusive | `known-negative-r3c-two-step-retire.xml` |
| plus-order reversal in `AppCoroutineScopeImpl` (agent-run) | `StoreGenerationJoinTest` test 1 red: `cancelAndJoin` returned while the DB-touching `finally` was still pending | reported in the commit; re-runnable |
| non-idempotent `dispose()`, re-stamping `requeue`, always-admit `beginResolve`, non-awaiting `fence`, collapsed resolve counter (agent-run) | each killed by its own new pin | reported in the commit; re-runnable |

### 22.3a What the adversarial review changed (2026-08-23)

A fresh-context review over crash ordering, concurrent asset ownership, admission and test
vacuity confirmed eleven defects; all are fixed and the tests that pinned the old behavior are
now pins for the new invariants. The material ones:

- **An interrupted-but-COMMITTED rollback was re-driven**, looked for the preserved file it had
  itself consumed, failed, and left the attempt unresolved FOREVER — which then refused every
  future restore and undo (their `beginAttempt` saw a foreign owner) with no in-app remedy. Such
  an attempt now finishes its bookkeeping (`PreflightOutcome.RecoveryCompleted`).
- **The recovery rollback erased the journal's `rollbackSnapshotPath`** on its re-claim, so a
  second interruption fell back to the canonical slot — an OLDER snapshot — and silently
  reverted data the failed attempt never touched. The path is carried through.
- **The ladder recorded the CALLER's attempt as `Committed` for a rollback**, so the next
  preflight read a rolled-back database as a successful restore and published `RestoreSuccess`.
  The ladder no longer runs the caller's commit bookkeeping at all.
- **The ladder applied the canonical slot instead of the attempt's reservation**, overwriting an
  intact live database with older data and then deleting the reservation. It now applies the
  reservation when it has one and consumes only what it applied.
- **`promoteRollbackReservation` deleted the canonical slot before the move**, so a failed
  promotion destroyed the undo slot while the journal still named a reservation the runtime then
  deleted. It stages first, never pre-deletes, and a failed promotion keeps the reservation.
- **`UiAdmissionGate.admit` carried its verdict in a flag mutated inside a `MutableStateFlow
  .update` lambda**; on a losing CAS retry the flag kept the LOSING iteration's value, issuing a
  token for a generation `awaitRetired` had already reported clear. The verdict is read off the
  committed state.
- **A refused region was cached against the generation id alone**, so an aborted transition's
  same-id re-publish never re-asked for admission and left the app blank until the next Activity
  recreation. The grant is keyed on the published phase value.
- **Test vacuity, three cases:** the generation-job seam had NO test (a one-token revert
  restored the defect with the suite green) — the parameter is now REQUIRED, so dropping it is a
  compile error; the promote-before-record ordering was unpinned — it now has an interleaving
  test; and the retire-CAS hammer could not observe its own gap — rewritten as above and proven
  by R3-C.

### 22.3b The generation-deps seam, closed on-device (found by the Smoke gate)

Making `generationJob` required stops the ARGUMENT being dropped, but not the VALUE being wrong:
`remember { null }` still compiles and still un-parents every Store job. The device Smoke gate is
what surfaced the remaining hole, by going red for an unrelated-looking reason — `:core:ui:mvi`'s
own `AppFeatureScopeTest` probe deliberately builds its Store with no app graph, and
`rememberStoreProcessor` now legitimately requires ONE app-scope binding, so the probe's plain
`Application` failed the `appDeps` cast.

The fix supplies the contract rather than softening it. `appDeps` is strict by design — the cast
throwing is what forbids a Store from silently starting un-parented jobs — so a `?: null` fallback
in the processor would have turned the red gate green while re-opening the exact defect blocker 4
exists to prevent. The probe instead provides the binding the way production does, through an
`applicationContext` that implements `AppDepsHolder` (`ProbeAppDepsHost`, which overrides only
`LocalContext` and so leaves the `LocalViewModelStoreOwner` / `LocalLifecycleOwner` invariants the
test asserts untouched).

`storeJobsAreDescendantsOfTheGenerationSuppliedByTheDepsSeam` then pins the seam end to end on a
device: it composes the real `rememberMetroStoreProcessor`, starts a job through the ordinary
`launchDefault` surface with a `finally` standing in for a database touch, and ends the lifetime
the holder handed out. **Proof boundary:** it proves the job the SEAM supplies is the parent —
`cancelAndJoin` cannot return before that `finally` runs. The host `StoreGenerationJoinTest` still
owns the `AppCoroutineScopeImpl` plus-order proof, which this does not re-prove.
Known-negative **R3-D** (`remember { null }` in `StoreProcessor`): red on exactly this test,
green on the scope test beside it — which is why the gap survived the review's first pass.

### 22.4 Residuals and Phase 7 obligations (delta over §21.3)

- A pre-R3 install's `restore_in_progress` marker is read as `Prepared`, so the one launch that
  spans the upgrade recovers conservatively instead of peeking. Deliberate: the old flags cannot
  prove a commit, and the window is a single launch between the restore tap and the next start.
- The saveable-slot one-frame death window (§18(a)) remains deferred.
- `UiAdmissionRaceTest` executes only the REFUSAL half of `GenerationAdmission`'s leak-freedom.
  The other half — `onAbandoned`, a composition composed and then thrown away before it is
  applied — stays covered by construction through the `RememberObserver` contract rather than by
  execution: Compose offers no supported way to force an abandoned composition from a test.
- Phase 7 additionally owns: implementing the token admission gate for its composition root
  (grant during composition, release on both the forgotten and abandoned paths); parenting its
  Store equivalents to the generation lifetime so teardown can join them; advancing its snackbar
  equivalent's epoch before publication; and honoring the attempt journal's phases in its
  cold-start pre-flight rather than peeking.

## 23. Round-4 maintainer-correction record (2026-08-23)

Three fix commits on top of `936ab699` — `1dacb403` (backup ownership: blockers A, B, C, E),
`3e9aee6e` (graph-only teardown terminality: blocker D), and `e9459025` (the defects the
mandated fresh-context adversarial review confirmed, §23.5) — closing the remaining Android
production correctness gaps. §8.4 (the graph-only terminality paragraph), §8.5a and §8.5b were
rewritten IN PLACE; this section is the record. No Phase 7 work, no swappable DAO/repository
indirection; Android production remains `RestartProcess` and the Room same-instance reopen gate
is untouched.

### 23.1 Corrections → mechanisms → the discriminating test

Every test listed ran RED on `936ab699` (the raw XMLs are committed as
`kmp-phase-5-evidence/r4-red-on-base-*.xml`, produced by copying the new test files onto a
worktree at that commit) and is green at the current head.

| # | Correction | Mechanism | Red-on-base pin |
|---|---|---|---|
| A1 | promotion is crash-safe | the promotion COPIES (R5 split it into `stagePromotedRollback` + `completePromotedRollback`); the reservation survives every crash point; the runtime deletes it only after the durable `Committed` record; committed cold-start finalization cleans a retained copy idempotently; stale `.promoting` deterministically discarded | `promotion COPIES — the journal-named reservation survives with its exact bytes` (REAL files, sentinels A/B, `DatabaseSnapshotProviderImplTest`) |
| A2 | journal-named source authoritative | `selectRollbackOperationSource`: missing explicit path → typed rejection, never the canonical; `selectRecoverySource`: vanished reservation → Fatal; failed explicit-source rollback → Fatal; post-clean-commit source legal by the reservation-lifetime proof (R5; pre-R5, the ordering proof) | `a MISSING journal-named rollback source is a typed rejection…`; `ladder recovery whose reservation VANISHED goes Fatal…` |
| A3 | exact-file consumption | typed `SourceConsumption` (None/CanonicalSlot/ExactFile) in `MutationPlan`; a rollback consumes exactly what it applied | `an explicit rollback applies and consumes EXACTLY its named source — the canonical survives` |
| A4 | reservation kept on unresolved-journal outcomes | `keepReservation` covers every outcome with `effectsError != null` | `a rejection whose terminal compensation fails KEEPS the reservation…` |
| B | terminal classification | `RetrySafe` removed; every non-commit rollback outcome → `RecoveryRequired` (zero DB-bound arming, no Main UI) | `every non-commit rollback outcome requires terminal recovery` (`RejectedBeforeMutation` case) + `StartupProcessorTest`'s zero-arming pins |
| C | legacy owner isolation | atomic legacy→owner-scoped conversion under the synthetic id only; `resolveAttempt` requires that id; `RecoveryRollbackEffects` (R4: `ScenarioOneRollbackEffects`) CHECKS its re-claim | repository: `only the synthetic legacy owner claims…` + the flipped arbitrary-resolver pin; coordinator: `the recovery re-claim CHECKS ownership…` + the legacy end-to-end |
| E1 | replay-safe success finalization | mark → publish → reservation cleanup → resolve LAST, `runCatching`-wrapped | `success finalization is data-bearing-first…`; `a finalization failure never leaves the journal resolved with the undo hidden` (mandated test 11); `…cleans up the RETAINED reservation copy idempotently` |
| E2 | committed-rollback replay ground truth | availability from canonical-file existence; resolve last; at-most-once dialog documented (§8.5b) | `an interrupted but COMMITTED rollback…` (order + clear) and `a committed RESERVATION-sourced rollback keeps the previous restore's still-valid undo` |
| E3 | Committed recovers from canonical | `recoverFrom` passes `sourcePath` only for `Prepared` | `a COMMITTED attempt recovers from the CANONICAL slot…` |
| D1 | post-PONR teardown failure terminal | best-effort candidate release → aggregate → `publishFatal`; no epoch advance, no publication, UI gate stays retired | `outgoing ViewModelStore clear failure after PONR is FATAL…` (test 7); `unjoinable outgoing lifetime after PONR is FATAL…` (test 8) |
| D2 | candidate teardown verdict checked | preflight-fail path: `tearDownCandidate == false` → Fatal, never a republished N | `candidate preflight failure with an unjoinable candidate is FATAL…` (test 9) |
| D3 | partial-unwind signal distinct | `releasePartialGeneration` honors the join verdict for `ownsDatabase=false` → `PartialCandidateUnwindException` → Fatal | `partial construction with an unjoinable child is TERMINAL…` (test 10) — the child job started from `graphFactoryAction` must run on a REAL dispatcher (`Dispatchers.Unconfined`), not the test scheduler, because the unwind path `ReplacementMechanics.releasePartialGeneration` joins it inside its own `runBlocking`, which the `TestScope` scheduler never advances |

### 23.2 Crash-state matrix — one restore attempt, RestartProcess, current protocol

| Death point | Journal | Files (R = reservation, C = canonical, L = live) | Next launch |
|---|---|---|---|
| before `onBeforeMutation` | none | R exists (discarded by frame if reached), C old, L old | `NoOp`; normal launch |
| after Prepared, before close | `Prepared`+R | R = pre-image, C old, L old | recovery rollback from R (idempotent re-apply) → `RestoreRolledBack` → restart |
| after close, before rename | `Prepared`+R | R = pre-image, C old, L old | same — R applied, correct |
| after rename, before stage | `Prepared`+R | R = pre-image (COPY survives), **C = PREVIOUS undo, intact**, L NEW | rollback from R → pre-image restored ✓; the previous undo is still valid AND still truthfully advertised (pre-R5: C was already overwritten here) |
| mid-stage (partial staging) | `Prepared`+R | R intact; S torn; **C intact**; L new | rollback from R ✓; S is attempt-named, never read on a `Prepared` path, discarded by the next stage |
| after stage, before record | `Prepared`+R | R intact, S complete, **C intact**, L new | identical to the row above ✓ |
| staging or record FAILS (no death) | `Prepared`+R | R intact, **S discarded**, **C intact**, L new | `NotDurable` → `FailedAfterMutation`; next launch rolls back from R ✓ |
| **after record, before install** | `Committed`+R | R intact, S complete, C = PREVIOUS, L new | the committed cold-start completion installs S → C = pre-image → peek → success, availability marked with THIS restore's date ✓ |
| mid-install | `Committed`+R | rename is atomic → either the row above or the next; a failed delete+rename fallback leaves C ABSENT, never truncated | the completion re-installs from R ✓; C absent is a state every reader handles honestly |
| after install, before reservation delete | `Committed`+R | R intact, C = pre-image, L new | completion re-stages+re-installs the same bytes; finalization deletes R idempotently ✓ |
| install FAILS permanently after the record | `Committed`+R | R retained, C = PREVIOUS, L new | success reported; NO new availability claim, `previousVersionAvailable = false`; the previous restore's flag+date stay truthful about C ✓ |
| after reservation delete, before terminal effects / restart preempts frame | `Committed` | C = pre-image, L new | peek → success; peek-fail → rollback from CANONICAL (provably ours: no reservation survives, so the install landed) ✓ |
| mid-finalization (any point before resolve) | `Committed` | C = pre-image, L new | full replay: mark, publish, cleanup, resolve — idempotent ✓ (pre-R4: resolve-first death hid the undo forever) |
| after resolve | none | C = pre-image, L new, flag set | `NoOp` ✓ |

And for a committed CANONICAL-sourced rollback (`Committed`/`Kind.Rollback`, source path null):

| Death point | Journal | Files | Next launch |
|---|---|---|---|
| after `record Committed`, before the canonical consume | `Committed`+Rollback, path=null | C still present, flag still true, L = rolled-back data | the SOURCE-AWARE finalization (R4.1) finishes the consumption from the journal's own discriminator: consume C → clear availability → resolve LAST — the same undo is never offered again (pre-R4.1 the surviving file read as "a valid previous undo" and the rollback stayed replayable, erasing later writes) |
| mid-finalization (before resolve) | `Committed`+Rollback | per progress | idempotent replay to the same terminal state, re-publishing the journaled origin's dialog before the resolve (R5) ✓ |

**Proof boundary:** this matrix is a COMPOSED proof — the file-level copy-survival semantics
are pinned against the real provider over real files, the ordering pins against the runtime,
and the classification/finalization pins against the coordinator and repository. No literal
two-launch process-death integration test exists; the multi-launch rows compose those pins.

### 23.3 Known-negatives (executed, red, reverted; XMLs committed)

| Mutation | Red pins | Raw XML |
|---|---|---|
| R4-A — explicit-path→canonical fallback re-enabled | `a MISSING journal-named rollback source is a typed rejection…` | `known-negative-r4a-canonical-substitution.xml` |
| R4-B — "publish the candidate anyway" restored after a failed graph-only teardown | tests 7 + 8 (both PONR terminality pins) | `known-negative-r4b-publish-anyway.xml` |
| R4-C — the invalid safe-retry classification restored (rejection → continue-and-arm) | `every non-commit rollback outcome requires terminal recovery` (rejection case) | `known-negative-r4c-safe-retry.xml` |

### 23.4 Residuals (deliberate, with exact boundaries)

- **Pre-R5 rollback entries replay as a recovery failure** — an entry written before the
  `rollbackOrigin` key (or a torn write, or an unknown name) has no origin and reads as
  `ScenarioOneRecovery`, so an undo interrupted by process death AND an app upgrade before the
  next launch still reports "Restore failed". §8.5b carries the boundary and why the safe
  reading is the pessimistic one.
- **A retained reservation after a permanently failed undo-slot install** — the restore is
  committed and verified, the attempt resolves, and `rollback_reservation_*.db` is left
  un-journaled in `cacheDir`. Bounded, evictable litter of the same class as the entry below;
  nothing reads an un-journaled reservation.
- **A `Committed` restore whose install is owed can mis-advertise if BOTH its staging and its
  reservation are evicted from `cacheDir` while the canonical survives** — the completion reads
  the surviving canonical as already-installed and marks availability with this restore's date
  over the previous restore's image. It requires two independent faults (a crash inside the
  record→install window plus selective eviction of two files but not a third), and it
  MIS-ADVERTISES, it never DESTROYS. What it replaces is a single-fault, always-armed
  destruction: pre-R5 every attempt overwrote the previous undo image before becoming durable.
- **An unusable canonical wedges the scenario-1 recovery route** — validation rejects it on every
  launch, so the launch keeps classifying as `RecoveryRequired` with journal and assets preserved
  rather than converging. The user's route out is the recovery surface's export + report. A user
  UNDO over the same file terminates instead (`SourceUnusable` clears availability).
- **A leaked reservation copy** when the process dies between the terminal effects' resolve and
  the same-process submission-frame delete on the in-process (RebuildInProcess) recovered-by-
  rollback path: the journal is already resolved, so no launch cleans the orphan
  `rollback_reservation_*.db`; it is bounded cacheDir litter under system eviction, never a
  correctness input (nothing reads an un-journaled reservation).
- **`DatabaseReplacementEffects.None.attemptId == "no-effects"`** is shared by construction; no
  production mutating caller passes `None` (both coordinator effects and the settings restore
  effects are explicit), and `None` writes no journal — latent only, noted for Phase 7.
- A `Prepared` attempt with a null source path falls back to the canonical slot: its listed
  producers — a legacy marker, an interrupted rollback whose source WAS the canonical, or a
  torn write — all have the canonical as the owner-correct source.

### 23.5 Fresh-context adversarial review (4 hunts) — findings → fixes

The mandated review ran over crash ordering, source-owner identity, teardown return values, and
outcome truth / test vacuity, against the tree at `3e9aee6e`. Production `RestartProcess`
checked clean on every prong; every confirmed finding was `RebuildInProcess`-scoped
(instrumentation + the Phase-7 host) and is fixed in the third round-4 commit. Each fix's pin
ran RED against the pre-fix production (`r4-review-red-prefix-*.xml` beside the evidence
README) and green after.

| Finding | Fix | Pin |
|---|---|---|
| CRITICAL — the INLINE scenario-1 rollback dropped `sourcePath` and hard-coded the canonical slot: a journal-named reservation was ignored, ANOTHER attempt's older snapshot was applied AND consumed (invariants 2+3), and the flag policy then mis-decided | `runInlineRollback` resolves through the same `selectRollbackOperationSource` policy as the top level — explicit path honored, missing path a typed rejection, exact-file consumption | `the INLINE rollback honors the journal-named source…`; `…with a MISSING journal-named source rejects…` |
| HIGH — the inline rollback never ran `onBeforeMutation`, so the journal stayed `Committed`/`Kind.Restore`; a death inside the inline mutation replayed as a FALSE `RestoreSuccess` with a phantom undo | the inline branch runs the caller's `onBeforeMutation` BEFORE anything irreversible — the scenario-1 re-claim converts the slot to `Prepared`/`Rollback`, so every death replays truthful bookkeeping | the re-claim-before-mutation ordering assert inside the inline pin |
| HIGH — the post-clean-commit ladder recovery rolled back while the journal still read `Committed`: the NEXT preflight (no crash needed) or the next launch peeked the rolled-back file and published `RestoreSuccess` | `recoverViaRollback(afterCleanCommit)` durably UN-commits first (the caller's `onBeforeMutation` re-claim → `Prepared`), applies the canonical WITHOUT consuming it, and grants ONE fresh attempt after a preflight that re-drove the recovery inline (bounded: the fresh attempt runs over an empty journal) | `post-clean-commit recovery durably UN-commits…`; `a preflight that re-drives the recovery inline gets ONE fresh attempt…` |
| MEDIUM — `RestoreTransactionEffects.onRecoveredByRollback` unconditionally cleared undo availability even when the recovery applied the reservation and the previous restore's canonical undo remained valid | ground-truth verdict (canonical file existence), same policy as the coordinator's replay branch; the stale seam KDoc ("the preserved file was consumed") corrected | `a reservation-sourced recovery keeps the previous restore's undo availability` |
| LOW — a generation-1 build whose orphan close threw resolved to a RETRYABLE rejection: the retry opened a new handle beside the unknown-state one and later renamed over it | `OrphanCloseException` / `PartialCandidateUnwindException` are terminal in both escape resolvers, pre-PONR included | `a generation-1 orphan close-throw is FATAL…` |
| LOW — the post-PONR teardown paths could hang unboundedly inside the mutex (the ViewModelStore clear had no timeout) | `GenerationQuiescer.clearStoreBounded`: the clear dispatches detached to the main dispatcher and is awaited within the drain budget; a timeout reads as a FAILED clear and the machine reaches its terminal verdict | covered by every existing clear-path pin (completion) + the Fatal verdicts (failure); a wedged-main unit test would block the test thread itself and is documented instead |
| vacuity — the stale-`.promoting` test's comment overclaimed (the pre-delete is defensive, not separately observable) | comment states the exact claim boundary | — |

Hunt verdicts otherwise: every RestartProcess kill point resolves truthfully; PCUE is raised on
exactly the right condition and cannot be swallowed; epoch/gate state after every Fatal is
exact; `RestoreSucceeded`-with-unresolved-journal after a finalization failure is safe
(`beginAttempt` refuses foreign claims until the replay); the graph-only unresolved-`Prepared`
abort arms nothing on the candidate.

## 24. R4.1 final correction record (2026-08-23)

Two commits on `7c82c368`: `39e9155c` (the three proven protocol defects + the missing
liveness proof) and the docs/evidence commit carrying this section. Every new pin ran RED at
`7c82c368` (`r41-red-on-base-*.xml`, worktree run — six tests across the two suites) or
against the executed-and-reverted unbounded-clear mutant
(`known-negative-r41-unbounded-clear.xml`), and is green at head.

| Blocker | Fix | Red pins |
|---|---|---|
| 1 — a committed canonical rollback stayed repeatable after a record→consume crash (the replay read the surviving file as a valid previous undo; replaying it after later writes would erase them) | source-aware finalization from the journal's own `rollbackSnapshotPath` discriminator (§8.5b): null → finish the canonical's consumption + clear availability; non-null → consume exactly the named file, preserve a surviving canonical and its availability; resolve LAST, idempotent replay | `a committed CANONICAL-sourced rollback consumes the canonical — the same undo is never offered again`; `a committed-rollback finalization failure keeps the journal — the replay is idempotent` (+ the explicit-source pair, mandated tests 3–4, green-by-design on this base) |
| 2 — a requested rollback's successful retry committed as anonymous compensation: canonical consumed, real journal left `Prepared`/Rollback, next preflight → unresolvable → Fatal | the retry commits through the ORIGINAL effects — `Committed` recorded before the exact-source consumption, failures surfaced (never a clean `Completed`), same bounded ladder, outcome `Completed`; anonymous commit retained ONLY for the compensating rollback of a failed Restore | `a requested rollback's successful retry COMMITS as the requested operation…` (composed, journal-aware production-shaped preflight: two swaps, no third, preflight observes `Committed`/Rollback, terminal effects exactly once, successor publishes); `…whose commit record FAILS keeps the asset and reports no clean Completed` |
| 3 — the inline rollback closed the candidate database before VM-clear/join | inline invalidation routes through THE candidate teardown protocol (suspend disposal callback → `tearDownCandidate(candidate, close = true)`), swap only after; `candidateDisposed` prevents a second teardown; a failed clear/join/close is Fatal with zero renames after | `the INLINE rollback disposes the candidate through the ONE teardown protocol before the swap` (order: VM clear / job `finally` → close → swap; exact source applied and consumed); `an UNJOINABLE candidate stops the inline rollback FATAL — zero renames after admission` |
| liveness | — (the R4 `clearStoreBounded` stands; the missing DISCRIMINATING pin is added) | `a clear that is ACCEPTED but never RUN cannot hang the machine — Fatal within the drain budget` (a queue-only dispatcher + advanceable scheduler; the executed-and-reverted unbounded mutant hangs it) |

`AppRuntime` re-crossed the LargeClass ceiling from these fixes; `InFlightReplacement`,
`GraphOnlyTransition` and `releasePartialGeneration` moved to `ReplacementMechanics`,
`DerivedStateFlow` to `RuntimeGeneration.kt` — no suppression added.

Truth corrections in this pass: the round-4 red-on-base evidence is **21 failures total (20
named tests + 1 parameterized invocation)**; the §23.2 matrix is explicitly a COMPOSED proof
(no literal two-launch process-death integration test exists); §23.4's null-source residual
covers any `Prepared` attempt (legacy, interrupted canonical-sourced rollback, torn write);
and the pre-R4.1 claim that a committed rollback's source identity "cannot be known" is
removed — `rollbackSnapshotPath` is the durable discriminator, and §8.5b now says so.

## 25. R4.2 final correction record (2026-08-23)

Two commits on `83793531`: `f2d3c81a` (blocker A: durable-phase-aware terminal dispatch;
blocker B: the suppression removal; the liveness-truth rewrite) and the docs commit carrying
this section. §8.4's terminal-selection paragraph and §8.5a's durable-commit-ordering paragraph
were rewritten IN PLACE.

### 25.1 Blocker A — pre-durable failures are never committed terminals

`commitMutation` now returns the explicit protocol phase — `CommitResult.Durable |
NotDurable` — and every one of its four call sites maps `NotDurable` to a NON-committed
continuation: `runRestartProcessSwap` → `FailedAfterMutation` (journal `Prepared`, reservation
retained); `executeRebuildTransaction` → the bounded recovery ladder (a restore rolls back onto
its kept reservation, deterministic `RecoveredByRollback`; a requested rollback retries source
+ record once); `recoverViaRollback` → Fatal on a persistent record failure, everything
preserved; `runInlineRollback` → `FailedAfterMutation` (feedback allowed, no resolve, no
clear). Pre-R4.2, `Completed(effectsError)` + the unconditional `onCommitted` dispatch let the
production committed effects resolve the still-`Prepared` attempt, clear availability, publish
success and acknowledge — erasing the state conservative recovery needs. The one remaining
origin of `Completed.effectsError` is a failure OF the terminal `onCommitted` callback after a
DURABLE commit.

Production-shaped pins, seven of them RED at `83793531` (`r42-red-on-base-runtime.xml`):
persistent-record retry with the REAL committed shape (resolve/clear/publish/ack — never
invoked; `Prepared` + source + availability survive; bounded Fatal); one-shot record failure on
a requested rollback (exactly ONE committed terminal — the base dispatched a stale SECOND from
the failed first record); promotion failure under both policies; inline record failure (cannot
erase the `Prepared` journal); the focused durable-phase-selects-terminal pin; and the two
rewritten legacy pins. The one-shot RESTORE proof was initially green-on-base; the R4.2
adversarial review's one confirmed finding was that a targeted mutant (gating the NotDurable
divert on `requestedRollback`) survived it, so it was STRENGTHENED with the two missing axes —
the recovery swap must precede the first candidate preflight (no candidate is ever built over
the unprovable file), and the recovered outcome carries no stale pre-durable `effectsError` —
after which it too is red on base: all EIGHT durable-phase pins fail at `83793531`.
KNOWN-NEGATIVE R4.2-KN (the old dispatch restored on the retry path) kills the
production-shaped pin (`known-negative-r42-predurable-dispatch.xml`); reverted, empty diff.

### 25.2 Blocker B — the zero-suppression claim is true again

`39e9155c` had added one `@Suppress("UNCHECKED_CAST")` in `AppRuntimeReplacementTest`. It is
removed via a typed `ProbeViewModelFactory` (`Class.cast`), the pre-existing sibling factory
converted with it, and the claim is verified against the FULL correction range:
`git diff -U0 936ab699..HEAD -- '*.kt' | rg '^\+\s*@Suppress'` returns nothing.

### 25.3 Liveness truth

The wedged-main pin now uses a REAL queueing dispatcher (runnables retained, never executed)
and, after the Fatal verdict, EXECUTES the abandoned clear — proving the documented residual:
the late clear runs and changes nothing (still Fatal, no publication, epoch unchanged,
admission retired). The unbounded-clear mutant was re-executed against this exact test — it
fails it at the deferred-completion assertion ("the submitted deferred must complete";
`known-negative-r42-unbounded-clear-queued.xml`) — and reverted.
Every prior "queue-only" phrasing now describes what the test literally does.

### 25.4 Adversarial review outcome and residual

The mandated fresh-context review (4 hunts: durable phase vs terminal dispatch; every
`commitMutation` call site; production caller effects and outcome mappings; test vacuity and
evidence truth) found **no invariant counterexample** in the shipped protocol. Its one
confirmed finding was test vacuity: the one-shot RESTORE proof survived a targeted
divert-gating mutant — fixed by strengthening the pin (§25.1), after which all eight
durable-phase pins are red on base. Two harness-fidelity nits (the fake journal resolves
before its markers where production resolves last; its `onRecoveredByRollback` resolves
unconditionally) are non-load-bearing for what the pins assert and are noted here rather than
churned.

**Named residual (outside the locked invariant, on record) — CLOSED in R5 (§26).** A mid-copy
promotion failure could leave the CANONICAL slot partially written, and rollback sources were
applied without re-validating the SQLite magic — so a later user undo of a PREVIOUS attempt could
swap in a truncated canonical. The bound stated here, "never a false success", was WRONG: the
undo reported `Succeeded` and then deleted the last remaining copy. R5 closes both halves — the
canonical is only ever written by an atomic rename of a fully staged file, and every rollback
source is validated for magic and page-count completeness before the point of no return.

## 26. R5 correction record (2026-08-24) — the PR #252 review findings

This is a historical correction record. §27 supersedes its positional undo, cache and
policy-specific finalization mechanics; the findings remain here to preserve the review trail.

An independent six-lens review of the branch (`phase5-review.md`, since consumed) raised 19
findings and confirmed 6 after two-verifier adversarial refutation, plus two unverified notes.
All eight are closed here. §8.5a, §8.5b, §8.7, §9, §11.3, §23.2 and §23.4 were rewritten IN
PLACE; this section is the record.

| # | Finding | Mechanism | Pin |
|---|---|---|---|
| 1 `major` | the reservation promotion overwrote the canonical undo slot BEFORE the durable `Committed` record, so an attempt that never commits destroyed the previous restore's undo image while its availability flag and date still advertised it | two-phase promotion: stage (attempt-named, canonical untouched) → record → install (atomic rename); a pre-durable failure discards the staging; the committed cold-start branch completes an OWED install idempotently before anything reads the canonical, and a restore whose install cannot complete claims no undo (`previousVersionAvailable = false`, no availability mark) | `the undo slot is INSTALLED only after the durable commit record`; `a record failure leaves the PREVIOUS undo slot byte-for-byte intact`; `an install failure after the durable record is a COMMIT, not a rollback`; `staging a promotion never touches the canonical slot`; `a COMMITTED restore whose promotion cannot complete never claims a fresh undo`; `a COMMITTED attempt whose promotion is still owed recovers from its RESERVATION` |
| 2 `major` | the generation-owned `ViewModelStore` had no production clear, so every feature Store, NavEntry store and paging cache outlived `MainActivity` — and the NavEntry decorator's deterministic `parentKey` resurrected them with stale state on the next launch | `UiHostLifecycleTracker` registered by `BaseApplication`: clears the serving generation's store on a permanent Activity destroy (`!isChangingConfigurations`, no other live host), by identity rather than by a counter | `UiHostLifecycleTest` (5 pins: recreate, last destroy, two-host veto, unmatched detach, and the tracker reading the Activity's own flag) |
| 3 `major` | Scenario-1 recovery routed into the Scenario-2 surface: "App update needed", a Play Store button that helps nothing, and a startup-format diagnostic with none of the restore context the journal carries | `RecoveryScenario` stamped on the launching Intent (it rides the task record; a cached verdict does not survive process death), scenario-specific copy in `values`/`values-ru`, no Update-app button on the restore route, and the export routed to the shared `RestoreDiagnosticsExport` — the one Scenario-1 call, now used by both the dialog reactor and the surface | `RecoveryActivityDbFreeTest` (extended `warmDeps`); the enum's exhaustive `when`s make a third scenario a compile error |
| 4 `major` | the PR body claimed "`MainActivity` untouched; cold-start order identical" while `onCreate` had gained a second routing predicate and a second `appGraph` dereference | PR body corrected: the hunk quoted, the branch named as the one every Scenario-1 recovery launch takes, and the two new cold-start stages stated | — (documentation) |
| 5 `minor` | `recoverFrom` drove EVERY `Prepared` attempt through the scenario-1 effects, so a crash-interrupted user undo was re-driven to a correct data outcome and then reported as "Restore failed" | `RestoreAttempt.RollbackOrigin` journalled in the claim, carried through the re-claim, cleared on resolve; `beginAttempt` refuses an origin-less rollback; absent/unparsable reads as `ScenarioOneRecovery` | `an interrupted USER UNDO replays as UndoRestoreSuccess, never a restore failure`; `an interrupted SCENARIO-1 recovery still replays as RestoreFailure`; `a Rollback attempt with an UNKNOWN origin replays as a recovery failure`; `the recovery rollback CARRIES the journal's origin through its re-claim`; `a failed undo replay publishes nothing and keeps its retry assets`; `a COMMITTED user undo replays its UndoRestoreSuccess dialog`; plus five wire-format pins in `RestoreStateRepositoryImplTest` |
| 6 `minor` | a rollback applied its source unvalidated, so a canonical left partially written by a crashed promotion was renamed over the live database and reported `Succeeded` — the outcome §25.4 excluded | `validateRollbackSource` (magic + in-header page count vs file length) on both the journal-named and the canonical source, and on the restore direction too; a `CorruptedBackup` rejection is pre-PONR and KEEPS the file; a user undo maps it to `UndoRestoreOutcome.SourceUnusable` | `a rollback whose source fails validation is a typed rejection - nothing is swapped`; `a TRUNCATED rollback source fails validation` + its anti-vacuity partner; `an undo whose source is unusable is acknowledged, not retried forever` + `an IO rejection still keeps the undo offer for a retry` |
| A | worker admission was never closed on either terminal-recovery route, so a persisted `BackupWorker` could bind a lease over a database the app had just declared unprovable — upload it, record a false success, and rotate one of the user's three Drive backups away | `WorkerAdmissionGate.seal()`: a terminal refusal distinct from the reversible transition barrier, driven ONLY from `coldStart`'s `RouteToRecovery` verdict (never from a candidate preflight, whose abort leaves a healthy generation serving); `awaitBackupWorkLease` answers `null` and `BackupWorker` returns `failure()` BEFORE `setLastAttempt` | `a terminal recovery route SEALS worker admission - both scenarios`; `an ordinary launch never seals worker admission`; `the CANDIDATE preflight never seals`; `a sealed admission refuses BEFORE any bookkeeping or upload` |
| B | the Scenario-2 peek was gated on `== NoOp`, so the new `RecoveryCompleted` value fell through and the one launch whose live file a rollback replaced OUT OF PROCESS skipped the peek — and, when the finalization write fails, skipped it forever | one exhaustive `when` over `PreflightOutcome` → `PostPreflightStep`, driven by both entry points: skip the peek only when THIS launch already proved the file openable. `RecoveryCompleted` now peeks | `RecoveryCompleted peeks the live schema - the rollback replaced the file out of process`; `a RecoveryCompleted launch over an unopenable file routes to recovery`; `suspend preflight - RecoveryCompleted peeks the live schema too` |

Also corrected in this pass: the diagnostics export's "(no in-progress context — flag was set but
payload missing)" line, which asserted a flag that no longer exists; `RestoreDialogChoiceObserver`
lost its duplicate `PackageManager` read and with it one `@Suppress("DEPRECATION")`, so the
zero-added-suppressions claim is strengthened rather than merely preserved; and
`selectOperationSource` moved to `ReplacementMechanics` to keep `AppRuntime` under `LargeClass`.

**Also closed on the branch in `205bcbf88`, from the PR #252 review threads rather than the
six-lens review** — recorded here in the merge-hygiene pass, having lived only in the commit
message until then:

- **the fenced-refusal requeue spun a cancelled collector.** `resolveSnackbarOutcomeOrRequeue`
  requeued the model and returned with no suspension point on the refusal branch, so a collector
  cancelled while `fenceResolves` was up received the same buffered model again before it could
  observe cancellation — the channel fast path returns a buffered element without a cancellation
  check. The fix is a `yield()` on that branch, deliberately not a park: callers invoke the
  function directly and rely on it returning. Pin: `a cancelled collector terminates on the
  fenced-refusal path` in `SnackbarManagerTest`, on REAL dispatchers because virtual time cannot
  observe a busy loop; it is red with the `yield()` removed. `fenceResolves`, `unfenceResolves` and
  `advanceGenerationEpoch` also became opt-in-only behind `@SnackbarGenerationTransition`. The
  finding was filed as unreachable in Android production on the grounds that
  `fenceSnackbarResolves` had no production call site; that is not the state of the tree — it is
  wired at `BaseApplication` into `RuntimeTransitionPolicy` and invoked by
  `GenerationQuiescer.quiesce`, which the production `RestartProcess` path runs before its swap.
- **the androidTest harness manufactured admission grants.**
  `MetroTestGraphHolder.admitUiGeneration` read
  `runtimeDelegate?.admitUiGeneration(id) ?: StaticToken(id)`, so a real gate's REFUSAL (null) was
  indistinguishable from "no delegate installed" and became a grant: mutating
  `UiAdmissionGate.admit` to always refuse left `AppRuntimeUiHandshakeDeviceTest` — the branch's
  one test that puts a real runtime behind the real shell — green, while the same mutant blanks
  the app in production. The delegate's answer is now returned verbatim, refusal included, and
  the static fallback with its `staticAttachments` bookkeeping is reached only when no delegate is
  installed, so `outstandingAdmissions` no longer counts a grant the real gate never issued.

**Not re-measured at this head:** the forced full host battery and the device Regression/Smoke
suites. What ran green here is `detekt`, the full `testDebugUnitTest` suite and
`assembleDebugAndroidTest`. The instrumented suites do not gate PRs (weekly), and the §18 battery
counts predate seven commits; both are stated as superseded in the PR body rather than reasserted.

## 27. Immutable restore ownership correction (2026-08-25)

This round replaces the positional C/R/S protocol recorded in §§22–26. It is a bounded
restore/undo durability correction; it does not redesign the general runtime-generation machine.
The sections below are normative. Evidence named here is an obligation until the final forced host,
device, iOS and mutation runs are recorded on the pushed SHA.

### 27.1 State, storage and installation ownership

The authoritative state is the §8.5a `RestoreProtocolState` envelope. Every new-format attempt,
active pointer and terminal outbox carries the installation epoch in addition to the envelope's
epoch. The stable random epoch is atomically published in
`noBackupFilesDir/restore-recovery`; process-like reconstruction reads the same token.

Before reading any attempt, pointer, availability, outbox or persisted source ref:

- epoch match permits owner/ref decoding;
- epoch mismatch atomically clears all foreign protocol records, runs no owner callback, follows
  no stored path, deletes no same-named local file and leaves the live database byte-identical;
- same epoch plus a missing ref remains genuine local recovery failure;
- missing protocol epoch invokes the explicit legacy table below, never a blanket foreign-state
  rule.

The no-backup root owns the epoch, immutable undo files, runtime-staged restore sources, durable
raw recovery export and protocol partials. A caller download may begin in cache, but ownership
transfer must finish before suspension, journal claim or PONR. Root creation/write failure rejects
before mutation. Sharing copies the durable export into a narrow cache share directory only on an
explicit request; the durable root is never broadly exposed through `FileProvider`.

Immutable epoch, undo and staged-source publication uses a unique same-directory
`<final>.<nonce>.creating` file and a complete synced copy. The permanent `.publication.lock`
serializes app processes across the no-follow final-name absence check and same-directory atomic
move. Unique partials prevent two processes from writing the same publication inode; a final name
already owned by another process is a failure, not an overwrite. Publication returns only after
syncing the complete file and the parent directory entry. Mutable export/share/live-DB
replacement similarly syncs its complete temporary and the containing directory around atomic
rename. Live replacement treats failure to remove either stale `-wal` or `-shm` sidecar as a typed
pre-publication failure, leaving the old main file intact; publishing under an unremoved sidecar
would make the installed generation unprovable. A readable same-process filename after a
directory-sync failure is not durable evidence.

Static Android backup policy independently excludes
`datastore/restore_state_prefs.preferences_pb` from legacy rules and API-31+ cloud-backup and
device-transfer rules. This correction does not disable Android backup and does not change the
Room database backup policy; that remains a separate maintainer/product decision.

### 27.2 Explicit released-state rollout table

Released installs have `restore_in_progress`, its context,
`pre_restore_backup_available`, its original date and optional
`cache/pre_restore_backup.db`. Each boundary is replay-safe: publish a complete immutable copy,
persist its owner/ref state, then consume the released file only after the new state is durable.

| Released state | New state / action |
|---|---|
| in-progress + valid released file | copy to the immutable ref of the stable synthetic interrupted-restore owner; persist that one `Restore/Prepared` attempt before deleting the released file; ignore stale availability as a second pointer |
| in-progress + released file missing/unusable + healthy compatible live DB | preserve the synthetic owned attempt and route to integrity-gated `InterruptedRestore`; never classify it as foreign |
| in-progress + invalid live DB + valid released file | recover from the migrated exact immutable ref |
| in-progress + neither live nor released file usable | route to `RecoveryRequired` |
| no in-progress + availability/date + valid released file | migrate to the stable synthetic active-undo ref and preserve the original date |
| no in-progress + availability + missing/unusable released file | clear stale released availability |
| no released markers | install the epoch envelope and continue normal preflight |

Synthetic legacy owners are stable wire-format values, distinct for an interrupted attempt and an
active pointer. A replay after copy, state write or released-file deletion must converge on the
same descriptor and never create a second undo interpretation. When the immutable final already
exists, replay explicitly syncs that file and the recovery-root directory before trusting it. A
copy or sync failure installs no new state and does not delete C, even if the unsynced final is
readable in the current process.

### 27.3 Restore transaction and finalization

For restore N while P is the previously active undo:

```text
validate and no-backup-stage source N
→ capacity admission
→ atomically publish immutable undo N
→ reversibly quiesce UI and DB-bound work
→ persist Restore/Prepared(N), active=P
→ swap live database
→ persist Restore/Committed(N), active=P
→ verify the new generation
→ atomically write active=N-or-null + terminal outbox and remove N attempt
→ publish outbox; owner-check acknowledgement as replay cleanup
→ sweep unreferenced P and debris
```

UI and DB-bound admission stay closed while `Committed(N), active=P` exists. Once the restore is
visible, P is never advertised as undo of N. If the new live database verifies and N is missing,
the restore is proven but undo is unavailable; finalization atomically writes no active pointer
and `previousVersionAvailable=false`. A verified committed N also replaces a missing/unusable old
P; P is not a prerequisite for activating exact N.

The Prepared claim occurs only after reversible quiescence succeeds. The claim attempt itself is
the PONR boundary: the tracker crosses before the owner write because DataStore may persist and
then throw. Therefore validation, capacity and quiesce rejection resume the old generation with
no journal claim, while any ambiguous claim result seals the runtime. Under `RestartProcess`, the
runtime owns the recovery restart. It seals restart-terminal admission before releasing the
transition mutex, rejects queued transactions, and reports a failed restart callback as `Fatal`.

Cold-start preflight and in-process candidate preflight use the same finalizer. Pointer activation
does not live in a restart or rebuild callback. Before the atomic finalization transition, a write
failure preserves `Committed(N)`, P and N and reports neither clean success nor
`RecoveryCompleted`. A candidate cannot publish while finalization is pending. After the atomic
transition, terminal publication is replayed from the outbox; acknowledgement follows successful
publication. Failure to write the terminal into the app-dialog DataStore is still
`FinalizationPending`: cold start routes to recovery and seals worker admission before chores,
while `RebuildInProcess` arms the candidate first and refuses candidate publication if the handoff
then fails. A graph-only transition terminalizes instead of republishing the outgoing generation.
Once the app-dialog write succeeds, failure to acknowledge the restore-state outbox is replay
cleanup rather than an unsurfaced terminal: deduplicated publication is already durable. A success
terminal can never be followed by compensation in the same transaction.

For `RebuildInProcess`, the atomic finalizer leaves a newly written success outbox pending until
the candidate completes all fallible arming. If arming escapes after finalization, the runtime
rereads epoch-reconciled state and accepts only the exact proof `attempt=null`, success owner=N,
and `activeUndo=N-or-null` consistent with the terminal payload. It then releases the failed
candidate and retries arming once over the restored live database; it never compensates N. A
missing, mismatched or unreadable proof is terminally unprovable, not permission to roll back.

### 27.4 Exact rollback ownership

Every rollback applies one `UndoRef`:

- user undo selects the current `activeUndo.ref` and journals a fresh rollback owner with
  `UserUndo` origin;
- restore compensation selects the failed restore's immutable ref and atomically replaces that
  restore descriptor with a fresh rollback owner carrying `ScenarioOneRecovery`;
- committed bookkeeping records that exact rollback owner and ref;
- finalization executes `ClearIf(appliedRef)`; user undo clears its own pointer, while compensation
  N cannot clear unrelated P;
- source deletion happens after durable finalization and is best-effort; a committed rollback can
  finalize from descriptor identity when the file is already absent.

An operation never selects a source from `null`, a slot position or an arbitrary path. Runtime
owner creation and callbacks are one-to-one. Production has no mutation default and cannot create
`undo_no-effects.db`. Concurrent callers serialize as separate transactions even when their source
bytes match.

### 27.5 Owner-aware garbage collection

Sweeping runs under startup/transition serialization and derives its protection set from
epoch-reconciled state. Preserve:

- the install token;
- refs held by any unresolved restore or rollback;
- active undo;
- staged source and other assets needed by finalization or a pending outbox.

Delete only strict protocol-owned filename patterns under the dedicated root. Eligible garbage
includes orphan legacy `<final>.creating` files, orphan unique
`<final>.<nonce>.creating` files, unowned staged sources and obsolete undo files. Never follow a
persisted path or delete outside the root. A failed delete remains retryable garbage; it never
changes attempt, pointer or outbox truth.

### 27.6 Advisory capacity gate

Use injectable `StorageManager.getAllocatableBytes()`; do not reserve with `allocateBytes()`.
Run the gate after source validation and no-backup ownership transfer, but before immutable undo
creation, journal claim, generation teardown, database close or live swap.

- restore requirement: post-checkpoint live size for immutable N + staged-source size for the live
  `${AppDatabase.NAME}.tmp` + explicit margin;
- rollback requirement: exact source size for live `.tmp` + margin;
- calculate overflow-safely; available bytes equal to required bytes pass;
- insufficient bytes, overflow or query failure returns a typed pre-mutation rejection with the
  serving generation unchanged and zero protocol mutation.

The check is advisory. Copy, checkpoint and rename paths still handle ENOSPC conservatively. The
immutable protocol reduces repeated full-file writes and crash states; it does not reduce the
approximately five-file successful restore peak to four.

### 27.7 Recovery surface, Continue and export truth

`RecoveryActivity` remains DB-free during launch and composition. It opens neither Room nor
framework SQLite until the user explicitly requests Continue for `InterruptedRestore`.
Continue is unavailable for startup-migration recovery.

The Continue sequence is:

1. User explicitly requests Continue.
2. On IO, open the live file directly with framework `SQLiteDatabase`.
3. Consume every `PRAGMA integrity_check` row and accept only a healthy `ok` result.
4. Read `user_version` and require the current schema or a supported migration path.
5. Show a second explicit confirmation stating that the app cannot determine whether the restore
   completed.
6. After confirmation, atomically abandon only the owned interrupted attempt and clear pointer,
   outbox and protocol state that can no longer truthfully describe the accepted live DB.
7. Restart last; worker admission stays sealed until restart.

Export success is not a prerequisite for Continue because ENOSPC is a primary recovery scenario.
Export UI state is typed as `Available`, `Unavailable(reason)` or `Failed(reason)`; copy,
checkpoint and capacity errors are visible rather than silent. Recovery copy must not claim that
nothing was deleted or preserved when that cannot be proven, in either English or Russian.

### 27.8 SQLite header and integrity proof boundaries

`SqliteHeaderCheck` is a structural preflight, not an integrity proof. Characterization must use
real Workeeper snapshots and execute all combinations required to distinguish:

- matching change counter and version-valid-for;
- mismatched counters under SQLite file-format semantics;
- page count zero;
- tail truncation under each counter/page-count case.

The validator documentation must state exactly which header and length relationships it proves.
Negative coverage is required before changing mismatch behavior. Continue always requires the
full framework-SQLite integrity check described in §27.7.

WAL-backed copies have a separate proof boundary: both Room and framework-SQLite checkpoint paths
consume the actual `PRAGMA wal_checkpoint(TRUNCATE)` result row and require `busy == 0` plus
`logFrames == checkpointedFrames`. A missing row or busy/incomplete checkpoint fails before an
undo/export filename can be published.

### 27.9 Verification and mutation obligations

No new evidence result is asserted by this section. Final evidence must include real files and
persisted DataStore where practical, exact host/device testcase and Gradle task counts, Paparazzi
mover count, final SHA and remaining unverified boundaries. Required behavioral coverage includes:

- cache deletion versus authoritative recovery assets;
- disjoint install roots and backup-only transfer, same-epoch missing refs, epoch stability and
  epoch mismatch despite a same-named local file;
- every §27.2 rollout row and replay boundary;
- pointer activation under both replacement policies through the shared finalizer;
- injected finalization writes, exact N-versus-P rollback discrimination and GC retry behavior;
- capacity equality, one-byte-short, overflow, query exception and post-admission ENOSPC;
- real healthy/corrupt SQLite, visible export failure and two-confirmation Continue;
- all three backup XML exclusions, absence of a production constant owner and concurrent-owner
  callback isolation.

Behavior pins compatible with the reviewed starting SHA must be shown red there. New-API-only
claims require discriminating mutants through `documentation/mockups/mutation_harness.py`, not a
compile failure. Required mutants redirect the recovery root to cache, remove epoch comparison or
ignore a missing ref, omit rebuild finalization, resolve without the correct pointer transition,
delete an attempt-owned file during sweep, bypass capacity, ignore integrity failure, and allow
the constant `"no-effects"` owner. Raw JUnit XML is committed only after those runs execute with
non-zero inputs.

### 27.10 Separate unimplemented follow-ups

These remain outside this bounded correction and must not be inferred as completed:

- encode `AppRuntime` phases and admissible operations as compile-time typestate;
- replace any process-lifetime strong Activity ownership with audited weak-reference handling;
- decide whether the requested-rollback record ladder's two-attempt bound is final. A requested
  rollback whose durable record fails ONCE already heals: the recovery re-applies the same exact
  `UndoRef` — idempotent, since the immutable undo file is copied and not consumed — re-runs the
  record, and commits clean (`one-shot requested rollback record failure commits once without stale
  terminal`). A SECOND consecutive record failure is the terminal `Fatal`, with the journal, the
  owner and the undo bytes preserved (`a requested rollback whose record persistently fails ends
  FATAL - every asset preserved`). Whether two attempts is the right bound is a protocol question
  about when a requested rollback goes terminal, not a local edit; `RebuildInProcess` only, since
  production is `RestartProcess`, whose swap path carries no rollback ladder at all;
- define and prove multi-instance `ViewModelStore` ownership rather than extending the current
  single-host lifecycle correction.

### 27.11 Post-review terminal-publication closure

The exact-SHA review of `d869e11393c5741c404e9e62936dd581815cb96a` found that the owner/pointer
transition was durable but `AppDialogPublisher.publish` failure was swallowed. Cold startup could
therefore arm workers and UI after an unpublished restore terminal, and a committed rollback could
return `RecoveryCompleted` without surfacing its terminal. The finding was classified
`correct-and-new` before the fix was pushed.

The corrected gate distinguishes the two sides of the cross-DataStore tear:

- failure of the app-dialog write keeps the outbox pending, returns `FinalizationPending`, and
  refuses UI/worker admission;
- failure of restore-state acknowledgement after a successful app-dialog write is replayable
  cleanup and does not revoke the already-durable terminal;
- cold restore success publishes before chores; an in-process candidate arms before publishing so
  the observer exists, then remains unpublished if the handoff fails;
- graph-only `FinalizationPending` is Fatal and cannot republish the outgoing generation.

Behavioral RED, the executed-and-restored post-arming bypass mutant, and focused green XMLs are
recorded under `kmp-phase-5-evidence/` and indexed by its README.
