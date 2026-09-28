// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.PhoneActionReason
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import io.github.stslex.workeeper.wear.state.ActiveFreshness
import io.github.stslex.workeeper.wear.state.ReducerEvent
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.state.WatchInteractionEligibility
import io.github.stslex.workeeper.wear.state.WatchReducerState
import io.github.stslex.workeeper.wear.state.WearDraftPolicy
import io.github.stslex.workeeper.wear.state.sourceVersion
import io.github.stslex.workeeper.wear.state.targetKeyOrNull
import java.util.Locale

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

internal enum class CompletionUnavailableReason {
    DISCONNECTED,
    REFRESH_REQUIRED,
    COMMAND_IN_FLIGHT,
    INVALID_REPS,
    INVALID_WEIGHT,
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
    val completionUnavailableReason: CompletionUnavailableReason? = null,
    val hasUnsubmittedDraft: Boolean = false,
    val retryEnabled: Boolean = false,
    val fieldError: NumericField? = null,
    val selectedLocale: Locale = Locale.getDefault(),
) {
    // A body property is recomputed by copy; a constructor default would retain stale labels.
    val formattedValues: WearFormattedValues = WearValueFormatter.format(reps, weightHundredthsKg, selectedLocale)
    val incrementRepsEnabled: Boolean = controlsEnabled && reps?.let(WearDraftPolicy::incrementReps) != null
    val decrementRepsEnabled: Boolean = controlsEnabled && reps?.let(WearDraftPolicy::decrementReps) != null
    val incrementWeightEnabled: Boolean = controlsEnabled && weighted &&
        WearDraftPolicy.incrementWeight(weightHundredthsKg) != null
    val decrementWeightEnabled: Boolean = controlsEnabled && weighted &&
        WearDraftPolicy.decrementWeight(weightHundredthsKg) != null
    val setScaleSlots: List<WearSetScaleSlot> = if (setOrdinal != null && totalSets != null) {
        wearSetScaleSlots(setOrdinal, totalSets)
    } else {
        emptyList()
    }
}

internal typealias WearSurfaceState = WearSurfaceModel

internal object WearSurfaceMapper {

    fun map(state: WatchReducerState): WearSurfaceModel = map(WatchRuntimeSnapshot(workout = state))

    fun map(snapshot: WatchRuntimeSnapshot): WearSurfaceModel {
        val state = snapshot.workout
        val locale = snapshot.locale
        if (snapshot.noSession) return WearSurfaceModel(WearSurfaceKind.NO_SESSION, selectedLocale = locale)
        if (state.display is WatchDisplayState.ProtocolMismatch) {
            return WearSurfaceModel(kind = WearSurfaceKind.PROTOCOL_MISMATCH, selectedLocale = locale)
        }
        val eligibility = WatchInteractionEligibility.from(state)
        if (eligibility.retry) {
            return WearSurfaceModel(
                kind = WearSurfaceKind.RETRYABLE_ERROR,
                retryEnabled = !snapshot.recoveryRequired && !snapshot.readOnly,
                selectedLocale = locale,
            )
        }
        return when (val display = state.display) {
            is WatchDisplayState.Loading -> WearSurfaceModel(WearSurfaceKind.LOADING, selectedLocale = locale)
            is WatchDisplayState.NoSession -> WearSurfaceModel(WearSurfaceKind.NO_SESSION, selectedLocale = locale)
            is WatchDisplayState.ProtocolMismatch ->
                WearSurfaceModel(WearSurfaceKind.PROTOCOL_MISMATCH, selectedLocale = locale)
            is WatchDisplayState.Active -> active(display, snapshot, eligibility)
            is WatchDisplayState.PhoneActionRequired -> phoneAction(display, locale)
            is WatchDisplayState.WorkoutComplete -> complete(display, locale)
        }
    }

