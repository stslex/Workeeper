// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui

import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStore.Action

internal sealed interface PastSessionBodyAction {

    data object BackClick : PastSessionBodyAction

    data object DeleteClick : PastSessionBodyAction

    data object DeleteConfirm : PastSessionBodyAction

    data object DeleteDismiss : PastSessionBodyAction

    data object RetryLoad : PastSessionBodyAction

    data class SetWeightInput(val setUuid: String, val raw: String) : PastSessionBodyAction

    data class SetRepsInput(val setUuid: String, val raw: String) : PastSessionBodyAction

    data class SetTypeChange(val setUuid: String, val type: SetTypeUiModel) : PastSessionBodyAction

    data class SetReorder(
        val performedExerciseUuid: String,
        val from: Int,
        val to: Int,
    ) : PastSessionBodyAction
}

internal fun PastSessionBodyAction.toAction(): Action = when (this) {
    PastSessionBodyAction.BackClick -> Action.Click.OnBackClick
    PastSessionBodyAction.DeleteClick -> Action.Click.OnDeleteClick
    PastSessionBodyAction.DeleteConfirm -> Action.Click.OnDeleteConfirm
    PastSessionBodyAction.DeleteDismiss -> Action.Click.OnDeleteDismiss
    PastSessionBodyAction.RetryLoad -> Action.Click.OnRetryLoad
    is PastSessionBodyAction.SetWeightInput -> Action.Input.OnSetWeightChange(setUuid, raw)
    is PastSessionBodyAction.SetRepsInput -> Action.Input.OnSetRepsChange(setUuid, raw)
    is PastSessionBodyAction.SetTypeChange -> Action.Click.OnSetTypeChange(setUuid, type)
    is PastSessionBodyAction.SetReorder -> Action.Click.OnSetReorder(
        performedExerciseUuid = performedExerciseUuid,
        from = from,
        to = to,
    )
}
