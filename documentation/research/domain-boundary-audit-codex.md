# Domain model boundary audit — Workeeper

> Agent: codex
> Commit SHA reviewed: 067554b63afa9dc218fea2e611061b102bad9505
> Date: 2026-05-03T00:00:00Z

## 1. Per-feature summary

| Feature module | V1 | V2 | V3 | V4 | V5 | V6 | Total |
|---|---:|---:|---:|---:|---:|---:|---:|
| feature/exercise | 8 | 1 | 4 | 0 | 3 | 2 | 18 |
| feature/single-training | 8 | 0 | 1 | 0 | 1 | 3 | 13 |
| feature/live-workout | 6 | 0 | 3 | 0 | 0 | 2 | 11 |
| feature/past-session | 2 | 0 | 5 | 0 | 0 | 2 | 9 |
| feature/home | 4 | 0 | 3 | 0 | 0 | 1 | 8 |
| feature/all-exercises | 3 | 0 | 2 | 0 | 1 | 1 | 7 |
| feature/exercise-chart | 1 | 4 | 1 | 0 | 0 | 1 | 7 |
| feature/all-trainings | 3 | 0 | 2 | 0 | 0 | 0 | 5 |
| feature/settings | 0 | 1 | 0 | 0 | 0 | 0 | 1 |
| feature/image-viewer | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

## 2. V1 — Data leak through domain

