// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.input.AppFieldLabel
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopAppBar
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.Mode
import io.github.stslex.workeeper.feature.single_training.ui.components.TagPickerInline
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingExerciseEditRow
import io.github.stslex.workeeper.core.ui.kit.R as KitR

@Composable
internal fun TrainingEditScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCreate = (state.mode as? Mode.Edit)?.isCreate == true
    val titleRes = if (isCreate) {
        R.string.feature_training_edit_title_create
    } else {
        R.string.feature_training_edit_title_edit
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppUi.colors.surfaceTier0)
            .testTag("TrainingEditScreen"),
    ) {
        AppTopAppBar(
            title = stringResource(titleRes),
            navigationIcon = {
                IconButton(
                    modifier = Modifier.testTag("TrainingEditCloseButton"),
                    onClick = { consume(Action.Click.OnCancelClick) },
                ) {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconSm),
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.feature_training_edit_close),
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
            FormSection(label = stringResource(R.string.feature_training_edit_label_name)) {
                val errorText = if (state.nameError) {
                    stringResource(R.string.feature_training_edit_error_name_required)
                } else {
                    null
                }
                AppTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("TrainingEditNameField"),
                    value = state.name,
                    onValueChange = { consume(Action.Input.OnNameChange(it)) },
                    placeholder = stringResource(R.string.feature_training_edit_label_name),
                    isError = errorText != null,
                )
                if (errorText != null) {
                    Text(
                        modifier = Modifier.padding(top = AppDimension.Space.xs),
                        text = errorText,
                        style = AppUi.typography.bodySmall,
                        color = AppUi.colors.status.error,
                    )
                }
            }
            FormSection(label = stringResource(R.string.feature_training_edit_label_description)) {
                // No explicit height: `.tf.multi` is the same box taller, and the field owns
                // that height now (§7.2). The 120.dp that used to sit here was a call site
                // guessing at a number the drawing puts at 96.
                AppTextField(
                    modifier = Modifier.testTag("TrainingEditDescriptionField"),
                    value = state.description,
                    onValueChange = { consume(Action.Input.OnDescriptionChange(it)) },
                    placeholder = stringResource(R.string.feature_training_edit_placeholder_description),
                    singleLine = false,
                )
            }
            FormSection(label = stringResource(R.string.feature_training_edit_label_tags)) {
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
            ExercisesEditSection(state = state, consume = consume)
            Spacer(Modifier.height(AppDimension.Space.md))
        }
        EditActionBar(consume = consume)
    }
}

@Composable
private fun ExercisesEditSection(
    state: State,
    consume: (Action) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(
                    R.string.feature_training_edit_label_exercises_format,
                    state.exercises.size,
                ),
                style = AppUi.typography.labelSmall,
                color = AppUi.colors.textTertiary,
            )
            AppButton.Tertiary(
                modifier = Modifier.testTag("TrainingEditAddExerciseButton"),
                text = stringResource(R.string.feature_training_edit_add_exercise),
                onClick = { consume(Action.Click.OnAddExerciseClick) },
                size = AppButtonSize.SMALL,
                leadingIcon = Icons.Default.Add,
            )
        }
        state.exercises.forEachIndexed { index, exercise ->
            TrainingExerciseEditRow(
                item = exercise,
                isFirst = index == 0,
                isLast = index == state.exercises.lastIndex,
                onMoveUp = {
                    consume(Action.Click.OnExerciseReorder(from = index, to = index - 1))
                },
                onMoveDown = {
                    consume(Action.Click.OnExerciseReorder(from = index, to = index + 1))
                },
                onRemove = { consume(Action.Click.OnExerciseRemove(exercise.exerciseUuid)) },
                onEditPlan = { consume(Action.Click.OnEditPlanClick(exercise.exerciseUuid)) },
            )
        }
    }
}

@Composable
private fun FormSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        // `.flabel` — see the twin in `ExerciseEditScreen`. One implementation, in the kit.
        AppFieldLabel(text = label)
        content()
    }
}

@Composable
private fun EditActionBar(
    consume: (Action) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppUi.colors.surfaceTier0)
            .padding(AppDimension.screenEdge)
            .testTag("TrainingEditActionBar"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton.Tertiary(
            modifier = Modifier.testTag("TrainingEditCancelButton"),
            text = stringResource(KitR.string.core_ui_kit_action_cancel),
            onClick = { consume(Action.Click.OnCancelClick) },
        )
        AppButton.Primary(
            modifier = Modifier
                .weight(1f)
                .testTag("TrainingEditSaveButton"),
            text = stringResource(KitR.string.core_ui_kit_action_save),
            onClick = { consume(Action.Click.OnSaveClick) },
            // No `enabled` — §26 "Save is never disabled". See the note where `canSave` used to
            // live: it hid two error branches, not one.
        )
    }
}
