// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Modal bottom sheet variant of the plan editor. v2.4 deprecates the sheet host in favor of
 * a dedicated `PlanEditorScreen`; new callers must use the screen route. This composable
 * remains while existing callers (live workout, exercise detail) are migrated step by step.
 *
 * Stateless — every field change emits an [AppPlanEditorAction] back to the parent store,
 * which is the single source of truth for the draft. The component does NOT own any
 * discard state; the parent decides whether to show a confirm dialog when
 * [AppPlanEditorAction.OnDismiss] fires.
 */
@Deprecated(
    message = "Use PlanEditorScreen via Screen.PlanEditor route instead. Bottom-sheet host " +
        "removed in v2.4 — see documentation/feature-specs/v2.4-design-foundation.md (D1).",
)
@Composable
fun AppPlanEditor(
    exerciseName: String,
    draft: ImmutableList<PlanSetUiModel>,
    isWeighted: Boolean,
    onAction: (AppPlanEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        modifier = modifier.testTag("AppPlanEditor"),
        onDismiss = { onAction(AppPlanEditorAction.OnDismiss) },
    ) {
        PlanEditorHeader(exerciseName = exerciseName)
        Spacer(Modifier.height(AppDimension.Space.md))
        PlanEditorBody(
            draft = draft,
            isWeighted = isWeighted,
            onAction = onAction,
        )
        Spacer(Modifier.height(AppDimension.Space.md))
        AppButton.Tertiary(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("AppPlanEditorAddSet"),
            text = stringResource(R.string.core_ui_kit_plan_editor_add_set),
            onClick = { onAction(AppPlanEditorAction.OnAddSet) },
            size = AppButtonSize.MEDIUM,
        )
        Spacer(Modifier.height(AppDimension.Space.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton.Tertiary(
                modifier = Modifier.testTag("AppPlanEditorCancel"),
                text = stringResource(R.string.core_ui_kit_action_cancel),
                onClick = { onAction(AppPlanEditorAction.OnDismiss) },
                size = AppButtonSize.MEDIUM,
            )
            AppButton.Primary(
                modifier = Modifier
                    .weight(1f)
                    .testTag("AppPlanEditorSave"),
                text = stringResource(R.string.core_ui_kit_plan_editor_save),
                onClick = { onAction(AppPlanEditorAction.OnSave) },
                size = AppButtonSize.MEDIUM,
            )
        }
    }
}

@Suppress("DEPRECATION")
@Preview(name = "Weighted populated · Light", showBackground = true)
@Preview(
    name = "Weighted populated · Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppPlanEditorWeightedPopulatedPreview() {
    AppTheme {
        AppPlanEditor(
            exerciseName = "Bench Press",
            draft = listOf(
                PlanSetUiModel(60.0, 10, SetTypeUiModel.WARMUP),
                PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK),
                PlanSetUiModel(100.0, 5, SetTypeUiModel.WORK),
                PlanSetUiModel(85.0, 6, SetTypeUiModel.FAILURE),
            ).toImmutableList(),
            isWeighted = true,
            onAction = {},
        )
    }
}

@Suppress("DEPRECATION")
@Preview(name = "Weighted empty · Light", showBackground = true)
@Preview(
    name = "Weighted empty · Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppPlanEditorWeightedEmptyPreview() {
    AppTheme {
        AppPlanEditor(
            exerciseName = "Bench Press",
            draft = persistentListOf(),
            isWeighted = true,
            onAction = {},
        )
    }
}
