// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

import androidx.compose.runtime.Stable

/**
 * D5: the screen's copy outside composition, read once per load at the load's suspend boundary.
 * The status pipeline, the dialogs, the toasts and the two synchronous errors read it from State.
 * Every field is its catalog text, verbatim: each `…Template` keeps its positional placeholders,
 * which kit's `fillTemplate` fills synchronously where the text is used.
 */
@Stable
data class LiveWorkoutCopy(
    val trainingNamePlaceholder: String,
    val statusSkipped: String,
    val statusNoPlan: String,
    val repsUnit: String,
    val metaTemplate: String,
    val metaSkippedTemplate: String,
    val resetTitle: String,
    val resetBody: String,
    val resetConfirm: String,
    val resetDismiss: String,
    val emptyFinishDiscard: String,
    val emptyFinishContinue: String,
    val cancelTitle: String,
    val cancelBody: String,
    val cancelConfirm: String,
    val cancelDismiss: String,
    val deleteSessionUnnamed: String,
    val deleteSessionProgressTemplate: String,
    val finishNameLabel: String,
    val finishNameRequired: String,
    val finishSetsCountTemplate: String,
    val finishExercisesTemplate: String,
    val finishExercisesWithSkippedTemplate: String,
    val finishPrWeightedTemplate: String,
    val finishPrWeightlessTemplate: String,
    val toastSetAdded: String,
    val toastSetRemoved: String,
    val toastExerciseRemovedTemplate: String,
    val toastExerciseRemovedFromWorkoutTemplate: String,
    val errorInvalidReps: String,
    val errorFinishFailed: String,
) {

    companion object {

        /** Before the first load; that load's State replaces every label derived from it. */
        val EMPTY: LiveWorkoutCopy = LiveWorkoutCopy(
            trainingNamePlaceholder = "",
            statusSkipped = "",
            statusNoPlan = "",
            repsUnit = "",
            metaTemplate = "",
            metaSkippedTemplate = "",
            resetTitle = "",
            resetBody = "",
            resetConfirm = "",
            resetDismiss = "",
            emptyFinishDiscard = "",
            emptyFinishContinue = "",
            cancelTitle = "",
            cancelBody = "",
            cancelConfirm = "",
            cancelDismiss = "",
            deleteSessionUnnamed = "",
            deleteSessionProgressTemplate = "",
            finishNameLabel = "",
            finishNameRequired = "",
            finishSetsCountTemplate = "",
            finishExercisesTemplate = "",
            finishExercisesWithSkippedTemplate = "",
            finishPrWeightedTemplate = "",
            finishPrWeightlessTemplate = "",
            toastSetAdded = "",
            toastSetRemoved = "",
            toastExerciseRemovedTemplate = "",
            toastExerciseRemovedFromWorkoutTemplate = "",
            errorInvalidReps = "",
            errorFinishFailed = "",
        )
    }
}
