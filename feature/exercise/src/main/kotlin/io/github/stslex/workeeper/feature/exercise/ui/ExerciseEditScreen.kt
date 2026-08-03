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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.input.AppFieldLabel
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorBody
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseTopBarThumb
import io.github.stslex.workeeper.feature.exercise.ui.components.TagPickerInline
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
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
        // §26 "The editors' six code-diverges": `AppTopBar` is the extracted `.topbar`, and it
        // is the bar in BOTH modes of this screen. Read and Edit must not draw different bar
        // components — this screen flips modes in place, and a bar that changes under the user
        // is the defect the ruling closes. Same navigation mark in both, too: §26 "The three bar
        // shapes" rules that a pushed screen carries a back arrow, and `#s-ex` / `#s-arch` /
        // `#s-editor` all draw it as `.icon-btn.lead` + `h1.sm` + the record's own name. The
        // chevron here is one of B33(a)'s four `Icons.Default.Close` sites.
        //
        // The title falls back to the create/edit string only while the name is blank, which is
        // the create flow before the first keystroke — an unnamed record has no name to show.
        AppTopBar(
            title = state.name.ifBlank { stringResource(titleRes) },
            smallTitle = true,
            navigation = {
                AppIconButton(
                    modifier = Modifier.testTag("ExerciseEditCloseButton"),
                    icon = AppIcons.ChevronLeft,
                    contentDescription = stringResource(
                        R.string.feature_exercise_edit_close_description,
                    ),
                    onClick = { consume(Action.Click.OnCancelClick) },
                )
            },
            actions = {
                // §26 "The image moves into the pushed top bar". This is the whole of what used
                // to be a form row — a 72dp thumb and two buttons — and it costs the form no
                // vertical space at all.
                ExerciseTopBarThumb(
                    type = state.type,
                    imageDisplay = state.effectiveImageDisplay,
                    onOpenImage = { consume(Action.Click.OnImageThumbnailClick) },
                    onPickImage = { consume(Action.Click.OnEditImageClick) },
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
            // NO IMAGE ROW IN THE FORM (§26, "The image moves into the pushed top bar"). The
            // thumb is in the bar above; «Изменить» and «Удалить» are in the viewer the thumb
            // opens, which is where the picture they act on is.
            FormSection(label = stringResource(R.string.feature_exercise_edit_label_name)) { fieldLabel ->
                val nameErrorText = when {
                    state.nameError -> stringResource(R.string.feature_exercise_edit_error_name_required)
                    state.nameDuplicateError ->
                        stringResource(R.string.feature_exercise_edit_error_name_duplicate)

                    else -> null
                }
                AppTextField(
                    modifier = Modifier.testTag("ExerciseEditNameField"),
                    accessibilityLabel = fieldLabel,
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
            FormSection(label = stringResource(R.string.feature_exercise_edit_label_type)) { fieldLabel ->
                TypeChipReadOnly(type = state.type)
            }
            FormSection(label = stringResource(R.string.feature_exercise_edit_label_tags)) { fieldLabel ->
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
            FormSection(label = stringResource(R.string.feature_exercise_edit_label_description)) { fieldLabel ->
                // No explicit height — `.tf.multi` is the same box taller and the FIELD owns
                // that number (§7.2). A call site that sets its own guesses at a value the
                // drawing already puts at 96.
                AppTextField(
                    modifier = Modifier.testTag("ExerciseEditDescriptionField"),
                    accessibilityLabel = fieldLabel,
                    value = state.description,
                    onValueChange = { consume(Action.Input.OnDescriptionChange(it)) },
                    placeholder = stringResource(R.string.feature_exercise_edit_placeholder_description),
                    singleLine = false,
                )
            }
            DefaultPlanSection(state = state, consume = consume)
            Spacer(Modifier.height(AppDimension.Space.md))
        }
        EditActionBar(consume = consume)
    }
}

/**
 * Read-only display of the current WEIGHTED / WEIGHTLESS type. PlanEditor owns the type
 * for both Existing and Draft exercises (v1.42.0); the toggle moved with it. The chip
 * here is purely informational with a hint that the user can change the type from the
 * plan editor.
 */
@Composable
private fun TypeChipReadOnly(type: ExerciseTypeUiModel) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        Text(
            modifier = Modifier.testTag("ExerciseTypeReadOnlyChip"),
            text = stringResource(type.labelRes),
            style = AppUi.typography.labelLarge,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.feature_exercise_edit_type_chip_hint),
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
    }
}

@Composable
private fun DefaultPlanSection(
    state: State,
    consume: (Action) -> Unit,
) {
    val isCreate = (state.mode as? Mode.Edit)?.isCreate == true
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
        if (isCreate) {
            // Create-mode has no exercise UUID yet, so it cannot navigate to the
            // full-screen `Screen.PlanEditor` route (which keys off `last_adhoc_sets`).
            // Render the body inline against the in-memory `state.adhocPlan`; the
            // existing Save path persists it via `ExerciseChangeDomain.lastAdhocSets`.
            InlineAdhocPlanEditor(state = state, consume = consume)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ExerciseEditPlanSummary"),
                    text = state.adhocPlanSummaryLabel,
                    style = AppUi.typography.bodySmall,
                    color = AppUi.colors.textTertiary,
                )
                AppButton.Tertiary(
                    modifier = Modifier.testTag("ExerciseEditPlanEditButton"),
                    text = stringResource(
                        if (state.adhocPlan.isNullOrEmpty()) {
                            R.string.feature_exercise_edit_plan_add
                        } else {
                            R.string.feature_exercise_edit_plan_edit
                        },
                    ),
                    onClick = { consume(Action.Click.OnEditPlanClick) },
                    size = AppButtonSize.SMALL,
                )
            }
        }
    }
}

