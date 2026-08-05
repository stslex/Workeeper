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
import io.github.stslex.workeeper.feature.exercise.ui.components.TagPickerInline
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import io.github.stslex.workeeper.core.ui.kit.R as KitR
import io.github.stslex.workeeper.core.ui.plan_editor.R as CoreEditorR

/**
 * `v3-editors.md` §3.2's frame, in its order:
 *
 *     topbar   ‹ · name, or the create title dim          no thumb (ED6)
 *     fgrp     Название                                   the one .flabel + .tf (ED3)
 *     head     ПЛАН ПО УМОЛЧАНИЮ  (i)                     type toggle + plan card (ED1, ED5)
 *     head     ТЕГИ  N из 10
 *     head     ОПИСАНИЕ                                   + image beside it (D-OPEN-3)
 *     dock     Отмена · Сохранить                         Save always enabled (§7.3)
 *
 * The plan is edited **where it is drawn, in both modes** (ED1) — this screen is the plan's
 * whole editor, and nothing routes away from it. ED3 rules the rhythm — a labelled field only
 * where text is typed, a `.section-head` everywhere else — which is why the name keeps its
 * `.flabel` and everything below it is a section.
 */
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
        // The bar is `AppTopBar` in BOTH modes of this screen (§26, "The editors' six
        // code-diverges") and carries NO trailing thumb (ED6): the image affordance lives
        // beside the description now, and it is the only one. The title falls back to the
        // mode's own string only while the name is blank — an unnamed record has no name to
        // show — and the fallback renders DIM (§3.2), because a placeholder title is not the
        // record's name.
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

/**
 * The form's ONE labelled field (ED3): `.flabel` above `.tf`. **No placeholder** — ED4 rules
 * that a placeholder repeating the label is a second description of one object, so an empty
 * name field is empty.
 */
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

/**
 * §3.2 — `ПЛАН ПО УМОЛЧАНИЮ` with the `(i)` in the head's trailing slot (ED8), over
 * [PlanEditorBody]: the monochrome toggle (ED5) above the set card with its `.setbar` foot. The
 * head
 * carries a short label; the REASON — what a default plan is for — lives in the sheet the `(i)`
 * opens, and not in a subtitle under the label.
 *
 * Creation starts from an EMPTY draft (ED13) — no seeded sets, and the card's own foot disables
 * «− подход» while the draft is empty. Supplying [Action.Click.OnTypeToggle] is what makes the
 * toggle appear (the null is the exclusion, `PlanEditorBody`'s own grammar), and this form
 * supplies it in both modes: the type belongs to the exercise and this is the exercise's editor.
 */
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

/**
 * The plan head, hand-composed from the kit's own pieces because `AppSectionHeader` has no
 * icon slot: [AppLabel] on the left — the same rung both real heads on this screen use — and
 * the `.mini`-treatment `(i)` on the right. The mini button is 34dp visual, so the head keeps
 * `AppSectionHeader`'s text rhythm by letting the button's extra height centre around the
 * label rather than pushing the section apart.
 */
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

/**
 * §3.2 — `ТЕГИ` with the `N из 10` counter as the head's trailing label. The counter renders
 * **only** on this editor: the limit is this feature's ([State.MAX_TAGS_PER_EXERCISE]), and
 * `feature/single-training` has none, so showing one there would be a lie. The picker below is
 * the tag affordance this screen has; nothing here builds a second one.
 */
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
    }
}

/**
 * §3.2 — `ОПИСАНИЕ` over [ExerciseDescriptionBlock] in its **editable** mode: `.tf.multi` with
 * the image beside it (D-OPEN-3). The whole image affordance is here, and there is none in the
 * top bar (ED6).
 */
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

/**
 * `.dock` (§3.2, §7.1): sticky over the same gradient scrim as the read screen's, ghost
 * `Отмена` beside primary `Сохранить`, both flex — the drawn `.btn{flex:1}` with no fixed
 * width on either, unlike `#s-ex`'s 130px `Изменить`.
 *
 * Save carries no `enabled` (§7.3, "Save is never disabled"): the only condition available
 * here is the one that produces `nameError`, so gating on it makes that error unreachable.
 */
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
                    TagUiModel(uuid = "t1", name = "Push"),
                    TagUiModel(uuid = "t2", name = "Pull"),
                    TagUiModel(uuid = "t3", name = "Legs"),
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
                    TagUiModel(uuid = "t1", name = "Push"),
                    TagUiModel(uuid = "t2", name = "Chest"),
                ).toImmutableList(),
                availableTags = listOf(
                    TagUiModel(uuid = "t1", name = "Push"),
                    TagUiModel(uuid = "t2", name = "Chest"),
                    TagUiModel(uuid = "t3", name = "Pull"),
                    TagUiModel(uuid = "t4", name = "Legs"),
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
