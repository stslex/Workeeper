// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

/**
 * CB10: every value is its field's name, a template's placeholders after it in the catalog's
 * order, so an identity that reads the wrong field, or fills the wrong argument, shows it in its
 * expected string.
 */
internal val NAMED_COPY = LiveWorkoutCopy(
    trainingNamePlaceholder = "trainingNamePlaceholder",
    statusSkipped = "statusSkipped",
    statusNoPlan = "statusNoPlan",
    repsUnit = "repsUnit",
    metaTemplate = "metaTemplate %1\$d %2\$d %3\$d %4\$d",
    metaSkippedTemplate = "metaSkippedTemplate %1\$s %2\$d",
    resetTitle = "resetTitle",
    resetBody = "resetBody",
    resetConfirm = "resetConfirm",
    resetDismiss = "resetDismiss",
    emptyFinishDiscard = "emptyFinishDiscard",
    emptyFinishContinue = "emptyFinishContinue",
    cancelTitle = "cancelTitle",
    cancelBody = "cancelBody",
    cancelConfirm = "cancelConfirm",
    cancelDismiss = "cancelDismiss",
    deleteSessionUnnamed = "deleteSessionUnnamed",
    deleteSessionProgressTemplate = "deleteSessionProgressTemplate %1\$d %2\$d %3\$d",
    finishNameLabel = "finishNameLabel",
    finishNameRequired = "finishNameRequired",
    finishSetsCountTemplate = "finishSetsCountTemplate %1\$d",
    finishExercisesTemplate = "finishExercisesTemplate %1\$d %2\$d",
    finishExercisesWithSkippedTemplate = "finishExercisesWithSkippedTemplate %1\$d %2\$d %3\$d",
    finishPrWeightedTemplate = "finishPrWeightedTemplate %1\$s %2\$d",
    finishPrWeightlessTemplate = "finishPrWeightlessTemplate %1\$d",
    toastSetAdded = "toastSetAdded",
    toastSetRemoved = "toastSetRemoved",
    toastExerciseRemovedTemplate = "toastExerciseRemovedTemplate %1\$s",
    toastExerciseRemovedFromWorkoutTemplate = "toastExerciseRemovedFromWorkoutTemplate %1\$s",
    errorInvalidReps = "errorInvalidReps",
    errorFinishFailed = "errorFinishFailed",
)
