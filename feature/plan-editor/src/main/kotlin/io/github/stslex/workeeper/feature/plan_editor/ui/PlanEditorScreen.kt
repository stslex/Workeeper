// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorBody
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.R
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import kotlinx.collections.immutable.toImmutableList
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanEditorScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val title = if (state.exerciseName.isBlank()) {
        stringResource(R.string.core_ui_plan_editor_screen_title_default)
    } else {
        stringResource(R.string.core_ui_plan_editor_screen_title_format, state.exerciseName)
    }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .testTag("PlanEditorScreen"),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = title,
                        style = AppUi.typography.headlineSmall,
                        color = AppUi.colors.textPrimary,
                    )
                },
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
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = AppUi.colors.surfaceTier0,
                    scrolledContainerColor = AppUi.colors.surfaceTier0,
                    titleContentColor = AppUi.colors.textPrimary,
                    navigationIconContentColor = AppUi.colors.textPrimary,
                    actionIconContentColor = AppUi.colors.textPrimary,
                ),
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
                    consume(Action.EditorAction(editorAction))
                },
                setTypeTooltipText = stringResource(
                    R.string.core_ui_plan_editor_set_type_tooltip,
                ),
            )
            AppButton.Tertiary(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("PlanEditorAddSet"),
                text = stringResource(KitR.string.core_ui_kit_plan_editor_add_set),
                onClick = { consume(Action.Click.OnAddSet) },
                size = AppButtonSize.MEDIUM,
            )
        }

        if (state.confirmDiscardOpen) {
            DiscardDialog(
                onSave = { consume(Action.Click.OnConfirmSave) },
                onDiscard = { consume(Action.Click.OnConfirmDiscard) },
                onContinue = { consume(Action.Click.OnDismissDiscard) },
            )
        }
    }
}

/**
 * Three-action discard dialog (v2.4 5.5 / D2): Save / Discard / Continue editing. The
 * standard `AppConfirmDialog` only renders two actions; this dialog inlines a custom
 * three-button row so the user can commit or abandon edits without leaving the screen
 * twice.
 */
@Composable
private fun DiscardDialog(
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onContinue: () -> Unit,
) {
    val dialogBg = if (AppUi.colors.isDark) AppUi.colors.surfaceTier1 else AppUi.colors.surfaceTier2
    Dialog(onDismissRequest = onContinue) {
        Column(
            modifier = Modifier
                .testTag("PlanEditorDiscardDialog")
                .clip(AppUi.shapes.medium)
                .background(dialogBg)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            Text(
                text = stringResource(R.string.core_ui_plan_editor_discard_title),
                style = AppUi.typography.titleLarge,
                color = AppUi.colors.textPrimary,
            )
            Text(
                text = stringResource(R.string.core_ui_plan_editor_discard_body),
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
            // FlowRow so the three buttons wrap onto a second line on narrow screens
            // rather than overflowing horizontally. End-aligned matches the
            // AppConfirmDialog convention.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    space = AppDimension.Space.sm,
                    alignment = Alignment.End,
                ),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
            ) {
                AppButton.Tertiary(
                    modifier = Modifier.testTag("PlanEditorDiscardContinue"),
                    text = stringResource(R.string.core_ui_plan_editor_discard_continue),
                    onClick = onContinue,
                    size = AppButtonSize.MEDIUM,
                )
                AppButton.Destructive(
                    modifier = Modifier.testTag("PlanEditorDiscardDiscard"),
                    text = stringResource(R.string.core_ui_plan_editor_discard_discard),
                    onClick = onDiscard,
                    size = AppButtonSize.MEDIUM,
                )
                AppButton.Primary(
                    modifier = Modifier.testTag("PlanEditorDiscardSave"),
                    text = stringResource(R.string.core_ui_plan_editor_screen_save),
                    onClick = onSave,
                    size = AppButtonSize.MEDIUM,
                )
            }
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

@Composable
internal fun PlanEditorHeader(exerciseName: String) {
    val title = if (exerciseName.isBlank()) {
        stringResource(KitR.string.core_ui_kit_plan_editor_title_default)
    } else {
        stringResource(KitR.string.core_ui_kit_plan_editor_title_format, exerciseName)
    }
    Text(
        text = title,
        style = AppUi.typography.titleLarge,
        color = AppUi.colors.textPrimary,
    )
    Text(
        modifier = Modifier.padding(top = AppDimension.Space.xs),
        text = stringResource(KitR.string.core_ui_kit_plan_editor_subtitle),
        style = AppUi.typography.bodySmall,
        color = AppUi.colors.textTertiary,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
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
