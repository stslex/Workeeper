# Technical Debt Register

This document tracks known debt that should be addressed after functional delivery. It is a **living ratchet**: entries are added when debt is incurred, removed when paid down, and audited periodically against reality (last full audit: 2026-04-28 via dual-model triangulation; v2.0 stage updates applied 2026-04-28).

Each tracked location should carry a `TODO(tech-debt): <category> — <ref>` marker in code so debt is grep-able during development.

## How to read this document

- **Severity** is informal: 🔴 critical for release, 🟡 medium (polish/cleanup), 🟢 low (architectural hygiene).
- **Status** indicates current state. ACTIVE = work to do; PARKED = intentionally deferred to a known horizon.

---

## UI Mapping Boundary Debt

**Rule:** UI composables and graph files render already mapped, localized, and formatted state. Mapping and localization shaping happen in handler / state-mapper layers. See [architecture.md → UI types vs domain types](architecture.md).

| Severity | Location | Description |
|---|---|---|
| 🟢 | [feature/settings/.../ui/ArchiveGraph.kt](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/ui/ArchiveGraph.kt) | Snackbar templates (`restoredTemplate.format(event.item.name)`) substituted in graph. Should be pre-formatted; event payload should carry the ready string. |
| 🟢 | [feature/exercise/.../ui/ExerciseGraph.kt](../feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseGraph.kt) | `Event.ShowImageError` → `when (event.errorType) { ... }` shaping in graph. Move to mapper or carry resolved message in the event itself. |
| 🟢 | [feature/single-training/.../ui/SingleTrainingGraph.kt](../feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/ui/SingleTrainingGraph.kt) | Discard-dialog title/body strings still chosen in graph. Push to state or to event payload. |
| 🟢 | [feature/home/.../ui/components/ActiveSessionBanner.kt](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/components/ActiveSessionBanner.kt) | Concatenation `stringResource(label) + " · " + stringResource(progress)` in composable. Pre-format full label in `HomeUiMapper`. |
| 🟢 | [feature/home/.../ui/components/RecentSessionRow.kt](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/components/RecentSessionRow.kt) | String interpolation `"${item.finishedAtRelativeLabel} · ${item.durationLabel}"` in composable. Add a single combined label to `RecentSessionItem`. |
| 🟢 | [feature/home/.../ui/components/TrainingPickerSheet.kt](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/ui/components/TrainingPickerSheet.kt) | `listOfNotNull(...).joinToString(" · ")` in composable. Same pattern — pre-format in mapper. |
| 🟢 | [feature/past-session/.../ui/PastSessionGraph.kt](../feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/ui/PastSessionGraph.kt) | `Event.ShowError` → `when (event.errorType) { ... }` shaping in graph. Same fix as ExerciseGraph. |
| 🟢 | [feature/past-session/.../ui/PastSessionScreen.kt](../feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/ui/PastSessionScreen.kt) | Error headline `when (errorType) { ... }` in composable. Push message into `Phase.Error` payload. |

---

## Schema Migration Debt

| Severity | Location | Description |
|---|---|---|
| 🟢 PARKED | [core/database/.../di/CoreDatabaseModule.kt](../core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/di/CoreDatabaseModule.kt) | No explicit `Migration(3, 4)` and `Migration(4, 5)` defined. Uses `fallbackToDestructiveMigrationFrom(dropAllTables = true, 2, 3, 4)`. **Pre-release context (2026-04-28):** v5 is the first build going to Play Store review; previous v3/v4 builds were never published. Destructive fallback is acceptable until the first stable release ships. **Trigger to act:** before any version after v5 changes the schema, write proper `Migration(N, N+1)` and remove the fallback for the corresponding versions. |

---

## Reactive Aggregations

