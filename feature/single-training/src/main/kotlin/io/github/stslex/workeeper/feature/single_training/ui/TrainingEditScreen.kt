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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppDashedAddButton
import io.github.stslex.workeeper.core.ui.kit.components.input.AppFieldLabel
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.rememberReorderableColumnState
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.reorderableColumnDragHandle
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.reorderableColumnItem
import io.github.stslex.workeeper.core.ui.kit.components.section.AppLabel
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagFormRow
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_cancel
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_save
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State.Mode
import io.github.stslex.workeeper.feature.single_training.ui.components.TrainingExerciseCard
import org.jetbrains.compose.resources.stringResource

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
        // §26's pushed bar shape: lead icon + `h1.sm` + the record's own name, with the
        // create/edit string standing in only while the name is blank.
        AppTopBar(
            title = state.name.ifBlank { stringResource(titleRes) },
            smallTitle = true,
            navigation = {
                AppIconButton(
                    modifier = Modifier.testTag("TrainingEditCloseButton"),
                    icon = AppIcons.ChevronLeft,
                    contentDescription = stringResource(R.string.feature_training_edit_close),
                    onClick = { consume(Action.Click.OnCancelClick) },
                )
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
            FormSection(label = stringResource(R.string.feature_training_edit_label_name)) { fieldLabel ->
                val errorText = if (state.nameError) {
                    stringResource(R.string.feature_training_edit_error_name_required)
                } else {
                    null
                }
                AppTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("TrainingEditNameField"),
                    accessibilityLabel = fieldLabel,
                    value = state.name,
                    onValueChange = { consume(Action.Input.OnNameChange(it)) },
                    // ED4: no placeholder repeating the label — an empty name field is empty.
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
            // ED3's order, overturning the drawn §7.1 frame: name, exercises, tags, description.
            ExercisesEditSection(state = state, consume = consume)
            TagsSection(state = state, consume = consume)
            FormSection(label = stringResource(R.string.feature_training_edit_label_description)) { fieldLabel ->
                // No explicit height — the field owns that number (§7.2).
                AppTextField(
                    modifier = Modifier.testTag("TrainingEditDescriptionField"),
                    accessibilityLabel = fieldLabel,
                    value = state.description,
                    onValueChange = { consume(Action.Input.OnDescriptionChange(it)) },
                    placeholder = stringResource(R.string.feature_training_edit_placeholder_description),
                    singleLine = false,
                )
            }
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
    // `ReorderableColumnState`, not the lazy variant: this screen is one `verticalScroll`
    // column, so nesting a lazy scroller would break the layout.
    val reorderState = rememberReorderableColumnState { from, to ->
        consume(Action.Click.OnExerciseReorder(from = from, to = to))
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm)) {
        // The count keeps the section label; the add action is `.addex` at the foot of the list.
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = stringResource(
                R.string.feature_training_edit_label_exercises_format,
                state.exercises.size,
            ),
            style = AppUi.typography.labelSmall,
            color = AppUi.colors.textTertiary,
        )
        state.exercises.forEachIndexed { index, exercise ->
            // GUARD: keyed, or the drag dies after one swap — the handle's `pointerInput(state,
            // key)` restarts when a positional key changes, cancelling the long-press mid-drag.
            key(exercise.exerciseUuid) {
                TrainingExerciseCard(
                    item = exercise,
                    expanded = exercise.exerciseUuid in state.expandedExerciseUuids,
                    onToggle = {
                        consume(Action.Click.OnExerciseCardToggle(exercise.exerciseUuid))
                    },
                    onRemove = { consume(Action.Click.OnExerciseRemove(exercise.exerciseUuid)) },
                    onPlanAction = { planAction ->
                        consume(
                            Action.Click.OnExercisePlanAction(
                                exerciseUuid = exercise.exerciseUuid,
                                action = planAction,
                            ),
                        )
                    },
                    modifier = Modifier.reorderableColumnItem(
                        state = reorderState,
                        key = exercise.exerciseUuid,
                        index = index,
                        lastIndex = state.exercises.lastIndex,
                    ),
                    dragHandleModifier = Modifier.reorderableColumnDragHandle(
                        state = reorderState,
                        key = exercise.exerciseUuid,
                    ),
                )
            }
        }
        AppDashedAddButton(
            modifier = Modifier.testTag("TrainingEditAddExerciseButton"),
            text = stringResource(R.string.feature_training_edit_add_exercise),
            onClick = { consume(Action.Click.OnAddExerciseClick) },
        )
    }
}

/** ED3: ТЕГИ is a section, not a labelled field; the row is ED7's chips plus «+ тег». */
@Composable
private fun TagsSection(
    state: State,
    consume: (Action) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md)) {
        // The scroll column already carries the gutter, so `AppSectionHeader` would double it.
        AppLabel(text = stringResource(R.string.feature_training_edit_label_tags))
        AppTagFormRow(
            selectedTags = state.tags,
            onTagRemove = { consume(Action.Click.OnTagRemove(it)) },
            onAddClick = { consume(Action.Click.OnTagAddClick) },
        )
    }
}

@Composable
private fun FormSection(
    label: String,
    content: @Composable (fieldLabel: String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        AppFieldLabel(text = label)
        // Handed down, not re-resolved: the drawn label and the spoken one must be one string.
        content(label)
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
            text = stringResource(Res.string.core_ui_kit_action_cancel),
            onClick = { consume(Action.Click.OnCancelClick) },
        )
        AppButton.Primary(
            modifier = Modifier
                .weight(1f)
                .testTag("TrainingEditSaveButton"),
            text = stringResource(Res.string.core_ui_kit_action_save),
            onClick = { consume(Action.Click.OnSaveClick) },
            // No `enabled` — §26 "Save is never disabled" (a predicate would hide two branches).
        )
    }
}
