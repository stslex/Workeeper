package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.runtime.ControllerAction
import io.github.stslex.workeeper.wear.state.WearDraftPolicy

internal fun WearSurfaceModel.withRelativeDraft(action: ControllerAction.AdjustDraft): WearSurfaceModel =
    when (action.field) {
        NumericField.REPS -> copy(
            reps = WearDraftPolicy.adjustReps(requireNotNull(reps), action.steps),
            hasUnsubmittedDraft = true,
        )
        NumericField.WEIGHT -> copy(
            weightHundredthsKg = WearDraftPolicy.adjustWeight(weightHundredthsKg, action.steps),
            hasUnsubmittedDraft = true,
        )
    }