    private fun active(
        display: WatchDisplayState.Active,
        snapshot: WatchRuntimeSnapshot,
        eligibility: WatchInteractionEligibility,
    ): WearSurfaceModel {
        val state = snapshot.workout
        val blocked = snapshot.recoveryRequired || snapshot.readOnly
        val payload = display.snapshot.payload as SnapshotPayload.ActiveWithTarget
        val draft = state.draft
        val reps = draft?.reps ?: payload.target.reps
        // A present draft may explicitly clear the weight; only an absent draft uses the snapshot.
        val weight = if (draft != null) draft.weightHundredthsKg else payload.target.weightHundredthsKg
        val available = eligibility.authorityAvailable
        val commandIdle = eligibility.commandIdle
        val submittedDraft = state.command?.takeUnless { commandIdle }?.let { command ->
            command.draft == draft &&
                command.source == display.snapshot.sourceVersion() &&
                command.target == display.snapshot.targetKeyOrNull()
        } == true
        val completeEnabled = !blocked && eligibility.completion
        return WearSurfaceModel(
            kind = activeKind(display.freshness, snapshot),
            trainingName = payload.trainingName.valueOrNull(),
            exerciseName = payload.target.exerciseName.valueOrNull(),
            completedExercises = payload.completedExercises,
            totalExercises = payload.totalExercises,
            setOrdinal = payload.target.setOrdinal,
            totalSets = payload.target.totalSets,
            reps = reps,
            weightHundredthsKg = weight,
            hasUnsubmittedDraft = !snapshot.readOnly && draft != null && !submittedDraft,
            weighted = payload.target.exerciseType == ExerciseTypeWire.WEIGHTED,
            controlsVisible = true,
            controlsEnabled = !blocked && eligibility.editing,
            completeEnabled = completeEnabled,
            completionUnavailableReason = if (completeEnabled) {
                null
            } else if (blocked) {
                CompletionUnavailableReason.REFRESH_REQUIRED
            } else {
                completionUnavailableReason(
                    display.freshness,
                    state.refreshRequired,
                    commandIdle,
                    available,
                    eligibility.invalidField,
                )
            },
            fieldError = state.events.filterIsInstance<ReducerEvent.FieldError>().lastOrNull()?.field,
            selectedLocale = snapshot.locale,
        )
    }

    private fun activeKind(freshness: ActiveFreshness, snapshot: WatchRuntimeSnapshot): WearSurfaceKind = when {
        snapshot.recoveryRequired || snapshot.readOnly && freshness == ActiveFreshness.FRESH ->
            WearSurfaceKind.REFRESH_REQUIRED
        freshness == ActiveFreshness.FRESH -> WearSurfaceKind.ACTIVE
        freshness == ActiveFreshness.DISCONNECTED -> WearSurfaceKind.DISCONNECTED
        else -> WearSurfaceKind.REFRESH_REQUIRED
    }

    private fun completionUnavailableReason(
        freshness: ActiveFreshness,
        refreshRequired: Boolean,
        commandIdle: Boolean,
        available: Boolean,
        invalidField: NumericField?,
    ): CompletionUnavailableReason = when {
        freshness == ActiveFreshness.DISCONNECTED -> CompletionUnavailableReason.DISCONNECTED
        freshness != ActiveFreshness.FRESH || refreshRequired -> CompletionUnavailableReason.REFRESH_REQUIRED
        !commandIdle -> CompletionUnavailableReason.COMMAND_IN_FLIGHT
        !available -> CompletionUnavailableReason.REFRESH_REQUIRED
        invalidField == NumericField.REPS -> CompletionUnavailableReason.INVALID_REPS
        invalidField == NumericField.WEIGHT -> CompletionUnavailableReason.INVALID_WEIGHT
        else -> error("A blocked active completion must have an unavailable reason")
    }

    private fun phoneAction(display: WatchDisplayState.PhoneActionRequired, locale: Locale): WearSurfaceModel {
        val payload = display.snapshot.payload as SnapshotPayload.PhoneActionRequired
        return when (val reason = payload.reason) {
            is PhoneActionReason.NoSetRows -> WearSurfaceModel(
                kind = WearSurfaceKind.PHONE_ACTION_NO_SETS,
                exerciseName = reason.exerciseName.valueOrNull(),
                selectedLocale = locale,
            )
            is PhoneActionReason.UnsupportedNumericValues -> WearSurfaceModel(
                kind = WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
                exerciseName = reason.exerciseName.valueOrNull(),
                fieldError = reason.field,
                selectedLocale = locale,
            )
            is PhoneActionReason.PayloadTooLarge -> WearSurfaceModel(
                kind = WearSurfaceKind.PAYLOAD_TOO_LARGE,
                selectedLocale = locale,
            )
        }
    }

    private fun complete(display: WatchDisplayState.WorkoutComplete, locale: Locale): WearSurfaceModel {
        val payload = display.snapshot.payload as SnapshotPayload.WorkoutComplete
        return WearSurfaceModel(
            kind = WearSurfaceKind.WORKOUT_COMPLETE,
            trainingName = payload.trainingName.valueOrNull(),
            completedExercises = payload.completedExercises,
            totalExercises = payload.totalExercises,
            selectedLocale = locale,
        )
    }

    private fun BoundedDisplayName.valueOrNull(): String? =
        (this as? BoundedDisplayName.Value)?.value
}
