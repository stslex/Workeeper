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
swappable indirection, unrelated bug fixes (including the inert `SupervisorJob()` in
`AppCoroutineScopeImpl.kt:29` — §15), golden re-recording, new suppressions/baselines.

## 2. Measured startup-stage matrix

`BaseApplication` is abstract; manifest apps are `DevMobileApp`/`StoreMobileApp`. All stages run
synchronously on the main thread unless noted. Graph construction happens inside stage 5a — the
first `appGraph` read forces the `by lazy` build (`BaseApplication.kt:81-87`), which also forces
the lazy cold `buildAppDatabase` (`:66`, opens no SQLite file — `AppDatabaseFactory.kt:30-34`).

| # | Stage | Sync/async | Dispatcher | Graph | DB | Predecessor / deadline | Owner & cancellation | Failure policy | Test seam |
|---|-------|-----------|-----------|-------|----|------------------------|----------------------|----------------|-----------|
| 1 | `super.onCreate` + Crashlytics init + `Log.isLogging` + trace flag (`BaseApplication.kt:119-122`) | sync | main | no | no | first | n/a | crashlytics null-guarded | none needed |
| 2 | `handleRecoveryPreflightChain()` (`:158-178`) — Scenario 1 then Scenario 2 | **sync, `runBlocking` ×2** (`:159,:173`) | main blocks; inner hops to IO (`DatabaseSnapshotProviderImpl.kt:40,57,65`) | **first graph read** | S1: one DataStore read; SQLite open only if `restore_in_progress`. S2: Room-free framework-SQLite peek | must complete before any Activity (async would flash MainActivity — KDoc `:155-157`) | `runBlocking` — uncancellable by design | S1 peek `runCatching`→rollback→`restartApp()` (process exit, `:162-165`); S2 failure → `RouteToRecovery` decision, not a crash | `TestApplication.onCreateGraphBootstrap` no-op (`harness/TestApplication.kt:32-34`) |
| 3 | `cleanupOrphanedImageTempFiles()` (`:180-187`) | fire-and-forget launch | **anonymous** `CoroutineScope(SupervisorJob()+Dispatchers.IO)` (`:184`) | reads `appGraph.imageStorage` | no | after preflight; no deadline | **UNOWNED — never cancelled** | none at launch site; body deletes `files/images/temp/*` (`ImageStorageImpl.kt:93-97`) | same no-op seam |
| 4 | `warmQueryPlanner()` (`:222-229`) | 2 sync guards, then fire-and-forget | **anonymous** `CoroutineScope(SupervisorJob()+Dispatchers.IO)` (`:225`) | reads `lastDecision` | **yes — `ANALYZE` opens the DB** (`QueryPlannerStatistics.kt:37-41`) | strictly after preflight (decision cached); never on `RouteToRecovery` (`:223`); skipped on `isLowRamDevice` (`:224`); no reader waits on it | **UNOWNED — never cancelled** | `runCatching` + `Log.e` (`:226-227`) | same |
| 5 | `bootstrapAppDialogObserver()` (`:237-239`) — eager `appGraph.recoveryBootstrap` read | sync construction; collector async | observer's own `CoroutineScope(SupervisorJob()+@DefaultDispatcher)` (`RestoreDialogChoiceObserver.kt:91`) | yes | no | **must subscribe before first `MainActivity.onCreate`** — choice bus is `MutableSharedFlow(replay=0)`, pre-subscription emits lost (`AppDialogObserverImpl.kt:60-64`) | scope owned by the `@SingleIn(AppScope)` singleton — **never cancelled** | per-choice `runCatching` (`:100-103`) | same |
| 6 | `PerformanceMetricsRecorder.process(AppCreated)` (`:124`) | sync | main | no | no | last in `onCreate` | n/a | — | none |
| 7 | `MainActivity.onCreate`: S2 routing via cached `lastDecision` → direct `Intent(RecoveryActivity)`+`finish()` (`MainActivity.kt:50-57`); else metrics + `activityProducer.produce(this)` + `setContent { App() }` (`:59-64`) | sync | main | via `get()` accessors — **no capture** (`:23-25`) | no | after stage 2's cached decision | Activity | — | instrumented tests use the same activity |

Measured edge (recorded, behavior preserved): on the Scenario-1 `RestoreSucceeded` path Scenario 2
never runs, so `lastDecision == null` and stage 4's guard passes — the `ANALYZE` is then the first
open of the freshly-restored file, and any pending restore migration runs on that IO coroutine
inside `runCatching` (`BaseApplication.kt:166-171` + `:223`).

WorkManager: default initializer removed in manifest (`AndroidManifest.xml:72-81`); on-demand init
via `Configuration.Provider` building `MetroWorkerFactory(this)` per read (`BaseApplication.kt:113-116`);
the factory `by lazy`-captures `BackupWorkerDeps` (= the graph) for its own lifetime
(`MetroWorkerFactory.kt:28-30`) — a production graph **capture** (§4).

## 3. Lifetime matrix

