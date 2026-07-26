# Read-only — per-graph dependency matrix (for splitting AppGraphContract)

**HEAD SHA:** `d54129ddce24dfda5ec00035a8c4aa633835db9b` — confirmed tip of `feature/metro-batch`.
**Working tree:** DIRTY (22 `git status --porcelain` entries, from the intact `spike/metro-kmp-extension` task). **All reads are against the git ref `feature/metro-batch`** (`git grep feature/metro-batch`, `git show feature/metro-batch:…`), NOT the working tree. Sweeps exclude `*/build/*` and `*/.claude/worktrees/*`.
Facts only — no groupings, interfaces, or design.

> ⚠️ **CORRECTION, standing (2026-07-26) — every call site tabulated here is pinned to `d54129dd` and
> several no longer exist.** The `AppGraphContract` god-object was deleted by the split this matrix
> fed, and the per-consumer interfaces that replaced it were themselves mostly deleted by the later
> graph-extension arc: the 11 feature `XDeps` plus the `StoreCoreDeps` / `NavigatorDeps` spine are gone
> (`7f48093f`), leaving `RecoveryDeps` + `BackupWorkerDeps`. Two specifics for anyone grepping from
> here:
>
> 1. The `graph.<accessor>` bound-instance lists that STEP 2 derives each consumed set from are gone.
>    A feature's extension inherits those bindings; the only bound instance still passed to a factory
>    is a route arg (shape B).
> 2. The excluded non-contract arg `appDialogPublisher = context.appDialogPublisher()` no longer
>    exists — `AppDialogPublisherHolder` and its `Context` accessor were deleted in the same commit,
>    and consumers take `AppDialogPublisher` as a constructor dep instead.
>
> The tables below are left exactly as measured at `d54129dd`.

---

## STEP 1 — The `AppGraphContract` accessor universe (32 accessors)

Source: `core/di/src/main/kotlin/io/github/stslex/workeeper/core/di/AppGraphContract.kt` (line numbers from the ref).

| # | line | accessor | return type | owning module |
|---|---|---|---|---|
| 1 | :57 | `analyticsHolder` | `AnalyticsHolder` | core:ui:mvi |
| 2 | :58 | `loggerHolder` | `LoggerHolder` | core:ui:mvi |
| 3 | :59 | `storeDispatchers` | `StoreDispatchers` | core:ui:mvi |
| 4 | :63 | `defaultDispatcher` (`@DefaultDispatcher`) | `CoroutineDispatcher` | core:core |
| 5 | :66 | `mainImmediateDispatcher` (`@MainImmediateDispatcher`) | `CoroutineDispatcher` | core:core |
| 6 | :69 | `ioDispatcher` (`@IODispatcher`) | `CoroutineDispatcher` | core:core |
| 7 | :72 | `resourceWrapper` | `ResourceWrapper` | core:core |
| 8 | :73 | `platformInfoProvider` | `PlatformInfoProvider` | core:core |
| 9 | :74 | `tempFileProvider` | `TempFileProvider` | core:core-android |
| 10 | :75 | `appReinitializer` | `AppReinitializer` | core:core |
| 11 | :79 | `imageStorage` | `ImageStorage` | core:core |
| 12 | :83 | `sessionConflictResolver` | `SessionConflictResolver` | core:data:exercise |
| 13 | :86 | `navigator` | `Navigator` | core:ui:navigation |
| 14 | :89 | `backupAuth` | `BackupAuth` | core:data:backup:api |
| 15 | :90 | `backupStorage` | `BackupStorage` | core:data:backup:api |
| 16 | :91 | `snapshotExportRunner` | `SnapshotExportRunner` | core:data:backup:api |
| 17 | :92 | `restoreStateRepository` | `RestoreStateRepository` | core:data:backup:api |
| 18 | :93 | `autoBackupController` | `AutoBackupController` | core:data:backup:api |
| 19 | :94 | `backupPreferencesRepository` | `BackupPreferencesRepository` | core:data:backup:api |
| 20 | :95 | `backupNotificationHelper` | `BackupNotificationHelper` | core:data:backup:api |
| 21 | :98 | `recoveryDiagnosticsExporter` | `RecoveryDiagnosticsExporter` | core:data:backup:api |
| 22 | :101 | `commonDataStore` | `CommonDataStore` | core:data:dataStore |
| 23 | :104 | `databaseSnapshotProvider` | `DatabaseSnapshotProvider` | core:data:database |
| 24 | :105 | `liveDatabaseLocator` | `LiveDatabaseLocator` | core:data:database |
| 25 | :108 | `exerciseRepository` | `ExerciseRepository` | core:data:exercise |
| 26 | :109 | `sessionRepository` | `SessionRepository` | core:data:exercise |
| 27 | :110 | `setRepository` | `SetRepository` | core:data:exercise |
| 28 | :111 | `tagRepository` | `TagRepository` | core:data:exercise |
| 29 | :112 | `personalRecordRepository` | `PersonalRecordRepository` | core:data:exercise |
| 30 | :113 | `performedExerciseRepository` | `PerformedExerciseRepository` | core:data:exercise |
| 31 | :114 | `trainingExerciseRepository` | `TrainingExerciseRepository` | core:data:exercise |
| 32 | :115 | `trainingRepository` | `TrainingRepository` | core:data:exercise |

