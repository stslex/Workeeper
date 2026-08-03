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
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
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
        // §26 "The editors' six code-diverges" and "The three bar shapes" — see the twin in
        // `ExerciseEditScreen` for the full reasoning. `AppTopBar`, the pushed shape:
        // `.icon-btn.lead` + `h1.sm` + the record's own name, with the create/edit string
        // standing in only while the name is blank.
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
            FormSection(label = stringResource(R.string.feature_training_edit_label_description)) { fieldLabel ->
                // No explicit height — `.tf.multi` is the same box taller and the FIELD owns
                // that number (§7.2). A call site that sets its own guesses at a value the
                // drawing already puts at 96.
                AppTextField(
                    modifier = Modifier.testTag("TrainingEditDescriptionField"),
                    accessibilityLabel = fieldLabel,
                    value = state.description,
                    onValueChange = { consume(Action.Input.OnDescriptionChange(it)) },
                    placeholder = stringResource(R.string.feature_training_edit_placeholder_description),
                    singleLine = false,
                )
            }
            FormSection(label = stringResource(R.string.feature_training_edit_label_tags)) { fieldLabel ->
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
    // `ReorderableColumnState`, not the lazy variant: this screen is one `verticalScroll`
    // column, so nesting a lazy scroller would break the layout. Same reasoning past-session
    // wrote down for the same choice, and the same component.
    val reorderState = rememberReorderableColumnState { from, to ->
        consume(Action.Click.OnExerciseReorder(from = from, to = to))
    }
    Column(verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm)) {
        // The count keeps the section label and NO ADD BUTTON SITS BESIDE IT — this is one of
        // B33(a)'s six `Icons.Default.Add` sites, and the resolution is not a stroke plus: the
        // action is `.addex` at the foot of the list, which is drawn and carries the plus inside
        // it (§26, "Sets: add and remove move to the card's foot"; extraction §7.6).
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
            // KEYED, and the drag does not survive the first swap without it. The handle's
            // `pointerInput(state, key)` is restarted whenever `key` changes, and in a positional
            // loop the first reorder puts a different exercise in this slot — which cancels the
            // long-press coroutine mid-drag, so one press could never cross more than one
            // neighbour. `key` moves the whole subtree with the item instead. `PastExerciseCard`
            // does the same thing for the same reason (§26, "Reorder is long-press drag").
            key(exercise.exerciseUuid) {
                TrainingExerciseEditRow(
                    item = exercise,
                    onRemove = { consume(Action.Click.OnExerciseRemove(exercise.exerciseUuid)) },
                    onEditPlan = { consume(Action.Click.OnEditPlanClick(exercise.exerciseUuid)) },
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

@Composable
private fun FormSection(
    label: String,
    content: @Composable (fieldLabel: String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        // `.flabel` — see the twin in `ExerciseEditScreen`. One implementation, in the kit.
        AppFieldLabel(text = label)
        // Handed down rather than re-resolved at the call site: the drawn label and the one
        // a screen reader hears must be the same string, and two `stringResource` calls drift.
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
            text = stringResource(KitR.string.core_ui_kit_action_cancel),
            onClick = { consume(Action.Click.OnCancelClick) },
        )
        AppButton.Primary(
            modifier = Modifier
                .weight(1f)
                .testTag("TrainingEditSaveButton"),
            text = stringResource(KitR.string.core_ui_kit_action_save),
            onClick = { consume(Action.Click.OnSaveClick) },
            // No `enabled` — §26 "Save is never disabled". See `State`'s note: on this screen a
            // save predicate would hide TWO error branches, not one.
        )
    }
}
