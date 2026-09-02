// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CommandValidation
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.PhoneActionReason
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.wear.state.ActiveFreshness
import io.github.stslex.workeeper.wear.state.CommandStatus
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.state.ReducerEvent
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.state.WatchReducerState

internal enum class WearSurfaceKind {
    LOADING,
    NO_SESSION,
    ACTIVE,
    PHONE_ACTION_NO_SETS,
    PHONE_ACTION_UNSUPPORTED,
    PAYLOAD_TOO_LARGE,
    WORKOUT_COMPLETE,
    REFRESH_REQUIRED,
    DISCONNECTED,
    RETRYABLE_ERROR,
    PROTOCOL_MISMATCH,
}

internal data class WearSurfaceModel(
    val kind: WearSurfaceKind,
    val trainingName: String? = null,
    val exerciseName: String? = null,
    val completedExercises: Int? = null,
    val totalExercises: Int? = null,
    val setOrdinal: Int? = null,
    val totalSets: Int? = null,
    val reps: Int? = null,
    val weightHundredthsKg: Int? = null,
    val weighted: Boolean = false,
    val controlsVisible: Boolean = false,
    val controlsEnabled: Boolean = false,
    val completeEnabled: Boolean = false,
    val retryEnabled: Boolean = false,
    val fieldError: NumericField? = null,
)

internal typealias WearSurfaceState = WearSurfaceModel

internal object WearSurfaceMapper {

    fun map(state: WatchReducerState): WearSurfaceModel {
        if (state.display is WatchDisplayState.ProtocolMismatch) {
            return WearSurfaceModel(kind = WearSurfaceKind.PROTOCOL_MISMATCH)
        }
        if (state.command?.status in RETRYABLE_STATUSES) {
            return WearSurfaceModel(
                kind = WearSurfaceKind.RETRYABLE_ERROR,
                retryEnabled = true,
            )
        }
        return when (val display = state.display) {
            is WatchDisplayState.Loading -> WearSurfaceModel(WearSurfaceKind.LOADING)
            is WatchDisplayState.NoSession -> WearSurfaceModel(WearSurfaceKind.NO_SESSION)
            is WatchDisplayState.ProtocolMismatch -> WearSurfaceModel(WearSurfaceKind.PROTOCOL_MISMATCH)
            is WatchDisplayState.Active -> active(display, state)
            is WatchDisplayState.PhoneActionRequired -> phoneAction(display)
            is WatchDisplayState.WorkoutComplete -> complete(display)
        }
    }

    private fun active(
        display: WatchDisplayState.Active,
        state: WatchReducerState,
    ): WearSurfaceModel {
        val payload = display.snapshot.payload as SnapshotPayload.ActiveWithTarget
        val draft = state.draft
        val reps = draft?.reps ?: payload.target.reps
        val weight = draft?.weightHundredthsKg ?: payload.target.weightHundredthsKg
        val available = state.authority is LocalMutationAuthority.Available
        val commandIdle = state.command == null || state.command.status in TERMINAL_STATUSES
        val numericValid = CommandValidation.validate(
            reps = reps,
            weightHundredthsKg = weight,
            exerciseType = payload.target.exerciseType,
        ) == null
        return WearSurfaceModel(
            kind = when (display.freshness) {
                ActiveFreshness.FRESH -> WearSurfaceKind.ACTIVE
                ActiveFreshness.DISCONNECTED -> WearSurfaceKind.DISCONNECTED
                ActiveFreshness.REFRESH_REQUIRED,
                ActiveFreshness.STALE,
                -> WearSurfaceKind.REFRESH_REQUIRED
            },
            trainingName = payload.trainingName.valueOrNull(),
            exerciseName = payload.target.exerciseName.valueOrNull(),
            completedExercises = payload.completedExercises,
            totalExercises = payload.totalExercises,
            setOrdinal = payload.target.setOrdinal,
            totalSets = payload.target.totalSets,
            reps = reps,
            weightHundredthsKg = weight,
            weighted = payload.target.exerciseType == ExerciseTypeWire.WEIGHTED,
            controlsVisible = true,
            controlsEnabled = available && commandIdle,
            completeEnabled = available && commandIdle && numericValid,
            fieldError = state.events.filterIsInstance<ReducerEvent.FieldError>().lastOrNull()?.field,
        )
    }

    private fun phoneAction(display: WatchDisplayState.PhoneActionRequired): WearSurfaceModel {
        val payload = display.snapshot.payload as SnapshotPayload.PhoneActionRequired
        return when (val reason = payload.reason) {
            is PhoneActionReason.NoSetRows -> WearSurfaceModel(
                kind = WearSurfaceKind.PHONE_ACTION_NO_SETS,
                exerciseName = reason.exerciseName.valueOrNull(),
            )
            is PhoneActionReason.UnsupportedNumericValues -> WearSurfaceModel(
                kind = WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
                exerciseName = reason.exerciseName.valueOrNull(),
                fieldError = reason.field,
            )
            is PhoneActionReason.PayloadTooLarge -> WearSurfaceModel(
                kind = WearSurfaceKind.PAYLOAD_TOO_LARGE,
            )
        }
    }

    private fun complete(display: WatchDisplayState.WorkoutComplete): WearSurfaceModel {
        val payload = display.snapshot.payload as SnapshotPayload.WorkoutComplete
        return WearSurfaceModel(
            kind = WearSurfaceKind.WORKOUT_COMPLETE,
            trainingName = payload.trainingName.valueOrNull(),
            completedExercises = payload.completedExercises,
            totalExercises = payload.totalExercises,
        )
    }

    private fun BoundedDisplayName.valueOrNull(): String? =
        (this as? BoundedDisplayName.Value)?.value

    private val RETRYABLE_STATUSES = setOf(
        CommandStatus.TIMED_OUT_RETRYABLE,
        CommandStatus.RETRY_READY,
    )
    private val TERMINAL_STATUSES = setOf(
        CommandStatus.SOURCE_INVALIDATED,
        CommandStatus.TERMINAL,
        CommandStatus.ABANDONED,
    )
}
