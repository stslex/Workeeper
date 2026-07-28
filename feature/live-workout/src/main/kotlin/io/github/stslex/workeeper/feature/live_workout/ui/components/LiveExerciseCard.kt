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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import io.github.stslex.workeeper.core.ui.kit.components.button.AppMiniIconButton
import io.github.stslex.workeeper.core.ui.kit.components.ordinal.AppOrdinalChip
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
 * `.card` (extraction §1.5). Radius 18px → 16dp; **no border in any state** — the open card
 * is marked by the lift (`sec → slab` + `slabtop`, via [AppActiveSurface]) and by nothing
 * else.
 *
 * `active` is the mockup's `.card.active`, which is **`isOpen(e)`** — the card is lifted when
 * it is *expanded*, not when it is the current exercise (`session-v3f.html:409`,
 * `c.classList.toggle('active', isOpen(e))`). A finished card the user reopens lifts exactly
 * like the current one. Step 5 keyed the lift on CURRENT; that was the nearest state the old
 * skeleton had, not what the mockup draws.
 *
 * Done is **not** an opacity change: `.fin` re-chips the ordinal (checkmark on `donefill`)
 * and mutes the title to `meta`/500. The old `DONE_ALPHA` fade is gone. Skip keeps the
 * mockup's `opacity:.5` plus the strikethrough title.
 */
@Composable
internal fun LiveExerciseCard(
    exercise: LiveExerciseUiModel,
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

/**
 * `.chead`: ordchip · title/sub/pstrip column · the `.mini` action cluster. Padding 16dp,
 * gap 8dp (mockup 11px), top-aligned; the action cluster hangs 6dp up and out of the padding
 * (`margin:-6px -6px 0 0`). Tapping anywhere except the menu toggles the card.
 */
@Composable
private fun ExerciseCardHeader(
    exercise: LiveExerciseUiModel,
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
        AppOrdinalChip(
            ordinal = exercise.position + 1,
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
                // Strikethrough shares the text colour; the mockup's separate
                // `text-decoration-color: --dim` has no Compose equivalent on one Text.
                textDecoration = if (isSkipped) TextDecoration.LineThrough else TextDecoration.None,
            ),
            color = titleColor,
        )
        if (!exercise.isPlanAttached) {
            OneOffBadge()
        }
    }
}

/**
 * `.tempbadge` — mono caption, `.12em` tracking, uppercase, `body` text inside a dashed
 * `dim` outline (extraction §1.5). Rendered only on one-off cards; the string ships
 * lowercase like the mockup's markup and is uppercased at the edge, the same move
 * `AppSectionHeader` makes.
 */
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
    var menuExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_OPEN_DEGREES else 0f,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "rot-chevron",
    )
    Row(
        modifier = Modifier.offset(x = ACTIONS_HANG, y = -ACTIONS_HANG),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        // `.mini.info` renders here once descriptions reach the UI model — sheet region (C6).
        Box {
            AppMiniIconButton(
                icon = AppIcons.MoreVertical,
                contentDescription = stringResource(R.string.feature_live_workout_more),
                onClick = { menuExpanded = true },
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_live_workout_action_edit_plan)) },
                    onClick = {
                        menuExpanded = false
                        consume(LiveWorkoutStore.Action.Click.OnEditPlan(exercise.performedExerciseUuid))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_live_workout_action_reset_sets)) },
                    onClick = {
                        menuExpanded = false
                        consume(LiveWorkoutStore.Action.Click.OnResetSets(exercise.performedExerciseUuid))
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.feature_live_workout_action_skip)) },
                    onClick = {
                        menuExpanded = false
                        consume(LiveWorkoutStore.Action.Click.OnSkipExercise(exercise.performedExerciseUuid))
                    },
                )
            }
        }
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
 * `.pstrip` — the card's own 4dp miniature of the rail (extraction §1.5): one segment per
 * visible row, track `raise`, fill `max` (or `molten-solid` for a record), 2dp radius and
 * gaps. Skipped cards outline the segments dashed and never fill them. Fill width animates
 * over [PSTRIP_FILL_MS] — the mockup's 380ms, one of the durations outside the three-token
 * motion scale (extraction B9); named here rather than rounded onto a token it is not.
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
 * `.sets{padding:0 12px}` with `border-top: 1px --hair` on every row but the first —
 * the hairline is intra-card row trim (spec §3.1, decorative), drawn by the container.
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
 * `.setbar` (extraction §1.7) — "missing entirely from the build" until now. Two
 * equal-width mono buttons at the foot of every expanded card, ruled off by hairlines:
 * `+ подход` appends a copy of the last row; `− подход` removes the last row and is
 * disabled at one. Present on completed cards too — §6.4: adding a set to a completed
 * exercise returns it to incomplete. The mockup's toasts and their undo land with the
 * toast component (C6).
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

/** `.setbar button:disabled{opacity:.35}`. */
private const val SETBAR_DISABLED_ALPHA = 0.35f

/** `.setbar{letter-spacing:.06em}` at the 12.5sp meta rung. */
private val SETBAR_TRACKING = 0.75.sp

/** `.chead-act{margin:-6px -6px 0 0}` — the cluster hangs into the header padding. */
private val ACTIONS_HANG: Dp = 6.dp

/** `.card.active .mini.rot svg{transform:rotate(90deg)}`. */
private const val CHEVRON_OPEN_DEGREES = 90f

/** `.tempbadge{letter-spacing:.12em}` at the 11sp caption rung. */
private val ONE_OFF_BADGE_TRACKING = 1.32.sp

private val PSTRIP_HEIGHT: Dp = 4.dp

/** 2px in the mockup; below the `Radius` ladder's smallest rung, kept as drawn. */
private val PSTRIP_RADIUS: Dp = 2.dp

/** `.pstrip i b{transition:width 380ms}` — outside the motion tokens, reported under B9. */
private const val PSTRIP_FILL_MS = 380

@Preview
@Composable
private fun LiveExerciseCardCurrentLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        LiveExerciseCard(
            exercise = previewCurrent(),
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
