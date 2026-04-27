// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.sheet

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import kotlinx.collections.immutable.ImmutableList

/**
 * Render-only state for [AppPlanEditor]. The component owns no state — every change
 * flows through [AppPlanEditorAction] back to the parent store, which is the single
 * source of truth for the draft.
 *
 * `exerciseName` is shown in the title; pass blank to use the default "Plan" string.
 * `draft` is the current set list — empty when the user hasn't added anything yet.
 */
@Stable
data class AppPlanEditorState(
    val exerciseName: String,
    val draft: ImmutableList<PlanSetDataModel>,
)

/**
 * Actions emitted by [AppPlanEditor]. The parent store is expected to translate these
 * into its own MVI actions. Save and Dismiss are surfaced separately so the parent can
 * implement the unified discard flow (see architecture.md → Back gesture handling).
 */
@Stable
sealed interface AppPlanEditorAction {

    data class OnSetWeightChange(val index: Int, val value: Double?) : AppPlanEditorAction

    data class OnSetRepsChange(val index: Int, val reps: Int) : AppPlanEditorAction

    data class OnSetTypeChange(val index: Int, val type: SetTypeDataModel) : AppPlanEditorAction

    data class OnSetRemove(val index: Int) : AppPlanEditorAction

    data object OnAddSet : AppPlanEditorAction

    data object OnSave : AppPlanEditorAction

    data object OnDismiss : AppPlanEditorAction
}
