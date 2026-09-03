// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.components.border.dashedBorder
import io.github.stslex.workeeper.core.ui.kit.components.button.AppCheckmarkButtonTouchSize
import io.github.stslex.workeeper.core.ui.kit.components.button.AppMiniIconButton
import io.github.stslex.workeeper.core.ui.kit.components.ordinal.AppOrdinalChip
import io.github.stslex.workeeper.core.ui.kit.components.setrow.SetColumnHeader
import io.github.stslex.workeeper.core.ui.kit.components.setrow.SetRowGeometry
import io.github.stslex.workeeper.core.ui.kit.components.surface.AppActiveSurface
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.R
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** `.card.skip{opacity:.5}` — the one state the mockup does mute with alpha. */
private const val SKIPPED_ALPHA = 0.5f

/**
 * `.card` (extraction §1.5): no border in any state; the open card is marked by the lift.
 * `active` mirrors the mockup's `.card.active`, which is `isOpen(e)`, not "is current".
 */
@Composable
internal fun LiveExerciseCard(
    exercise: LiveExerciseUiModel,
    ordinal: Int,
    expanded: Boolean,
    consume: (LiveWorkoutStore.Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardAlpha by animateFloatAsState(
        targetValue = if (exercise.status == ExerciseStatusUiModel.SKIPPED) SKIPPED_ALPHA else 1f,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "card-alpha",
    )
    val shape = RoundedCornerShape(AppDimension.Radius.medium)
    // One call site for every card state — `ActiveSurfaceSingleReaderRule` names this file.
    AppActiveSurface(
        active = expanded,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .alpha(cardAlpha),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .animateContentSize(
                    animationSpec = tween(
                        durationMillis = AppUi.motion.base,
                        easing = AppUi.motion.out,
                    ),
                ),
        ) {
            ExerciseCardHeader(
                exercise = exercise,
                ordinal = ordinal,
                expanded = expanded,
                consume = consume,
            )
            if (expanded) {
                ExerciseCardBody(
                    exercise = exercise,
                    isReadOnly = exercise.status == ExerciseStatusUiModel.DONE,
                    consume = consume,
                )
            }
        }
    }
}

/** `.chead`: ordchip · title/sub/pstrip column · `.mini` cluster; a tap toggles the card. */
@Composable
private fun ExerciseCardHeader(
    exercise: LiveExerciseUiModel,
    ordinal: Int,
    expanded: Boolean,
    consume: (LiveWorkoutStore.Action) -> Unit,
) {
    val isDone = exercise.status == ExerciseStatusUiModel.DONE
    val isSkipped = exercise.status == ExerciseStatusUiModel.SKIPPED
    val toggle = {
        consume(LiveWorkoutStore.Action.Click.OnExerciseHeaderClick(exercise.performedExerciseUuid))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = toggle)
            .padding(AppDimension.Space.lg),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        // Index-dense, not position-based: the mockup renumbers on splice, so no gaps.
        AppOrdinalChip(
            ordinal = ordinal,
            isActive = expanded,
            isDone = isDone,
            isSkipped = isSkipped,
            isOneOff = !exercise.isPlanAttached,
        )
        Column(modifier = Modifier.weight(1f)) {
            TitleRow(
                exercise = exercise,
                isDone = isDone,
                isSkipped = isSkipped,
            )
            Spacer(modifier = Modifier.height(AppDimension.Space.xs))
            Text(
                modifier = Modifier.testTag("LiveExerciseCardSub_${exercise.performedExerciseUuid}"),
                text = exercise.statusLabel,
                style = AppUi.typography.mono.meta,
                color = AppUi.colors.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (exercise.visibleSets.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppDimension.Space.sm))
                ExercisePstrip(
                    sets = exercise.visibleSets,
                    isSkipped = isSkipped,
                )
            }
        }
        HeaderActions(
            exercise = exercise,
            expanded = expanded,
            onToggle = toggle,
            consume = consume,
        )
    }
}

