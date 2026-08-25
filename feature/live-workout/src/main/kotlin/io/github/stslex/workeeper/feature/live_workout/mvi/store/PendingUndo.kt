// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.store

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/**
 * One undoable step: single-level and one-shot — a new undoable action replaces and commits the
 * previous one. See documentation/feature-specs/live-workout.md.
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
