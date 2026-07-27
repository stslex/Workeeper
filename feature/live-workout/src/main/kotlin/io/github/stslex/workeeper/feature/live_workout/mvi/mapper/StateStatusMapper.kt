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
import kotlinx.collections.immutable.toImmutableSet

@Inject
@SingleIn(LiveWorkoutScope::class)
internal class StateStatusMapper(
    private val resourceWrapper: ResourceWrapper,
) {

    fun recomputeStatuses(state: LiveWorkoutStore.State): LiveWorkoutStore.State {
        val refreshed = recomputeOnly(state.exercises, state.activeExerciseUuids)
        // The disclosure automaton (§7) is the single writer of `expandedExerciseUuids`. It
        // runs after statuses are recomputed because rules 4-6 read the fresh status, and it
        // replaces the old "prune stale entries" pass — a card that leaves DONE/CURRENT is
        // simply not selected by the table, so there is nothing left to prune.
        return state.copy(
            exercises = refreshed,
            expandedExerciseUuids = DisclosureAutomaton.resolve(
                exercises = refreshed,
                intent = state.disclosureIntent,
                previouslyExpanded = state.expandedExerciseUuids,
            ).toImmutableSet(),
        ).withPresentation(resourceWrapper)
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
