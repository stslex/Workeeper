# GATE 0 — read-only discovery + placement decision (AppGraphContract split, variant A)

**HEAD:** `d54129ddce24dfda5ec00035a8c4aa633835db9b` — tip of `feature/metro-batch`.
**Working tree:** DIRTY (22 porcelain entries, spike leftovers). **All reads against the git ref `feature/metro-batch`**, not the working tree. Excludes `*/build/*`, `*/.claude/worktrees/*`.
**Status:** discovery only — **NO edits made**. Maintainer GO required before EXECUTION.

> ⚠️ **CORRECTION, standing (2026-07-26) — the decisions this gate framed were taken, executed, and
> then partly undone.** The two-tier spine argued for in §"Decisions … 2. Spine granularity"
> (`StoreCoreDeps` {AH,LH,SD} + navigator kept separate) was built in C1 and **deleted** by `7f48093f`:
> the later graph-extension arc ported all 13 features to contributed `@GraphExtension`s, which inherit
> those bindings from the parent graph, leaving the spine with no readers. The per-consumer framework
> interfaces (`RecoveryDeps`, `BackupWorkerDeps`) survive and are `AppGraph`'s only two supertypes.
> Every reading below is pinned to the ref `d54129dd` and is left exactly as measured — it is a
> snapshot of that tree, not a description of HEAD.

---

## 0.1 — Interface homes & directional-cycle check

### `StoreDeps` (spine: analyticsHolder, loggerHolder, storeDispatchers, navigator) — HOME: `core:ui:mvi` ✓ with one caveat

- **`core:ui:mvi` DOES depend on `core:ui:navigation`** — `core/ui/mvi/build.gradle.kts:25 implementation(project(":core:ui:navigation"))`. So `core:ui:mvi` can name all four spine types: `AnalyticsHolder`/`LoggerHolder`/`StoreDispatchers` (its own) + `Navigator` (from navigation). **[resolved]**
- **All 13 features depend on `core:ui:mvi`** (verified each `feature/*/build.gradle.kts` → `project(":core:ui:mvi")` = 1). So a `StoreDeps` in `core:ui:mvi` is reachable by every feature with no new edge. **[resolved]**
- Spine types are **public**: `AnalyticsHolder.kt:12 class AnalyticsHolder`, `LoggerHolder.kt:20 class LoggerHolder`, `StoreDispatchers.kt:23 data class StoreDispatchers`, `Navigator.kt:7 interface Navigator`. All nameable in a public interface. **[resolved]**
- **CAVEAT — `api`-promotion required.** `core:ui:mvi`'s dep on navigation is `implementation` (`:25`), so `Navigator` is NOT on `core:ui:mvi`'s ABI. A public `StoreDeps` exposing `val navigator: Navigator` requires promoting to **`api(project(":core:ui:navigation"))`** in `core:ui:mvi` — else feature consumers cannot resolve the `Navigator` return type. This is a real build-script change in C1. **[resolved]**
- **`:app` can implement `StoreDeps`** — `app/app/build.gradle.kts:43 implementation(project(":core:ui:mvi"))`. **[resolved]**

### `BackupDeps` (the Settings+Worker+Recovery cluster) — SPANS 5 MODULES, not cleanly homeable in `:backup:api` ⚠️

The cluster types resolve (from `AppGraphContract.kt` imports — authoritative FQN→module) to **five** modules:

| accessor | owning module |
|---|---|
| backupAuth, backupStorage, snapshotExportRunner, restoreStateRepository, autoBackupController, backupPreferencesRepository, backupNotificationHelper, recoveryDiagnosticsExporter | `core:data:backup:api` |
| commonDataStore | `core:data:dataStore` |
| databaseSnapshotProvider, liveDatabaseLocator | `core:data:database` |
| platformInfoProvider | `core:core` |
| tempFileProvider | `core:core-android` (package `core.core.platform`) |

- **`core:data:backup:api` depends ONLY on `core:core`** (`core/data/backup/api/build.gradle.kts:6`). It **cannot name** `CommonDataStore` (dataStore), `DatabaseSnapshotProvider` (database), or `TempFileProvider` (core-android). So a **single** `BackupDeps` interface homed in `:backup:api` is **NOT viable** without widening `:api`'s dep list (adding data:dataStore + data:database + core-android). **[resolved — this is the placement blocker to decide]**
- **The three consumers read very different slices** (per the dep-matrix; module in parens):
  - **RecoveryActivity (2):** `databaseSnapshotProvider` (data:database), `recoveryDiagnosticsExporter` (backup:api).
  - **MetroWorkerFactory (6):** `backupStorage`, `snapshotExportRunner`, `backupNotificationHelper`, `autoBackupController`, `backupPreferencesRepository` (all backup:api) + `databaseSnapshotProvider` (data:database).
  - **Settings (16, of which backup-cluster = 11):** the Worker's 6 (minus backupNotificationHelper) + `backupAuth`, `restoreStateRepository` (api) + `commonDataStore` (dataStore) + `platformInfoProvider` (core:core) + `tempFileProvider` (core-android) + `databaseSnapshotProvider`.
