# KMP Phase 5 — startup processor, graph generations, restart-free reinitialization foundation

| | |
|---|---|
| Status | SPEC + **GATE RESULT: RED** (2026-08-22, §7.1) — implementation STOPPED at the §7 entry gate per the maintainer protocol; graph-generation design stands as the recommended post-decision path |
| Baseline | `dev` @ `c935227df22020f394f1ea0748fdeba7b7a67fd5` (2026-08-22, "Fix ad-hoc exercise removal copy (#250)") |
| Branch | `feature/kmp-phase-5-startup-processor` |
| Upstream phases | 0–4 complete; Phase 6 data layer complete (#239/#240/#241 merged); Phase 7 (CMP UI, `iosApp`, iOS composition root) strictly downstream |
| Supersedes | the Phase-4 startup inventory (predates `warmQueryPlanner()`); `kmp-migration-assessment.md` §"restart-free actual" reinit order (Nav2-era, Room 2.8.4-era — see §7.1) |

Every code claim below was measured on the baseline SHA (file:line cites). Where a document
contradicts the code, the code is authoritative and the contradiction is listed in §15.

## 1. Objective

Deliver an explicitly owned startup and graph-generation lifecycle that:

- preserves current Android production restart behavior (process relaunch stays the shipping
  mechanism for every restore/rollback/undo path);
- makes safe restart-free reinitialization possible within one process, proven on Android
  instrumentation;
- proves graph replacement **without replacing `AppDatabase`**;
- resets Nav3 and UI/store ownership on a graph-generation boundary;
- removes the anonymous, unowned `CoroutineScope(SupervisorJob() + dispatcher)` instances and
  gives every app-scoped job/collector a deterministic owner;
- establishes an honest platform contract for the future iOS host **without** claiming a runnable
  iOS restore flow exists before Phase 7's iOS database/application composition root.

Non-goals: Phase 7 UI/CMP work, `iosApp`, KMP conversion of `app:common`, Room schema/dependency/
toolchain changes, `File`→okio, Metro/Nav3/Room replacement, unrelated bug fixes (including the
inert `SupervisorJob()` in `AppCoroutineScopeImpl.kt:29` — recorded in §15, not fixed here),
golden re-recording, new suppressions/baselines.

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
(`MetroWorkerFactory.kt:28-30`) — the one production graph **capture** outside UI (§4).

## 3. Lifetime matrix

| Object | Classification | Evidence / consequence for reinit |
|---|---|---|
| `AppDatabase` | **process** — preserve | single cold build (`BaseApplication.kt:66`); enters the graph as a `create()` bound-instance root (`AppGraph.kt:277-281`); the only `close()` sites are the restore/rollback file swaps (`DatabaseSnapshotProviderImpl.kt:104,196`), after which production always process-restarts. Generation swaps MUST reuse the same object and MUST NOT close it. |
| `ImageStorage` | **process** — preserve | stateless dir wrapper, `create()` root (`BaseApplication.kt:85`); reused across generations. |
| DataStore instances (5 files: `common_prefs`, `backup_scheduling_prefs`, `restore_state_prefs`, `app_dialogs_prefs`, `backup_account_prefs`) | **process** — preserve | `DataStoreProvider`'s companion CAS memo is explicitly process-lifetime, "deliberately outliving any AppGraph" (`DataStoreProvider.kt:32-66`); pinned by `AppScopeDataStoreSingletonTest` building two graphs. |
| Dialog state (`pending_*` flags) | **process (persisted)** — preserve | `AppDialogRepository` derives state from DataStore on every read, no in-memory queue (`AppDialogRepository.kt:26-31,61-63`). Survives reinit for free; instance identity NOT required (measured: nothing holds cross-call state in the repository object). |
| `AppReinitializer`, Firebase/Crashlytics holders, `PerformanceMetricsRecorder` | **process** | stateless / static singletons. |
| `AppGraph` + every `@SingleIn(AppScope)` binding (61 sites: 43 class-level, 18 provides — scope-sweep) | **graph-generation** — rebuild & dispose | includes `NavigatorEventBus` (the app-wide nav bus), `AppDialogObserverImpl` (choice SharedFlow), the 9 repositories, DAO providers (derive from the process DB root), gd auth chain, coordinators. |
| `RestoreDialogChoiceObserver` (+ its collector) | **graph-generation** | today process-lifetime, never cancelled (`RestoreDialogChoiceObserver.kt:91-97`). A second generation would arm a second reactor while the first still collects → must be cancelled with its generation. Note: each reactor collects **its own graph's** `AppDialogObserverImpl` flow, so cross-generation double-reaction cannot occur by bus identity — the leak is still removed. |
| `DriveBackupAuth.authScope` + `observeAccount()` collector | **graph-generation** | app-lifetime infinite collector #2 (`DriveBackupAuth.kt:70-80`), never cancelled today. |
| `SnapshotExportRunnerImpl.scope` | **graph-generation** | fire-and-forget export launches (`SnapshotExportRunnerImpl.kt:59,67-69`); in-flight export may be cancelled with its generation (best-effort by contract, `:54-58`). |
| Orphan-image cleanup job, planner warm-up job | **graph-generation** (one-shot chores re-run per generation; both idempotent, `ANALYZE` durable) | today on anonymous scopes (`BaseApplication.kt:184,225`). |
| `MetroWorkerFactory`'s captured deps | **graph-generation (currently process — defect)** | `by lazy` capture (`MetroWorkerFactory.kt:28-30`) would pin generation 1 forever; §8.6 de-captures. |
| Nav3 back stack, `NavigatorHolder`, per-entry ViewModelStores/Stores, `AppRootViewModel`, `AppDialogStoreImpl`, `NavigationEventBusSetup` collector | **UI/navigation-generation** — recreate/reset | back stack is `rememberNavBackStack(screenSavedStateConfiguration, Home)` (`App.kt:79-83`); `AppRootViewModel` ctor-captures `commonDataStore` + `navigatorEventBus` (`App.kt:64-70`); `AppDialogStoreImpl` is Activity-store-scoped via `AppFeature` (`AppDialogFeature.kt:35-46`); nav command collection is composition-side `LaunchedEffect(navigatorHolder)` (`NavigatorExt.kt:32-41`). |
| `ActivityHolderImpl` content (the `WeakReference<Activity>`) | **UI-generation adjacent** | holder object is graph-owned; MainActivity `produce(this)`/`produce(null)` re-registers on the current generation's holder via non-capturing `get()` (`MainActivity.kt:23-25,60,69`). |
| `iosApp` host, iOS DB factory, iOS composition root | **platform-host — Phase 7** | do not exist; nothing here may pretend they do. |

## 4. Graph-reader / dependency-capture inventory

Five publication seams, all on `BaseApplication` (`:49-56`), all returning the one `appGraph`:
`AppGraphOwner` (internal, MainActivity `:23`), `AppDepsHolder.appDeps()` (13 feature
`context.appDeps<XxxGraph.Factory>()` readers — all acquire-and-drop inside
`rememberMetroStoreProcessor` factories, **no caching**), `RecoveryDepsHolder` (RecoveryActivity,
activity-scoped use), `BackupWorkerDepsHolder` (**captures** via `by lazy`,
`MetroWorkerFactory.kt:28-30`), `AppRootDepsHolder` (`App.kt:65` — **captures** `commonDataStore` +
`navigatorEventBus` into `AppRootViewModel`). JVM identity tests (14 files in
`app/app/src/test/.../di/`) and the androidTest harness call `buildAppGraph` directly.

Conclusion: the graph-swap blast radius outside DI wiring is exactly **two captures**
(worker factory, root ViewModel) plus the UI tree's per-generation objects — everything else
re-reads per access.

## 5. Restore / rollback / undo call paths (measured)

- **Restore** (Settings, old process): `BackupInteractorImpl.restoreLatest` → version gates →
  `preserveCurrentDb()` (WAL checkpoint via Room writer connection + copy to
  `cache/pre_restore_backup.db`) → `markRestoreInProgress` (DataStore) → download to cache temp →
  `restoreFromSnapshot(temp)` = magic check → source-version peek → **`appDatabase.close()`** →
  delete `-wal`/`-shm` → copy → `app.db.tmp` → `renameTo(app.db)` (`DatabaseSnapshotProviderImpl.kt:176-212`)
  → `NavigatorEventBus.restartApp()` → `AppReinitializer.reinitialize()` (process exit). Verification
  happens in the **new** process's Scenario 1 (`RestoreRecoveryCoordinator.kt:69-81`: the
  `currentSchemaVersion()` read through the same-process fresh `AppDatabase` *is* the verification).
