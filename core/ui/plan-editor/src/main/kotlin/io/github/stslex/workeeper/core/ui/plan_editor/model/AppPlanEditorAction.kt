// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import androidx.compose.runtime.Stable

@Stable
sealed interface AppPlanEditorAction {

    @Stable
    data class OnSetWeightChange(
        val index: Int,
        val value: Double?,
    ) : AppPlanEditorAction

    @Stable
    data class OnSetRepsChange(
        val index: Int,
        val value: Int,
    ) : AppPlanEditorAction

    @Stable
    data class OnSetTypeChange(
        val index: Int,
        val value: SetTypeUiModel,
    ) : AppPlanEditorAction

    @Stable
    data class OnSetRemove(
        val index: Int,
    ) : AppPlanEditorAction


    @Stable
    data object OnAddSet : AppPlanEditorAction

    @Stable
    object OnDismiss : AppPlanEditorAction


    @Stable
    object OnSave : AppPlanEditorAction
}