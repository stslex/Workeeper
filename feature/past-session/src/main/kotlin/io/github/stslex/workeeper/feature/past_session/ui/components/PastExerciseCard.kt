// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.ReorderableColumnState
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.rememberReorderableColumnState
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.reorderableColumnDragHandle
import io.github.stslex.workeeper.core.ui.kit.components.reorderable.reorderableColumnItem
import io.github.stslex.workeeper.core.ui.kit.components.setrow.SetColumnHeader
import io.github.stslex.workeeper.core.ui.kit.components.setrow.SetRowGeometry
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import kotlinx.collections.immutable.persistentListOf

/**
 * `.card` / `.card.open` (extraction §2.5) — the past-session exercise card. Collapsed draws the
 * summary and a static chevron; open lifts the surface and drops both. The header is the target.
 */
@Composable
internal fun PastExerciseCard(
    exercise: PastExerciseUiModel,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
    onWeightChange: (String, String) -> Unit,
    onRepsChange: (String, String) -> Unit,
    onPrTagClick: () -> Unit,
    onDragStarted: () -> Unit,
    onSetReorder: (performedExerciseUuid: String, from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Non-lazy reorderable column: the parent screen is already a LazyColumn.
    val reorderState = rememberReorderableColumnState(
        onDragStarted = { onDragStarted() },
    ) { from, to ->
        onSetReorder(exercise.performedExerciseUuid, from, to)
    }
    val cardAlpha by animateFloatAsState(
        targetValue = if (exercise.skipped) SKIPPED_ALPHA else 1f,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "past-card-alpha",
    )
    val shape = RoundedCornerShape(AppDimension.Radius.medium)
    LookaheadScope {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .alpha(cardAlpha)
                .liftedSurface(shape = shape, lifted = expanded)
                .clip(shape)
                .animateContentSize(
                    animationSpec = tween(
                        durationMillis = AppUi.motion.base,
                        easing = AppUi.motion.out,
                    ),
                ),
        ) {
            CardHeader(
                exercise = exercise,
                expanded = expanded,
                onHeaderClick = onHeaderClick,
            )
            if (expanded) {
                CardBody(
                    exercise = exercise,
                    reorderState = reorderState,
                    onWeightChange = onWeightChange,
                    onRepsChange = onRepsChange,
                    onPrTagClick = onPrTagClick,
                )
            }
        }
    }
}

/** `.chead`: `.ord` · title/plan-line column · the static chevron. Padding 16dp, gap 8dp. */
@Composable
private fun CardHeader(
    exercise: PastExerciseUiModel,
    expanded: Boolean,
    onHeaderClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onHeaderClick)
            .padding(AppDimension.Space.lg),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        Text(
            modifier = Modifier.widthIn(min = OrdinalWidth),
            text = (exercise.position + 1).toString(),
            style = AppUi.typography.mono.meta,
            color = AppUi.colors.textDim,
            maxLines = 1,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.exerciseName,
                style = AppUi.typography.text.body.copy(
                    fontWeight = FontWeight.SemiBold,
                    // Compose cannot colour the strike apart from the text; the mockup can.
                    textDecoration = if (exercise.skipped) {
                        TextDecoration.LineThrough
                    } else {
                        TextDecoration.None
                    },
                ),
                color = if (exercise.skipped) {
                    AppUi.colors.textTertiary
                } else {
                    AppUi.colors.textPrimary
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // `.plan-line` is collapsed-only (§2.5); a skipped card states the fact instead.
            val planLine = when {
                expanded -> null
                exercise.skipped -> stringResource(R.string.feature_past_session_skipped_line)
                exercise.setSummary.isNotEmpty() -> exercise.setSummary
                else -> null
            }
            if (planLine != null) {
                Text(
                    modifier = Modifier.padding(top = AppDimension.Space.xs),
                    text = planLine,
                    style = AppUi.typography.mono.meta,
                    color = AppUi.colors.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!expanded) {
            // `.chev` — a decorative static glyph; the whole header is the toggle target.
            Icon(
                modifier = Modifier
                    .padding(top = AppDimension.Space.xs)
                    .size(AppDimension.iconSm),
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = AppUi.colors.textDim,
            )
        }
    }
}

/**
 * `.cbody > .sets`, rows split by hairline rules. The column header is the live `SetsColumn`'s
 * twin: index and trailing widths resolved once for header and rows (§4 D3).
 */
@Composable
private fun CardBody(
    exercise: PastExerciseUiModel,
    reorderState: ReorderableColumnState,
    onWeightChange: (String, String) -> Unit,
    onRepsChange: (String, String) -> Unit,
    onPrTagClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AppDimension.Space.md,
                end = AppDimension.Space.md,
                bottom = AppDimension.Space.sm,
            ),
    ) {
        if (exercise.sets.isEmpty()) {
            Text(
                modifier = Modifier.padding(
                    horizontal = AppDimension.Space.xs,
                    vertical = AppDimension.Space.sm,
                ),
                text = stringResource(R.string.feature_past_session_no_sets),
                style = AppUi.typography.mono.meta,
                color = AppUi.colors.textDim,
            )
        } else {
            val indexColumnWidth = SetRowGeometry.resolveIndexColumnWidth(exercise.sets.size)
            SetColumnHeader(
                isWeighted = exercise.isWeighted,
                indexColumnWidth = indexColumnWidth,
                trailingWidth = SetRowGeometry.resolveTrailingSlotWidth() +
                    AppDimension.Space.sm +
                    DragHandleSize,
            )
            exercise.sets.forEachIndexed { index, set ->
                key(set.setUuid) {
                    if (index > 0) {
                        HorizontalDivider(
                            thickness = AppDimension.Border.small,
                            color = AppUi.colors.borderSubtle,
                        )
                    }
                    PastSetEditRow(
                        set = set,
                        isWeighted = exercise.isWeighted,
                        indexColumnWidth = indexColumnWidth,
                        onWeightChange = { raw -> onWeightChange(set.setUuid, raw) },
                        onRepsChange = { raw -> onRepsChange(set.setUuid, raw) },
                        onPrTagClick = onPrTagClick,
                        modifier = Modifier.reorderableColumnItem(
                            state = reorderState,
                            key = set.setUuid,
                            index = index,
                            lastIndex = exercise.sets.lastIndex,
                        ),
                        // Skipped exercises stay read-only: the handle draws, the gesture is off.
                        dragHandleModifier = Modifier.reorderableColumnDragHandle(
                            state = reorderState,
                            key = set.setUuid,
                            enabled = !exercise.skipped,
                        ),
                    )
                }
            }
        }
    }
}

