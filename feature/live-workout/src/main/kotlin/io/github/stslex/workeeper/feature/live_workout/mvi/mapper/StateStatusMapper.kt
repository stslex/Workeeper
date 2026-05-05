package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.withPresentation
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import javax.inject.Inject

@ViewModelScoped
internal class StateStatusMapper @Inject constructor(
    private val resourceWrapper: ResourceWrapper,
) {

    fun recomputeStatuses(state: LiveWorkoutStore.State): LiveWorkoutStore.State {
        val refreshed = recomputeOnly(state.exercises, state.activeExerciseUuids)
        // Prune expanded entries that no longer correspond to a DONE or CURRENT row.
        // SKIPPED/PENDING rows can't be expanded so any leftover entry is stale.
        val visibleUuids = refreshed
            .asSequence()
            .filter {
                it.status == ExerciseStatusUiModel.DONE ||
                    it.status == ExerciseStatusUiModel.CURRENT
            }
            .map { it.performedExerciseUuid }
            .toSet()
        return state.copy(
            exercises = refreshed,
            expandedExerciseUuids = state.expandedExerciseUuids
                .filter { it in visibleUuids }
                .toImmutableSet(),
        ).withPresentation(resourceWrapper)
    }

    fun recomputeOnly(
        items: List<LiveExerciseUiModel>,
        activeUuids: Set<String>,
    ): ImmutableList<LiveExerciseUiModel> {
        val computed = items.map { exercise ->
            val planSets = exercise.planSets
            val performed = exercise.performedSets
            val skipped = exercise.status == ExerciseStatusUiModel.SKIPPED
            val performedByPosition = performed.associateBy { it.position }
            val isDone = !skipped && if (planSets.isEmpty()) {
                performed.any { it.isDone }
            } else {
                performed.size >= planSets.size &&
                    planSets.indices.all { index -> performedByPosition[index]?.isDone == true }
            }
            Triple(exercise, isDone, skipped)
        }
        // Auto-default mirrors the mapper: if no exercise is explicitly active, the first
        // non-skipped non-done row stays CURRENT.
        val autoCurrentUuid = if (activeUuids.isEmpty()) {
            computed.firstOrNull { !it.third && !it.second }?.first?.performedExerciseUuid
        } else {
            null
        }
        return computed.map { (exercise, isDone, skipped) ->
            val nextStatus = when {
                skipped -> ExerciseStatusUiModel.SKIPPED
                isDone -> ExerciseStatusUiModel.DONE
                exercise.performedExerciseUuid in activeUuids ||
                    exercise.performedExerciseUuid == autoCurrentUuid ->
                    ExerciseStatusUiModel.CURRENT

                else -> ExerciseStatusUiModel.PENDING
            }
            exercise.copy(status = nextStatus)
        }.toImmutableList()
    }
}
