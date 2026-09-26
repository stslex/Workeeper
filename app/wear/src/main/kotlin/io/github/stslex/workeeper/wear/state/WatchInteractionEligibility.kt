package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.CommandValidation
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload

internal data class WatchInteractionEligibility(
    val editing: Boolean = false,
    val completion: Boolean = false,
    val retry: Boolean = false,
    val commandIdle: Boolean = true,
    val authorityAvailable: Boolean = false,
    val invalidField: NumericField? = null,
) {
    companion object {
        fun from(state: WatchReducerState): WatchInteractionEligibility {
            if (state.display is WatchDisplayState.ProtocolMismatch) return WatchInteractionEligibility()
            if (state.command?.status in RETRYABLE_STATUSES) return WatchInteractionEligibility(retry = true)
            val display = state.display as? WatchDisplayState.Active ?: return WatchInteractionEligibility()
            val payload = display.snapshot.payload as SnapshotPayload.ActiveWithTarget
            val draft = state.draft
            val invalidField = CommandValidation.validate(
                reps = draft?.reps ?: payload.target.reps,
                weightHundredthsKg = if (draft != null) draft.weightHundredthsKg else payload.target.weightHundredthsKg,
                exerciseType = payload.target.exerciseType,
            )?.field
            val available = state.authority is LocalMutationAuthority.Available
            val idle = state.command == null || state.command.status in TERMINAL_STATUSES
            return WatchInteractionEligibility(
                editing = available && idle,
                completion = available && idle && !state.refreshRequired && invalidField == null,
                commandIdle = idle,
                authorityAvailable = available,
                invalidField = invalidField,
            )
        }

        private val RETRYABLE_STATUSES = setOf(CommandStatus.TIMED_OUT_RETRYABLE, CommandStatus.RETRY_READY)
        private val TERMINAL_STATUSES = setOf(
            CommandStatus.SOURCE_INVALIDATED,
            CommandStatus.TERMINAL,
            CommandStatus.ABANDONED,
        )
    }
}
