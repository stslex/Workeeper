package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CommandValidation
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.OutlineExercise
import io.github.stslex.workeeper.core.wear.protocol.OutlineRules
import io.github.stslex.workeeper.core.wear.protocol.OutlineSelection
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.OutlineStatus
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.exercise

internal data class WatchInteractionEligibility(
    val editing: Boolean = false,
    val completion: Boolean = false,
    val retry: Boolean = false,
    val commandIdle: Boolean = true,
    val authorityAvailable: Boolean = false,
    val invalidField: NumericField? = null,
) {
    companion object {
        /**
         * wear-phase-2-exercise-list.md §7.1: completion needs `Available` authority, no open command,
         * no refresh required, an `Open` selected set and valid draft values.
         */
        fun from(state: WatchReducerState, selection: OutlineSelection?): WatchInteractionEligibility {
            if (state.display is WatchDisplayState.ProtocolMismatch) return WatchInteractionEligibility()
            if (state.command?.status in RETRYABLE_STATUSES) return WatchInteractionEligibility(retry = true)
            val outline = state.display.activeOutlineOrNull() ?: return WatchInteractionEligibility()
            val selected = selection?.let { outline.openSetOrNull(it) }
            val draft = state.draft
            val invalidField = selected?.let { (exercise, set) ->
                CommandValidation.validate(
                    reps = draft?.reps ?: set.reps,
                    weightHundredthsKg = if (draft != null) draft.weightHundredthsKg else set.weightHundredthsKg,
                    exerciseType = exercise.exerciseType,
                )?.field
            }
            val available = state.authority is LocalMutationAuthority.Available
            val idle = state.isCommandIdle()
            return WatchInteractionEligibility(
                editing = available && idle && selected != null,
                completion = available && idle && !state.refreshRequired && selected != null && invalidField == null,
                commandIdle = idle,
                authorityAvailable = available,
                invalidField = invalidField,
            )
        }

        /**
         * §7.1: skip ([skipped] true) and return need `Available` authority, no open command, no refresh
         * required, and the admitted outline's `skippable`.
         */
        fun canSetSkipped(state: WatchReducerState, performedExerciseUuid: CanonicalUuid, skipped: Boolean): Boolean {
            if (state.display is WatchDisplayState.ProtocolMismatch) return false
            val outline = state.display.activeOutlineOrNull() ?: return false
            return state.authority is LocalMutationAuthority.Available &&
                state.isCommandIdle() &&
                !state.refreshRequired &&
                OutlineRules.skippable(outline, performedExerciseUuid, skipped)
        }

        private fun WatchReducerState.isCommandIdle(): Boolean = command == null || command.status in TERMINAL_STATUSES

        private val RETRYABLE_STATUSES = setOf(CommandStatus.TIMED_OUT_RETRYABLE, CommandStatus.RETRY_READY)
        private val TERMINAL_STATUSES = setOf(
            CommandStatus.SOURCE_INVALIDATED,
            CommandStatus.TERMINAL,
            CommandStatus.ABANDONED,
        )
    }
}

/**
 * The selected exercise and its `Open` set when the selection is one the watch may complete
 * (`OutlineRules.completable`), else null.
 */
internal fun SnapshotPayload.Active.openSetOrNull(
    selection: OutlineSelection,
): Pair<OutlineExercise, OutlineSet.Open>? {
    val exercise = exercise(selection.performedExerciseUuid)?.takeUnless { it.skipped } ?: return null
    val set = exercise.sets.firstOrNull { it.position == selection.setPosition } as? OutlineSet.Open ?: return null
    return exercise to set
}

/**
 * True when [selection] names something this outline shows (§7.2): an incomplete set of an `OPEN`
 * exercise, or no position on a `NO_SETS` exercise.
 */
internal fun SnapshotPayload.Active.isSelectable(selection: OutlineSelection): Boolean {
    val exercise = exercise(selection.performedExerciseUuid) ?: return false
    return when (OutlineRules.status(exercise)) {
        OutlineStatus.OPEN -> exercise.sets.any { it.position == selection.setPosition && it !is OutlineSet.Done }
        OutlineStatus.NO_SETS -> selection.setPosition == null
        OutlineStatus.FINISHED, OutlineStatus.SKIPPED -> false
    }
}