(Owning module resolved from the import in `AppGraphContract.kt`; `tempFileProvider` is the sole `core:core-android` type, the rest split across core:core / core:ui:* / core:data:*.)

---

## STEP 2 — Per-reader consumed set (the matrix)

Method: each `*Feature.kt` reader's consumed set == the accessors passed into its `createGraphFactory<XGraph.Factory>().create(...)` bound-instance list (`graph.<accessor>`). RecoveryActivity + MetroWorkerFactory read directly. `path:line` = the `.create(` call site (or the direct-read lines). Non-contract args (`context`, `appDialogPublisher = context.appDialogPublisher()`) are excluded — they don't come off the contract.

### Per-reader consumed lists (sorted)

| Reader | count | `create(...)` / read site | consumed accessors |
|---|---|---|---|
| **AllExercises** | 8 | `AllExercisesFeature.kt:35` | analyticsHolder, defaultDispatcher, exerciseRepository, loggerHolder, navigator, resourceWrapper, storeDispatchers, tagRepository |
| **AllTrainings** | 8 | `AllTrainingsFeature.kt:34` | analyticsHolder, defaultDispatcher, loggerHolder, navigator, resourceWrapper, storeDispatchers, tagRepository, trainingRepository |
| **AppDialog** | 3 | `AppDialogFeature.kt:41` | analyticsHolder, loggerHolder, storeDispatchers |
| **Archive** | 8 | `ArchiveFeature.kt:38` | analyticsHolder, defaultDispatcher, exerciseRepository, loggerHolder, navigator, resourceWrapper, storeDispatchers, trainingRepository |
| **ExerciseChart** | 8 | `ExerciseChartFeature.kt:39` | analyticsHolder, defaultDispatcher, exerciseRepository, loggerHolder, navigator, resourceWrapper, sessionRepository, storeDispatchers |
| **Exercise** | 13 | `ExerciseFeature.kt:36` | analyticsHolder, defaultDispatcher, exerciseRepository, imageStorage, loggerHolder, mainImmediateDispatcher, navigator, personalRecordRepository, resourceWrapper, sessionRepository, storeDispatchers, tagRepository, trainingRepository |
| **Home** | 9 | `HomeFeature.kt:35` | analyticsHolder, defaultDispatcher, loggerHolder, navigator, resourceWrapper, sessionConflictResolver, sessionRepository, storeDispatchers, trainingRepository |
| **ImageViewer** | 4 | `ImageViewerFeature.kt:38` | analyticsHolder, loggerHolder, navigator, storeDispatchers |
| **LiveWorkout** | 13 | `LiveWorkoutFeature.kt` (create block :40–52) | analyticsHolder, defaultDispatcher, exerciseRepository, loggerHolder, navigator, performedExerciseRepository, personalRecordRepository, resourceWrapper, sessionRepository, setRepository, storeDispatchers, trainingExerciseRepository, trainingRepository |
| **PastSession** | 9 | `PastSessionFeature.kt` (create block :40–48) | analyticsHolder, ioDispatcher, loggerHolder, navigator, personalRecordRepository, resourceWrapper, sessionRepository, setRepository, storeDispatchers |
| **PlanEditor** | 8 | `PlanEditorFeature.kt` (create block :39–46) | analyticsHolder, defaultDispatcher, exerciseRepository, loggerHolder, navigator, resourceWrapper, storeDispatchers, trainingExerciseRepository |
| **Settings** | 16 | `SettingsFeature.kt` (create block :42–58) | analyticsHolder, autoBackupController, backupAuth, backupPreferencesRepository, backupStorage, commonDataStore, databaseSnapshotProvider, defaultDispatcher, ioDispatcher, loggerHolder, navigator, platformInfoProvider, restoreStateRepository, snapshotExportRunner, storeDispatchers, tempFileProvider |
| **SingleTraining** | 13 | `SingleTrainingFeature.kt` (create block :43–55) | analyticsHolder, defaultDispatcher, exerciseRepository, loggerHolder, mainImmediateDispatcher, navigator, resourceWrapper, sessionConflictResolver, sessionRepository, storeDispatchers, tagRepository, trainingExerciseRepository, trainingRepository |
| **RecoveryActivity** | 2 | `RecoveryActivity.kt:69` + `:71` (direct reads) | databaseSnapshotProvider, recoveryDiagnosticsExporter |
| **MetroWorkerFactory** | 6 | `MetroWorkerFactory.kt:40–45` (direct reads) | autoBackupController, backupNotificationHelper, backupPreferencesRepository, backupStorage, databaseSnapshotProvider, snapshotExportRunner |

