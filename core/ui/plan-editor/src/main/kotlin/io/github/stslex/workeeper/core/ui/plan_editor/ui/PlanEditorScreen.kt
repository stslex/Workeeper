// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorBody
import io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorHeader
import io.github.stslex.workeeper.core.ui.plan_editor.R
import io.github.stslex.workeeper.core.ui.plan_editor.model.AppPlanEditorAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.core.ui.plan_editor.mvi.store.PlanEditorStore.State
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun PlanEditorScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (state.exerciseName.isBlank()) {
        stringResource(R.string.core_ui_plan_editor_screen_title_default)
    } else {
        stringResource(R.string.core_ui_plan_editor_screen_title_format, state.exerciseName)
    }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("PlanEditorScreen"),
        topBar = {
            AppTopAppBar(
                title = title,
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.testTag("PlanEditorBack"),
                        onClick = { consume(Action.Click.OnBackClick) },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.core_ui_plan_editor_screen_back,
                            ),
                        )
                    }
                },
            )
        },
        bottomBar = {
            PlanEditorActionBar(
                isSaving = state.isSaving,
                onCancel = { consume(Action.Click.OnBackClick) },
                onSave = { consume(Action.Click.OnSave) },
            )
        },
        containerColor = AppUi.colors.surfaceTier0,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppUi.colors.surfaceTier0)
                .padding(padding)
                .padding(horizontal = AppDimension.screenEdge)
                .verticalScroll(rememberScrollState()),
        ) {
            PlanEditorHeader(exerciseName = state.exerciseName)
            PlanEditorBody(
                draft = state.draft,
                isWeighted = state.isWeighted,
                onAction = { editorAction ->
                    consume(editorAction.toStoreAction())
                },
                setTypeTooltipText = stringResource(
                    R.string.core_ui_plan_editor_set_type_tooltip,
                ),
            )
            AppButton.Tertiary(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("PlanEditorAddSet"),
                text = stringResource(
                    io.github.stslex.workeeper.core.ui.kit.R.string.core_ui_kit_plan_editor_add_set,
                ),
                onClick = { consume(Action.Click.OnAddSet) },
                size = AppButtonSize.MEDIUM,
            )
        }

        if (state.confirmDiscardOpen) {
            AppConfirmDialog(
                title = stringResource(R.string.core_ui_plan_editor_discard_title),
                body = stringResource(R.string.core_ui_plan_editor_discard_body),
                impactSummary = "",
                confirmLabel = stringResource(R.string.core_ui_plan_editor_discard_discard),
                dismissLabel = stringResource(R.string.core_ui_plan_editor_discard_continue),
                onConfirm = { consume(Action.Click.OnConfirmDiscard) },
                onDismiss = { consume(Action.Click.OnDismissDiscard) },
                modifier = Modifier.testTag("PlanEditorDiscardDialog"),
            )
        }
    }
}

@Composable
private fun PlanEditorActionBar(
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppUi.colors.surfaceTier0)
            .padding(
                horizontal = AppDimension.screenEdge,
                vertical = AppDimension.Space.md,
            ),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton.Tertiary(
            modifier = Modifier.testTag("PlanEditorCancel"),
            text = stringResource(R.string.core_ui_plan_editor_screen_cancel),
            onClick = onCancel,
            size = AppButtonSize.MEDIUM,
            enabled = !isSaving,
        )
        AppButton.Primary(
            modifier = Modifier
                .weight(1f)
                .testTag("PlanEditorSave"),
            text = stringResource(R.string.core_ui_plan_editor_screen_save),
            onClick = onSave,
            size = AppButtonSize.MEDIUM,
            enabled = !isSaving,
        )
    }
}

private fun AppPlanEditorAction.toStoreAction(): Action = when (this) {
    AppPlanEditorAction.OnAddSet -> Action.Click.OnAddSet
    AppPlanEditorAction.OnDismiss -> Action.Click.OnBackClick
    AppPlanEditorAction.OnSave -> Action.Click.OnSave
    is AppPlanEditorAction.OnSetRemove -> Action.Click.OnSetRemove(index)
    is AppPlanEditorAction.OnSetRepsChange -> Action.Input.OnSetRepsChange(index, value)
    is AppPlanEditorAction.OnSetTypeChange -> Action.Click.OnSetTypeChange(index, value)
    is AppPlanEditorAction.OnSetWeightChange -> Action.Input.OnSetWeightChange(index, value)
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlanEditorScreenPreview() {
    AppTheme {
        PlanEditorScreen(
            state = State(
                mode = State.Mode.Exercise(exerciseUuid = "uuid"),
                isLoading = false,
                exerciseName = "Bench press",
                isWeighted = true,
                initialDraft = listOf<PlanSetUiModel>().toImmutableList(),
                draft = listOf(
                    PlanSetUiModel(60.0, 10, SetTypeUiModel.WARMUP),
                    PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK),
                    PlanSetUiModel(100.0, 5, SetTypeUiModel.WORK),
                    PlanSetUiModel(85.0, 6, SetTypeUiModel.FAILURE),
                ).toImmutableList(),
                confirmDiscardOpen = false,
                isSaving = false,
            ),
            consume = {},
        )
    }
}