| Object | Classification (R2) | Evidence / consequence |
|---|---|---|
| `AppDatabase` | **DB-generation** — same object across graph-only handovers; a NEW object after every file-swap replacement | the only `close()` sites are the restore/rollback swaps (`DatabaseSnapshotProviderImpl.kt:104,196`); §7.1 measured close() terminal. Under R2 the runtime owns close+swap and mints the next DB generation via the full production factory (`buildAppDatabase`). |
| `ImageStorage` | **process** — preserve | stateless dir wrapper, `create()` root (`BaseApplication.kt:85`); reused across generations. |
| DataStore instances (5 files: `common_prefs`, `backup_scheduling_prefs`, `restore_state_prefs`, `app_dialogs_prefs`, `backup_account_prefs`) | **process** — preserve | `DataStoreProvider`'s companion CAS memo is explicitly process-lifetime (`DataStoreProvider.kt:32-66`); pinned by `AppScopeDataStoreSingletonTest`. |
| Dialog state (`pending_*` flags) | **process (persisted)** — preserve | `AppDialogRepository` derives state from DataStore on every read (`AppDialogRepository.kt:26-31,61-63`). Survives replacement; exactly-once consumption via dismiss-after acknowledge. |
| `AppReinitializer`, Firebase holders, `PerformanceMetricsRecorder` | **process** | stateless / static singletons. |
| `AppGraph` + every `@SingleIn(AppScope)` binding (61 sites — scope-sweep) | **runtime-generation** — rebuild & dispose as one unit with the DB it binds | includes `NavigatorEventBus`, `AppDialogObserverImpl`, the 9 repositories, DAO providers (derive from the generation's DB root), gd auth chain, coordinators, `DatabaseSnapshotProviderImpl` (holds the generation's `AppDatabase`). |
| `RestoreDialogChoiceObserver` (+ collector), `DriveBackupAuth.authScope` (+ collector), `SnapshotExportRunnerImpl.scope`, both startup chores | **runtime-generation, lifetime-owned** | today process-lifetime, never cancelled (`RestoreDialogChoiceObserver.kt:91-97`, `DriveBackupAuth.kt:70-80`, `SnapshotExportRunnerImpl.kt:59`, `BaseApplication.kt:184,225`); §8.2 makes them children of the generation's `AppScopeLifetime`, cancelled-and-joined during Quiescing. |
| `MetroWorkerFactory` captured deps | **RESOLVED — the factory captures nothing** | §8.6: construction is dependency-free; a run binds deps+lease atomically at `doWork`'s first operation. |
| **In-flight `BackupWorker`** | **runtime-generation live capture** | a RUN (not a constructed worker) holds the six deps bound atomically into its admission lease at `doWork`'s first operation (§8.6); §8.4's Quiescing closes admission and awaits every outstanding lease before PONR. |
| Nav3 back stack, `NavigatorHolder`, per-entry Stores, `AppRootViewModel`, `AppDialogStoreImpl`, `NavigationEventBusSetup` collector | **UI/navigation-generation** — recreate/reset with the runtime generation | back stack `rememberNavBackStack(screenSavedStateConfiguration, Home)` (`App.kt:79-83`); `AppRootViewModel` ctor-captures `commonDataStore`+`navigatorEventBus` (`App.kt:64-70`); `AppDialogStoreImpl` Activity-store-scoped today (`AppDialogFeature.kt:35-46`); command collection is composition-side (`NavigatorExt.kt:32-41`). |
| `ActivityHolderImpl` content | **UI-generation adjacent** | re-registered only on Activity lifecycle events (`MainActivity.kt:60,69`); after a mid-Activity replacement the new generation's holder is empty until the next event — zero production readers today (measured); recorded property. |
| `iosApp` host, iOS DB factory, iOS composition root | **platform-host — Phase 7** | do not exist; nothing here may pretend they do. |

## 4. Graph-reader / capture inventory — including live captures

Five publication seams, all on `BaseApplication` (`:49-56`): `AppGraphOwner` (MainActivity `:23`,
non-capturing `get()`), `AppDepsHolder.appDeps()` (13 feature readers — acquire-and-drop inside
`rememberMetroStoreProcessor` factories), `RecoveryDepsHolder` (RecoveryActivity, activity-scoped),
`BackupWorkerDepsHolder` (**captures** via `by lazy`, `MetroWorkerFactory.kt:28-30`),
`AppRootDepsHolder` (**captures** into `AppRootViewModel`, `App.kt:64-70`).

**Live-capture audit** (objects holding old-generation dependencies while work is in flight —
graph-access-site analysis alone is insufficient):

| Live capture | Held for | Quiesce treatment (§8.4) |
|---|---|---|
| `BackupWorker` in `doWork()` — six lease-bound deps incl. DB-bound provider | the admitted run (lease acquired at the first op, released in the finally) | close admission + await outstanding leases, bounded; timeout → abort pre-PONR |
| `SnapshotExportRunnerImpl` in-flight `runExport()` (DB JSON export, `SnapshotExportRunnerImpl.kt:67-69`) | export duration | lifetime child → `cancelAndJoin` |
| `RestoreDialogChoiceObserver` collector; `DriveBackupAuth` collector | infinite | lifetime children → `cancelAndJoin` (last quiesce step, §8.4) |
| Per-entry Stores / `AppRootViewModel` / `AppDialogStoreImpl` (repos, bus captured at ctor) | entry / generation-VM-store lifetime | UI region disposal + generation `ViewModelStore.clear()` |
| Startup chores (cleanup, `ANALYZE`) | one-shot | lifetime children → `cancelAndJoin` |
| `MetroWorkerFactory` lazy deps | ~~process (defect)~~ RESOLVED | §8.6: the factory captures NOTHING; admission moved to doWork's first operation |
| **Snackbar deferred-commit path** — `AppSnackbarModel`'s `onDismissed` runs under `withContext(NonCancellable)`; the ED11 deferred permanent-delete commits DB work there via a closure capturing gen-N repositories; `SnackbarManager` is a process-level object with an unlimited queue whose requeue path can carry gen-N closures into the gen-N+1 collector (review v2 finding 1) | until the resolve completes / queue drained | Quiescing sub-step: await the in-flight resolve before PONR; queued models are generation-tagged at enqueue — discarded at delivery after a COMMITTED handover (epoch advance), preserved on abort (§8.4 step 3) |
| JVM identity tests + androidTest singleton tests build graphs (`buildAppGraph` — 14 JVM files + `AccountDataStoreSingletonTest.kt:49,54`, `AppScopeDataStoreSingletonTest.kt:89,94`) | test | reach the 4th root via `buildAppGraph`'s defaulted parameter (the default = pre-Phase-5 anonymous-scope behavior) |