### ✓-matrix (rows = 15 readers · columns = 30 consumed accessors)

Legend for columns (abbreviated): AH=analyticsHolder LH=loggerHolder SD=storeDispatchers NAV=navigator DD=defaultDispatcher RW=resourceWrapper ER=exerciseRepository SR=sessionRepository TR=trainingRepository TAG=tagRepository DSP=databaseSnapshotProvider PRR=personalRecordRepository TER=trainingExerciseRepository ABC=autoBackupController BPR=backupPreferencesRepository BS=backupStorage IO=ioDispatcher MID=mainImmediateDispatcher SCR=sessionConflictResolver SETR=setRepository SER=snapshotExportRunner BA=backupAuth BNH=backupNotificationHelper CDS=commonDataStore IMG=imageStorage PER=performedExerciseRepository PIP=platformInfoProvider RDE=recoveryDiagnosticsExporter RSR=restoreStateRepository TFP=tempFileProvider

```
Reader           AH LH SD NAV DD RW ER SR TR TAG DSP PRR TER ABC BPR BS IO MID SCR SETR SER BA BNH CDS IMG PER PIP RDE RSR TFP | n
AllExercises      ✓  ✓  ✓  ✓  ✓  ✓  ✓  .  .  ✓   .   .   .   .   .  .  .  .   .   .    .  .   .   .   .   .   .   .   .   .  | 8
AllTrainings      ✓  ✓  ✓  ✓  ✓  ✓  .  .  ✓  ✓   .   .   .   .   .  .  .  .   .   .    .  .   .   .   .   .   .   .   .   .  | 8
AppDialog         ✓  ✓  ✓  .  .  .  .  .  .  .   .   .   .   .   .  .  .  .   .   .    .  .   .   .   .   .   .   .   .   .  | 3
Archive           ✓  ✓  ✓  ✓  ✓  ✓  ✓  .  ✓  .   .   .   .   .   .  .  .  .   .   .    .  .   .   .   .   .   .   .   .   .  | 8
ExerciseChart     ✓  ✓  ✓  ✓  ✓  ✓  ✓  ✓  .  .   .   .   .   .   .  .  .  .   .   .    .  .   .   .   .   .   .   .   .   .  | 8
Exercise          ✓  ✓  ✓  ✓  ✓  ✓  ✓  ✓  ✓  ✓   .   ✓   .   .   .  .  .  ✓   .   .    .  .   .   .   ✓   .   .   .   .   .  | 13
Home              ✓  ✓  ✓  ✓  ✓  ✓  .  ✓  ✓  .   .   .   .   .   .  .  .  .   ✓   .    .  .   .   .   .   .   .   .   .   .  | 9
ImageViewer       ✓  ✓  ✓  ✓  .  .  .  .  .  .   .   .   .   .   .  .  .  .   .   .    .  .   .   .   .   .   .   .   .   .  | 4
LiveWorkout       ✓  ✓  ✓  ✓  ✓  ✓  ✓  ✓  ✓  .   .   ✓   ✓   .   .  .  .  .   .   ✓    .  .   .   .   .   ✓   .   .   .   .  | 13
PastSession       ✓  ✓  ✓  ✓  .  ✓  .  ✓  .  .   .   ✓   .   .   .  .  ✓  .   .   ✓    .  .   .   .   .   .   .   .   .   .  | 9
PlanEditor        ✓  ✓  ✓  ✓  ✓  ✓  ✓  .  .  .   .   .   ✓   .   .  .  .  .   .   .    .  .   .   .   .   .   .   .   .   .  | 8
Settings          ✓  ✓  ✓  ✓  ✓  .  .  .  .  .   ✓   .   .   ✓   ✓   ✓  ✓  .   .   .    ✓  ✓   .   ✓   .   .   ✓   .   ✓   ✓  | 16
SingleTraining    ✓  ✓  ✓  ✓  ✓  ✓  ✓  ✓  ✓  ✓   .   .   ✓   .   .  .  .  ✓   ✓   .    .  .   .   .   .   .   .   .   .   .  | 13
RecoveryActivity  .  .  .  .  .  .  .  .  .  .   ✓   .   .   .   .  .  .  .   .   .    .  .   .   .   .   .   .   ✓   .   .  | 2
MetroWorkerFactory .  .  .  .  .  .  .  .  .  .   ✓   .   .   ✓   ✓   ✓ .  .   .   .    ✓  .   ✓   .   .   .   .   .   .   .  | 6
```