| Severity | Location | Description |
|---|---|---|
| ✅ RESOLVED | [feature/exercise-chart](../feature/exercise-chart/) | **Heavy-aggregation re-execution policy** (parked from v2.1). The v2.2 chart consumer chooses one-shot reads over a `Flow` subscription: the screen reads `getHistoryByExercise` once on entry / preset change / picker change and buckets in Kotlin. No persistent subscription means no spurious recomputation when other sessions log sets. The "if a cache is needed, cache at the consumer side" guidance was effectively answered by binding the data to `State` instead. See [feature-specs/v2.2-exercise-charts.md → Architectural notes](feature-specs/v2.2-exercise-charts.md#architectural-notes). |
| 🟡 | [feature/exercise-chart/.../mvi/mapper/ExerciseChartUiMapper.kt](../feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/mapper/ExerciseChartUiMapper.kt) | **Per-day max-of-day collapse loses information** when the user does two sessions on one calendar date — only the higher set's session is reachable from the tooltip. v2.2 ships max-of-day for simplicity; follow-up is to render two points per day (each session's best set, both anchored to the day's X with a small jitter / vertical marker). **Trigger to act:** user reports that double-session days are surprising. |
| 🟢 | [feature/exercise-chart/.../mvi/handler/CommonHandler.kt](../feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/CommonHandler.kt) | **Window filtering happens client-side**, not in SQL. The mapper drops sets older than the active preset's start. Acceptable at v2.2 data sizes (~hundreds of rows per exercise); if profiling shows the read is slow for >2 years of dense history (>5000 rows per exercise), add a `:sinceMillis` overload to `SessionDao.getHistoryByExercise` and pass it from the handler. **Trigger to act:** load time exceeds ~150ms on a mid-range device. |
| 🟢 | [core/exercise/.../sets/PrComparator.kt](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/sets/PrComparator.kt) ↔ [SessionDao.observePersonalRecord](../core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/session/SessionDao.kt) | Two parallel implementations of the same comparator (Kotlin object-level and SQL `ORDER BY`). The Kotlin path is needed at session finish where the comparison happens against an immutable in-memory snapshot. If the comparator definition changes (e.g. tiebreak rule), both must be updated together. Acceptable duplication; covered by `PrComparatorTest`. |
| 🟢 | [core/exercise/.../sets/PrComparator.kt](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/sets/PrComparator.kt) ↔ [SessionDao.observePersonalRecordsBatch](../core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/session/SessionDao.kt) | Spec called for a parity test that seeds Room and asserts both `bestOf(...)` and the DAO pick the same set. Not implemented because Room test setup in `core/exercise/test` is cross-module; the test would need to live alongside `androidTest` infrastructure. **Trigger to act:** comparator semantics change (e.g. tiebreak rule). |
| 🟢 | [feature/live-workout/.../domain/LiveWorkoutInteractorImpl.kt:70-86](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractorImpl.kt) | Sequential (not parallel) per-entity queries — `loadSession` does N per-exercise calls (`getAdhocPlan` / `getPlan` / `setRepository.getByPerformedExercise`) in a loop. One-shot at session open, low frequency. Cheapest fix: wrap with `asyncMap` from [`core/core/coroutine/CoroutineExt.kt`](../core/core/src/main/kotlin/io/github/stslex/workeeper/core/core/coroutine/CoroutineExt.kt). Not blocking. |
| 🟢 | [core/exercise/.../session/SessionRepositoryImpl.kt:130-143](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/session/SessionRepositoryImpl.kt) | Sequential per-entity queries — `getSessionDetail` does N `setDao.getByPerformedExercise` calls inside `withTransaction`. One-shot at Past session open, low frequency. Same fix shape (`asyncMap`). Not blocking. |

---

## State Mutation Discipline

**Rule:** `BaseStore.updateState` and `updateStateImmediate` lambdas should perform pure state transformation only — given `current`, return a copy. Mapping, formatting, and any work involving `ResourceWrapper` or domain-to-UI conversions runs *before* the lambda body. See [architecture.md → State mutation discipline](architecture.md) and the [`compose-state-discipline`](../.claude/skills/compose-state-discipline.md) skill.

| Severity | Location | Description |
|---|---|---|
| 🟡 | [feature/home/.../mvi/handler/CommonHandler.kt:36-39](../feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/CommonHandler.kt) | Mapping inside `updateStateImmediate` lambda — `row?.toUi(now, resourceWrapper)` runs on Main.immediate every active-session emit. Hoist out before the lambda. |
| 🟡 | [feature/all-exercises/.../mvi/handler/PagingHandler.kt:49-52](../feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/handler/PagingHandler.kt) | Mapping `tags.map { it.toTagUi() }.toImmutableList()` inside `updateStateImmediate` lambda. Same fix shape as above. |
| 🟡 | [feature/all-trainings/.../mvi/handler/PagingHandler.kt:51-54](../feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/handler/PagingHandler.kt) | Same pattern as the home / all-exercises rows. |
| 🟡 | [feature/single-training/.../mvi/handler/CommonHandler.kt:55-56](../feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/CommonHandler.kt) | Same pattern as the home / all-exercises rows. |

---

## Live workout — release-phase hot-fix follow-ups

| Severity | Location | Description |
|---|---|---|
| 🟡 | [feature/live-workout/.../domain/LiveWorkoutInteractorImpl.kt loadSession](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractorImpl.kt) | Read-time `trainingPlan ?: exerciseRepository.getAdhocPlan(...)` fallback exists because we don't backfill old data via migration in this commit. When the next schema bump lands (with the proper Migration framework now in place — see Migration Policy in [architecture.md](architecture.md) → Room database), include a one-shot backfill: `UPDATE training_exercise_table SET plan_sets = (SELECT plan_sets FROM exercise_table WHERE exercise_table.uuid = training_exercise_table.exercise_uuid) WHERE plan_sets IS NULL`. After that, drop the runtime fallback. |
| 🟢 | [feature/live-workout/.../mvi/handler/ClickHandler.kt recomputeOnly](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ClickHandler.kt) + [LiveWorkoutMapper.kt toUiList](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/LiveWorkoutMapper.kt) | Status derivation logic duplicated between mapper (initial load) and click handler (post-mutation recompute). Extract to a shared helper in a follow-up so future status semantics changes happen in one place. |
| 🟢 | [feature/live-workout/.../mvi/store/LiveWorkoutStore.kt activeExerciseUuids](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/store/LiveWorkoutStore.kt) | Active-set state is ephemeral — resets on app background/restore. If users complain about losing parallel state, persist via a new column on `performed_exercise_table` or session-scoped DataStore. Not blocking. |

---

## v2.3 Quick start workout — follow-ups

Items deferred from the v2.3 PR (per spec Section 10). Track here so the v2.7 ratchet pass can pick them up.

| Severity | Location | Description |
|---|---|---|
| 🟢 | feature/exercise/.../ExerciseInteractorImpl, feature/live-workout/.../LiveWorkoutInteractorImpl | **Track Now / Quick start UI unification** (deferred to v2.7). Both flows now share the data layer (`SessionRepository.createAdhocSession`, `discardAdhocSession`) but stay as separate UI flows. UI-layer convergence is its own refactor. |
| 🟢 | feature/live-workout/.../mvi/handler/ | **Live workout feature module decomposition** (deferred to v2.7). `feature/live-workout` accumulated significant complexity through v2.1 (PR detection), v2.2 (chart hook), v2.3 (mid-session add, name edit, empty-finish dialog). `ExercisePickerHandler` was already split off via the `PlanEditAction`-style wrapper to keep ClickHandler from bloating; further decomposition (e.g. NameEditHandler, EmptyFinishHandler) is candidate. |
| 🟡 | feature/live-workout/.../mvi/handler/ExercisePickerHandler.kt `addExerciseFlow` | **PR snapshot fetch failure mode telemetry** (new in v2.3). When `fetchPrSnapshotForExercise` fails for a library pick, the exercise is still added to the session and the in-moment PR badge is suppressed (degraded mode silent failure). If telemetry shows this firing often, the user-facing UX needs revisit. |
| 🟡 | feature/live-workout/src/androidTest/ | **Mid-session add UI in instrumented tests** (deferred to v2.7). Per project policy (UI flow tests as dedicated test-coverage PRs), no androidTest landed in v2.3. The blank-init Quick start flow + picker bottom sheet + empty-finish discard cascade need smoke coverage. |
| 🟢 | core/database/.../exercise/ExerciseDao.kt + ExerciseRepositoryImpl.createInlineAdhocExercise | **`ExerciseEntity.isAdhoc` cleanup of stale graduated rows** (deferred, monitoring). After many cycles of inline create → graduate, the library may accumulate poorly-named single-use exercises. No action in v2.3; revisit if user-facing exercise-list pruning becomes a need. |

---

## Remaining from PR #78

| Severity | Location | Description |
|---|---|---|
| 🟡 | [core/exercise/.../personal_record/PersonalRecordRepository.kt](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/personal_record/PersonalRecordRepository.kt) | `observePersonalRecords(uuidsByType)` is a combine-of-N flow — N separate Room subscriptions. KDoc marks it as one-shot only, but there is no compile-time guard. Callers must use `firstOrNull()` or `getPersonalRecord`. Long-lived subscribers must use `observePersonalRecordsBatch` / `observePrSetUuids`. Consider removing from the public interface or converting to `suspend fun` to make the one-shot contract enforced. |
| 🟢 | [core/exercise/.../session/SessionRepositoryImpl.kt `finishSessionAtomic`](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/session/SessionRepositoryImpl.kt) | Double dispatcher switch: outer `withContext(ioDispatcher)` wraps `transition {}` which already does `withContext(ioDispatcher)`. Redundant context switch; clean up when touching this method next. |
| 🟢 | [core/exercise/.../session/SessionRepositoryImpl.kt `groupBySession()`](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/session/SessionRepositoryImpl.kt) | `sortedByDescending { it.finishedAt }` is a redundant O(N log N) pass — the DAO query already returns `ORDER BY sn.finished_at DESC` and `groupBy` preserves insertion order. Remove the sort. |

---

## Spec-vs-Reality Drift

Items where shipped behaviour diverges from what specs originally asked for. Surfaced by the 2026-04-28 audit.

| Severity | Spec | Item | Reality |
|---|---|---|---|
| 🟡 | exercises.md | "Phantom shims removed" | `TrainingDataModel.labels` and `TrainingDataModel.exerciseUuids` still present and populated by repo. Cleanup. |
| 🟡 | exercises.md | "`pagedActiveByTags(Set<String>)` AND semantics" | Shipped uses `IN (:tagUuids)` (OR semantics). The deprecated AND-semantics query was removed as dead code; OR is intentional and remains the supported behaviour — locked decision in v2.0 spec. |
| 🟡 | exercises.md | "Canonical NavigationHandler with `@Inject Navigator`" | All feature NavigationHandlers use the manual `Component.create(navigator, screen)` constructor pattern with `@Suppress("MviHandlerConstructorRule")`. The architecture relies on the manual pattern because handlers carry per-screen `data`. Treated as architectural; not migrated in v2.0. |
| 🟡 | exercises.md, trainings.md, live-workout.md | "Haptics emitted for every Click action" | Several dismiss / undo / cancel paths bypass haptic emission. Specifically: `processUndoArchive`, `processCancelPermanentDelete`, `processBulkDeleteDismiss` in all-exercises; `processBulkDeleteDismiss` in all-trainings; dismiss handlers and done-card header expansion in live-workout. |
| 🟡 | trainings.md, live-workout.md | "Composable `@Previews` for every public/internal Composable" | `AllTrainingsScreen`, `TrainingDetailScreen`, `TrainingEditScreen` expose internals without `@Preview`. `TrainingRow` lacks active/inactive permutations. `live-workout` is fully covered (verified). |

---

## androidTest Coverage Gap

Six stub files with `TODO(feature-rewrite-tests)` markers and zero test methods. Created during initial Stage rewrites (5.1 / 5.2 / 5.3) under the assumption tests would be filled once the smoke harness stabilised. v2.0 stage scheduled the fill-in work; remaining stubs are tracked in the v2.0 spec and addressed in their own PRs.

| Severity | Location | Stage |
|---|---|---|
| 🟡 | [feature/settings/.../SettingsScreenTest.kt](../feature/settings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/settings/SettingsScreenTest.kt) | 5.1 |
| 🟡 | [feature/settings/.../ArchiveScreenTest.kt](../feature/settings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/settings/ArchiveScreenTest.kt) | 5.1 |
| 🟡 | [feature/all-exercises/.../AllExercisesScreenTest.kt](../feature/all-exercises/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_exercises/AllExercisesScreenTest.kt) | 5.2 |
| 🟡 | [feature/exercise/.../ExerciseScreenTest.kt](../feature/exercise/src/androidTest/kotlin/io/github/stslex/workeeper/feature/exercise/ExerciseScreenTest.kt) | 5.2 |
| 🟡 | [feature/all-trainings/.../AllTrainingsScreenTest.kt](../feature/all-trainings/src/androidTest/kotlin/io/github/stslex/workeeper/feature/all_trainings/AllTrainingsScreenTest.kt) | 5.3 |
| 🟡 | [feature/single-training/.../SingleTrainingScreenTest.kt](../feature/single-training/src/androidTest/kotlin/io/github/stslex/workeeper/feature/single_training/SingleTrainingScreenTest.kt) | 5.3 |

**Plan:** address as a dedicated test-coverage PR after v2 stabilises. Don't try to fill in feature PRs.

---

## v2.0 Foundations Stage — closed entries

The v2.0 stage addressed the following items. They are listed here for traceability before they roll into the next audit cleanup.

- ✅ `feature/exercise/.../mvi/handler/ClickHandler.kt:163` Track now CTA stub replaced with a real flow that creates an ad-hoc training and opens Live workout via `SessionConflictResolver`.
- ✅ `feature/exercise/.../ui/ExerciseDetailScreen.kt` now renders `state.adhocPlanSummaryLabel` between the description and history sections.
- ✅ `LiveWorkoutInteractorImpl.finishSession` now delegates to `SessionRepository.finishSessionAtomic`, which wraps plan updates + state transition in a single `database.withTransaction { ... }`. `runCatching` + compensating-rollback removed.
- ✅ DAO unit tests added for `TrainingDao.pagedActiveWithStats`, `pagedActiveWithStatsByTags`, and `SessionDao.observeAnyActiveSession` plus the three new aggregation queries (`getPersonalRecord`, `getBestSessionVolumes`, `pagedHistoryByExercise`).
- ✅ Active session conflict modal (`core/ui/kit/.../ActiveSessionConflictDialog.kt`) shared by Home Start CTA, Training detail Start session, and Exercise detail Track now.
- ✅ Live workout overflow Delete session option + `DiscardSessionConfirmDialog` confirm flow.

---

## Resolved (kept for diff visibility, will be removed in next audit)

These were tracked as debt in earlier versions of this doc. Verified resolved by 2026-04-28 audit.

- ✅ `feature/all-trainings/.../ui/components/RelativeTimeFormatter.kt` — file deleted; logic now lives in `TrainingListItemMapper`.
- ✅ `feature/all-trainings/.../ui/AllTrainingsGraph.kt` blocked-name shaping — moved to `ClickHandler` with `ResourceWrapper`.
- ✅ `feature/all-exercises/.../ui/AllExercisesGraph.kt` blocked-name shaping — moved to `ClickHandler` with `ResourceWrapper`.
- ✅ `feature/exercise/.../ui/ExerciseEditScreen.kt` plan summary — `state.adhocPlanSummaryLabel` pre-formatted.
- ✅ `feature/exercise/.../ui/components/ExerciseHistoryRow.kt` date and sets — pre-formatted via `ExerciseUiMapper`.
- ✅ `feature/single-training/.../ui/components/TrainingHistoryRow.kt` date — pre-formatted via `CommonHandler`.
- ✅ `feature/live-workout/.../ui/components/LiveExerciseCard.kt` status-line — `exercise.statusLabel` pre-formatted in `LiveWorkoutMapper`.
- ✅ `feature/settings/.../ui/ArchiveGraph.kt` timestamp formatting — moved to `ArchiveUiMapper`. (Note: snackbar template substitution remains as a separate, smaller debt — see UI Mapping Boundary table above.)