## 5. Restore / rollback / undo call paths (measured, pre-R2)

- **Restore** (Settings): `BackupInteractorImpl.restoreLatest` → version gates → `preserveCurrentDb()`
  → `markRestoreInProgress` → download → `restoreFromSnapshot(temp)` = magic check → source-version
  peek → **`appDatabase.close()`** → delete `-wal`/`-shm` → copy → `app.db.tmp` → `renameTo`
  (`DatabaseSnapshotProviderImpl.kt:176-212`) → `NavigatorEventBus.restartApp()` → process exit.
  Verification happens in the **new** process's Scenario 1 (`RestoreRecoveryCoordinator.kt:69-81`).
- **Rollback** (Scenario-1 failure and undo): `rollbackToPreRestoreBackup()` — same swap core from
  `cache/pre_restore_backup.db` + `source.delete()` (`:95-121`). Restore has its own structurally
  identical copy of the swap core.
- **Undo**: `performUndoRestore` three-way outcome, dismiss-after discipline, restart on `Succeeded`
  (`RestoreDialogChoiceObserver.kt:152-175`).
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
`AppReinitializationHost` (§8.8).

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
their self-created scopes: `RestoreDialogChoiceObserver.kt:91` → `lifetime.childScope(default)`;
`DriveBackupAuth.kt:70` and `SnapshotExportRunnerImpl.kt:59` → `lifetime.childScope(io)`; the two
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
}
```

Stages, order-preserving: Scenario 1 → (short-circuits) → Scenario 2 → chores (image cleanup;
planner warm-up guarded by `RouteToRecovery` + injected `isLowRamDevice`) → dialog-observer arming
(eager `recoveryBootstrap` read). Chores launch on the **generation lifetime**, fire-and-forget,
no reader waits.

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

All transitions run under the runtime `Mutex` (single-flight). **Same-operation registration is
ATOMIC under the submission lock: a concurrent request for the SAME operation joins the
in-flight transaction's result; a different operation registers its own transaction, serializes
behind the mutex, and never receives another operation's result. Runtime liveness is RECHECKED
inside the mutex: a transaction queued behind one that reached Fatal performs no validation, no
close, no swap, and no publication — it completes with Fatal.**

**Submission, source ownership, effects.** Callers submit and await; the body runs on the
runtime's never-cancelled host scope. A restore's source file is STAGED into a runtime-owned
copy inside the non-suspending submission frame (ownership transfer — a cancelled caller's
temp-file cleanup can never mutate a file the transaction needs), and the runtime deletes the
staged copy on every terminal outcome. All caller compensation is ONE typed
`DatabaseReplacementEffects` object: `onBeforeMutation` runs inside the mutex after validation
and before anything irreversible; exactly one terminal method
(`onRejectedBeforeMutation` / `onCommitted` / `onRecoveredByRollback` / `onFailedAfterMutation`
/ `onFatal`) runs per transaction, on the transaction's coroutine, for every outcome including
internal escapes. Terminal selection is DURABLE-PHASE-AWARE (R4.2): `onCommitted` is legal only
once the durable `Committed` transition actually landed — a failure that PREVENTED it
(promotion or the record, `CommitResult.NotDurable`) is never dispatched as a committed
terminal, because the production committed effects resolve the still-`Prepared` attempt, clear
availability and acknowledge the initiating action — erasing exactly the state conservative
recovery needs. A failure OF the `onCommitted` callback itself, after a durable commit, is a
different origin: it folds onto `Committed(effectsError)` — never a silently clean commit.

**The point of no return is the START of the first irreversible action** — the outgoing
teardown, the `close()` INVOCATION, or the file mutation — never its completion. Before it,
every failure unwinds to `Serving(genN)` with the generation fully intact. After it, generation
N is never republished.

Two policies select the ending:

- **`RestartProcess` (Android production)** — preserves shipped behavior exactly: no Quiescing
  (process death is the quiescence, as today), the runtime executes close + file replacement and
  marks the published generation terminal; the caller's existing restart flow
  (`AppReinitializer`) follows. Observable production behavior is unchanged, including the brief
  closed-DB window before exit that exists today. **Scoping rules (review v2 condition 5, updated
  round-2): the recovery ladder applies to `RebuildInProcess` ONLY — a production post-mutation
  failure returns `FailedAfterMutation` with every recovery asset preserved and the interrupted
  mutation journaled (§8.5a), no restart, the closed DB fails loud until the user acts; a
  `RestartProcess` transaction is startable from an already-terminal generation (the failure
  re-tap re-runs the idempotent close + rename); and a `RestartProcess` close-throw is
  post-PONR (`FailedAfterMutation`), never a cleanup-safe rejection.**
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
   (R3) — then `lifetime.cancelAndJoin()` (bounded). Store jobs are descendants of that
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
pure file mechanics run (§8.5): delete sidecars → copy source → `.tmp` → atomic rename
(+ consume source for rollback ops). Validation (magic/version reads) ran in Running, through
the still-open DB, against the runtime's STAGED copy.

**BuildingGeneration**: `dbFactory()` — the **complete production factory** (`buildAppDatabase`:
driver + full `MIGRATIONS` chain, cold) — then a fresh `AppScopeLifetime`, fresh `ViewModelStore`,
new graph via `graphFactory(context, newDb, imageStorage, newLifetime, runtime)`. Allocation is
STAGED: a candidate orphaned by a later failure has its jobs cancelled-and-JOINED before its
database closes; a candidate/orphan close that itself throws STOPS the ladder (Fatal, no further
rename). Nothing published yet.

**Preflight**: the candidate's own coordinator verifies (Scenario-1 semantics:
`currentSchemaVersion()` through the candidate — the open IS the verification, migrations run
here) and applies success side-effects (DataStore writes, process-lifetime, exactly-once by flag
semantics).

**Publishing**: single atomic `Serving(genN+1)` write (one immutable value behind both phase
faces); the snackbar epoch advances (commit only); worker admission reopens; UI re-keys onto the
new generation; old saved-state slot dropped (`SaveableStateHolder.removeState(oldId)`); startup
chores + observer arming run on the new lifetime.

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

- Its swap methods (`restoreFromSnapshot`, `rollbackToPreRestoreBackup`) are decomposed:
  validation stays (`verifySqliteMagic`, version peeks, existence checks — read-only), and the
  pure file mechanics move to a method that performs sidecar-delete + copy + rename **without any
  `close()`** — invoked only by the runtime inside ReplacingFile.
- Non-swap surface unchanged: `captureSnapshot`, `preserveCurrentDb`, `preserveDbBeforeMigration`,
  peeks, `hasPreRestoreBackup`, `delete*` — these never close the DB.
- Callers reroute to a narrow seam, `DatabaseReplacement` (interface in `core:data:backup:api`
  **androidMain** — the module is KMP and the signature uses `java.io.File`; precedent:
  `BackupStorage` sits there for the same reason):
  `suspend fun restoreFromSnapshot(source: File, effects: DatabaseReplacementEffects = None):
  DatabaseReplacementResult` and `suspend fun rollbackToPreRestoreBackup(effects: … = None):
  DatabaseReplacementResult`, where `DatabaseReplacementResult` is the phase-aware sealed type
  `Committed(effectsError?)` / `RejectedBeforeMutation(error)` / `RecoveredByRollback(error)` /
  `FailedAfterMutation(error)` / `FatalNoGeneration`, and `DatabaseReplacementEffects` is the
  typed per-phase compensation contract (§8.4). The runtime implements the seam and enters the
  graph as a `create()` bound instance (5th root). Rerouted callers:
  `RestoreLatestBackupUseCase` (feature/settings), `RestoreRecoveryCoordinator
  .handleRestoreFailure` and `performUndoRestore` (feature/recovery) — each supplies its
  effects object; caller code after the await only MAPS results, it never compensates.
  Recovery layering is preserved: coordinators keep owning *semantics* (outcome mapping,
  dialogs, restart calls) while their flag/dialog WRITES ride the effects on the transaction's
  coroutine; the runtime owns *mechanics* (staging, quiesce, teardown, close, file swap,
  generation build, terminal cleanup). On Android production the seam runs the `RestartProcess`
  policy — caller-visible success behavior (result, then restart) is unchanged.

### 8.5a The attempt journal — one serialized attempt, crash-durable (R3)

The two independent booleans (`restore_in_progress` + `restore_mutation_interrupted`) had a gap
that produced a FALSE success: the interrupted flag was written only by the TERMINAL effects, so
a process death after the close began but before those effects left the OLD, still-valid
database on disk with `restore_in_progress` set and no interruption recorded — and the cold-start
schema peek, run against that healthy old file, published `RestoreSuccess` for a restore that
never happened.

R3 replaces them with an attempt-scoped persisted state machine. At most ONE unresolved attempt
exists at a time, and everything belonging to it — identity, kind, manifest context, and the path
of the rollback snapshot reserved for it — is one atomic DataStore edit.

| Phase | Written when | What a cold start may conclude |
|---|---|---|
| *(no attempt)* | slot free | normal launch (`NoOp`) |
| `Prepared` | atomically BEFORE anything irreversible (validation passed, rollback snapshot reserved) | **outcome UNKNOWN** — the live file may be old, new, or partially replaced. Recovery path, never a peek-driven success |
| `Committed` | after the live-file rename returned success AND the reservation was promoted | the mutation is durably known to have happened; the schema peek is now a genuine verification of the NEW file, so success is legal |
| *(resolved)* | one atomic clear by the OWNING attempt | nothing outstanding |

Ownership rules, all enforced by the repository rather than by convention:

- `beginAttempt` REFUSES when a different unresolved attempt owns the slot (same-id re-claim is
  idempotent) — a new restore can never inherit or overwrite another attempt's bookkeeping, and
  the refusal is what rejects the second of two rapid restores before anything irreversible.
- `recordAttemptCommitted` and `resolveAttempt` are no-ops for a non-owner, so a late terminal
  effect from a superseded attempt cannot erase the live one's state.
- An unparsable/legacy state reads as `Prepared`. A pre-R3 install's `restore_in_progress` marker
  carries no phase, so its outcome is unknown by construction: the conservative reading routes
  that one launch through recovery rather than letting a peek claim a success the old flags
  cannot prove.

**Rollback-slot reservation.** The rollback snapshot is no longer taken by the caller before
submission (where two concurrent restores would race over one canonical path). The runtime takes
it INSIDE the serialized transaction, after validation and before the point of no return, into a
per-attempt reservation file whose path goes into the journal entry. Consequences:

- a rejected attempt discards only its OWN reservation — the previous undo slot survives intact —
  UNLESS its terminal compensation failed: any outcome whose `effectsError` is non-null may have
  left the journal unresolved and still naming the reservation, so the file is KEPT (R4
  invariant 8), exactly as on `FailedAfterMutation`/Fatal;
- a committed attempt promotes its reservation onto the canonical `pre_restore_backup.db`, so the
  undo slot holds exactly the database that immediately preceded that restore. The promotion
  COPIES (R4): the reservation — the file the still-`Prepared` journal names — survives every
  crash point of the promotion, and the pre-R4 move-based promotion is the recorded defect: a
  death between the move and the durable `Committed` record left the journal pointing at a
  missing file, and recovery silently fell back to the canonical slot's OLDER snapshot (or, with
  no canonical, wedged the launch on a file of unknown provenance). Stale `.promoting` staging
  debris is deleted deterministically at the next promotion and is never read by recovery (the
  canonical lookup is an exact-name match);
- **the journal-named source is AUTHORITATIVE for a `Prepared` attempt** (R4 invariant 2): when
  that file is missing, the canonical slot — which belongs to ANOTHER attempt — is never
  substituted for it; the seam rejects with a typed error and the launch classifies as terminal
  recovery. For a `Committed` attempt the ordering proof runs the other way: `Committed` can
  only exist after the promotion completed, so the canonical slot provably holds THIS attempt's
  pre-image and IS the recovery source (the retained reservation may already be cleaned up);
- **a rollback consumes EXACTLY the file it applied** (R4 invariant 3, the typed
  `SourceConsumption` in `MutationPlan`): a reservation-sourced recovery consumes only its
  reservation and leaves the previous restore's canonical undo — and its availability flag —
  valid; a canonical-sourced rollback consumes the slot. Any canonical invalidation beyond the
  exact applied file is a separate owner-side decision, never an implicit side effect.

**Durable commit ordering** (the one order that keeps every crash window truthful):
`rename` → `promote (copy) reservation` → `record Committed` → *only then* delete the retained
reservation → consume the exact rollback source a rollback op applied. A crash before the
`Committed` record recovers via the journal's reservation, which the copy-based promotion
guarantees still exists; a crash after the record but before the reservation delete is cleaned
up idempotently by the committed cold-start finalization (§8.5b); a failure to promote or to
record `Committed` leaves the mutation standing but unprovable, so recovery proceeds
conservatively — which the invariant explicitly permits, while claiming success does not. That
PRE-DURABLE failure is typed `CommitResult.NotDurable` (R4.2) and is **never dispatched as a
committed terminal**: under `RestartProcess` it maps to `FailedAfterMutation` (journal
`Prepared`, reservation retained, the next launch recovers); under `RebuildInProcess` the
transaction diverts straight into the bounded recovery ladder (a restore rolls back onto its
kept reservation; a requested rollback retries its own source and its durable record once, and
a persistent record failure ends Fatal with everything preserved) — a candidate is never
published over the unprovable file. Pre-R4.2 this failure surfaced as `Completed(effectsError)`
and the committed terminal ran anyway, letting the production `onCommitted` effects resolve the
still-`Prepared` attempt, clear availability, publish success and acknowledge — erasing exactly
the state conservative recovery needed. The only `effectsError` a `Completed` can carry now
originates from a failure OF the terminal `onCommitted` callback after a DURABLE commit — a
different origin, still folded onto the result, never swallowed. The in-process ladder obeys
the same owner rule:
a pre-commit recovery applies the attempt's reservation WITHOUT consuming it (the journal still
names it until the terminal effects resolve; the submission frame discards it after), a
post-clean-commit recovery legally applies the canonical (the ordering proof above), a vanished
reservation or a failed explicit-source rollback stops the ladder Fatal — never a cross-owner
substitution.

### 8.5b Terminal recovery classification (R3, corrected R4)

`PreflightOutcome` distinguishes what the launch may still do:

| Outcome | Condition | What the launch does |
|---|---|---|
| `RestoreSucceeded` | `Committed` attempt + successful peek | continue normally; the finalization is replay-safe (below) |
| `RestoreRolledBack` | recovery rollback COMMITTED durably with clean bookkeeping | restart (the in-process Room handle is stale) |
| `RecoveryCompleted` | an interrupted rollback found already durably `Committed` | finish its bookkeeping idempotently (ground-truth availability, resolve LAST) and continue |
| `RecoveryRequired` | **every other rollback outcome** — rejected pre-PONR, post-PONR, fatal runtime, or a commit whose durable record failed | **arm ZERO DB-bound work** — no query-planner warm-up, no repositories, no dialog observer, no main UI — and hand off to the DB-free recovery surface with every asset preserved |

The R3 `RetrySafe` outcome is REMOVED (R4 blocker B). Its premise — "a rejection proves the
live database is intact" — confused the two transactions: a pre-PONR rejection of the RECOVERY
rollback proves only that the rollback did not mutate, never what the ORIGINAL `Prepared`
attempt did to the live file before dying, so nothing short of a clean durable rollback commit
can license Main UI or DB-bound arming over that file. The one in-process consequence is
deliberate and pinned: a graph-only reinitialize whose candidate preflight finds an unresolved
`Prepared` attempt now aborts (the outgoing generation keeps serving on its already-open handle,
journal and assets intact for a cold start or replacement transaction to complete) instead of
publishing a fresh generation — arming its chores — over unproven data, which is what locked
invariant 4 requires.

**Replay-safe finalization** (R4 invariant 7). Every finalization writes data-bearing state
BEFORE resolving the attempt, so a death anywhere in the sequence leaves the replay token:

- committed restore: mark undo availability → publish the success dialog → delete the retained
  reservation copy (idempotent) → resolve LAST. A death replays the whole method next launch; a
  same-process finalization failure is logged and the launch still proceeds as a success — the
  restore IS durably committed and verified, and the unresolved journal makes the NEXT launch
  retry (a new restore/undo in the meantime is honestly refused by `beginAttempt`).
- committed rollback (undo and scenario-1): data-bearing writes (availability per the flag
  policy, the persisted dialog) → resolve LAST → the caller's acknowledge after everything.
- the `RecoveryCompleted` replay branch is SOURCE-AWARE (R4.1): `rollbackSnapshotPath` is the
  durable discriminator of which file the committed rollback applied. Null (the canonical was
  the source) finishes the canonical's consumption idempotently and clears availability — a
  death between the commit record and the consume must never leave the same undo offered
  again, because replaying it after later writes would erase them. Non-null (an exact named
  source) consumes that file idempotently, preserves the canonical — the PREVIOUS restore's
  undo — and clears availability only when the canonical is actually absent. Never inferred
  from file existence alone; the attempt resolves LAST and the branch replays idempotently.

**Accepted residual (documented, deliberate):** the replay branch does not re-publish the
interrupted rollback's user-facing dialog. `Kind.Rollback` does not record whether the rollback
was a scenario-1 recovery (`RestoreFailure`) or a user undo (`UndoRestoreSuccess`), and
inventing either would be worse than at-most-once feedback. The exact boundary: the dialog (and
the undo flow's dialog-choice acknowledge) is lost only when the process dies between the
rollback's durable commit record and its terminal `onCommitted` effects; data correctness is
unaffected (the replay finishes availability + resolve from ground truth), and the
un-acknowledged dialog choice self-heals on the next launch through the observer's re-dispatch
(`performUndoRestore` → `FileMissing` → defensive clear + acknowledge).

`RecoveryRequired` is cached on the coordinator (`recoverySurfaceRequired`) exactly as the
migration decision is, and `MainActivity` routes on either. There is no automatic restart loop
anywhere on these paths.

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

### 8.7 UI generation boundary (`app:common`, narrowest seam)

```kotlin
sealed interface AppUiPhase {
    data class Generation(val id: Int, val viewModelStoreOwner: ViewModelStoreOwner) : AppUiPhase
    data object Transitioning : AppUiPhase
}
interface AppUiGenerationsHolder { val appUiPhases: StateFlow<AppUiPhase> }
```

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
identical to today; process death: counters restart at 1, `key(1)`+slot(1) matches what gen-1
saved → the existing restoration oracle (`BackStackStateRestorationTest`) is unchanged. Saved
state from a >1 generation is intentionally not restored after process death
(production-unreachable on Android; recorded for the Phase 7 host). No new CompositionLocal is
introduced — `LocalViewModelStoreOwner` is the standard androidx local (same pattern NavDisplay
uses per entry); `AppUiGenerationsHolder` carries no graph and no navigator.

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
- Single-flight: one `Mutex` over all transitions; same-operation registration is atomic under
  the submission lock; re-entrant rollback inlines into the running transaction (§8.4). Fatal is
  rechecked INSIDE the mutex — queued transactions behind a Fatal one do nothing.
- Failure ordering: every fallible caller-visible step (UI retire, lease drain, resolve drain,
  validation, beforeMutation) precedes PONR; aborts republish `Serving(genN)` with saved state
  AND ViewModelStore intact. PONR = the START of the first irreversible action (teardown / close
  invocation / rename); post-PONR failures end in the requested commit, `RecoveredByRollback`,
  `FailedAfterMutation` (RestartProcess, assets preserved + journaled), or the explicit `Fatal`
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
| DataStore state (5 files) + dialog exactly-once | process-lifetime memoization untouched; flags persisted; dismiss-after discipline untouched; verified across replacement |
| Recovery ordering (S1→S2, short-circuits, blocking boundaries, no Room on `RouteToRecovery`, observer-before-Activity, TestApplication bypass) | `StartupProcessor` is an order-preserving extraction; coordinators keep semantics; `TestApplication.onCreateGraphBootstrap` no-op seam retained; harness publishes phases explicitly |
| Startup jobs owned; planner semantics | §8.2/§8.3 |
| Nav3 canonical; root reset; no resurrection; restoration oracle unchanged; no graph/navigator CompositionLocals | §8.7 (measured: `NavCommand` has 5 variants, no `ResetToRoot`) |
| Metro-only DI; module boundaries; `core:core` ↛ app | §8.2 root + §8.5 seam placement + §8.8 direction |
| Schema, migrations, driver unchanged | new DB generations use `buildAppDatabase` verbatim; no builder changes |

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
| Quiesce (UI await / lease drain / resolve drain timeout) | abort pre-PONR; gen N serving; saved state AND ViewModelStore intact; admission gates reopen; `onRejectedBeforeMutation` compensates |
| Concurrent request for a DIFFERENT operation mid-transaction | its own serialized transaction — never the in-flight operation's result |
| Concurrent request for the SAME operation | joins atomically (submission-lock registration); one staging, one transaction, one outcome |
| Worker admission during the closed window | the run SUSPENDS at its first-op lease acquisition and binds to the published successor; a worker constructed but never started holds no lease |
| Late UI attach after the zero observation | refused by the atomic retire CAS — never passes, never blocks the machine; attach BEFORE zero blocks the machine until disposed |
| Snackbar deferred-commit in flight at quiesce | awaited before PONR; queued models generation-tagged — discarded at delivery on commit, preserved on abort; a gen-N callback never executes in gen N+1 |
| Restore source staging failure / caller cancellation | staging is submission-frame-atomic; a cancelled caller's temp cleanup is a no-op; staging failure → `RejectedBeforeMutation` before validation; the runtime deletes the staged copy on every terminal outcome |
| Unjoinable outgoing job after teardown began | Fatal WITHOUT closing — never a close under an unjoined job, never a republish |
| Outgoing DB `close()` throw | **Fatal** (post-PONR unknown state); no rename; never `RejectedBeforeMutation` (RestartProcess: `FailedAfterMutation`, assets preserved + journaled) |
| Candidate/orphan `close()` throw (dispose, orphan, inline rollback) | ladder STOPS: Fatal, no further rename |
| File replacement (rename fails) | rollback mechanics + fresh generation → **`RecoveredByRollback`** (restore-failure semantics), else Fatal |
| New DB construction / graphFactory throw | staged unwind (jobs joined, orphan closed) then rollback recovery → `RecoveredByRollback`, else Fatal |
| Migration/preflight failure (incl. inline S1 rollback) | outer restore → `RecoveredByRollback` after the bounded retry over the rolled-back file; the inline caller's own result is `Committed`; exactly one bounded recovery |
| Committed-effects failure | `Committed(effectsError)` — surfaced, never a silently clean commit |
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
teardown-before-close and teardown-before-publish ordering, concurrent transitions
serialized+coalesced, candidate-not-published-before-preflight, state-machine ordering + the
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
5. `DatabaseSnapshotProvider.kt:44-47` ("caller MUST tear down every consumer"), `:52-54` ("via
   Room's open helper"), `:106-109` + `UndoRestoreOutcome.kt:20` ("atomic rename" — actually
   copy+rename+delete), `:150-151` (pre-migration WAL-checkpoint claim) — all superseded by the
   §8.5 split anyway.
6. `documentation/ci-cd.md:342-345` + `lint-rules.md:724-731` — pre-commit hook described as
   disabled/copied; measured: `core.hooksPath`, detekt runs on staged `.kt`.
7. Nav2-era wording: `AppFeature.kt:13,18-23`, `Feature.kt:20`, `FeatureAssisted.kt:21`,
   `MetroStoreProcessor.kt:17`; `NavigatorEventBus.kt:29` ("injects"); `AppDialogHost.kt:28`
   ("@Singleton"); coordinator/repository KDoc caller claims (`RestoreRecoveryCoordinator.kt:32-33`,
   `AppDialogRepository.kt:72-74`); post-§8.7: `AppFeature.kt:16-19` + `AppDialogFeature.kt:23-25`
   ("LocalViewModelStoreOwner is the host ComponentActivity").
8. `documentation/performance.md` `AppCreated` boundary — verify against §2 stage 6.
9. `android_build_unified.yml:146` "13 golden-holding modules" — 14 apply Paparazzi (recorded, not
   edited — CI text change out of scope).
9b. `MetroWorkerFactory.kt:19` KDoc cites work-runtime 2.10.0; the catalog ships 2.11.2.
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
   `RebuildInProcess` only — production's post-close failure behavior (return `Io`, no restart,
   loud-failing closed DB until the user acts) is preserved verbatim; and a `RestartProcess`
   transaction MUST be startable from an already-terminal generation (the undo `IoFailure` re-tap
   re-runs close+rename today; close is idempotent).
6. **Cold-start mutex rule** (§8.3): generation 1's build+publish completes and releases the
   transition mutex BEFORE the startup preflight runs; a cold-start Scenario-1 rollback takes the
   plain `RestartProcess` path, never the full machine — otherwise the first cold-start rollback
   deadlocks the main thread on the runtime's own mutex.

Notes folded: the §8.7 wrapper must enclose App()'s `AppRootViewModel` resolution (top of the
body), and the Transitioning interstitial uses a theme-independent background (the theme flows
from that VM); §10's DataStore count corrected to 5 files; the identity tests reach the 4th root
through `buildAppGraph`'s defaulted parameter (reconciled — the default IS the pre-Phase-5
anonymous-scope behavior); `DatabaseReplacement` lives in `core:data:backup:api` **androidMain**
(the module is KMP; `java.io.File` precedent: `BackupStorage`); `MetroWorkerFactory.kt:19`'s
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
  (`known-negative-ui-handshake-severed-dispose.xml`).
- `RuntimeGenerationSwapDeviceTest`: claims NARROWED (its KDoc now states the boundary): it
  proves the inode swap, terminal close, and fresh-Room coherence over EMPTY quiesce
  populations — it does not claim the UI handshake or live lease drain.

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
| 1 | `restore_mutation_interrupted` written only by terminal effects → a death after close-start left the OLD valid DB with no marker → cold start peeked it and published a false `RestoreSuccess` | Attempt journal (§8.5a): `Prepared` persisted atomically pre-PONR with identity + context + reserved rollback path; `Committed` recorded only after the rename returned success and the reservation was promoted; owner-only advance/resolve; legacy and unparsable states read as `Prepared` | `RestoreRecoveryCoordinatorTest`: `a PREPARED attempt never peeks the schema and never claims success` (asserts ZERO `currentSchemaVersion()` calls and no `RestoreSuccess` publish) + `a ROLLBACK-kind attempt never peeks the schema either` + `a COMMITTED attempt verifies by peek and succeeds`. `RestoreStateRepositoryImplTest` (18) pins ownership, idempotence, legacy migration and the unparsable-phase reading. **Boundary:** these prove the BRANCHING and the persisted round-trip; no test kills the process, so single-`edit` atomicity is DataStore's guarantee, not ours |
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
| A1 | promotion is crash-safe | `promoteRollbackReservation` COPIES; the reservation survives every crash point; the runtime deletes it only after the durable `Committed` record; committed cold-start finalization cleans a retained copy idempotently; stale `.promoting` deterministically discarded | `promotion COPIES — the journal-named reservation survives with its exact bytes` (REAL files, sentinels A/B, `DatabaseSnapshotProviderImplTest`) |
| A2 | journal-named source authoritative | `selectRollbackOperationSource`: missing explicit path → typed rejection, never the canonical; `selectRecoverySource`: vanished reservation → Fatal; failed explicit-source rollback → Fatal; post-clean-commit canonical legal by the ordering proof | `a MISSING journal-named rollback source is a typed rejection…`; `ladder recovery whose reservation VANISHED goes Fatal…` |
| A3 | exact-file consumption | typed `SourceConsumption` (None/CanonicalSlot/ExactFile) in `MutationPlan`; a rollback consumes exactly what it applied | `an explicit rollback applies and consumes EXACTLY its named source — the canonical survives` |
| A4 | reservation kept on unresolved-journal outcomes | `keepReservation` covers every outcome with `effectsError != null` | `a rejection whose terminal compensation fails KEEPS the reservation…` |
| B | terminal classification | `RetrySafe` removed; every non-commit rollback outcome → `RecoveryRequired` (zero DB-bound arming, no Main UI) | `every non-commit rollback outcome requires terminal recovery` (`RejectedBeforeMutation` case) + `StartupProcessorTest`'s zero-arming pins |
| C | legacy owner isolation | atomic legacy→owner-scoped conversion under the synthetic id only; `resolveAttempt` requires that id; `ScenarioOneRollbackEffects` CHECKS its re-claim | repository: `only the synthetic legacy owner claims…` + the flipped arbitrary-resolver pin; coordinator: `the recovery re-claim CHECKS ownership…` + the legacy end-to-end |
| E1 | replay-safe success finalization | mark → publish → reservation cleanup → resolve LAST, `runCatching`-wrapped | `success finalization is data-bearing-first…`; `a finalization failure never leaves the journal resolved with the undo hidden` (mandated test 11); `…cleans up the RETAINED reservation copy idempotently` |
| E2 | committed-rollback replay ground truth | availability from canonical-file existence; resolve last; at-most-once dialog documented (§8.5b) | `an interrupted but COMMITTED rollback…` (order + clear) and `a committed RESERVATION-sourced rollback keeps the previous restore's still-valid undo` |
| E3 | Committed recovers from canonical | `recoverFrom` passes `sourcePath` only for `Prepared` | `a COMMITTED attempt recovers from the CANONICAL slot…` |
| D1 | post-PONR teardown failure terminal | best-effort candidate release → aggregate → `publishFatal`; no epoch advance, no publication, UI gate stays retired | `outgoing ViewModelStore clear failure after PONR is FATAL…` (test 7); `unjoinable outgoing lifetime after PONR is FATAL…` (test 8) |
| D2 | candidate teardown verdict checked | preflight-fail path: `tearDownCandidate == false` → Fatal, never a republished N | `candidate preflight failure with an unjoinable candidate is FATAL…` (test 9) |
| D3 | partial-unwind signal distinct | `releasePartialGeneration` honors the join verdict for `ownsDatabase=false` → `PartialCandidateUnwindException` → Fatal | `partial construction with an unjoinable child is TERMINAL…` (test 10) |

