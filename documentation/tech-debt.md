# Technical Debt Register

This document tracks known debt that should be addressed after functional delivery. It is a **living ratchet**: entries are added when debt is incurred, removed when paid down, and audited periodically against reality (last full audit: 2026-04-28 via dual-model triangulation).

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

## Spec-vs-Reality Drift

Items where shipped behaviour diverges from what specs originally asked for. Surfaced by the 2026-04-28 audit.

| Severity | Spec | Item | Reality |
|---|---|---|---|
| 🟡 | exercises.md | "Phantom shims removed" | `TrainingDataModel.labels` and `TrainingDataModel.exerciseUuids` still present and populated by repo. Cleanup. |
| 🟡 | exercises.md | "`pagedActiveByTags(Set<String>)` AND semantics" | Shipped uses `IN (:tagUuids)` (OR semantics). AND-semantics method exists as `@Deprecated pagedActiveByAllTags`. Either accept OR as the v1 product decision (and remove `pagedActiveByAllTags`), or restore AND. **Decision needed.** |
| 🟡 | exercises.md | "Canonical NavigationHandler with `@Inject Navigator`" | `feature/all-exercises` and `feature/exercise` NavigationHandlers are manually constructed; `@Suppress("MviHandlerConstructorRule")`. Migrate to canonical pattern (Stage 5.5 features already use it). |
| 🟡 | exercises.md, trainings.md, live-workout.md | "Haptics emitted for every Click action" | Several dismiss / undo / cancel paths bypass haptic emission. Specifically: `processUndoArchive`, `processCancelPermanentDelete`, `processBulkDeleteDismiss` in all-exercises; `processBulkDeleteDismiss` in all-trainings; dismiss handlers and done-card header expansion in live-workout. |
| 🟡 | trainings.md, live-workout.md | "Composable `@Previews` for every public/internal Composable" | `AllTrainingsScreen`, `TrainingDetailScreen`, `TrainingEditScreen` expose internals without `@Preview`. `TrainingRow` lacks active/inactive permutations. `live-workout` is fully covered (verified). |
| 🟡 | trainings.md | "Unit tests for new DAO queries" | No tests for `TrainingDao.pagedActiveWithStats`, `pagedActiveWithStatsByTags`, or `observeAnyActiveSession`. |
| 🟡 | live-workout.md | "`finishSession` is one transaction" | Uses `runCatching` + compensating rollback instead of a single `db.withTransaction { ... }`. Edge case: partial rollback could leave inconsistent state. Worth fixing before v2 PR detection (which depends on session integrity). |

---

## androidTest Coverage Gap

Six stub files with `TODO(feature-rewrite-tests)` markers and zero test methods. Created during initial Stage rewrites (5.1 / 5.2 / 5.3) under the assumption tests would be filled once the smoke harness stabilised. Never filled.

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
