// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.domain.mapper.LiveWorkoutDomainMapper.beatsBaseline
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetRowsResolver.withVisibleSets
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.withPresentation
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import javax.inject.Inject

/**
 * Set-level state mutator: every transition that rewrites `performedSets`,
 * `setDrafts`, or `exercise.status` runs through here. Owns the recompute pipeline
 * (status mapper + presentation + visible-row resolver) so handlers stay focused on
 * action dispatch, side effects, and IO.
 *
 * `@ViewModelScoped` per the Hilt scope rule for `*Mapper` names — one instance per
 * `LiveWorkoutStoreImpl`. Not a singleton: we never want a long-lived instance
 * holding `ResourceWrapper` across the app.
 */
@ViewModelScoped
internal class LiveSetMutator @Inject constructor(
    private val resourceWrapper: ResourceWrapper,
    private val statusMapper: StateStatusMapper,
) {

    /**
     * Re-runs the status pipeline against the current state. Used by error-revert
     * paths to drop optimistic mutations and resnap statuses from `performedSets`.
     */
    fun recomputeStatuses(state: State): State = statusMapper.recomputeStatuses(state)

    fun findExercise(state: State, performedExerciseUuid: String): LiveExerciseUiModel? =
        state.exercises.firstOrNull { it.performedExerciseUuid == performedExerciseUuid }

    /**
     * Visible-row seed *with draft priority* for paths that read the row the user is
     * about to commit (mark-done). Priority: draft > performed > plan > fallback. The
     * draft-first priority is intentional and differs from the visible-row resolver
     * (`performed > draft > plan > fallback`): mark-done captures user input still in
     * the draft layer; performed winning here would freeze the row's previous values.
     * In normal flow the row is undone when mark-done fires, so performed is absent
     * and the two priorities collapse.
     */
    fun draftFor(state: State, performedExerciseUuid: String, position: Int): LiveSetUiModel {
        val key = State.DraftKey(performedExerciseUuid, position)
        state.setDrafts[key]?.let { return it }
        val exercise = findExercise(state, performedExerciseUuid)
        val performed = exercise?.performedSets?.firstOrNull { it.position == position }
        if (performed != null) return performed
        val plan = exercise?.planSets?.getOrNull(position)
        return LiveSetUiModel(
            position = position,
            weight = plan?.weight,
            reps = plan?.reps ?: 0,
            type = plan?.type ?: SetTypeUiModel.WORK,
            isDone = false,
        )
    }

    fun applySetMarked(
        state: State,
        performedExerciseUuid: String,
        position: Int,
        draft: LiveSetUiModel,
    ): State = with(state) {
        val nextDrafts = setDrafts.toMutableMap()
        nextDrafts.remove(State.DraftKey(performedExerciseUuid, position))
        val updated = exercises.map { exercise ->
            if (exercise.performedExerciseUuid != performedExerciseUuid) return@map exercise
            val nextSets = exercise.performedSets.toMutableList()
            val existingIdx = nextSets.indexOfFirst { it.position == position }
            val baseline = preSessionPrSnapshot[exercise.exerciseUuid]
            val candidate = PlanSetDomain(
                weight = draft.weight,
                reps = draft.reps,
                type = draft.type.toDomain(),
            )
            val isPr = candidate.beatsBaseline(
                baselineWeight = baseline?.weight,
                baselineReps = baseline?.reps,
                type = exercise.exerciseType.toDomain(),
                hasBaseline = baseline != null,
            )
            val marked = draft.copy(
                position = position,
                isDone = true,
                isPersonalRecord = isPr,
            )
            if (existingIdx >= 0) {
                nextSets[existingIdx] = marked
            } else {
                nextSets.add(marked)
                nextSets.sortBy { it.position }
            }
            exercise.copy(performedSets = nextSets.toImmutableList())
        }.toImmutableList()
        copy(
            exercises = updated,
            setDrafts = nextDrafts.toImmutableMap(),
        ).let {
            statusMapper.recomputeStatuses(it)
        }
    }

    fun applySetUnchecked(state: State, performedExerciseUuid: String, position: Int): State {
        val updated = state.exercises.map { exercise ->
            if (exercise.performedExerciseUuid != performedExerciseUuid) return@map exercise
            val nextSets = exercise.performedSets
                .filterNot { it.position == position }
                .toImmutableList()
            exercise.copy(performedSets = nextSets)
        }.toImmutableList()
        return statusMapper.recomputeStatuses(state.copy(exercises = updated))
    }

    fun applySetTypeChange(
        state: State,
        performedExerciseUuid: String,
        position: Int,
        type: SetTypeUiModel,
    ): State {
        val updated = state.exercises.map { exercise ->
            if (exercise.performedExerciseUuid != performedExerciseUuid) return@map exercise
            val nextSets = exercise.performedSets.map { set ->
                if (set.position == position) set.copy(type = type) else set
            }.toImmutableList()
            exercise.copy(performedSets = nextSets)
        }.toImmutableList()
        return state.copy(exercises = updated).withVisibleSets()
    }

    fun applyResetSets(state: State, performedExerciseUuid: String): State {
        val updated = state.exercises.map { exercise ->
            if (exercise.performedExerciseUuid != performedExerciseUuid) return@map exercise
            exercise.copy(performedSets = persistentListOf())
        }.toImmutableList()
        val nextDrafts = state.setDrafts
            .filterKeys { it.performedExerciseUuid != performedExerciseUuid }
            .toImmutableMap()
        return statusMapper.recomputeStatuses(
            state.copy(
                exercises = updated,
                setDrafts = nextDrafts,
                pendingResetExerciseUuid = null,
            ),
        )
    }

    /**
     * Adds a new draft row at the next position seeded from `lastKnownSetSeed`
     * (latest draft → latest performed → last plan row). Returns `state` unchanged if
     * the exercise UUID is unknown — handler should not crash on a stale dispatch.
     */
    fun applyAddSet(state: State, performedExerciseUuid: String): State {
        val exercise = findExercise(state, performedExerciseUuid) ?: return state
        val nextPosition = nextSetPosition(state, exercise)
        val seed = lastKnownSetSeed(state, exercise)?.copy(
            position = nextPosition,
            isDone = false,
        ) ?: LiveSetUiModel(
            position = nextPosition,
            weight = null,
            reps = 0,
            type = SetTypeUiModel.WORK,
            isDone = false,
        )
        val key = State.DraftKey(performedExerciseUuid, nextPosition)
        return state.copy(
            setDrafts = (state.setDrafts + (key to seed)).toImmutableMap(),
        ).withVisibleSets()
    }

    fun applySkip(state: State, performedExerciseUuid: String): State {
        val updated = state.exercises.map { exercise ->
            if (exercise.performedExerciseUuid != performedExerciseUuid) return@map exercise
            exercise.copy(performedSets = persistentListOf())
        }.toImmutableList()
        val nextDrafts = state.setDrafts
            .filterKeys { it.performedExerciseUuid != performedExerciseUuid }
            .toImmutableMap()
        return markSkipped(
            state.copy(
                exercises = updated,
                setDrafts = nextDrafts,
                pendingSkipExerciseUuid = null,
            ),
            performedExerciseUuid,
        )
    }

    /**
     * Reproduces the snapshot → status pipeline in-memory: the targeted exercise
     * becomes SKIPPED while the rest are re-derived so the CURRENT marker walks past
     * the skipped row but still honors the user's explicit active set.
     */
    private fun markSkipped(state: State, performedExerciseUuid: String): State {
        val rebuilt = state.exercises.map { exercise ->
            if (exercise.performedExerciseUuid == performedExerciseUuid) {
                exercise.copy(status = ExerciseStatusUiModel.SKIPPED)
            } else {
                exercise
            }
        }.let { items ->
            statusMapper.recomputeOnly(items, state.activeExerciseUuids)
        }
        return state.copy(exercises = rebuilt).withPresentation(resourceWrapper)
    }

    fun nextSetPosition(state: State, exercise: LiveExerciseUiModel): Int {
        val draftMax = state.setDrafts.keys
            .filter { it.performedExerciseUuid == exercise.performedExerciseUuid }
            .maxOfOrNull { it.position } ?: -1
        val doneMax = exercise.performedSets.maxOfOrNull { it.position } ?: -1
        val planMax = exercise.planSets.lastIndex
        return maxOf(draftMax, doneMax, planMax) + 1
    }

    fun lastKnownSetSeed(state: State, exercise: LiveExerciseUiModel): LiveSetUiModel? {
        val draftMax = state.setDrafts
            .filterKeys { it.performedExerciseUuid == exercise.performedExerciseUuid }
            .maxByOrNull { it.key.position }?.value
        val doneMax = exercise.performedSets.maxByOrNull { it.position }
        val planMax = exercise.planSets
            .withIndex()
            .lastOrNull()
            ?.let { (position, plan) ->
                LiveSetUiModel(
                    position = position,
                    weight = plan.weight,
                    reps = plan.reps,
                    type = plan.type,
                    isDone = false,
                )
            }
        return sequenceOf(draftMax, doneMax, planMax)
            .filterNotNull()
            .maxByOrNull { it.position }
    }
}
