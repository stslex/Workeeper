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
| `MetroWorkerFactory` captured deps | **runtime-generation (currently process — defect)** | `by lazy` capture (`MetroWorkerFactory.kt:28-30`); §8.6 de-captures (one holder read per `createWorker` invocation). |
| **In-flight `BackupWorker`** | **runtime-generation live capture** | a worker already inside `doWork()` holds the six deps read at construction (`MetroWorkerFactory.kt:41-50`) — including the DB-bound `DatabaseSnapshotProvider` — for the whole run; §8.4's Quiescing drains RUNNING workers before close. |
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
| `BackupWorker` in `doWork()` — six ctor deps incl. DB-bound provider (`BackupWorker.kt:42-53`) | the whole run (awaits export inside `doWork`, `BackupWorker.kt:64-70`) | drain: await RUNNING unique works (`auto_backup`, `one_time_backup`) reaching a non-RUNNING state, bounded; timeout → abort pre-close |
| `SnapshotExportRunnerImpl` in-flight `runExport()` (DB JSON export, `SnapshotExportRunnerImpl.kt:67-69`) | export duration | lifetime child → `cancelAndJoin` |
| `RestoreDialogChoiceObserver` collector; `DriveBackupAuth` collector | infinite | lifetime children → `cancelAndJoin` (last quiesce step, §8.4) |
| Per-entry Stores / `AppRootViewModel` / `AppDialogStoreImpl` (repos, bus captured at ctor) | entry / generation-VM-store lifetime | UI region disposal + generation `ViewModelStore.clear()` |
| Startup chores (cleanup, `ANALYZE`) | one-shot | lifetime children → `cancelAndJoin` |
| `MetroWorkerFactory` lazy deps | process (defect) | §8.6 de-capture; per-invocation single read |
| **Snackbar deferred-commit path** — `AppSnackbarModel`'s `onDismissed` runs under `withContext(NonCancellable)`; the ED11 deferred permanent-delete commits DB work there via a closure capturing gen-N repositories; `SnackbarManager` is a process-level object with an unlimited queue whose requeue path can carry gen-N closures into the gen-N+1 collector (review v2 finding 1) | until the resolve completes / queue drained | Quiescing sub-step: await the in-flight resolve, drain-or-record the queue before close; queued-model cross-generation property recorded |
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

### 8.4 The replacement state machine (runtime-owned)

```
Running → Quiescing → ReplacingFile → BuildingGeneration → Preflight → Publishing → Running
```

All transitions run under the runtime `Mutex` (single-flight). **Coalescing is
operation-identity-scoped (review v2 condition 3): only a concurrent request for the SAME
operation joins the in-flight transaction's result; a different operation queues behind it or
returns an explicit busy outcome — never the other operation's result.** Two policies select the
ending:

- **`RestartProcess` (Android production)** — preserves shipped behavior exactly: no Quiescing
  (process death is the quiescence, as today), the runtime executes close + file replacement and
  marks the published generation terminal; the caller's existing restart flow
  (`AppReinitializer`) follows. Observable production behavior is unchanged, including the brief
  closed-DB window before exit that exists today. **Two scoping rules (review v2 condition 5):
  the §"Failure semantics" recovery ladder applies to `RebuildInProcess` ONLY — a production
  post-close failure keeps today's behavior verbatim (return `Io`, no restart, the closed DB
  fails loud until the user acts); and a `RestartProcess` transaction is startable from an
  already-terminal generation (the undo `IoFailure` re-tap re-runs the idempotent close + rename
  today, and must keep doing so).**
- **`RebuildInProcess` (Android instrumentation now; iOS Phase 7)** — the full machine:

**Quiescing** (every step precedes `close()`; fallible steps precede irreversible ones):
1. Publish `Transitioning` — new UI work sees no generation; the old `key(id)` region leaves
   composition; per-entry Stores and root VMs run their `dispose()` paths. Await the old region's
   departure via its `DisposableEffect(generation)` completion signal (bounded). *Reversible.*
2. Drain in-flight workers: for the unique works (`auto_backup`, `one_time_backup`), await any
   RUNNING instance reaching a non-RUNNING state (bounded; the periodic schedule is NOT cancelled
   — WorkManager's own persistence carries it, as across today's restarts). Timeout → **abort**:
   republish `Serving(genN)`; the old generation resumes serving (state restored via the
   `SaveableStateHolder`, §8.7). *Reversible.*
