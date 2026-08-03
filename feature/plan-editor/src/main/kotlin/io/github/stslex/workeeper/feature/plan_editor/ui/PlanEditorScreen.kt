// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppConfirmDialog
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorBody
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.R
import io.github.stslex.workeeper.feature.plan_editor.ui.components.TypeToggle
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.github.stslex.workeeper.core.ui.kit.R as KitR

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
    val saveLabel = if (state.mode is Mode.Draft) {
        stringResource(R.string.feature_plan_editor_action_done)
    } else {
        stringResource(R.string.core_ui_plan_editor_screen_save)
    }
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("PlanEditorScreen"),
        topBar = {
            // §26 "The editors' six code-diverges": this was the raw M3 `TopAppBar` of the three
            // — its own colours, its own title rung, its own filled `ArrowBack` — and it is now
            // the extracted `.topbar` like the other two. The collapsing scroll behaviour goes
            // with it: nothing in either mockup draws a bar that collapses, and `AppTopBar` is a
            // 56dp row on `surfaceTier0` with no surface of its own to collapse into.
            // `Icons.AutoMirrored.Filled.ArrowBack` → `AppIcons.ChevronLeft` is B34, which is
            // one of B33(a)'s eighteen and one of this stage's six.
            AppTopBar(
                title = title,
                smallTitle = true,
                navigation = {
                    AppIconButton(
                        modifier = Modifier.testTag("PlanEditorBack"),
                        icon = AppIcons.ChevronLeft,
                        contentDescription = stringResource(
                            R.string.core_ui_plan_editor_screen_back,
                        ),
                        onClick = { consume(Action.Click.OnBackClick) },
                    )
                },
            )
        },
        bottomBar = {
            PlanEditorActionBar(
                isSaving = state.isSaving,
                saveLabel = saveLabel,
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
            // Type toggle is editable from PlanEditor whenever the type ownership is the
            // editor's own concern (Mode.Exercise — owns parent exercise.type, Mode.Draft —
            // seeds the in-flight exercise's type). Mode.PerformedExercise renders nothing
            // here: type lives on the parent exercise and isn't editable through a
            // training-scoped editor.
            if (state.mode is Mode.Exercise || state.mode is Mode.Draft) {
                Spacer(Modifier.height(AppDimension.Space.lg))
                Text(
                    text = stringResource(R.string.feature_plan_editor_label_type),
                    style = AppUi.typography.labelSmall,
                    color = AppUi.colors.textTertiary,
                )
                Spacer(Modifier.height(AppDimension.Space.xs))
                TypeToggle(
                    selected = state.type,
                    onSelect = { type -> consume(Action.Click.OnTypeToggle(type)) },
                )
            }
            Spacer(Modifier.height(AppDimension.Space.lg))
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
            Spacer(Modifier.height(AppDimension.Space.lg))
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

        when (val dialog = state.dialogState) {
            DialogState.Hidden -> Unit

            is DialogState.TypeChangeConfirm -> AppConfirmDialog(
                title = dialog.title,
                body = dialog.body,
                impactSummary = dialog.impactSummary,
                confirmLabel = dialog.confirmLabel,
                onConfirm = { consume(Action.Click.OnTypeChangeConfirm) },
                onDismiss = { consume(Action.Click.OnTypeChangeDismiss) },
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
    saveLabel: String,
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
            text = saveLabel,
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
                type = ExerciseTypeUiModel.WEIGHTED,
                initialDraft = persistentListOf(),
                draft = listOf(
                    PlanSetUiModel(60.0, 10, SetTypeUiModel.WARMUP),
                    PlanSetUiModel(80.0, 8, SetTypeUiModel.WORK),
                    PlanSetUiModel(100.0, 5, SetTypeUiModel.WORK),
                    PlanSetUiModel(85.0, 6, SetTypeUiModel.FAILURE),
                ).toImmutableList(),
                initialType = ExerciseTypeUiModel.WEIGHTED,
                pendingTypeChange = null,
                confirmDiscardOpen = false,
                isSaving = false,
                dialogState = DialogState.Hidden,
            ),
            consume = {},
        )
    }
}