- Consumer dep edges (all three CAN reach `:backup:api` + `:database`): Settings `build.gradle.kts:28,29` (backup:api, database) + `:23` (dataStore); Worker `:17,19` (backup:api, database); Recovery `:31,30` (backup:api, database). So a `BackupDeps` limited to **{backup:api ∪ data:database}** types is below all three; but the **Settings-only** extras (commonDataStore, platformInfoProvider, tempFileProvider) span 3 more modules and are read by **Settings alone**. **[resolved]**
- **`:app` can implement a `:backup:api`-homed `BackupDeps`** — `app/app/build.gradle.kts:47 implementation(project(":core:data:backup:api"))`. **[resolved]**

> **PLACEMENT DECISION REQUIRED (maintainer):** the spec's "ONE shared `BackupDeps` for the three consumers" is only clean if it covers the **Worker∩Recovery-reachable** subset (backup:api + data:database types). Settings' 3 extra single-reader types (`commonDataStore`, `platformInfoProvider`, `tempFileProvider`) are read by **Settings only** — they belong in Settings' own `SettingsDeps` tail, NOT in a shared `BackupDeps`. Options: (i) `BackupDeps` = the backup:api+database cluster (home `:backup:api` **widened** with a `data:database` dep, or homed in a module that already sees both — check `:backup:worker` deps), Settings' extras go to `SettingsDeps`; (ii) split `BackupApiDeps` (api-only, no widening) + expose `databaseSnapshotProvider` separately. This is the one real placement call; the data above is the input.

### Per-feature `XDeps` (domain tails) — HOME: each feature's own module ✓

- **0 internal exercise repositories** (`git grep "internal interface \w+Repository"` in `core/data/exercise` = 0) — all 8 repos + `SessionConflictResolver` (`SessionConflictResolver.kt:27 class`) are public. Each feature already depends on `core:data:exercise` (its repos), so a feature-local `XDeps` naming its own repos + `@DefaultDispatcher`/`ResourceWrapper` has no cycle and no visibility block. **[resolved]**

---

## 0.2 — Dead-surface verification (both proven DROPPABLE)

Both accessors are read by **0/15 through the contract**, and their bindings survive via `@Inject`/`@ContributesBinding` (independent of the contract accessor):

- **`appReinitializer` (contract `:75`)** — the `AppReinitializer` binding is consumed by **constructor injection**, not the contract:
  - `app/app/.../navigation/NavigatorEventBus.kt:56 appReinitializer.reinitialize()` (ctor-injected into `NavigatorEventBus`)
  - `feature/recovery/.../RestoreRecoveryCoordinator.kt:165 appReinitializer.reinitialize()` (ctor-injected)
  - The only contract-surface refs are the accessor decl (`AppGraphContract.kt:75`) + its `AppGraph.kt:136` override. **Dropping the accessor is safe.** **[resolved]**
- **`liveDatabaseLocator` (contract `:105`)** — bound via `@ContributesBinding(AppScope, binding=binding<LiveDatabaseLocator>())` (`DatabaseSnapshotProviderImpl.kt:36`) and consumed by **ctor `@Inject`**: `StartupMigrationCoordinator.kt:100 @Inject internal constructor(... :102 private val liveDatabaseLocator: LiveDatabaseLocator ...)` → `:125 liveDatabaseLocator.liveDatabaseFile()`. Contract-surface refs: accessor decl (`AppGraphContract.kt:105`) + `AppGraph.kt:256` override only. **Dropping the accessor is safe.** **[resolved]**

**Verdict:** both dead accessors are droppable in C(n+1); their `AppReinitializer`/`LiveDatabaseLocator` bindings are untouched.

---

## 0.3 — Coverage arithmetic (completeness gate)

