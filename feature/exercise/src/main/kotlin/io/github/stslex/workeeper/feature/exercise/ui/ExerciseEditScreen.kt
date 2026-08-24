// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppMiniIconButton
import io.github.stslex.workeeper.core.ui.kit.components.input.AppFieldLabel
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.section.AppLabel
import io.github.stslex.workeeper.core.ui.kit.components.section.AppSectionHeader
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagFormRow
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagItem
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppIconButton
import io.github.stslex.workeeper.core.ui.kit.components.topbar.AppTopBar
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.PlanEditorBody
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.ui.components.ExerciseDescriptionBlock
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.github.stslex.workeeper.core.ui.kit.R as KitR
import io.github.stslex.workeeper.core.ui.plan_editor.R as CoreEditorR

/** `v3-editors.md` §3.2's frame: name field · plan · tags · description · dock (ED1, ED3). */
@Composable
internal fun ExerciseEditScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCreate = (state.mode as? Mode.Edit)?.isCreate == true
    val fallbackTitleRes = if (isCreate) {
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
        // `AppTopBar` in BOTH modes, with no trailing thumb (ED6). The title falls back to the
        // mode's own string only while the name is blank, and that fallback renders dim (§3.2).
        AppTopBar(
            title = state.name.ifBlank { stringResource(fallbackTitleRes) },
            smallTitle = true,
            titleDimmed = state.name.isBlank(),
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
        )
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // Content scrolls out UNDER the dock; the clearance keeps the last block
                    // reachable above it.
                    .padding(bottom = DOCK_CLEARANCE),
            ) {
                NameField(state = state, consume = consume)
                PlanSection(state = state, consume = consume)
                TagsSection(state = state, consume = consume)
                DescriptionSection(state = state, consume = consume)
                Spacer(Modifier.height(AppDimension.Space.md))
            }
            Dock(
                consume = consume,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Blocks carry the gutter individually so the full-bleed section heads can opt out. */
@Composable
private fun InGutter(
    top: Dp = AppDimension.Space.none,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.padding(
            start = AppDimension.screenEdge,
            end = AppDimension.screenEdge,
            top = top,
        ),
    ) {
        content()
    }
}

/** The form's ONE labelled field (ED3): `.flabel` above `.tf`, and no placeholder (ED4). */
@Composable
private fun NameField(
    state: State,
    consume: (Action) -> Unit,
) {
    val label = stringResource(R.string.feature_exercise_edit_label_name)
    val nameErrorText = when {
        state.nameError -> stringResource(R.string.feature_exercise_edit_error_name_required)
        state.nameDuplicateError ->
            stringResource(R.string.feature_exercise_edit_error_name_duplicate)

        else -> null
    }
    InGutter(top = AppDimension.Space.sm) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs)) {
            AppFieldLabel(text = label)
            AppTextField(
                modifier = Modifier.testTag("ExerciseEditNameField"),
                accessibilityLabel = label,
                value = state.name,
                onValueChange = { consume(Action.Input.OnNameChange(it)) },
                isError = nameErrorText != null,
            )
            if (nameErrorText != null) {
                Text(
                    text = nameErrorText,
                    style = AppUi.typography.bodySmall,
                    color = AppUi.colors.status.error,
                )
            }
        }
    }
}

/** §3.2 — `ПЛАН ПО УМОЛЧАНИЮ` with the `(i)` (ED8) over [PlanEditorBody]; ED13 starts empty. */
@Composable
private fun PlanSection(
    state: State,
    consume: (Action) -> Unit,
) {
    Column {
        PlanSectionHead(consume = consume)
        InGutter {
            PlanEditorBody(
                draft = state.adhocPlan ?: persistentListOf(),
                isWeighted = state.type == ExerciseTypeUiModel.WEIGHTED,
                onAction = { bodyAction ->
                    consume(Action.Click.OnAdhocPlanEditorAction(bodyAction))
                },
                setTypeTooltipText = stringResource(
                    CoreEditorR.string.core_ui_plan_editor_set_type_tooltip,
                ),
                scrollable = false,
                onTypeChange = { type -> consume(Action.Click.OnTypeToggle(type)) },
            )
        }
    }
}

/** Hand-composed from the kit's pieces: `AppSectionHeader` has no slot for the `.mini` `(i)`. */
@Composable
private fun PlanSectionHead(consume: (Action) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AppDimension.screenEdge,
                end = AppDimension.screenEdge,
                top = AppDimension.Space.xl,
                bottom = AppDimension.Space.sm,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLabel(text = stringResource(R.string.feature_exercise_detail_default_plan))
        AppMiniIconButton(
            modifier = Modifier.testTag("ExerciseEditPlanInfoButton"),
            icon = AppIcons.Info,
            contentDescription = stringResource(
                R.string.feature_exercise_edit_plan_info_description,
            ),
            onClick = { consume(Action.Click.OnPlanInfoClick) },
        )
    }
}