/** `.card.skip{opacity:.5}` — the session card's skip fade, reused as the sibling treatment. */
private const val SKIPPED_ALPHA = 0.5f

/**
 * `.chead .ord { width: 16px }` as a MINIMUM with `maxLines = 1`: a fixed box silently wraps a
 * two-digit ordinal at a grapheme boundary above fontScale 1.0.
 */
private val OrdinalWidth: Dp = 16.dp

@Preview(name = "Expanded — Light")
@Composable
private fun PastExerciseCardExpandedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        PastExerciseCard(
            exercise = stubExercise(),
            expanded = true,
            onHeaderClick = {},
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onPrTagClick = {},
            onSetReorder = { _, _, _ -> },
            onDragStarted = { },
        )
    }
}

@Preview(name = "Collapsed — Dark")
@Composable
private fun PastExerciseCardCollapsedDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastExerciseCard(
            exercise = stubExercise(),
            expanded = false,
            onHeaderClick = {},
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onPrTagClick = {},
            onSetReorder = { _, _, _ -> },
            onDragStarted = { },
        )
    }
}

@Preview(name = "Skipped — Collapsed")
@Composable
private fun PastExerciseCardSkippedPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PastExerciseCard(
            exercise = stubExercise().copy(
                skipped = true,
                setSummary = "",
                sets = persistentListOf(),
            ),
            expanded = false,
            onHeaderClick = {},
            onWeightChange = { _, _ -> },
            onRepsChange = { _, _ -> },
            onPrTagClick = {},
            onSetReorder = { _, _, _ -> },
            onDragStarted = { },
        )
    }
}

private fun stubExercise(): PastExerciseUiModel = PastExerciseUiModel(
    performedExerciseUuid = "pe-1",
    exerciseName = "разведение ног",
    position = 0,
    skipped = false,
    isWeighted = true,
    setSummary = "49×15 · 71×15",
    sets = persistentListOf(
        PastSetUiModel(
            setUuid = "s-1",
            performedExerciseUuid = "pe-1",
            position = 0,
            type = SetTypeUiModel.WORK,
            weightInput = "49",
            repsInput = "15",
            weightError = false,
            repsError = false,
            isPersonalRecord = true,
        ),
        PastSetUiModel(
            setUuid = "s-2",
            performedExerciseUuid = "pe-1",
            position = 1,
            type = SetTypeUiModel.WORK,
            weightInput = "71",
            repsInput = "15",
            weightError = false,
            repsError = false,
            isPersonalRecord = false,
        ),
    ),
)
