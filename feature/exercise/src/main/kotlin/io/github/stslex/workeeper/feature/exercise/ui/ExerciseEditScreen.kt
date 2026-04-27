// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State.Mode
import io.github.stslex.workeeper.feature.exercise.ui.components.TagPickerInline
import io.github.stslex.workeeper.feature.exercise.ui.components.TypeToggle
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@Composable
internal fun ExerciseEditScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCreate = (state.mode as? Mode.Edit)?.isCreate == true
    val titleRes = if (isCreate) {
        R.string.feature_exercise_edit_title_create
    } else {
        R.string.feature_exercise_edit_title_edit
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("ExerciseEditScreen"),
    ) {
        AppTopAppBar(
            title = stringResource(titleRes),
            navigationIcon = {
                IconButton(
                    modifier = Modifier.testTag("ExerciseEditCloseButton"),
                    onClick = { consume(Action.Click.OnCancelClick) },
                ) {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconSm),
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(
                            R.string.feature_exercise_edit_close_description,
                        ),
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppDimension.screenEdge),
            verticalArrangement = Arrangement.spacedBy(AppDimension.sectionSpacing),
        ) {
            Spacer(Modifier.height(AppDimension.Space.sm))
            FormSection(label = stringResource(R.string.feature_exercise_edit_label_name)) {
                val nameErrorText = when {
                    state.nameError -> stringResource(R.string.feature_exercise_edit_error_name_required)
                    state.nameDuplicateError ->
                        stringResource(R.string.feature_exercise_edit_error_name_duplicate)

                    else -> null
                }
                AppTextField(
                    modifier = Modifier.testTag("ExerciseEditNameField"),
                    value = state.name,
                    onValueChange = { consume(Action.Input.OnNameChange(it)) },
                    placeholder = stringResource(R.string.feature_exercise_edit_label_name),
                    isError = nameErrorText != null,
                )
                if (nameErrorText != null) {
                    Text(
                        modifier = Modifier.padding(top = AppDimension.Space.xs),
                        text = nameErrorText,
                        style = AppUi.typography.bodySmall,
                        color = AppUi.colors.status.error,
                    )
                }
            }
            FormSection(label = stringResource(R.string.feature_exercise_edit_label_type)) {
                TypeToggle(
                    selected = state.type,
                    onSelect = { type -> consume(Action.Click.OnTypeSelect(type)) },
                )
            }
            FormSection(label = stringResource(R.string.feature_exercise_edit_label_tags)) {
                TagPickerInline(
                    selectedTags = state.tags,
                    availableTags = state.availableTags,
                    searchQuery = state.tagSearchQuery,
                    onSearchQueryChange = { consume(Action.Input.OnTagSearchChange(it)) },
                    onTagToggle = { consume(Action.Click.OnTagToggle(it)) },
                    onTagRemove = { consume(Action.Click.OnTagRemove(it)) },
                    onTagCreate = { consume(Action.Click.OnTagCreate(it)) },
                )
            }
            FormSection(label = stringResource(R.string.feature_exercise_edit_label_description)) {
                AppTextField(
                    modifier = Modifier
                        .testTag("ExerciseEditDescriptionField")
                        .height(120.dp),
                    value = state.description,
                    onValueChange = { consume(Action.Input.OnDescriptionChange(it)) },
                    placeholder = stringResource(R.string.feature_exercise_edit_placeholder_description),
                    singleLine = false,
                )
            }
            DefaultPlanSection(state = state, consume = consume)
            Spacer(Modifier.height(AppDimension.Space.md))
        }
        EditActionBar(state = state, consume = consume)
    }
}

@Composable
private fun DefaultPlanSection(
    state: State,
    consume: (Action) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Text(
            text = stringResource(R.string.feature_exercise_edit_label_default_plan),
            style = AppUi.typography.labelSmall,
            color = AppUi.colors.textTertiary,
        )
        Text(
            text = stringResource(R.string.feature_exercise_edit_default_plan_subtitle),
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val planText = state.adhocPlan
                ?.joinToString(separator = " · ") { set ->
                    val weightStr = set.weight?.let { weight ->
                        if (weight % 1.0 == 0.0) weight.toLong().toString() else weight.toString()
                    }
                    if (weightStr != null) weightStr + "×" + set.reps else set.reps.toString()
                }
                ?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.feature_exercise_edit_plan_summary_no_plan)
            Text(
                modifier = Modifier
                    .weight(1f)
                    .testTag("ExerciseEditPlanSummary"),
                text = planText,
                style = AppUi.typography.bodySmall,
                color = AppUi.colors.textTertiary,
            )
            io.github.stslex.workeeper.core.ui.kit.components.button.AppButton.Tertiary(
                modifier = Modifier.testTag("ExerciseEditPlanEditButton"),
                text = stringResource(
                    if (state.adhocPlan.isNullOrEmpty()) {
                        R.string.feature_exercise_edit_plan_add
                    } else {
                        R.string.feature_exercise_edit_plan_edit
                    },
                ),
                onClick = { consume(Action.Click.OnEditPlanClick) },
                size = io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize.SMALL,
            )
        }
    }
}

@Composable
private fun FormSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Text(
            text = label,
            style = AppUi.typography.labelSmall,
            color = AppUi.colors.textTertiary,
        )
        content()
    }
}

@Composable
private fun EditActionBar(
    state: State,
    consume: (Action) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppUi.colors.surfaceTier0)
            .padding(AppDimension.screenEdge)
            .testTag("ExerciseEditActionBar"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton.Tertiary(
            modifier = Modifier.testTag("ExerciseEditCancelButton"),
            text = stringResource(KitR.string.core_ui_kit_action_cancel),
            onClick = { consume(Action.Click.OnCancelClick) },
        )
        AppButton.Primary(
            modifier = Modifier
                .weight(1f)
                .testTag("ExerciseEditSaveButton"),
            text = stringResource(KitR.string.core_ui_kit_action_save),
            onClick = { consume(Action.Click.OnSaveClick) },
            enabled = state.isSaveEnabled,
        )
    }
}
