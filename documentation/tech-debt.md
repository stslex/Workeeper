# Technical Debt Register

This document tracks known debt that should be addressed after functional delivery.

## UI Mapping Boundary Debt

Rule target: UI composables and graph files should render already mapped, localized, and
formatted state. Mapping and localization shaping should happen in handler/state mapping layers.

Tracked `TODO(tech-debt)` locations:

- [feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/ui/ArchiveGraph.kt](../feature/settings/src/main/kotlin/io/github/stslex/workeeper/feature/settings/ui/ArchiveGraph.kt)
  - Archive timestamp-to-label formatting is currently performed in UI graph.
- [feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/RelativeTimeFormatter.kt](../feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/components/RelativeTimeFormatter.kt)
  - Relative time label formatting is currently performed in UI component layer.
- [feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/AllTrainingsGraph.kt](../feature/all-trainings/src/main/kotlin/io/github/stslex/workeeper/feature/all_trainings/ui/AllTrainingsGraph.kt)
  - Blocked-name list message shaping is currently performed in UI graph.
- [feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/AllExercisesGraph.kt](../feature/all-exercises/src/main/kotlin/io/github/stslex/workeeper/feature/all_exercises/ui/AllExercisesGraph.kt)
  - Blocked-name list message shaping is currently performed in UI graph.
- [feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseGraph.kt](../feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseGraph.kt)
  - Event-to-message shaping is currently performed in UI graph.
- [feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/ui/SingleTrainingGraph.kt](../feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/ui/SingleTrainingGraph.kt)
  - Event-to-message shaping is currently performed in UI graph.
- [feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseEditScreen.kt](../feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/ExerciseEditScreen.kt)
  - Plan summary string shaping is currently performed in UI screen.
- [feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/components/ExerciseHistoryRow.kt](../feature/exercise/src/main/kotlin/io/github/stslex/workeeper/feature/exercise/ui/components/ExerciseHistoryRow.kt)
  - History date and set-summary shaping is currently performed in UI component.
- [feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/ui/components/TrainingHistoryRow.kt](../feature/single-training/src/main/kotlin/io/github/stslex/workeeper/feature/single_training/ui/components/TrainingHistoryRow.kt)
  - History date formatting is currently performed in UI component.
- [feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/ui/components/LiveExerciseCard.kt](../feature/live-workout/src/main/kotlin/io/github/stslex/workeeper/feature/live_workout/ui/components/LiveExerciseCard.kt)
  - Status-line text shaping is currently performed in UI component.

## Notes

- `feature/charts` is intentionally excluded from this audit as requested.