3. Drain the snackbar deferred-commit path (review v2 condition 1): await any in-flight
   `resolveSnackbarOutcome` — its `NonCancellable` commit survives step 1's collector cancellation
   by design — and record (not silently drop) any queued models; a model queued across the
   replacement carries old-generation closures and must not execute against gen N+1. *Reversible.*
4. Clear the generation `ViewModelStore` (deterministic `onCleared`). *Rebuildable from the live
   graph if aborted after this point.*
5. `lifetime.cancelAndJoin()` — reactors, collectors, export/chore jobs END. Join is bounded; a
   straggler ignoring cancellation is recorded and the machine proceeds — post-close DB touches
   from cancelled stragglers fail loud (§7.1's measured pin), and the atomic rename cannot be
   corrupted by a reader. This is the LAST step before the point of no return; nothing between it
   and `close()` can fail.

New DB work during Quiescing is excluded structurally: after steps 1–5 no old-generation producer
remains (UI disposed, workers + snackbar resolves drained, lifetime joined); the transaction
itself is the only actor. No DAO/repository proxying, no graph-wide gate.

**ReplacingFile**: runtime calls `generation.database.close()` — **generation N is now terminal:
it is never republished and never treated as a fallback** — then invokes the provider's pure file
mechanics (§8.5): delete sidecars → copy source → `.tmp` → atomic rename (+ consume source for
rollback ops). Validation (magic/version/live-version reads) ran in Running, before Quiescing,
through the still-open DB.

**BuildingGeneration**: `dbFactory()` — the **complete production factory** (`buildAppDatabase`:
driver + full `MIGRATIONS` chain, cold) — then a fresh `AppScopeLifetime`, fresh `ViewModelStore`,
new graph via `graphFactory(context, newDb, imageStorage, newLifetime)`. Nothing published yet.

**Preflight**: the candidate's own coordinator verifies (for restore/rollback contexts the
Scenario-1 semantics: `currentSchemaVersion()` through the candidate — the open IS the
verification, migrations run here) and applies the success side-effects (clear
`restore_in_progress`, `markPreRestoreBackupAvailable`, publish `RestoreSuccess` — DataStore
writes, process-lifetime, exactly-once by flag semantics).

**Publishing**: single atomic `Serving(genN+1)` write; UI re-keys onto the new generation; old
saved-state slot dropped (`SaveableStateHolder.removeState(oldId)`); startup chores + observer
arming run on the new lifetime.

**Failure semantics** (locked):
- Before `close()`: abort → generation N keeps serving (steps 1–3 unwind; step 4 cannot fail).
- After `close()`: generation N is terminal. A failure in ReplacingFile/BuildingGeneration/
  Preflight must (a) construct a fresh generation from the swapped file, or (b) perform the
  rollback file mechanics (from `pre_restore_backup.db`) and construct another fresh DB+graph
  generation — with the coordinator's failure side-effects (clear flags, publish `RestoreFailure`)
  applied through the surviving generation's process-lifetime repositories. Exactly one bounded
  recovery attempt per failure point; if both construction and rollback recovery fail, the machine
  emits the explicit terminal outcome `ReplacementOutcome.Fatal` — the runtime publishes no
  generation for new UI work and surfaces the recovery route (RecoveryActivity-equivalent path);
  it never continues serving a closed generation.

**Re-entrancy** (review v2 condition 4): a rollback request arriving from inside the
transaction's own Preflight (the coordinator's failure path) is executed as the current
transaction's rollback branch via a **CoroutineContext transaction marker** — the machine installs
a per-transaction context element around the Preflight invocation, and the `DatabaseReplacement`
implementation inlines the file mechanics only when `coroutineContext[marker]` matches the current
transaction (kotlinx `Mutex` is non-reentrant and holder-anonymous, so mutex introspection cannot
implement this). It never starts a nested transaction.

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
  `suspend fun restoreFromSnapshot(source: File): BackupResult<Unit>` and
  `suspend fun rollbackToPreRestoreBackup(): BackupResult<Unit>`. The runtime implements it and
  enters the graph as a `create()` bound instance (5th root). Rerouted callers:
  `BackupInteractorImpl.restoreLatest` (`feature/settings/.../BackupInteractorImpl.kt:157`),
  `RestoreRecoveryCoordinator.handleRestoreFailure` (`:143`) and `performUndoRestore` (`:109`).
  Recovery layering is preserved: coordinators keep owning *semantics* (outcomes, flags, dialogs,
  restart calls); the runtime owns *mechanics* (quiesce, close, file swap, generation build).
  On Android production the seam runs the `RestartProcess` policy — caller-visible behavior
  (results, then restart) is unchanged.

