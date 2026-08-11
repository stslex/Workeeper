package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.withPresentation
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

@Inject
@SingleIn(LiveWorkoutScope::class)
internal class StateStatusMapper(
    private val resourceWrapper: ResourceWrapper,
) {

    fun recomputeStatuses(state: LiveWorkoutStore.State): LiveWorkoutStore.State {
        val refreshed = recomputeOnly(state.exercises, state.activeExerciseUuids)
        // Deliberately does NOT touch `expandedExerciseUuids`: under the amended disclosure
        // model (spec §7 superseded) completing, skipping or otherwise recomputing an
        // exercise never opens or closes a card. Only the header tap, the first-entry
        // initialisation and the mid-session add write that set.
        return state.copy(exercises = refreshed).withPresentation(resourceWrapper)
    }

    fun recomputeOnly(
        items: List<LiveExerciseUiModel>,
        activeUuids: Set<String>,
    ): ImmutableList<LiveExerciseUiModel> {
        val computed = items.map { exercise ->
            val skipped = exercise.status == ExerciseStatusUiModel.SKIPPED
            val isDone = ExerciseDoneRule.isDoneLive(
                planSets = exercise.planSets,
                performedSets = exercise.performedSets,
                visibleSets = exercise.visibleSets,
                skipped = skipped,
            )
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