- **Universe = 32** accessors on `AppGraphContract` (Step-1 list from the matrix report).
- **Consumed union = 30** (verified: `⋃` of all 15 readers' `graph.<accessor>` reads).
- **Dead = exactly 2**: `appReinitializer`, `liveDatabaseLocator` (§0.2). `32 − 2 = 30`. Diff confirmed zero elsewhere. **[resolved]**
- The new interface set's accessor UNION must == these **30**:
  `analyticsHolder, autoBackupController, backupAuth, backupNotificationHelper, backupPreferencesRepository, backupStorage, commonDataStore, databaseSnapshotProvider, defaultDispatcher, exerciseRepository, imageStorage, ioDispatcher, loggerHolder, mainImmediateDispatcher, navigator, performedExerciseRepository, personalRecordRepository, platformInfoProvider, recoveryDiagnosticsExporter, resourceWrapper, restoreStateRepository, sessionConflictResolver, sessionRepository, setRepository, snapshotExportRunner, storeDispatchers, tagRepository, tempFileProvider, trainingExerciseRepository, trainingRepository`
  (add nothing outside this 30; drop nothing inside it.)

---

## 0.4 — Per-reader → composition map (15 readers)

Spine = `StoreDeps` {analyticsHolder, loggerHolder, storeDispatchers, navigator}. `XDeps` = each reader's remaining accessors. (RecoveryActivity/MetroWorkerFactory read no spine — framework readers.)

| Reader | spine? | tail (XDeps) accessors | count |
|---|---|---|---|
| AllExercises | StoreDeps | exerciseRepository, tagRepository, resourceWrapper, defaultDispatcher | 8 |
| AllTrainings | StoreDeps | trainingRepository, tagRepository, resourceWrapper, defaultDispatcher | 8 |
| AppDialog | StoreCore only (no navigator) | — | 3 |
| Archive | StoreDeps | exerciseRepository, trainingRepository, resourceWrapper, defaultDispatcher | 8 |
| ExerciseChart | StoreDeps | exerciseRepository, sessionRepository, resourceWrapper, defaultDispatcher | 8 |
| Exercise | StoreDeps | exerciseRepository, tagRepository, sessionRepository, trainingRepository, personalRecordRepository, imageStorage, resourceWrapper, defaultDispatcher, mainImmediateDispatcher | 13 |
| Home | StoreDeps | trainingRepository, sessionRepository, sessionConflictResolver, resourceWrapper, defaultDispatcher | 9 |
| ImageViewer | StoreDeps | — | 4 |
| LiveWorkout | StoreDeps | exerciseRepository, sessionRepository, setRepository, trainingRepository, trainingExerciseRepository, performedExerciseRepository, personalRecordRepository, resourceWrapper, defaultDispatcher | 13 |
| PastSession | StoreDeps | sessionRepository, setRepository, personalRecordRepository, resourceWrapper, ioDispatcher | 9 |
| PlanEditor | StoreDeps | exerciseRepository, trainingExerciseRepository, resourceWrapper, defaultDispatcher | 8 |
| Settings | StoreDeps | + BackupDeps + {commonDataStore, platformInfoProvider, tempFileProvider, backupAuth, restoreStateRepository, ioDispatcher, defaultDispatcher} | 16 |
| SingleTraining | StoreDeps | exerciseRepository, tagRepository, sessionRepository, trainingRepository, trainingExerciseRepository, sessionConflictResolver, resourceWrapper, defaultDispatcher, mainImmediateDispatcher | 13 |
| RecoveryActivity | (none) | BackupDeps-subset: databaseSnapshotProvider, recoveryDiagnosticsExporter | 2 |
| MetroWorkerFactory | (none) | BackupDeps: backupStorage, snapshotExportRunner, backupNotificationHelper, autoBackupController, backupPreferencesRepository, databaseSnapshotProvider | 6 |

---

## Decisions the maintainer must lock (from the data, before EXECUTION)

1. **`BackupDeps` shape/home (§0.1).** Single interface covering only the {backup:api + data:database} cluster (Worker/Recovery-reachable, no widening if homed where both are visible), with Settings' 3 extras (`commonDataStore`, `platformInfoProvider`, `tempFileProvider`) pushed to `SettingsDeps`? Or widen `:backup:api`? RecoveryActivity reads only 2 of the cluster — does it get the full `BackupDeps` (2 used, rest ignored) or a narrower `RecoveryDeps`?
2. **Spine granularity (§0.1 caveat).** `AppDialog` reads {AH,LH,SD} WITHOUT navigator AND its module does NOT depend on `core:ui:navigation`. Giving it `StoreDeps` (with navigator) forces adding a `core:ui:navigation` dep to `app-dialogs/impl` for an unused type. → concrete argument for **two-tier: `StoreCoreDeps` {AH,LH,SD}** + **`StoreDeps : StoreCoreDeps` {+navigator}**. Decide.
3. **`api`-promotion of `core:ui:navigation` in `core:ui:mvi`** (needed for `StoreDeps` to expose `Navigator`) — confirm acceptable.
4. **Bottom-bar tier (open item).** The five size-8 readers share {AH,LH,SD,NAV,**DD,RW**}. `defaultDispatcher` is read by 10/15 and `resourceWrapper` by 10/15 — NOT exactly those five — so a `BottomBarDeps : StoreDeps (+DD+RW)` would NOT be exclusive to them; DD/RW appear across many tails. **Recommendation-free fact:** DD/RW are broad (10/15 each), so folding them into each `XDeps` is the non-god-object choice; a shared BottomBar tier does not match an exact-overlap cluster.

---

## Verification method for EXECUTION (unchanged from spec, confirmed applicable)
- Per commit: `:app:dev:assembleDebug --rerun-tasks --no-build-cache` + `detekt --no-daemon --rerun-tasks --no-build-cache` (zero suppressions) + affected-module `testDebugUnitTest --rerun-tasks`.
- Additive-first strangler: `AppGraph : AppGraphContract` (`AppGraph.kt:65`) stays until C(last); new interfaces added to its supertype list additively.
- Final gates: `git grep "appGraphContract("` and `"AppGraphContract"` in real source (excl worktrees/build/KDoc) = 0; new-interface accessor union == the 30; `:core:di` removed from `settings.gradle.kts` + every `implementation(project(":core:di"))`.
- Working tree dirty → all verification against the ref, as here.

**GATE 0 verdict: discovery complete, no blockers that stop the plan — but 4 placement decisions (esp. BackupDeps home) must be locked by the maintainer before C1.** No edits made.