---

## STEP 3 — Overlap facts (raw)

### Exact-match clusters (byte-identical consumed set)
**NONE.** All 15 readers have a **unique** consumed set (no two are identical). Every reader is a singleton by exact match.

### Near-match clusters (symmetric difference 1–2 accessors)
- **AppDialog vs ImageViewer** — diff = **{navigator}** (1). AppDialog{AH,LH,SD} ⊂ ImageViewer{AH,LH,SD,NAV}. *(AppDialog's 3 are a strict subset of ImageViewer's 4.)*
- **AllExercises vs AllTrainings** — diff = {exerciseRepository, trainingRepository} (2); both size 8, share {AH,LH,SD,NAV,DD,RW,TAG}.
- **AllExercises vs Archive** — diff = {tagRepository, trainingRepository} (2).
- **AllExercises vs ExerciseChart** — diff = {sessionRepository, tagRepository} (2).
- **AllExercises vs PlanEditor** — diff = {tagRepository, trainingExerciseRepository} (2).
- **AllTrainings vs Archive** — diff = {exerciseRepository, tagRepository} (2).
- **Archive vs ExerciseChart** — diff = {sessionRepository, trainingRepository} (2).
- **Archive vs PlanEditor** — diff = {trainingExerciseRepository, trainingRepository} (2).
- **ExerciseChart vs PlanEditor** — diff = {sessionRepository, trainingExerciseRepository} (2).

Observation (structure only): the size-8 readers {AllExercises, AllTrainings, Archive, ExerciseChart, PlanEditor} form a tight near-match web — each shares the same 6-accessor spine {AH,LH,SD,NAV,DD,RW} and differs only in 2 repository picks.