@Composable
private fun TitleRow(
    exercise: LiveExerciseUiModel,
    isDone: Boolean,
    isSkipped: Boolean,
) {
    val titleColor by animateColorAsState(
        targetValue = if (isDone || isSkipped) AppUi.colors.textTertiary else AppUi.colors.textPrimary,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "ctitle-color",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        Text(
            modifier = Modifier.weight(1f, fill = false),
            text = exercise.exerciseName,
            style = AppUi.typography.text.body.copy(
                fontWeight = if (isDone) FontWeight.Medium else FontWeight.SemiBold,
                // Strikethrough shares the text colour; there is no separate decoration colour.
                textDecoration = if (isSkipped) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = titleColor,
        )
        if (!exercise.isPlanAttached) {
            OneOffBadge()
        }
    }
}

/** `.tempbadge` — mono caption inside a dashed `dim` outline; uppercased at the edge. */
@Composable
private fun OneOffBadge() {
    Text(
        modifier = Modifier
            .dashedBorder(
                color = AppUi.colors.textDim,
                cornerRadius = AppDimension.Radius.smallest,
            )
            .padding(
                horizontal = AppDimension.Space.xs,
                vertical = AppDimension.Space.xxs,
            ),
        text = stringResource(R.string.feature_live_workout_one_off_badge).uppercase(),
        style = AppUi.typography.mono.caption.copy(letterSpacing = ONE_OFF_BADGE_TRACKING),
        color = AppUi.colors.textSecondary,
        maxLines = 1,
    )
}

@Composable
private fun HeaderActions(
    exercise: LiveExerciseUiModel,
    expanded: Boolean,
    onToggle: () -> Unit,
    consume: (LiveWorkoutStore.Action) -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_OPEN_DEGREES else 0f,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "rot-chevron",
    )
    Row(
        modifier = Modifier.offset(x = ACTIONS_HANG, y = -ACTIONS_HANG),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        if (exercise.description != null) {
            AppMiniIconButton(
                icon = AppIcons.Info,
                contentDescription = stringResource(R.string.feature_live_workout_description),
                onClick = {
                    consume(
                        LiveWorkoutStore.Action.Click.OnShowDescription(exercise.performedExerciseUuid),
                    )
                },
            )
        }
        AppMiniIconButton(
            modifier = Modifier.testTag("LiveExerciseCard_Menu_${exercise.performedExerciseUuid}"),
            icon = AppIcons.MoreVertical,
            contentDescription = stringResource(R.string.feature_live_workout_more),
            onClick = {
                consume(
                    LiveWorkoutStore.Action.Click.OnExerciseMenuClick(exercise.performedExerciseUuid),
                )
            },
        )
        AppMiniIconButton(
            icon = AppIcons.ChevronRight,
            contentDescription = stringResource(
                if (expanded) {
                    R.string.feature_live_workout_collapse
                } else {
                    R.string.feature_live_workout_expand
                },
            ),
            onClick = onToggle,
            glyphRotationDegrees = chevronRotation,
        )
    }
}

/**
 * `.pstrip` — the card's 4dp miniature of the rail: one segment per visible row, skipped
 * cards outlined and never filled. Fill width animates over [PSTRIP_FILL_MS].
 */