- **Rollback** (Scenario-1 failure path and undo): `rollbackToPreRestoreBackup()` — same swap core
  from `cache/pre_restore_backup.db`, plus `source.delete()` (consumes the undo slot)
  (`DatabaseSnapshotProviderImpl.kt:95-121`). Scenario-1 auto-rollback (`RestoreRecoveryCoordinator.kt:143`)
  and Scenario-3 undo (`:109`) share this method; restore has its own structurally-identical copy.
  **Both gate branches (§7) must therefore be exercised.**
- **Undo**: `performUndoRestore` three-way outcome (`UndoRestoreOutcome`), dialog dismiss-after
  discipline, restart only on `Succeeded` (`RestoreDialogChoiceObserver.kt:152-175`).
- Every replacement path ends in `Runtime.getRuntime().exit(0)` today — **no code path reuses a
  closed `AppDatabase`** (grep-verified: the only `close()` callers are the two swap methods).

## 6. The Room 3 feasibility fact (source-measured; the gate decides)

`kmp-migration-assessment.md:546` ("captured DAOs follow the reopen for free") and `:552-553` (the
reinit order + reopen spike) were written against **Room 2.8.4**, whose framework open-helper
lazily reopens after `close()`. Phase 6 (#240) moved production to **Room 3.0.0 driver mode**
(`BundledSQLiteDriver`). Reading the shipped `room3-runtime-android-3.0.0-sources.jar`:

- `RoomDatabase.close()` → `closeBarrier.close()` → `onClosed()`: cancels the database's own
  `coroutineScope`, stops the `InvalidationTracker`, closes the connection manager
  (`RoomDatabase.android.kt:398-406`);
- `CloseBarrier.closeInitiated` is a one-way CAS (`CloseBarrier.kt:82-88`); `connectionManager` is a
  `lateinit var` assigned once (`RoomDatabase.android.kt:96,202`);
- `ConnectionPoolImpl.useConnection` on a closed pool throws
  `SQLiteException(SQLITE_MISUSE, "Connection pool is closed")` (`ConnectionPoolImpl.kt:117-119`);
  `close()` is one-way (`:207-210`).

**Source-level conclusion: Room 3 `close()` is terminal for the object — there is no reopen path.**
This predicts the §7 gate goes red at its step 7. The prediction is not evidence; the gate is.
The architecture in §8 is therefore structured so that the graph-generation mechanism **never
closes the database** — its correctness does not depend on reopen-after-close. What the gate
decides is whether the **in-process restore/rollback flows** (the DB-file-swap class) can ever be
served restart-free with the same `AppDatabase`; a red gate STOPs that claim per the maintainer
protocol and leaves the graph-generation foundation as the deliverable pending maintainer decision.

## 7. Mandatory Room entry gate (device, production driver)

Test: `core/data/database/src/androidDeviceTest/.../SameInstanceReopenAfterSwapDeviceTest.kt`,
`@Regression`, modeled on `AtomicRollbackDeviceTest`'s anti-vacuity discipline. Protocol:

1. Build the production-path DB (`buildAppDatabase`-equivalent builder: production name irrelevant —
   file-backed probe name, `BundledSQLiteDriver`, production `MIGRATIONS`), retain **both** the
   `AppDatabase` and a DAO instance.
2. Write sentinel **NEW**; `captureSnapshot(file)` (production checkpoint+copy) → snapshot holds NEW.
3. Delete NEW, write sentinel **OLD** → live file holds OLD only. Also copy the snapshot to the
   `pre_restore_backup.db` slot for the rollback branch.
4. Branch A (restore): `DatabaseSnapshotProviderImpl.restoreFromSnapshot(snapshot)` — the actual
   production close + sidecar-delete + copy + atomic rename. Branch B (rollback/undo):
   `rollbackToPreRestoreBackup()` — the second production replacement implementation.
5. **Disk truth** (isolates swap-success from reopen-success): a fresh framework-SQLite read of the
   swapped file must show NEW present, OLD absent.
6. **The gate assertion**: read through the *retained* DAO / *retained* `AppDatabase`: NEW visible,
   OLD absent. OLD-absent is the stale-inode killer (`kmp-migration-assessment.md:553`'s FALSE-PASS
   trap): a stale handle serving the old inode would show OLD.
7. Known-negative mutation: bypass the swap (skip step 4) — the gate assertion must go **red**
   (proves non-vacuity). Mutation reverted before commit; the disk-truth + gate assertions stay as
   the committed regression test, pinning whatever the measured truth is.

Red criteria (any → STOP, report device/command/logs/file-identity evidence, keep the branch):
step 6 throws (`SQLITE_MISUSE` expected per §6), reads OLD, or cannot run on the device.
Green criteria: both branches pass steps 5–6 and the known-negative goes red.
Runtime: `emulator-5554`, API 34, arm64-v8a (CI reference config is API 34 x86_64 —
`ui_tests.yml:55-59`; the gate also runs under the Regression job's annotation filter).

### 7.1 GATE RESULT — RED (measured 2026-08-22)

Device: `sdk_gphone64_arm64` emulator (Pixel 6 AVD), API 34, arm64-v8a; Room 3.0.0 +
`BundledSQLiteDriver`. Command:
`ANDROID_SERIAL=emulator-5554 ./gradlew :core:data:database:connectedDebugAndroidTest
-Pandroid.testInstrumentationRunnerArguments.class=…SameInstanceReopenAfterSwapDeviceTest`.

- **Both** production replacement paths (`restoreFromSnapshot`, `rollbackToPreRestoreBackup`)
  succeed on disk: fresh-handle read shows snapshot-sentinel-present / live-sentinel-absent, and
  the live file's inode changes across the atomic rename (`Os.stat` evidence).
- The subsequent read through the **retained** DAO on the **retained** `AppDatabase` throws
  `android.database.SQLException: Error code: 21, message: Connection pool is closed`
  (`ConnectionPoolImpl.useConnection` → `RoomConnectionManager.useConnection` →
  `RoomDatabase.useConnection` → `performSuspending`) — deterministically, on both paths,
  matching §6's source reading exactly. It does **not** read OLD data: the stale-inode
  silent-corruption outcome does not occur; the failure is loud.
- Known-negative: bypassing the swap turns both tests red at the file-identity assertion
  (inode unchanged) — the measurement is not vacuous. Mutation reverted.
- The committed `SameInstanceReopenAfterSwapDeviceTest` pins the measured truth three ways:
  swap-is-real, failure-is-loud-never-stale, and a green-flip tripwire (if a future Room lets the
  retained object serve the swapped file, the test fails with a "gate flipped GREEN — revisit"
  message so the descope decision is consciously reopened).

**Consequence (per the locked protocol): implementation of restart-free reinitialization is
STOPPED pending a maintainer decision.** The database-identity invariant ("reuse the exact same
`AppDatabase` object") and in-process DB-file-swap restore cannot coexist on Room 3 — `close()` is
terminal for the object, and every swap path must close before renaming. The §8 architecture
remains internally consistent for the no-swap graph-generation class (it never closes the DB), but
the phase's reinitialization foundation cannot honestly claim to serve the restore/rollback flows
in-process, which is what the `AppReinitializer` common KDoc promises iOS. Options for the
maintainer are recorded in the phase STOP report (PR description).

## 8. Architecture

### Options considered

- **A (chosen): application-owned runtime host outside the Metro graph.** A single `AppRuntime`
  owns the process-lifetime roots (DB, ImageStorage) and a serialized sequence of
  `GraphGeneration(id, graph, lifetime, viewModelStore)` values published atomically. The swap
  authority sits outside the thing being swapped; Metro stays the only DI framework; the graph
  interface is unchanged except one added `create()` root.
- **B (rejected): graph-owned self-replacement** — a `@SingleIn(AppScope)` generation manager
  inside the graph builds its successor. Rejected: the manager would have to outlive its own graph
  (self-referential lifetime), every in-graph reader could capture its own generation, and test
  swapping would need the graph to be told about itself. Strictly worse ownership.
- **C (rejected as the whole answer): process-restart forever + iOS `exit(0)`** — no in-process
  mechanism at all. Rejected: contradicts the Phase 5 mandate and the measured need (the leaked
  scopes/collectors exist regardless); retained only as the §7-red fallback **for the DB-swap
  restore class**, where it is today's shipped Android behavior anyway.

### 8.1 `AppRuntime` (new, `app/app` `runtime/`, internal)

Owns: `applicationContext`; lazy process `AppDatabase` (production factory, cold); lazy process
`ImageStorage`; the generation counter; the current `GraphGeneration`; a `Mutex` for single-flight
reinitialization; `generations: StateFlow<GraphGeneration>`. Construction is factory-parameterized
(`dbFactory`, `imageStorageFactory`, `graphFactory`) so the androidTest harness can install a
runtime over in-memory roots without touching production factories.

`GraphGeneration` = `(id: Int, graph: AppGraph, lifetime: AppScopeLifetime, viewModelStore: ViewModelStore)`.

Implements `AppReinitializationHost` (§8.7) — the compile-checked proof that the common intent
contract fits the real mechanism.

### 8.2 `AppScopeLifetime` (new, `core:core` commonMain)

```kotlin
class AppScopeLifetime(parent: Job? = null) {
    val job: CompletableJob = SupervisorJob(parent)
    fun childScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob(job) + dispatcher)
    fun cancel()
}
```

Enters the graph as the **4th `create()` bound-instance root** (`AppGraph.Factory.create` gains
`appScopeLifetime: AppScopeLifetime`; threaded through `buildAppGraph`; `MetroTestRule` passes a
per-test lifetime it cancels in `after()`; the 14 identity tests pass one explicitly). Consumers
replace their self-created scopes:

- `RestoreDialogChoiceObserver.kt:91` → `lifetime.childScope(defaultDispatcher)`;
- `DriveBackupAuth.kt:70` → `lifetime.childScope(ioDispatcher)`;
- `SnapshotExportRunnerImpl.kt:59` → `lifetime.childScope(ioDispatcher)`;
- the two `BaseApplication` chores run on the generation lifetime via the startup processor.

Per-consumer `SupervisorJob(parent)` children preserve today's isolation semantics exactly while
making `lifetime.cancel()` deterministically stop every generation-owned job and collector.
No anonymous scope remains in production (`git grep "CoroutineScope("` exit criterion, §13).

### 8.3 `StartupProcessor` (new, `app/app` `runtime/`, internal)

The extracted, typed form of `onCreateGraphBootstrap` — one implementation, two entry modes:

```kotlin
sealed interface StartupOutcome {
    data object Proceed : StartupOutcome
    data object RouteToRecovery : StartupOutcome          // decision cached on the coordinator, as today
    data object RestartRequired : StartupOutcome          // Scenario-1 RestoreRolledBack
}
```

Stages (identical order and semantics to today): Scenario 1 → (short-circuit on `RestoreSucceeded`
/ `RestoreRolledBack`) → Scenario 2 → chores (image cleanup; planner warm-up guarded by
`RouteToRecovery` + injected `isLowRamDevice: () -> Boolean`) → dialog-observer arming (eager
`recoveryBootstrap` read). Chores launch on the **generation lifetime**, still fire-and-forget,
still no reader waits on them.

- **Cold start** (`BaseApplication.onCreate`): generation 1 is published on build (as today — the
  graph exists before preflight because preflight *uses* it; UI cannot observe it before
  `onCreate` returns), preflight runs under the two existing `runBlocking` boundaries,
  `RestartRequired` maps to `restartApp()` (process exit) — byte-equivalent behavior.
- **Reinitialize** (suspend, off-main): candidate generation is built **unpublished**, the same
  processor runs against the candidate (no `runBlocking` — the caller is already suspending),
  and only a `Proceed`/`RouteToRecovery`-free pass publishes. `RestartRequired` in reinit mode
  triggers **exactly one** bounded retry (rebuild candidate against the rolled-back file, re-run);
  a second non-`Proceed` fails the reinitialization, the old generation stays published and
  serving. No recursion, no unbounded retry.

### 8.4 Reinitialization order (the state machine's happy path)

1. `mutex.lock` — single-flight. A caller passing a stale expected-generation returns the already-
   published newer generation (coalescing; no queued double-reinit).
2. Build candidate `GraphGeneration(id+1)` over the **same** `AppDatabase`/`ImageStorage` roots,
   fresh `AppScopeLifetime`, fresh `ViewModelStore`. Not published.
3. Run `StartupProcessor` against the candidate (preflight → chores → observer arming). Failure →
   cancel candidate lifetime, clear candidate VM store, bounded retry per §8.3, else abort with the
   old generation intact.
4. Publish atomically: single reference/`StateFlow` write of the whole immutable generation value —
   readers see old or new, never a mix.
5. UI re-keys (§8.5): old composition region leaves composition (Store `dispose()` runs via the
   existing `DisposableEffect`s).
6. Dispose old generation: `viewModelStore.clear()` (deterministic `onCleared` for
   `AppRootViewModel` + `AppDialogStoreImpl`), then `lifetime.cancel()` (reactor, auth mirror,
   export scope, any still-running chores). Overlap between old-observer-alive and
   new-observer-armed is harmless by bus identity (§3, reactor row); ordering arms the new observer
   before the old dies so no window has zero armed reactors.
7. `mutex.unlock`.

Production Android never calls this (locked invariant): the only callers are androidTest and the
future Phase 7 iOS host. Settings/recovery restart paths keep `AppReinitializer` untouched.

### 8.5 UI generation boundary (`app:common`, narrowest seam)

New holder in `app:common` following the existing typed-holder idiom (`AppRootDepsHolder`):

```kotlin
class AppUiGeneration(val id: Int, val viewModelStoreOwner: ViewModelStoreOwner)
interface AppUiGenerationsHolder { val appUiGenerations: StateFlow<AppUiGeneration> }
```

`BaseApplication` implements it from the runtime (mapping `GraphGeneration` → `AppUiGeneration`);
`App()` collects it and wraps its existing body in:

```kotlin
key(generation.id) {
    CompositionLocalProvider(LocalViewModelStoreOwner provides generation.viewModelStoreOwner) { …existing body… }
}
```

Consequences, each pinned by a test (§12):

- fresh `rememberNavBackStack` → the new generation starts at `Screen.BottomBar.Home`; the old
  stack object is gone; Back cannot reach it;
- old saved navigation entries are not restored: gen-N state saves under the `key(N)` slot path,
  which gen-N+1 never composes;
- `AppRootViewModel` and `AppDialogStoreImpl` resolve from the **generation's** ViewModelStore →
  new instances constructed from the new graph's deps (the `viewModel {}` factory re-reads
  `appRootDeps()`, `AppDialogFeature` re-reads `appDeps<…>()`), old ones deterministically cleared
  in §8.4 step 6 — no idle-VM residue in the Activity store;
- per-entry Stores are unaffected mechanically (NavDisplay's decorators override the local
  underneath) and die with their entries;
- ordinary Activity recreation mid-generation: runtime survives, same generation id, same VM store
  → identical retention behavior to today (the store moved from Activity to runtime; both survive
  recreation; both die with the process);
- process death: generation counter restarts at 1, `key(1)` matches the path gen-1 saved under →
  the existing restoration oracle (`BackStackStateRestorationTest`) is unchanged. Saved state from
  a >1 generation (only reachable in instrumented runs / future iOS) is intentionally not restored
  after process death — recorded property, not production-reachable on Android.

No new CompositionLocal is introduced — `LocalViewModelStoreOwner` is the standard androidx local,
provided with a runtime-owned owner (the same pattern NavDisplay itself uses per entry). The
`AppUiGenerationsHolder` interface carries no graph and no navigator.

### 8.6 `MetroWorkerFactory` de-capture

Drop the `by lazy` deps field; read `(appContext as BackupWorkerDepsHolder).backupWorkerDeps()`
inside `createWorker` per invocation. Behavior-identical in production (same single graph);
generation-correct after a swap. `MetroWorkerFactoryTest` extended to pin per-call re-read.

### 8.7 `AppReinitializer` boundary — honest resolution

- commonMain gains `interface AppReinitializationHost { fun requestReinitialize() }` — the
  root-bound intent contract (`core:core`, no dependency on anything above it).
- androidMain actual: **unchanged process restart** (locked invariant).
- iosMain actual becomes `actual class AppReinitializer(private val host: AppReinitializationHost)`
  delegating to the host — the unconditional `TODO()` is eliminated **without** a silent no-op:
  constructing the iOS reinitializer without a real host is impossible by signature, and no Phase 5
  code constructs one. (Precedent: the androidMain actual already has a ctor the expect does not
  declare, so this compiles under the existing expect.)
- `AppRuntime : AppReinitializationHost` on Android proves the contract against the real in-process
  mechanism; production Android keeps binding the process-restart `AppReinitializer` — no consumer
  changes, no `core:core → app` edge (app implements a core interface, the legal direction).
- **Phase 7 handoff (explicit non-claim):** no runnable iOS restore flow exists after Phase 5. The
  iOS host must (a) construct the iOS DB + composition root, (b) bind `AppRuntime`-equivalent as
  the `AppReinitializationHost`, and (c) resolve the DB-swap question per the §7 gate outcome —
  green: same-object reopen is proven and the restore flow may go in-process; red: choose between
  a DB-generation ownership extension (relaxing the same-object invariant for the restore class
  only, maintainer decision) or `exit(0)`-style relaunch UX.

## 9. Concurrency and failure semantics

- Publication: one volatile/`StateFlow` write of an immutable value — readers never observe a mix.
- Single-flight: one `Mutex`; concurrent `reinitialize()` calls serialize; stale-expected callers
  coalesce onto the already-published result.
- Candidate failure: candidate lifetime cancelled + VM store cleared; old generation untouched;
  at most one retry (only on `RestartRequired`).
- Old-generation disposal is unconditional and idempotent (`cancel()` on a cancelled lifetime and
  `clear()` on a cleared store are no-ops).
- The cold-start `runBlocking` boundaries are untouched (locked).
- Chore failure policy unchanged: cleanup unguarded-best-effort, planner `runCatching`+log.

## 10. Locked invariants — preservation map

| Invariant | How preserved |
|---|---|
| Android prod `AppReinitializer` = process restart; no silent switch | androidMain actual untouched; `AppRuntime.reinitialize` has zero production callers (grep-pinned in review) |
| Same `AppDatabase` object; no replacement; no DAO/repo swappability | DB is a process root owned by the runtime, passed to every generation's `create()`; generation swap never closes it; §7 gate covers the captured-DAO question for the swap class |
| DataStore state (3 files) + dialog survival + exactly-once consumption | process-lifetime memoization untouched; `pending_*` flags persisted; dismiss-after discipline untouched; pinned by existing + new tests |
| Recovery ordering (S1→S2, short-circuits, blocking boundaries, no Room on `RouteToRecovery`, observer-before-Activity, TestApplication bypass) | `StartupProcessor` is an order-preserving extraction; cold-start mode keeps both `runBlocking`s; planner guard logic moved verbatim; `TestApplication.onCreateGraphBootstrap` no-op seam retained |
| Startup jobs owned; no anonymous scopes; planner semantics (after preflight, never on recovery, off-main, best-effort, low-RAM skip, no mandatory wait) | §8.2/§8.3 |
| Graph generations serialized; no mixed reads; candidate-not-published-before-preflight; deterministic disposal; no duplicate observers; coalesced concurrency | §8.4/§9 |
| Nav3 canonical; no Nav2/`ResetToRoot` assumptions; root reset + no-Back + no-resurrection + Store release + new-graph deps + unchanged ordinary restoration; no graph/navigator CompositionLocals | §8.5 (measured: `NavCommand` has 5 variants, no `ResetToRoot` — `NavCommand.kt:4-24`) |
| Metro-only DI; module boundaries; `core:core` ↛ app | §8.2 root + §8.7 direction |

## 11. Red/green gates

**Entry (before implementation relies on restart-free reinit):** §7 device gate. Red → STOP
protocol. **Exit:** all of — `assembleDebug`, `testDebugUnitTest`, `verifyPaparazziDebug`
(zero movers), `lintDebug`, `assembleDebugAndroidTest` under
`--rerun-tasks --no-build-cache --no-configuration-cache`; detekt in a separate forced run;
`:lint-rules:test`; connected **Regression** + **Smoke** suites via the `ui_tests.yml` invocation
(`connectedDebugAndroidTest -P…annotation=…Smoke|Regression`); affected `iosSimulatorArm64`
compile+KSP tasks (`compileKotlinIosSimulatorArm64` on `core:core` at minimum — the only KMP module
this phase touches); iOS link/runtime **UNVERIFIED** (no host exists — reported as such, never as
passed); CI green on the single draft PR. Task-execution counts reported; UP-TO-DATE/FROM-CACHE
runs are not evidence.

## 12. Test plan (required list → concrete tests)

| Requirement | Test (level) |
|---|---|
| same-object/same-DAO bundled-driver reopen (+ restore vs rollback branches, disk truth, known-negative) | `SameInstanceReopenAfterSwapDeviceTest` (device, §7) |
| two generations, exact same `AppDatabase`; distinct graph/navigator identities | `AppRuntimeGenerationsTest` (JVM, buildAppGraph-based like the identity tests) |
| DataStore memoization across two graphs | existing `AppScopeDataStoreSingletonTest` (androidTest) + runtime-driven variant |
| old lifetime cancelled, new active; exactly one active dialog/recovery observer; one choice → one reaction | `AppScopeLifetimeTest` + `RestoreDialogChoiceObserver` generation tests (JVM) |
| pending restore-success/undo dialog state surviving reinit | androidTest reinit suite (DataStore flag set → reinit → dialog resolves once) |
| S1→S2 ordering; S1 short-circuits S2; rollback → bounded single retry; `RouteToRecovery` avoids Room open/warm-up; low-memory skip | `StartupProcessorTest` (JVM, injected coordinators/failure controls) |
| UI generation not published before preflight | `AppRuntimeReinitializeTest` (JVM, gated fake preflight) |
| TestApplication does not construct the production graph | existing harness design + explicit pin |
| concurrent reinitialization | `AppRuntimeConcurrencyTest` (JVM) |
| Nav3 root reset, old-stack removal, Store disposal, no resurrection after Activity recreation; restoration oracle unchanged | androidTest reinit suite + existing `BackStackStateRestorationTest` untouched |
| mutation/injected-failure controls | §7 swap-bypass; ordering inversion via fake coordinator; lifetime-cancellation negative (assert leak when cancel skipped, in-test only); generation-reset negative (no `key()` → old VM observed — encoded as identity assertions); dialog double-consumption negative |

## 13. Commit decomposition (single draft PR to `dev`)

1. `docs(kmp): specify phase 5 startup lifecycle` — this file.
2. `test(database): prove same-instance reopen after snapshot swap` — §7 gate + measured evidence;
   content pins the measured truth whichever way the gate lands.
3. `refactor(runtime): introduce graph generations and owned lifetimes` — `AppScopeLifetime` root,
   `AppRuntime`, scope migrations (observer/auth/export), worker-factory de-capture, JVM tests.
4. `refactor(startup): extract the startup processor` — `StartupProcessor` + `StartupOutcome`,
   `BaseApplication.onCreate` delegation, ordering tests.
5. `feat(runtime): make application state generation-aware` — `AppUiGenerationsHolder`, `App()`
   keying, `AppReinitializer` iOS contract, androidTest reinit suite.
6. `docs(kmp): record phase 5 evidence and phase 7 handoff` — §15 corrections + gate evidence.

Each commit bisect-green; behavior-specific tests ride with their behavior commit.

## 14. Rollback / reversibility

Every commit is independently revertible: 3–5 layer strictly on top of existing seams (the five
holder interfaces, the `create()` factory, the `protected open` bootstrap seam) without deleting
any; reverting 5 restores today's unkeyed `App()`; reverting 3+4 restores the anonymous scopes and
inline bootstrap verbatim (the extraction is order-preserving). The spec and gate test carry no
runtime coupling. No migration, no persisted-format change, no golden change anywhere in the plan.

## 15. Stale-claim register (docs closeout, commit 6)

1. `kmp-migration-assessment.md:546-553` — Room 2.8.4 reopen claims + Nav2 `ResetToRoot`/
   `NavController` reinit order + never-run spike → dated supersession note pointing here + gate result.
2. `core/core` iosMain `AppReinitializer` KDoc "three DataStore-memoization bypasses" — fixed in
   Phase 6; reword with the §8.7 contract.
3. Phase 4 spec startup inventory — dated supersession note (predates `warmQueryPlanner`).
4. `app/app/src/main/AndroidManifest.xml:54-62` — Hilt/`HiltWorkerFactory`/`@AssistedInject`
   comment vs Metro reality.
5. `DatabaseSnapshotProvider.kt:44-47` ("caller MUST tear down every consumer" — no caller does),
   `:52-54` ("via Room's open helper" — Room 3 has none), `:106-109` + `UndoRestoreOutcome.kt:20`
   ("atomic rename" — actually copy+rename+delete), `:150-151` (pre-migration WAL-checkpoint claim).
6. `documentation/ci-cd.md:342-345` + `lint-rules.md:724-731` — pre-commit hook described as
   disabled/copied; measured: `core.hooksPath`, detekt runs on staged `.kt`.
7. `AppFeature.kt:13,18-23`, `Feature.kt:20`, `FeatureAssisted.kt:21`, `MetroStoreProcessor.kt:17`
   — Nav2-era `NavHost`/`NavController`/`NavBackStackEntry` wording; `NavigatorEventBus.kt:29`
   ("injects" — hand-constructed); `AppDialogHost.kt:28` ("@Singleton"); coordinator/repository
   KDoc caller claims (`RestoreRecoveryCoordinator.kt:32-33`, `AppDialogRepository.kt:72-74`).
8. `documentation/performance.md` `AppCreated` boundary — verify against the measured stage list
   (§2 stage 6: `AppCreated` fires **after** the graph bootstrap, so the trace includes preflight).
9. `android_build_unified.yml:146` "13 golden-holding modules" — 14 apply Paparazzi (not a doc file;
   recorded here, not edited — CI text change out of scope).
10. Unverified-claims register at implementation end: iOS link/runtime behavior of the reworked
    iosMain actual (compile-verified only — no host); `key()`-wrapper interaction with saved-state
    on OEM builds beyond the tested emulator (covered only by API-34 devices this phase).

## 16. Independent architecture review (2026-08-22): CONFIRM, with implementation conditions

A fresh-context adversarial review of this spec against the locked invariants returned
**CONFIRM — no invariant relaxed**, after probing (and failing to break) the `key()`/saved-state
reasoning, the observer-overlap-by-bus-identity argument, the cold-start publication argument
(manifest has no graph-reading providers), the two-captures inventory, the expect/actual ctor
precedent, and the no-locked-file/no-detekt-trigger checks. Binding conditions for the
implementation commits (now gated on the §7.1 maintainer decision):

1. **§8.4 steps 5→6 need an explicit ordering mechanism.** Old-generation disposal must be gated
   on the old UI region actually leaving composition (e.g. a `DisposableEffect(generation)` inside
   the `key(generation.id)` region signalling the runtime, or awaiting the new generation's first
   applied frame). Clearing the VM store while the old region can still recompose would re-run
   `viewModel()` factories against the cleared store and resolve the NEW graph inside the OLD
   region — a transient mixed-generation read.
2. **Reinit-mode `RestartRequired` is test-scoped while the §7 gate is red.** A real
   `restore_in_progress` encountered by in-process reinit must **abort without invoking the
   rollback swap** (the swap would close the process `AppDatabase` and strand both candidate and
   old generation on a terminally-closed object). The bounded-retry semantics are exercised via
   injected preflight failures only.
3. **The androidTest harness seam must be specified for `AppUiGenerationsHolder`.** `TestApplication`
   bypasses the runtime; without a published generation every instrumented UI test fails to
   compose. Either `MetroTestRule` installs a test runtime, or `BaseApplication`'s holder derives
   the generation lazily from whatever `appGraph` override is current.

Notes folded into the plan: the 4th-`create()`-root fan-out also includes
`AccountDataStoreSingletonTest` and `AppScopeDataStoreSingletonTest` (androidTest `buildAppGraph`
callers) beyond the 14 JVM tests; `MetroWorkerFactory.createWorker` must read the holder **once**
per invocation (all six deps from one returned graph — no torn cross-generation worker) and the
test extension must swap holder deps between calls; after a mid-Activity reinit the new
generation's `ActivityHolderImpl` is empty until the next Activity lifecycle event (currently
zero production readers — record or re-register on publish); §15 gains `AppFeature.kt:16-19` and
`AppDialogFeature.kt:23-25` ("LocalViewModelStoreOwner is the host ComponentActivity") as stale
after §8.5.