### 8.6 `MetroWorkerFactory` de-capture

Drop the `by lazy` field; `createWorker` reads
`(appContext as BackupWorkerDepsHolder).backupWorkerDeps()` **once per invocation** and takes all
six deps from that single returned graph (no torn cross-generation worker).
`MetroWorkerFactoryTest` extended to swap holder deps between calls. In-flight workers are the
Quiescing drain's concern (§8.4), not the factory's.

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

- Publication: one atomic write of an immutable `RuntimePhase` — no mixed-generation reads.
- Single-flight: one `Mutex` over all transitions; concurrent requests coalesce; re-entrant
  rollback inlines into the running transaction (§8.4).
- Quiescing failure ordering: fallible steps (UI await, worker drain) precede irreversible ones
  (lifetime join, close); aborts republish `Serving(genN)` with saved state intact.
- Terminal generations: closed ⇒ never republished; failure after close ⇒ fresh-generation or
  rollback+fresh-generation, else explicit `Fatal` — never continue on a closed generation.
- A seam read racing the transition window (e.g. a worker constructed after the step-2 drain,
  before close — WorkManager dispatch is not pausable) receives the terminal generation's deps and
  fails loud on first DB touch (§7.1's measured pin) — bounded, recorded, user-visible (review v2
  condition 2): the pool-closed `SQLiteException` propagates uncaught out of `BackupWorker.doWork`
  → the run is marked FAILED, not retried (`Result.retry()` exists only on caught `BackupResult`
  branches). The periodic chain survives via WorkManager's own persistence; a one-time backup
  requires a user re-trigger. Worker catch-blocks are deliberately NOT widened (out of scope).
- Cold-start `runBlocking` boundaries untouched. Chore failure policy unchanged.

## 10. Locked invariants — preservation map (R2)

| Invariant | How preserved |
|---|---|
| Android prod `AppReinitializer` = process restart; no silent switch | androidMain actual untouched; production replacement policy is `RestartProcess`; `RebuildInProcess` has zero production callers (grep-pinned) |
| **R2 replacement invariant** (§0) | `RuntimeGeneration` is the single published unit; DB-bound objects are graph-owned and die with their generation (Quiescing) or are drained (workers); DB generation increments only on file swaps |
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

| Injection point | Locked outcome |
|---|---|
| Quiesce (UI await / worker drain timeout) | abort pre-close; gen N serving; saved state restored |
| Concurrent request for a DIFFERENT operation mid-transaction | queues or returns busy — never receives the in-flight operation's result (review v2 condition 3) |
| Worker constructed after drain, before close | bounded loud FAILED run; periodic chain intact; never blocks or corrupts the swap |
| Snackbar deferred-commit in flight at quiesce | awaited before close; queued models recorded, never executed against gen N+1 |
| DB `close()` (throw) | close() does not meaningfully throw; hook asserts terminality bookkeeping regardless |
| File replacement (rename fails) | post-close: rollback mechanics + fresh generation; flags/dialog via coordinator failure path |
| New DB construction/open | rollback + another fresh DB+graph generation |
| Migration/preflight failure | same as above (Scenario-1 failure semantics); exactly one bounded recovery |
| Rollback mechanics failure | `ReplacementOutcome.Fatal`; no closed generation serving; recovery route surfaced |
| Post-rollback generation construction failure | `Fatal`, same |

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
lifetime cancelled/joined, concurrent transitions serialized+coalesced, candidate-not-published-
before-preflight, state-machine ordering + §11.3 injections, worker-factory per-invocation read.
JVM (`feature/recovery`): observer on lifetime scope; cancellation ends reactions; one choice →
one reaction across generations. Instrumented (`app/app`): §11.2 gate; dialog `pending_*` flags
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