| File | Method/property | Leaked DataModel type |
|---|---|---|
| feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/AllExercisesInteractor.kt | `observeExercises()` return type | `ExerciseDataModel` |
| feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/AllExercisesInteractor.kt | `observeAvailableTags()` return type | `TagDataModel` |
| feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/AllExercisesInteractor.kt | `getExercise()` return type | `ExerciseDataModel` |
| feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/AllTrainingsInteractor.kt | `observeTrainings()` return type | `TrainingListItem` |
| feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/AllTrainingsInteractor.kt | `observeAvailableTags()` return type | `TagDataModel` |
| feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/AllTrainingsInteractor.kt | `archiveTrainings()` return type | `BulkArchiveOutcome` |
| feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ExerciseChartInteractor.kt | `getRecentlyTrainedExercises()` return type | `RecentExerciseDataModel` |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `getExercise()` return type | `ExerciseDataModel` |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `getRecentHistory()` return type | `HistoryEntry` |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `observeAvailableTags()` return type | `TagDataModel` |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `observePersonalRecord()` return type | `PersonalRecordDataModel` |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `saveExercise()` parameter type | `ExerciseChangeDataModel` |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `createTag()` return type | `TagDataModel` |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `getAdhocPlan()` return type | `PlanSetDataModel` |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `setAdhocPlan()` parameter type | `PlanSetDataModel` |
| feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/domain/HomeInteractor.kt | `observeActiveSession()` return type | `ActiveSessionWithStats` |
| feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/domain/HomeInteractor.kt | `observeRecent()` return type | `RecentSessionDataModel` |
| feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/domain/HomeInteractor.kt | `observeRecentTrainings()` return type | `TrainingListItem` |
| feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/domain/HomeInteractor.kt | `resolveStartConflict()` return type | `StartDecision` |
| feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractor.kt | `loadSession()` return type via `SessionSnapshot.session` | `SessionDataModel` |
| feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractor.kt | `loadSession()` return type via `SessionSnapshot.exercises[].performed` | `PerformedExerciseDataModel` |
| feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractor.kt | `loadSession()` return type via `SessionSnapshot.preSessionPrSnapshot` | `PersonalRecordDataModel` |
| feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractor.kt | `loadSession()` return type via `SessionSnapshot.exercises[].exerciseType` | `ExerciseTypeDataModel` |
| feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractor.kt | `loadSession()` return type via `SessionSnapshot.exercises[].planSets/performedSets` | `PlanSetDataModel` |
| feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractor.kt | `setAdhocPlan()` parameter type | `PlanSetDataModel` |
| feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/domain/PastSessionInteractor.kt | `observeDetailWithPrs()` return type via `DetailWithPrs.detail` | `SessionDetailDataModel` |
| feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/domain/PastSessionInteractor.kt | `updateSet()` parameter type | `SetsDataModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt | `getTraining()` return type | `TrainingDataModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt | `getRecentSessions()` return type | `SessionDataModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt | `observeAvailableTags()` return type | `TagDataModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt | `saveTraining()` parameter type | `TrainingChangeDataModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt | `createTag()` return type | `TagDataModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt | `observeAnyActiveSession()` return type | `ActiveSessionInfo` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt | `TrainingExerciseDetail` public property type | `ExerciseDataModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt | `TrainingExerciseDetail` public property type | `PlanSetDataModel` |

## 3. V2 — UI / resource leak through domain

| File | Leaked type or resource | Notes |
|---|---|---|
| feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ExerciseChartInteractor.kt | `ExerciseTypeUiModel` | UI model imported in domain interface |
| feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ExerciseChartInteractor.kt | `ExerciseChartUiMapper.FoldResult` | Domain interface depends on mapper nested type |
| feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ExerciseChartInteractor.kt | `ChartMetricUiModel`, `ChartPresetUiModel` | UI model types in domain method signature |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractorImpl.kt | `feature.exercise.R` | Direct resource access in domain (`resourceWrapper.getString(R.string...)`) |
| feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/model/ArchivedItem.kt | `androidx.compose.runtime.Stable` | Compose annotation in domain model |

## 4. V3 — Skip-domain mapping

| Mapper file | DataModel imports | Maps to |
|---|---|---|
| feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/mvi/mapper/AllExercisesUiMapper.kt | `ExerciseDataModel`, `TagDataModel` | `ExerciseUiModel`, `TagUiModel` |
| feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/mapper/TrainingListItemMapper.kt | `TrainingListItem` | `TrainingListItemUi` |
| feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/mvi/mapper/TagUiMapper.kt | `TagDataModel` | `TagUiModel` |
| feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/mapper/ExerciseChartUiMapper.kt | `HistoryEntry` | chart/picker UI models |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/mvi/mapper/ExerciseUiMapper.kt | `HistoryEntry`, `SetSummary`, `PersonalRecordDataModel`, `TagDataModel` | `HistoryUiModel`, `PersonalRecordUiModel`, `TagUiModel` |
| feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/mvi/mapper/HomeUiMapper.kt | `SessionRepository.ActiveSessionWithStats`, `RecentSessionDataModel`, `TrainingListItem` | `ActiveSessionInfo`, `RecentSessionItem`, `PickerTrainingItem` |
| feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/mapper/LiveWorkoutMapper.kt | `PlanSetDataModel`, `ExerciseTypeDataModel`, `PersonalRecordDataModel` | `LiveExerciseUiModel`, `LiveSetUiModel`, store state |
| feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/mapper/PastSessionUiMapper.kt | `ExerciseTypeDataModel`, `SetsDataModel`, `SetsDataType`, `PerformedExerciseDetailDataModel`, `SessionDetailDataModel` | `PastExerciseUiModel`, `PastSessionUiModel`, `PastSetUiModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/mapper/TagUiMapper.kt | `TagDataModel` | `TagUiModel` |

## 5. V4 — Generic naming

| File | Type name | Suggested rename direction |
|---|---|---|

## 6. V5 — Nested domain types in interactor

| Interactor | Nested type | Used by methods | Used outside interactor? |
|---|---|---|---|
| feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/AllExercisesInteractor.kt | `ArchiveResult` | `archiveExercise()` | yes |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `TrackNowConflict` | `resolveTrackNowConflict()` | yes |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `ArchiveResult` | `archive()` | yes |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt | `SaveResult` | `saveExercise()` | yes |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt | `ArchiveResult` | `archive()` | yes |

## 7. V6 — DataModel in handler/store