### Subset relationships (one reader's set fully contained in another's)
- AppDialog {AH,LH,SD} ⊆ ImageViewer, and ⊆ every reader that has all of {AH,LH,SD} (all features except Recovery/Worker).
- ImageViewer {AH,LH,SD,NAV} ⊆ every reader carrying {AH,LH,SD,NAV} (all 12 nav-features that include navigator).

### Universally-consumed accessors (the "spine" — read by ≥80% = ≥12 of 15)
| accessor | freq | note |
|---|---|---|
| analyticsHolder | 13/15 | absent only in RecoveryActivity, MetroWorkerFactory |
| loggerHolder | 13/15 | absent only in RecoveryActivity, MetroWorkerFactory |
| storeDispatchers | 13/15 | absent only in RecoveryActivity, MetroWorkerFactory |
| navigator | 12/15 | absent in AppDialog, RecoveryActivity, MetroWorkerFactory |

(The 13 that read AH/LH/SD are exactly the 13 `*Feature.kt`; the 2 that don't are the non-feature readers. navigator = those 13 minus AppDialog.)

### Mid-frequency accessors (read by 3–10)
defaultDispatcher 10/15 · resourceWrapper 10/15 · exerciseRepository 7/15 · sessionRepository 6/15 · trainingRepository 6/15 · tagRepository 4/15 · databaseSnapshotProvider 3/15 · personalRecordRepository 3/15 · trainingExerciseRepository 3/15.

### Narrow accessors (read by 1–2 readers)
- **2/15:** autoBackupController (Settings, Worker) · backupPreferencesRepository (Settings, Worker) · backupStorage (Settings, Worker) · ioDispatcher (PastSession, Settings) · mainImmediateDispatcher (Exercise, SingleTraining) · sessionConflictResolver (Home, SingleTraining) · setRepository (LiveWorkout, PastSession) · snapshotExportRunner (Settings, Worker).
- **1/15:** backupAuth (Settings) · backupNotificationHelper (Worker) · commonDataStore (Settings) · imageStorage (Exercise) · performedExerciseRepository (LiveWorkout) · platformInfoProvider (Settings) · recoveryDiagnosticsExporter (RecoveryActivity) · restoreStateRepository (Settings) · tempFileProvider (Settings).

### Dead surface — accessors on the contract consumed by ZERO of the 15 readers
- **`appReinitializer`** (contract :75) — no `graph.appReinitializer` / `appGraphContract().appReinitializer` in any of the 15. (Declared on `AppGraph.kt:136` as an override; not read through the contract.)
- **`liveDatabaseLocator`** (contract :105) — no contract read in any of the 15. (Declared on `AppGraph.kt:256`; used inside `feature/recovery/.../StartupMigrationCoordinator.kt:125` as `liveDatabaseLocator.liveDatabaseFile()`, but that is a **constructor-injected graph node**, not a read via `appGraphContract()`.)

So **2 of the 32** contract accessors are unread by any channel-(a) reader; the consumed **union is 30**.

### Cross-cutting facts
- The backup/restore cluster {backupAuth, backupStorage, snapshotExportRunner, restoreStateRepository, autoBackupController, backupPreferencesRepository, backupNotificationHelper, databaseSnapshotProvider, commonDataStore, platformInfoProvider, tempFileProvider} is read only by **Settings, MetroWorkerFactory, RecoveryActivity** — never by a nav feature other than Settings. (Settings=16, Worker=6, Recovery=2; their overlap = {databaseSnapshotProvider} for Recovery∩Worker∩Settings, and {autoBackupController, backupPreferencesRepository, backupStorage, snapshotExportRunner, databaseSnapshotProvider} for Worker∩Settings.)
- `recoveryDiagnosticsExporter` is read by RecoveryActivity ONLY; `backupNotificationHelper` by MetroWorkerFactory ONLY; `commonDataStore`/`backupAuth`/`platformInfoProvider`/`restoreStateRepository`/`tempFileProvider` by Settings ONLY.
- The 5 exercise-data repositories most shared are exerciseRepository(7)/sessionRepository(6)/trainingRepository(6); the rarest data reads are performedExerciseRepository(1, LiveWorkout) and imageStorage(1, Exercise).
