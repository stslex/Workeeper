// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveWorkoutMapper.toDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.store.PendingUndo

/**
 * The single-level undo machinery shared by every handler that snapshots (extraction §1.9,
 * mockup `snap()`/`undo()` semantics — one level, latest wins, replacement commits).
 *
 * All functions run against the store the handlers already delegate to; the interactor rides
 * in per call because each handler owns its own instance.
 */
internal object PendingUndoOps {

    private var counter: Long = 0L

    fun nextUndoId(): Long = ++counter

    /**
     * Replaces the pending undo with [next], committing the previous one's deferred write
     * first — the mockup's `snap()` overwrites the old snapshot the same way.
     */
    fun LiveWorkoutHandlerStore.pushUndo(interactor: LiveWorkoutInteractor, next: PendingUndo) {
        commitDeferred(interactor, state.value.pendingUndo)
        updateState { it.copy(pendingUndo = next) }
    }

    /** `Отменить`: restore the snapshot, run the DB compensation, drop the toast. */
    fun LiveWorkoutHandlerStore.undoPending(
        interactor: LiveWorkoutInteractor,
        setMutator: LiveSetMutator,
        onError: suspend (Throwable) -> Unit,
    ) {
        val pending = state.value.pendingUndo ?: return
        updateState { latest ->
            setMutator.recomputeStatuses(
                latest.copy(
                    exercises = pending.restoreExercises,
                    setDrafts = pending.restoreDrafts,
                    rowCountOverrides = pending.restoreOverrides,
                    pendingUndo = null,
                ),
            )
        }
        val compensation = pending.undoCompensation ?: return
        launch(onError = onError) {
            when (compensation) {
                is PendingUndo.UndoCompensation.ReupsertSet -> interactor.upsertSet(
                    performedExerciseUuid = compensation.performedExerciseUuid,
                    position = compensation.position,
                    set = PlanSetDomain(
                        weight = compensation.weight,
                        reps = compensation.reps,
                        type = compensation.type.toDomain(),
                    ),
                )

                is PendingUndo.UndoCompensation.RemoveAddedExercise ->
                    interactor.deleteExerciseFromSession(
                        performedExerciseUuid = compensation.performedExerciseUuid,
                        exerciseUuid = compensation.exerciseUuid,
                        trainingUuid = state.value.trainingUuid,
                        removeFromPlan = compensation.removeFromPlan,
                    )
            }
        }
    }

    /**
     * Closes the undo window (timeout, navigation, finish, or replacement) and commits the
     * deferred destructive write if one is held. Safe to call with no pending undo.
     */
    fun LiveWorkoutHandlerStore.flushPendingUndo(interactor: LiveWorkoutInteractor) {
        val pending = state.value.pendingUndo ?: return
        commitDeferred(interactor, pending)
        updateState { it.copy(pendingUndo = null) }
    }

    private fun LiveWorkoutHandlerStore.commitDeferred(
        interactor: LiveWorkoutInteractor,
        pending: PendingUndo?,
    ) {
        val deferred = pending?.deferredCommit ?: return
        launch(
            // The exercise is already gone from State; a failed hard-delete resurfaces on
            // the next reload rather than crashing the session. Logged, not surfaced.
            onError = { error -> logger.e(error, "deferred exercise delete failed") },
        ) {
            interactor.deleteExerciseFromSession(
                performedExerciseUuid = deferred.performedExerciseUuid,
                exerciseUuid = deferred.exerciseUuid,
                trainingUuid = state.value.trainingUuid,
                removeFromPlan = deferred.removeFromPlan,
            )
        }
    }
}

/** The mockup cuts toast names at 24 characters plus an ellipsis (session-v3f.html:471). */
internal fun String.truncateForToast(): String =
    if (length <= TOAST_NAME_MAX) this else take(TOAST_NAME_MAX) + "…"

private const val TOAST_NAME_MAX = 24