| File | Imported DataModel type |
|---|---|
| feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/mvi/handler/CommonHandler.kt | `RecentExerciseDataModel` |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/mvi/handler/ClickHandler.kt | `ExerciseDataModel`, `ExerciseTypeDataModel`, `PersonalRecordDataModel` |
| feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/mvi/handler/CommonHandler.kt | `TagDataModel` |
| feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/mvi/handler/ClickHandler.kt | `SessionRepository.ActiveSessionWithStats` |
| feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ClickHandler.kt | `PlanSetDataModel`, `SetTypeDataModel`, `ExerciseTypeDataModel` |
| feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/mvi/handler/ExercisePickerHandler.kt | `ExerciseTypeDataModel` |
| feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/ClickHandler.kt | `SetsDataModel`, `SetsDataType` |
| feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/mvi/handler/InputHandler.kt | `SetsDataModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/ClickHandler.kt | `ExerciseDataModel`, `TrainingDataModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/handler/CommonHandler.kt | `TagDataModel` |
| feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/mvi/store/SingleTrainingStore.kt | `ExerciseDataModel` |

## 8. Top remediation candidates

1. V1+V3 `feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractor.kt` (+ mapper/handlers) — крупнейшая концентрация data→domain и data→ui пересечений в одном feature, высокий риск каскадных утечек. — scope: medium
2. V1+V3 `feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/domain/SingleTrainingInteractor.kt` (+ handlers/store/mapper) — публичные domain API и downstream UI слой оба зависят от DataModel. — scope: medium
3. V2 `feature/exercise-chart/src/main/kotlin/io/github/stslex/workeeper/feature/exercise_chart/domain/ExerciseChartInteractor.kt` — domain контракт напрямую связан с UI-моделями и mapper nested-type. — scope: small
4. V1+V3+V6 `feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/domain/LiveWorkoutInteractor.kt` — domain snapshot типы инкапсулируют DataModel и протекают в mapper/handlers. — scope: medium
5. V1+V3 `feature/home/src/main/kotlin/io/github/stslex/workeeper/feature/home/domain/HomeInteractor.kt` — domain API отдаёт repository/session data структуры напрямую в UI mapping. — scope: small
6. V1+V5+V6 `feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/domain/AllExercisesInteractor.kt` — domain API DataModel + nested result уже импортируется handler-ом. — scope: small
7. V1+V3 `feature/past-session/src/main/kotlin/io/github/stslex/workeeper/feature/past_session/domain/PastSessionInteractor.kt` — detail/update paths целиком data-типизированы, mapper тоже напрямую сидит на DataModel. — scope: medium
8. V1+V3 `feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/domain/AllTrainingsInteractor.kt` — список тренировок/тегов не поднимается в domain abstractions. — scope: small
9. V2 `feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/domain/ExerciseInteractorImpl.kt` — direct `R` usage inside domain despite ResourceWrapper abstraction. — scope: small
10. V2 `feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/domain/model/ArchivedItem.kt` — Compose annotation in domain model adds UI dependency edge. — scope: small

## 9. Notes and caveats

- V1 rule interpreted strictly per prompt suffix/FQCN criteria plus core data package imports; types such as `TrainingListItem`, `SessionRepository.ActiveSessionWithStats`, and `SessionConflictResolver.StartDecision` were included because they live under `core.data.*` despite not ending in `DataModel`.
- V2 borderline explicitly separated: `ResourceWrapper` itself was not counted as leak; only direct `R` usage in domain (`ExerciseInteractorImpl`) was counted.
- V4 grep hits were all in `mvi/store` `State` declarations, outside `domain/`; therefore V4 count is zero after manual inspection.
- V5 only includes nested types that are return types of interactor methods; nested types not used in method signatures were excluded.
- All V3 and V6 rows were validated by opening each mapper/handler/store file and checking concrete imports.
- `feature/image-viewer` had no `domain/`, `mvi/mapper/`, or `mvi/handler/` files in audited paths.