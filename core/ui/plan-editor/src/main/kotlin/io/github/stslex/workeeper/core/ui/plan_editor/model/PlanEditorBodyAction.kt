// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import androidx.compose.runtime.Stable

/**
 * UI-side action emitted by [PlanEditorBody][io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorBody]
 * back to its parent. The parent (`PlanEditorScreen`) maps each variant to the
 * corresponding store [Action][io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action]
 * — keeping the body composable free of Hilt / `Store` plumbing so it stays previewable.
 */
@Stable
sealed interface PlanEditorBodyAction {

    @Stable
    data class OnSetWeightChange(
        val index: Int,
        val value: Double?,
    ) : PlanEditorBodyAction

    @Stable
    data class OnSetRepsChange(
        val index: Int,
        val value: Int,
    ) : PlanEditorBodyAction

    @Stable
    data class OnSetTypeChange(
        val index: Int,
        val value: SetTypeUiModel,
    ) : PlanEditorBodyAction

    @Stable
    data class OnSetRemove(
        val index: Int,
    ) : PlanEditorBodyAction

    @Stable
    data object OnAddSet : PlanEditorBodyAction

    @Stable
    object OnDismiss : PlanEditorBodyAction

    @Stable
    object OnSave : PlanEditorBodyAction
}
