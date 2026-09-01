// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppConfirmSheet
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_cancel
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_discard_sheet_body
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_discard_sheet_confirm
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_discard_sheet_dismiss
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_discard_sheet_title
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_subtitle
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_title_default
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_title_format
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorBody
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_screen_back
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_screen_cancel
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_screen_save
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_screen_title_default
import io.github.stslex.workeeper.feature.plan_editor.resources.core_ui_plan_editor_screen_title_format
import io.github.stslex.workeeper.feature.plan_editor.resources.feature_plan_editor_set_type_tooltip
import io.github.stslex.workeeper.feature.plan_editor.resources.feature_plan_editor_type_change_weightless_body
import io.github.stslex.workeeper.feature.plan_editor.resources.feature_plan_editor_type_change_weightless_confirm
import io.github.stslex.workeeper.feature.plan_editor.resources.feature_plan_editor_type_change_weightless_impact
import io.github.stslex.workeeper.feature.plan_editor.resources.feature_plan_editor_type_change_weightless_title
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import io.github.stslex.workeeper.feature.plan_editor.resources.Res as PlanEditorRes

@Composable
internal fun PlanEditorScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (state.exerciseName.isBlank()) {
        stringResource(PlanEditorRes.string.core_ui_plan_editor_screen_title_default)
    } else {
        stringResource(
            PlanEditorRes.string.core_ui_plan_editor_screen_title_format,
            state.exerciseName,
        )
    }
    val saveLabel = stringResource(PlanEditorRes.string.core_ui_plan_editor_screen_save)
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("PlanEditorScreen"),
        topBar = {
            // The kit's `AppTopBar`, shared with the other two editors; it does not collapse
            // on scroll. See the v3 redesign spec §26.
            AppTopBar(
                title = title,
                smallTitle = true,
                navigation = {
                    AppIconButton(
                        modifier = Modifier.testTag("PlanEditorBack"),
                        icon = AppIcons.ChevronLeft,
                        contentDescription = stringResource(
                            PlanEditorRes.string.core_ui_plan_editor_screen_back,
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
            Spacer(Modifier.height(AppDimension.Space.lg))
            // `PlanEditorBody` draws the toggle. `Mode.PerformedExercise` passes null and gets
            // none: type lives on the parent exercise, not on a training-scoped editor.
            PlanEditorBody(
                draft = state.draft,
                isWeighted = state.isWeighted,
                onAction = { editorAction ->
                    consume(Action.EditorAction(editorAction))
                },
                setTypeTooltipText = stringResource(
                    PlanEditorRes.string.feature_plan_editor_set_type_tooltip,
                ),
                onTypeChange = if (state.mode is Mode.PerformedExercise) {
                    null
                } else {
                    { type -> consume(Action.Click.OnTypeToggle(type)) }
                },
            )
            // No add button here: add and remove live in the card's foot, owned by
            // `PlanEditorBody`, so the two hosts cannot drift.
        }

        // GUARD: one sealed channel — a `Boolean` beside `dialogState` would let two modals
        // open at once.
        when (state.dialogState) {
            DialogState.Hidden -> Unit

            DialogState.DiscardConfirm -> AppConfirmSheet(
                title = stringResource(Res.string.core_ui_kit_discard_sheet_title),
                body = stringResource(Res.string.core_ui_kit_discard_sheet_body),
                confirmLabel = stringResource(Res.string.core_ui_kit_discard_sheet_confirm),
                dismissLabel = stringResource(Res.string.core_ui_kit_discard_sheet_dismiss),
                confirmDestructive = true,
                onConfirm = { consume(Action.Click.OnConfirmDiscard) },
                onDismiss = { consume(Action.Click.OnDismissDiscard) },
            )

            // The impact summary rides as `emphasis`: the drawn sheet has no panel.
            DialogState.TypeChangeConfirm -> AppConfirmSheet(
                title = stringResource(
                    PlanEditorRes.string.feature_plan_editor_type_change_weightless_title,
                ),
                body = stringResource(
                    PlanEditorRes.string.feature_plan_editor_type_change_weightless_body,
                ),
                emphasis = stringResource(
                    PlanEditorRes.string.feature_plan_editor_type_change_weightless_impact,
                ),
                confirmLabel = stringResource(
                    PlanEditorRes.string.feature_plan_editor_type_change_weightless_confirm,
                ),
                dismissLabel = stringResource(Res.string.core_ui_kit_action_cancel),
                onConfirm = { consume(Action.Click.OnTypeChangeConfirm) },
                onDismiss = { consume(Action.Click.OnTypeChangeDismiss) },
            )
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
            text = stringResource(PlanEditorRes.string.core_ui_plan_editor_screen_cancel),
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
        stringResource(Res.string.core_ui_kit_plan_editor_title_default)
    } else {
        stringResource(Res.string.core_ui_kit_plan_editor_title_format, exerciseName)
    }
    Text(
        text = title,
        style = AppUi.typography.titleLarge,
        color = AppUi.colors.textPrimary,
    )
    Text(
        modifier = Modifier.padding(top = AppDimension.Space.xs),
        text = stringResource(Res.string.core_ui_kit_plan_editor_subtitle),
        style = AppUi.typography.bodySmall,
        color = AppUi.colors.textTertiary,
    )
}

private object PlanEditorPreviewValues {
    const val WARMUP_WEIGHT = 60.0
    const val WARMUP_REPS = 10
    const val FIRST_WORK_WEIGHT = 80.0
    const val FIRST_WORK_REPS = 8
    const val SECOND_WORK_WEIGHT = 100.0
    const val SECOND_WORK_REPS = 5
    const val FAILURE_WEIGHT = 85.0
    const val FAILURE_REPS = 6
}

@Composable
@Preview(name = "Light")
private fun PlanEditorScreenLightPreview() {
    PlanEditorScreenPreview(themeMode = ThemeMode.LIGHT)
}

@Composable
@Preview(name = "Dark")
private fun PlanEditorScreenDarkPreview() {
    PlanEditorScreenPreview(themeMode = ThemeMode.DARK)
}

@Composable
private fun PlanEditorScreenPreview(themeMode: ThemeMode) {
    AppTheme(themeMode = themeMode) {
        PlanEditorScreen(
            state = State(
                mode = State.Mode.Exercise(exerciseUuid = "uuid"),
                isLoading = false,
                exerciseName = "Bench press",
                type = ExerciseTypeUiModel.WEIGHTED,
                initialDraft = persistentListOf(),
                draft = listOf(
                    PlanSetUiModel(
                        weight = PlanEditorPreviewValues.WARMUP_WEIGHT,
                        reps = PlanEditorPreviewValues.WARMUP_REPS,
                        type = SetTypeUiModel.WARMUP,
                    ),
                    PlanSetUiModel(
                        weight = PlanEditorPreviewValues.FIRST_WORK_WEIGHT,
                        reps = PlanEditorPreviewValues.FIRST_WORK_REPS,
                        type = SetTypeUiModel.WORK,
                    ),
                    PlanSetUiModel(
                        weight = PlanEditorPreviewValues.SECOND_WORK_WEIGHT,
                        reps = PlanEditorPreviewValues.SECOND_WORK_REPS,
                        type = SetTypeUiModel.WORK,
                    ),
                    PlanSetUiModel(
                        weight = PlanEditorPreviewValues.FAILURE_WEIGHT,
                        reps = PlanEditorPreviewValues.FAILURE_REPS,
                        type = SetTypeUiModel.FAILURE,
                    ),
                ).toImmutableList(),
                initialType = ExerciseTypeUiModel.WEIGHTED,
                pendingTypeChange = null,
                isSaving = false,
                dialogState = DialogState.Hidden,
            ),
            consume = {},
        )
    }
}