### 23.2 Crash-state matrix — one restore attempt, RestartProcess, current protocol

| Death point | Journal | Files (R = reservation, C = canonical, L = live) | Next launch |
|---|---|---|---|
| before `onBeforeMutation` | none | R exists (discarded by frame if reached), C old, L old | `NoOp`; normal launch |
| after Prepared, before close | `Prepared`+R | R = pre-image, C old, L old | recovery rollback from R (idempotent re-apply) → `RestoreRolledBack` → restart |
| after close, before rename | `Prepared`+R | R = pre-image, C old, L old | same — R applied, correct |
| after rename, before promote | `Prepared`+R | R = pre-image (COPY survives), C old, L NEW | rollback from R → pre-image restored ✓ (pre-R4: R could be GONE mid-promote → silent revert onto old C, or wedge) |
| mid-promote (any step) | `Prepared`+R | R intact; C old/absent/new; `.promoting` debris possible | rollback from R ✓; debris never read |
| after promote, before record | `Prepared`+R | R intact, C = pre-image, L new | rollback from R (same bytes as C) ✓ |
| after record, before reservation delete | `Committed`+R | R intact, C = pre-image, L new | peek → success → finalization deletes R idempotently ✓ |
| after reservation delete, before terminal effects / restart preempts frame | `Committed` | C = pre-image, L new | peek → success; peek-fail → rollback from CANONICAL (provably ours by ordering) ✓ |
| mid-finalization (any point before resolve) | `Committed` | C = pre-image, L new | full replay: mark, publish, cleanup, resolve — idempotent ✓ (pre-R4: resolve-first death hid the undo forever) |
| after resolve | none | C = pre-image, L new, flag set | `NoOp` ✓ |

