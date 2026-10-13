// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.OutlineExercise
import io.github.stslex.workeeper.core.wear.protocol.OutlineRules
import io.github.stslex.workeeper.core.wear.protocol.OutlineSelection
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.exercise
import io.github.stslex.workeeper.wear.runtime.WatchRuntimeSnapshot
import io.github.stslex.workeeper.wear.state.ActiveFreshness
import io.github.stslex.workeeper.wear.state.CommandTarget
import io.github.stslex.workeeper.wear.state.ReducerEvent
import io.github.stslex.workeeper.wear.state.TargetKey
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.state.WatchInteractionEligibility
import io.github.stslex.workeeper.wear.state.WatchReducerState
import io.github.stslex.workeeper.wear.state.WearDraftPolicy
import io.github.stslex.workeeper.wear.state.activeOutlineOrNull
import io.github.stslex.workeeper.wear.state.isSelectable
import io.github.stslex.workeeper.wear.state.matchesCommandSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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

@Immutable
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
    val setScaleSlots: ImmutableList<WearSetScaleSlot> = if (setOrdinal != null && totalSets != null) {
        wearSetScaleSlots(setOrdinal, totalSets)
    } else {
        persistentListOf()
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
        val selection = state.display.activeOutlineOrNull()?.let { outline -> shownSelection(outline, snapshot) }
        val eligibility = WatchInteractionEligibility.from(state, selection)
        if (eligibility.retry) {
            return WearSurfaceModel(
                kind = WearSurfaceKind.RETRYABLE_ERROR,
                retryEnabled = !snapshot.recoveryRequired && !snapshot.readOnly,
                selectedLocale = locale,
            )
        }
        return when (val display = state.display) {
            // wear-paired-transport.md §7.5: a failed handshake must not load forever. The DISCONNECTED
            // surface draws an active target and cannot render without a snapshot, so an unreachable
            // and an unanswered phone both show the retry surface, whose Retry issues a refresh.
            is WatchDisplayState.Loading -> if (snapshot.link.failed) {
                WearSurfaceModel(
                    kind = WearSurfaceKind.RETRYABLE_ERROR,
                    retryEnabled = !snapshot.recoveryRequired,
                    selectedLocale = locale,
                )
            } else {
                WearSurfaceModel(WearSurfaceKind.LOADING, selectedLocale = locale)
            }
            is WatchDisplayState.NoSession -> WearSurfaceModel(WearSurfaceKind.NO_SESSION, selectedLocale = locale)
            is WatchDisplayState.ProtocolMismatch ->
                WearSurfaceModel(WearSurfaceKind.PROTOCOL_MISMATCH, selectedLocale = locale)
            is WatchDisplayState.Active -> outline(display, snapshot, eligibility, selection)
            is WatchDisplayState.PayloadTooLarge ->
                WearSurfaceModel(kind = WearSurfaceKind.PAYLOAD_TOO_LARGE, selectedLocale = locale)
        }
    }

    /**
     * The Phase 1 controller on the watch's selection (wear-phase-2-exercise-list.md §12.1 step 2):
     * a selected `Open` set is the active surface, a `Blocked` one and an exercise without sets ask
     * for the phone, and no selection is the finished workout.
     */
    private fun outline(
        display: WatchDisplayState.Active,
        snapshot: WatchRuntimeSnapshot,
        eligibility: WatchInteractionEligibility,
        selection: OutlineSelection?,
    ): WearSurfaceModel {
        val payload = display.snapshot.payload as SnapshotPayload.Active
        val locale = snapshot.locale
        selection ?: return complete(payload, locale)
        val exercise = requireNotNull(payload.exercise(selection.performedExerciseUuid))
        return when (val set = exercise.sets.firstOrNull { it.position == selection.setPosition }) {
            is OutlineSet.Open -> active(display, snapshot, eligibility, exercise, set)
            is OutlineSet.Blocked -> WearSurfaceModel(
                kind = WearSurfaceKind.PHONE_ACTION_UNSUPPORTED,
                trainingName = payload.trainingName.valueOrNull(),
                exerciseName = exercise.name.valueOrNull(),
                fieldError = set.field,
                selectedLocale = locale,
            )
            else -> WearSurfaceModel(
                kind = WearSurfaceKind.PHONE_ACTION_NO_SETS,
                trainingName = payload.trainingName.valueOrNull(),
                exerciseName = exercise.name.valueOrNull(),
                selectedLocale = locale,
            )
        }
    }

    private fun active(
        display: WatchDisplayState.Active,
        snapshot: WatchRuntimeSnapshot,
        eligibility: WatchInteractionEligibility,
        exercise: OutlineExercise,
        set: OutlineSet.Open,
    ): WearSurfaceModel {
        val state = snapshot.workout
        val blocked = snapshot.recoveryRequired || snapshot.readOnly
        val payload = display.snapshot.payload as SnapshotPayload.Active
        val progress = OutlineRules.exerciseProgress(payload)
        val draft = state.draft
        val reps = draft?.reps ?: set.reps
        // A present draft may explicitly clear the weight; only an absent draft uses the snapshot.
        val weight = if (draft != null) draft.weightHundredthsKg else set.weightHundredthsKg
        val available = eligibility.authorityAvailable
        val commandIdle = eligibility.commandIdle
        val submittedDraft = state.command?.takeUnless { commandIdle }?.let { command ->
            command.draft == draft &&
                display.snapshot.matchesCommandSource(command.source) &&
                command.target == CommandTarget.CompleteSet(TargetKey(exercise.performedExerciseUuid, set.position))
        } == true
        val completeEnabled = !blocked && eligibility.completion
        return WearSurfaceModel(
            kind = activeKind(display.freshness, snapshot),
            trainingName = payload.trainingName.valueOrNull(),
            exerciseName = exercise.name.valueOrNull(),
            completedExercises = progress.finished,
            totalExercises = progress.total,
            setOrdinal = exercise.sets.indexOf(set) + 1,
            totalSets = exercise.sets.size,
            reps = reps,
            weightHundredthsKg = weight,
            hasUnsubmittedDraft = !snapshot.readOnly && draft != null && !submittedDraft,
            weighted = exercise.exerciseType == ExerciseTypeWire.WEIGHTED,
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

    /** The owner keeps its selection on the outline (§7.2); a snapshot without one shows rule R. */
    private fun shownSelection(outline: SnapshotPayload.Active, snapshot: WatchRuntimeSnapshot): OutlineSelection? =
        snapshot.selection?.takeIf { outline.isSelectable(it) } ?: OutlineRules.defaultSelection(outline)

    /** No selection: the workout is complete (§5.3), with the exercise progress of P13. */
    private fun complete(payload: SnapshotPayload.Active, locale: Locale): WearSurfaceModel {
        val progress = OutlineRules.exerciseProgress(payload)
        return WearSurfaceModel(
            kind = WearSurfaceKind.WORKOUT_COMPLETE,
            trainingName = payload.trainingName.valueOrNull(),
            completedExercises = progress.finished,
            totalExercises = progress.total,
            selectedLocale = locale,
        )
    }

    private fun BoundedDisplayName.valueOrNull(): String? =
        (this as? BoundedDisplayName.Value)?.value
}
