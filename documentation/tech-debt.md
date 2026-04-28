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
| 🟡 PARKED | TBD per v2.2 stage | **Heavy-aggregation re-execution policy.** Room native Flow re-runs queries on every change to involved tables, regardless of whether the change is relevant to the specific query. For PR (LIMIT 1, indexed) the cost is negligible; v2.1 ships as-is. For v2.2 chart series (full history scan + Kotlin-side bucketing), the cost may matter if a session logs many sets while a chart is observed. **Trigger to act:** profiling in v2.2. **Anti-patterns to avoid:** repo-level `mutableMapOf<Key, StateFlow>` caches (subscription leaks, concurrent mutation, no clean lifecycle owner — entries removed from the map don't cancel upstream subscriptions; collectors cancelled on the consumer side leave the StateFlow producer alive); manual event-bus invalidators built on top of Room (duplicates a primitive Room already provides). **If a cache is needed,** put it at the consumer side (`stateIn(viewModelScope, WhileSubscribed(...), initial)` on the interactor) where lifecycle is bounded and the StateFlow disappears with the consumer. |
| 🟢 | [core/exercise/.../sets/PrComparator.kt](../core/exercise/src/main/kotlin/io/github/stslex/workeeper/core/exercise/sets/PrComparator.kt) ↔ [SessionDao.observePersonalRecord](../core/database/src/main/kotlin/io/github/stslex/workeeper/core/database/session/SessionDao.kt) | Two parallel implementations of the same comparator (Kotlin object-level and SQL `ORDER BY`). The Kotlin path is needed at session finish where the comparison happens against an immutable in-memory snapshot. If the comparator definition changes (e.g. tiebreak rule), both must be updated together. Acceptable duplication; covered by `PrComparatorTest`. |

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