And for a committed CANONICAL-sourced rollback (`Committed`/`Kind.Rollback`, source path null):

| Death point | Journal | Files | Next launch |
|---|---|---|---|
| after `record Committed`, before the canonical consume | `Committed`+Rollback, path=null | C still present, flag still true, L = rolled-back data | the SOURCE-AWARE finalization (R4.1) finishes the consumption from the journal's own discriminator: consume C → clear availability → resolve LAST — the same undo is never offered again (pre-R4.1 the surviving file read as "a valid previous undo" and the rollback stayed replayable, erasing later writes) |
| mid-finalization (before resolve) | `Committed`+Rollback | per progress | idempotent replay to the same terminal state ✓ |

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

- **At-most-once rollback dialog/ack** — §8.5b carries the full statement and boundary.
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

Two commits on `83793531`: `decb5e7a` (blocker A: durable-phase-aware terminal dispatch;
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

**Named residual (outside the locked invariant, on record):** a mid-copy
`promoteRollbackReservation` failure can leave the CANONICAL slot partially written (the
staging fallback deletes the target before its copy; the catch cleans only the staging file),
and rollback sources are applied without re-validating the SQLite magic — so a later user undo
of a PREVIOUS attempt could swap in a truncated canonical. The NotDurable protocol itself
handles the failing attempt correctly (recovery uses the reservation; availability is
ground-truth), so this is a canonical-integrity hazard for a DIFFERENT attempt's asset, not a
violation of the R4.2 invariant; it would surface as a failed rollback swap or a
recovery-routed launch, never a false success.
