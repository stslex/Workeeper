// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * One undoable step, mirroring the mockup's toast machinery (extraction §1.9 + audit):
 * **single-level and one-shot** — a new undoable action replaces (and commits) the previous
 * one, `Отменить` restores exactly the latest, and the 5s timeout commits it.
 *
 * The restore is a snapshot of the three set-carrying State fields taken BEFORE the action
 * (the mockup's `snap()` deep-copies its whole model; these three are the whole model here —
 * statuses and disclosure re-derive). Two optional DB halves:
 *
 * - [undoCompensation] — a write that must happen ON UNDO because the action already hit the
 *   DB (re-upsert a deleted set row; delete a just-added exercise).
 * - [deferredCommit] — a destructive write POSTPONED until the undo window closes (the §6.1
 *   exercise deletion). Deferring is what makes undo safe against process death: a kill
 *   mid-toast leaves the exercise in the DB and the reload shows it again — the conservative
 *   direction.
 */
@Stable
data class PendingUndo(
    /** Monotonic per store instance; keys the toast's timeout effect. */
    val id: Long,
    val message: String,
    val restoreExercises: ImmutableList<LiveExerciseUiModel>,
    val restoreDrafts: ImmutableMap<LiveWorkoutStore.State.DraftKey, LiveSetUiModel>,
    val restoreOverrides: ImmutableMap<String, Int>,
    val undoCompensation: UndoCompensation? = null,
    val deferredCommit: DeferredCommit? = null,
) {

    @Stable
    sealed interface UndoCompensation {

        /** The undone `− подход` removed a persisted (done) row — put it back. */
        data class ReupsertSet(
            val performedExerciseUuid: String,
            val position: Int,
            val weight: Double?,
            val reps: Int,
            val type: SetTypeUiModel,
        ) : UndoCompensation

        /** The undone add wrote performed (+ maybe plan) rows — take them back out. */
        data class RemoveAddedExercise(
            val performedExerciseUuid: String,
            val exerciseUuid: String,
            val removeFromPlan: Boolean,
        ) : UndoCompensation
    }

    /** The §6.1 exercise deletion, held until the toast resolves. */
    @Stable
    data class DeferredCommit(
        val performedExerciseUuid: String,
        val exerciseUuid: String,
        val removeFromPlan: Boolean,
    )
}
