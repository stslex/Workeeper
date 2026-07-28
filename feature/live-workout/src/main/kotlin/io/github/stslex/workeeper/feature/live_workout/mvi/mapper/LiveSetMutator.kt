// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutScope
import io.github.stslex.workeeper.feature.live_workout.domain.mapper.LiveWorkoutDomainMapper.beatsBaseline
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetRowsResolver.withVisibleSets
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.DialogState
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

/**
 * Set-level state mutator: every transition that rewrites `performedSets`,
 * `setDrafts`, or `exercise.status` runs through here. Owns the recompute pipeline
 * (status mapper + presentation + visible-row resolver) so handlers stay focused on
 * action dispatch, side effects, and IO.
 *
 * `@SingleIn(LiveWorkoutScope)` (Metro; formerly Hilt `@ViewModelScoped`) — one instance per
 * `LiveWorkoutStoreImpl`.
 */
@Inject
@SingleIn(LiveWorkoutScope::class)
internal class LiveSetMutator(
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
        val exercise = findExercise(state, performedExerciseUuid) ?: return state
        val performedRow = exercise.performedSets.firstOrNull { it.position == position }
            ?: return state
        val updated = state.exercises.map { ex ->
            if (ex.performedExerciseUuid != performedExerciseUuid) return@map ex
            val nextSets = ex.performedSets
                .filterNot { it.position == position }
                .toImmutableList()
            ex.copy(performedSets = nextSets)
        }.toImmutableList()
        val draftKey = State.DraftKey(performedExerciseUuid, position)
        val restoredDraft = performedRow.copy(
            isDone = false,
            isPersonalRecord = false,
        )
        val nextDrafts = (state.setDrafts + (draftKey to restoredDraft)).toImmutableMap()
        return state.copy(
            exercises = updated,
            setDrafts = nextDrafts,
        ).withVisibleSets().let { statusMapper.recomputeStatuses(it) }
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
                rowCountOverrides = (state.rowCountOverrides - performedExerciseUuid)
                    .toImmutableMap(),
                dialogState = DialogState.Hidden,
            ),
        )
    }

    /**
     * `+ подход` (extraction §1.7): appends a row directly after the last VISIBLE one,
     * seeded by copying it (`addSet` copies the last set's w/r — session-v3f.html:423) and
     * never marked as a record (`addSet` omits `pr`). The row-count override moves with it,
     * so an exercise the user truncated grows back one row at a time rather than snapping
     * to the plan length.
     *
     * Statuses are recomputed because §6.4 says adding a set to a COMPLETED exercise
     * returns it to incomplete — the fresh unfilled row breaks `isDoneLive` and the card
     * re-derives. Returns `state` unchanged on a stale dispatch.
     */
    fun applyAddSet(state: State, performedExerciseUuid: String): State {
        val exercise = findExercise(state, performedExerciseUuid) ?: return state
        val visible = exercise.visibleSets
        val nextPosition = visible.size
        val seed = visible.lastOrNull()?.copy(
            position = nextPosition,
            isDone = false,
            isPersonalRecord = false,
        ) ?: lastKnownSetSeed(state, exercise)?.copy(
            position = nextPosition,
            isDone = false,
            isPersonalRecord = false,
        ) ?: LiveSetUiModel(
            position = nextPosition,
            weight = null,
            reps = 0,
            type = SetTypeUiModel.WORK,
            isDone = false,
        )
        val key = State.DraftKey(performedExerciseUuid, nextPosition)
        // Rows first, statuses second: `isDoneLive` reads `visibleSets`, so the fresh row
        // must be resolved before the DONE derivation runs or a completed exercise stays
        // completed (§6.4's rule would silently not fire).
        return state.copy(
            setDrafts = (state.setDrafts + (key to seed)).toImmutableMap(),
            rowCountOverrides = (state.rowCountOverrides + (performedExerciseUuid to nextPosition + 1))
                .toImmutableMap(),
        ).withVisibleSets().let { statusMapper.recomputeStatuses(it) }
    }

    /** What [applyRemoveLastSet] did, so the handler knows whether a DB row must go too. */
    data class RemoveLastSetResult(
        val state: State,
        /** Set when the removed row was a persisted (done) set; null for draft/plan rows. */
        val removedPerformedPosition: Int?,
    )

    /**
     * `− подход` (§6.4): removes the LAST visible row — always the last, middle deletion is
     * not planned — even when it is a done set (the mockup's `delSet` pops regardless,
     * session-v3f.html:429). Refuses to go below one row; the setbar disables the button
     * there and this guard backs it. The plan is untouched: truncation lives entirely in
     * [State.rowCountOverrides].
     */
    fun applyRemoveLastSet(state: State, performedExerciseUuid: String): RemoveLastSetResult {
        val exercise = findExercise(state, performedExerciseUuid)
            ?: return RemoveLastSetResult(state, null)
        val visible = exercise.visibleSets
        if (visible.size <= 1) return RemoveLastSetResult(state, null)
        val last = visible.last()
        val removedPerformed = exercise.performedSets
            .firstOrNull { it.position == last.position }
        val updated = state.exercises.map { ex ->
            if (ex.performedExerciseUuid != performedExerciseUuid) return@map ex
            ex.copy(
                performedSets = ex.performedSets
                    .filterNot { it.position == last.position }
                    .toImmutableList(),
            )
        }.toImmutableList()
        val nextDrafts = state.setDrafts
            .filterKeys {
                it.performedExerciseUuid != performedExerciseUuid || it.position < last.position
            }
            .toImmutableMap()
        val nextState = state.copy(
            exercises = updated,
            setDrafts = nextDrafts,
            rowCountOverrides = (state.rowCountOverrides + (performedExerciseUuid to visible.size - 1))
                .toImmutableMap(),
        ).withVisibleSets().let { statusMapper.recomputeStatuses(it) }
        return RemoveLastSetResult(
            state = nextState,
            removedPerformedPosition = removedPerformed?.position,
        )
    }

    /**
     * §6.1 skip: a reversible flag, NOT a destructive reset — performed sets and drafts all
     * survive, which is what makes `Вернуть в сессию` lossless. Un-skipping seeds the row
     * PENDING and lets the status pipeline re-derive DONE/CURRENT from the preserved sets.
     * (The earlier revision wiped sets here; the confirmation dialog that guarded that wipe
     * is gone with it — extraction §6.1/C9.)
     */
    fun applySkipToggle(state: State, performedExerciseUuid: String, skipped: Boolean): State {
        val rebuilt = state.exercises.map { exercise ->
            if (exercise.performedExerciseUuid == performedExerciseUuid) {
                exercise.copy(
                    status = if (skipped) {
                        ExerciseStatusUiModel.SKIPPED
                    } else {
                        ExerciseStatusUiModel.PENDING
                    },
                )
            } else {
                exercise
            }
        }.let { items ->
            statusMapper.recomputeOnly(items, state.activeExerciseUuids)
        }
        return statusMapper.recomputeStatuses(state.copy(exercises = rebuilt))
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