@Composable
private fun ExercisePstrip(
    sets: ImmutableList<LiveSetUiModel>,
    isSkipped: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PSTRIP_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        sets.forEach { set ->
            val fillFraction by animateFloatAsState(
                targetValue = if (!isSkipped && set.isDone) 1f else 0f,
                animationSpec = tween(durationMillis = PSTRIP_FILL_MS, easing = AppUi.motion.out),
                label = "pstrip-fill",
            )
            val fillColor = if (set.isPersonalRecord) {
                AppUi.colors.record.solid
            } else {
                AppUi.colors.accent
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(PSTRIP_HEIGHT)
                    .let { base ->
                        if (isSkipped) {
                            base.dashedBorder(
                                color = AppUi.colors.borderDefault,
                                cornerRadius = PSTRIP_RADIUS,
                            )
                        } else {
                            base
                                .clip(RoundedCornerShape(PSTRIP_RADIUS))
                                .background(AppUi.colors.surfaceTier4)
                        }
                    },
            ) {
                if (fillFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fillFraction)
                            .height(PSTRIP_HEIGHT)
                            .clip(RoundedCornerShape(PSTRIP_RADIUS))
                            .background(fillColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseCardBody(
    exercise: LiveExerciseUiModel,
    isReadOnly: Boolean,
    consume: (LiveWorkoutStore.Action) -> Unit,
) {
    val isWeighted = exercise.exerciseType == ExerciseTypeUiModel.WEIGHTED
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(
                    durationMillis = AppUi.motion.base,
                ),
            ),
    ) {
        SetsColumn(exercise, isWeighted, isReadOnly, consume)
        SetBar(
            exerciseUuid = exercise.performedExerciseUuid,
            canRemove = exercise.visibleSets.size > 1,
            consume = consume,
        )
    }
}

/**
 * `.sets` with a hairline above every row but the first, drawn by the container.
 * The index column width is resolved here once so header and rows grow together past nine.
 */
@Composable
private fun SetsColumn(
    exercise: LiveExerciseUiModel,
    isWeighted: Boolean,
    isReadOnly: Boolean,
    consume: (LiveWorkoutStore.Action) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.Space.md),
    ) {
        val indexColumnWidth = SetRowGeometry.resolveIndexColumnWidth(exercise.visibleSets.size)
        if (exercise.visibleSets.isNotEmpty()) {
            SetColumnHeader(
                isWeighted = isWeighted,
                indexColumnWidth = indexColumnWidth,
                trailingWidth = SetRowGeometry.resolveTrailingSlotWidth() +
                    AppDimension.Space.sm +
                    AppCheckmarkButtonTouchSize,
            )
        }
        exercise.visibleSets.forEachIndexed { index, row ->
            key(exercise.performedExerciseUuid, row.position) {
                if (index > 0) {
                    HorizontalDivider(
                        thickness = AppDimension.Border.small,
                        color = AppUi.colors.borderSubtle,
                    )
                }
                LiveSetRow(
                    set = row,
                    isWeighted = isWeighted,
                    indexColumnWidth = indexColumnWidth,
                    testTagPrefix = "LiveSetRow_${exercise.performedExerciseUuid}_${row.position}",
                    onWeightChange = { value ->
                        consume(
                            LiveWorkoutStore.Action.Input.OnSetWeightChange(
                                exercise.performedExerciseUuid,
                                row.position,
                                value,
                            ),
                        )
                    },
                    onRepsChange = { value ->
                        consume(
                            LiveWorkoutStore.Action.Input.OnSetRepsChange(
                                exercise.performedExerciseUuid,
                                row.position,
                                value,
                            ),
                        )
                    },
                    onTypeChange = { type ->
                        if (!isReadOnly) {
                            consume(
                                LiveWorkoutStore.Action.Click.OnSetTypeSelect(
                                    exercise.performedExerciseUuid,
                                    row.position,
                                    type,
                                ),
                            )
                        }
                    },
                    onMarkDone = {
                        if (!isReadOnly) {
                            consume(
                                LiveWorkoutStore.Action.Click.OnSetMarkDone(
                                    exercise.performedExerciseUuid,
                                    row.position,
                                ),
                            )
                        }
                    },
                    onUncheck = {
                        consume(
                            LiveWorkoutStore.Action.Click.OnSetUncheck(
                                exercise.performedExerciseUuid,
                                row.position,
                            ),
                        )
                    },
                    editable = !isReadOnly,
                )
            }
        }
    }
}

/**
 * `.setbar` — two equal-width mono buttons at the foot of every expanded card: `+ подход`
 * appends a copy of the last row, `− подход` removes it and is disabled at one row.
 */
@Composable
private fun SetBar(
    exerciseUuid: String,
    canRemove: Boolean,
    consume: (LiveWorkoutStore.Action) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = AppDimension.Border.small,
            color = AppUi.colors.borderSubtle,
        )
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            SetBarButton(
                text = stringResource(R.string.feature_live_workout_setbar_add),
                enabled = true,
                onClick = { consume(LiveWorkoutStore.Action.Click.OnAddSet(exerciseUuid)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("LiveExerciseCard_AddSet_$exerciseUuid"),
            )
            VerticalDivider(
                thickness = AppDimension.Border.small,
                color = AppUi.colors.borderSubtle,
            )
            SetBarButton(
                text = stringResource(R.string.feature_live_workout_setbar_remove),
                enabled = canRemove,
                onClick = { consume(LiveWorkoutStore.Action.Click.OnRemoveLastSet(exerciseUuid)) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("LiveExerciseCard_RemoveSet_$exerciseUuid"),
            )
        }
    }
}