/** §3.2 — `ТЕГИ` with the `N из 10` counter, which renders only on this editor; chips are ED7's. */
@Composable
private fun TagsSection(
    state: State,
    consume: (Action) -> Unit,
) {
    Column {
        AppSectionHeader(
            modifier = Modifier.padding(
                top = AppDimension.Space.xl,
                bottom = AppDimension.Space.md,
            ),
            label = stringResource(R.string.feature_exercise_edit_label_tags),
            trailingLabel = stringResource(
                R.string.feature_exercise_edit_tags_counter,
                state.tags.size,
                State.MAX_TAGS_PER_EXERCISE,
            ),
        )
        InGutter {
            AppTagFormRow(
                selectedTags = state.tags,
                onTagRemove = { consume(Action.Click.OnTagRemove(it)) },
                onAddClick = { consume(Action.Click.OnTagAddClick) },
            )
        }
    }
}

/** §3.2 — `ОПИСАНИЕ` over the editable [ExerciseDescriptionBlock], image beside it (D-OPEN-3). */
@Composable
private fun DescriptionSection(
    state: State,
    consume: (Action) -> Unit,
) {
    Column {
        AppSectionHeader(
            modifier = Modifier.padding(
                top = AppDimension.Space.xl,
                bottom = AppDimension.Space.md,
            ),
            label = stringResource(R.string.feature_exercise_edit_label_description),
        )
        InGutter {
            ExerciseDescriptionBlock(
                description = state.description,
                type = state.type,
                imageDisplay = state.effectiveImageDisplay,
                onOpenImage = { consume(Action.Click.OnImageThumbnailClick) },
                onPickImage = { consume(Action.Click.OnEditImageClick) },
                onDescriptionChange = { consume(Action.Input.OnDescriptionChange(it)) },
            )
        }
    }
}

/** `.dock` (§3.2, §7.1): ghost `Отмена` beside primary `Сохранить`; Save is never disabled. */
@Composable
private fun Dock(
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val base = AppUi.colors.surfaceTier0
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    DOCK_GRADIENT_STOP to base,
                    1f to base,
                ),
            )
            .padding(
                start = AppDimension.screenEdge,
                end = AppDimension.screenEdge,
                top = AppDimension.Space.lg,
                bottom = AppDimension.Space.xl,
            )
            .navigationBarsPadding()
            .testTag("ExerciseEditActionBar"),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppButton.Ghost(
            modifier = Modifier
                .weight(1f)
                .testTag("ExerciseEditCancelButton"),
            text = stringResource(KitR.string.core_ui_kit_action_cancel),
            onClick = { consume(Action.Click.OnCancelClick) },
        )
        AppButton.Primary(
            modifier = Modifier
                .weight(1f)
                .testTag("ExerciseEditSaveButton"),
            text = stringResource(KitR.string.core_ui_kit_action_save),
            onClick = { consume(Action.Click.OnSaveClick) },
        )
    }
}

/** `.dock`'s `linear-gradient(to top, base 62%, …)`: solid from the bottom 62%. */
private const val DOCK_GRADIENT_STOP = 0.38f

/** Clearance so the scroll content's tail clears the overlaid dock. */
private val DOCK_CLEARANCE = 104.dp

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
                    AppTagItem(uuid = "t1", name = "Push"),
                    AppTagItem(uuid = "t2", name = "Pull"),
                    AppTagItem(uuid = "t3", name = "Legs"),
                ).toImmutableList(),
            ),
            consume = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseEditScreenEditWithPlanPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseEditScreen(
            state = editPreviewBaseState(isCreate = false).copy(
                name = "Bench press",
                description = "Compound chest movement.",
                tags = listOf(
                    AppTagItem(uuid = "t1", name = "Push"),
                    AppTagItem(uuid = "t2", name = "Chest"),
                ).toImmutableList(),
                availableTags = listOf(
                    AppTagItem(uuid = "t1", name = "Push"),
                    AppTagItem(uuid = "t2", name = "Chest"),
                    AppTagItem(uuid = "t3", name = "Pull"),
                    AppTagItem(uuid = "t4", name = "Legs"),
                ).toImmutableList(),
                adhocPlan = listOf(
                    PlanSetUiModel(weight = 60.0, reps = 10, type = SetTypeUiModel.WORK),
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ).toImmutableList(),
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