@Composable
private fun InlineAdhocPlanEditor(
    state: State,
    consume: (Action) -> Unit,
) {
    val draft = state.adhocPlan ?: persistentListOf()
    // NO ADD BUTTON AFTER THIS CALL: add and remove live in the card's foot (§26, "Sets: add
    // and remove move to the card's foot"), which `PlanEditorBody` owns, so this host and the
    // full-screen route cannot drift on where a set comes from.
    PlanEditorBody(
        draft = draft,
        isWeighted = state.type == ExerciseTypeUiModel.WEIGHTED,
        onAction = { bodyAction ->
            consume(Action.Click.OnAdhocPlanEditorAction(bodyAction))
        },
        scrollable = false,
    )
}

@Composable
private fun FormSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (fieldLabel: String) -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        // `.flabel` — the drawn label sits above the box at the 12.5 rung in `textDim`. It was
        // `labelSmall`/`textTertiary` here, one rung under the drawing and a second description
        // of the same object; `AppFieldLabel` is the one implementation now (§7.2).
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
            // No `enabled` — §26 "Save is never disabled". The only condition available here is
            // the one that produces `nameError`, so gating on it makes that error unreachable.
        )
    }
}

private fun editPreviewBaseState(isCreate: Boolean): State = State
    .create(uuid = if (isCreate) null else "preview-uuid")
    .copy(mode = Mode.Edit(isCreate = isCreate), isLoading = false)

@Preview
@Composable
private fun ExerciseEditScreenCreateLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExerciseEditScreen(
            state = editPreviewBaseState(isCreate = true).copy(
                availableTags = listOf(
                    TagUiModel(uuid = "t1", name = "Push"),
                    TagUiModel(uuid = "t2", name = "Pull"),
                    TagUiModel(uuid = "t3", name = "Legs"),
                ).toImmutableList(),
                adhocPlanSummaryLabel = "No default plan",
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseEditScreenEditWithTagsPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseEditScreen(
            state = editPreviewBaseState(isCreate = false).copy(
                name = "Bench press",
                description = "Compound chest movement.",
                tags = listOf(
                    TagUiModel(uuid = "t1", name = "Push"),
                    TagUiModel(uuid = "t2", name = "Chest"),
                ).toImmutableList(),
                availableTags = listOf(
                    TagUiModel(uuid = "t1", name = "Push"),
                    TagUiModel(uuid = "t2", name = "Chest"),
                    TagUiModel(uuid = "t3", name = "Pull"),
                    TagUiModel(uuid = "t4", name = "Legs"),
                ).toImmutableList(),
                adhocPlanSummaryLabel = "3 sets · 80–95kg",
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseEditScreenNameErrorPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExerciseEditScreen(
            state = editPreviewBaseState(isCreate = true).copy(nameError = true),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseEditScreenDuplicateNameErrorPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseEditScreen(
            state = editPreviewBaseState(isCreate = true).copy(
                name = "Bench press",
                nameDuplicateError = true,
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseEditScreenWeightlessPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExerciseEditScreen(
            state = editPreviewBaseState(isCreate = false).copy(
                name = "Pull-ups",
                type = ExerciseTypeUiModel.WEIGHTLESS,
                description = "Bodyweight back exercise.",
                tags = listOf(TagUiModel(uuid = "t1", name = "Pull")).toImmutableList(),
            ),
            consume = {},
        )
    }
}