@Composable
private fun SetBarButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val color by animateColorAsState(
        targetValue = if (isPressed) AppUi.colors.textPrimary else AppUi.colors.textTertiary,
        animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.out),
        label = "setbar-color",
    )
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else SETBAR_DISABLED_ALPHA)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = AppDimension.Space.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = AppUi.typography.mono.meta.copy(letterSpacing = SETBAR_TRACKING),
            color = color,
        )
    }
}

private const val SETBAR_DISABLED_ALPHA = 0.35f

private val SETBAR_TRACKING = 0.75.sp

/** `.chead-act{margin:-6px -6px 0 0}` — the cluster hangs into the header padding. */
private val ACTIONS_HANG: Dp = 6.dp

private const val CHEVRON_OPEN_DEGREES = 90f

private val ONE_OFF_BADGE_TRACKING = 1.32.sp

private val PSTRIP_HEIGHT: Dp = 4.dp

/** 2px in the mockup; below the `Radius` ladder's smallest rung, kept as drawn. */
private val PSTRIP_RADIUS: Dp = 2.dp

/** `.pstrip i b{transition:width 380ms}` — outside the three-token motion scale. */
private const val PSTRIP_FILL_MS = 380

@Preview
@Composable
private fun LiveExerciseCardCurrentLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LiveExerciseCard(
            exercise = previewCurrent(),
            ordinal = 1,
            expanded = true,
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveExerciseCardCurrentDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveExerciseCard(
            exercise = previewCurrent(),
            ordinal = 1,
            expanded = true,
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveExerciseCardDonePreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveExerciseCard(
            exercise = previewDone(),
            ordinal = 1,
            expanded = false,
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveExerciseCardOneOffPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveExerciseCard(
            exercise = previewCurrent().copy(isPlanAttached = false),
            ordinal = 1,
            expanded = false,
            consume = {},
        )
    }
}

@Preview
@Composable
private fun LiveExerciseCardSkippedPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        LiveExerciseCard(
            exercise = previewSkipped(),
            ordinal = 1,
            expanded = false,
            consume = {},
        )
    }
}

private fun previewCurrent() = LiveExerciseUiModel(
    performedExerciseUuid = "pe-1",
    exerciseUuid = "ex-1",
    exerciseName = "жим лёжа",
    exerciseType = ExerciseTypeUiModel.WEIGHTED,
    position = 0,
    status = ExerciseStatusUiModel.CURRENT,
    statusLabel = "100×5 · 100×5 · 102.5×5",
    planSets = persistentListOf(
        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
        PlanSetUiModel(weight = 100.0, reps = 5, type = SetTypeUiModel.WORK),
        PlanSetUiModel(weight = 102.5, reps = 5, type = SetTypeUiModel.WORK),
    ),
    performedSets = persistentListOf(
        LiveSetUiModel(
            position = 0,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        ),
    ),
    visibleSets = (0 until 3).map { position ->
        LiveSetUiModel(
            position = position,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = position == 0,
        )
    }.toImmutableList(),
)

private fun previewDone() = previewCurrent().copy(
    status = ExerciseStatusUiModel.DONE,
    performedSets = (0 until 3).map { position ->
        LiveSetUiModel(
            position = position,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        )
    }.toImmutableList(),
    visibleSets = (0 until 3).map { position ->
        LiveSetUiModel(
            position = position,
            weight = 100.0,
            reps = 5,
            type = SetTypeUiModel.WORK,
            isDone = true,
        )
    }.toImmutableList(),
)

private fun previewSkipped() = previewCurrent().copy(
    status = ExerciseStatusUiModel.SKIPPED,
    statusLabel = "пропущено",
    performedSets = persistentListOf(),
)
