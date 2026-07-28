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
import androidx.compose.foundation.layout.width
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
 * `.card` / `.card.open` (extraction §2.5) — the past-session exercise card, two states only.
 *
 * ## The open card lifts — B8's second half, and §2.8's first defect closed
 *
 * `sec → slab` plus `--slabtop` is the mockup's whole disclosure signal, applied through
 * `Modifier.liftedSurface` directly: `AppActiveSurface` is capped at one call site app-wide
 * (`ActiveSurfaceSingleReaderRule` names `LiveExerciseCard`), while the lift *mechanism*
 * legitimately has four consumers and `LiftedSurface`'s KDoc names this card as one.
 * There is no border in either state.
 *
 * ## Collapsed vs open anatomy — §2.8's second defect closed twice
 *
 * Collapsed: bare `.ord` · title + `.plan-line` summary · a **static** 18dp `.chev` glyph —
 * not a button, not rotating. Open: the summary and the chevron are both **absent**; the
 * rows are the content and the affordance disappears. The whole `.chead` stays the tap
 * target either way.
 *
 * ## Skipped
 *
 * The past mockup does not draw a skipped card; the session screen's treatment is the
 * sibling (extraction §1.5): 0.5 alpha, title struck through in `textTertiary`, and the
 * plan-line replaced by the literal `пропущено`. The v2.4 warning-tinted "Skipped" chip is
 * retired with it.
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
    // ReorderableColumnState rather than ReorderableLazyColumn — the parent screen is
    // already a LazyColumn, so nesting another lazy scroller would break layout. The
    // non-lazy variant registers each row's measured Y bounds via onGloballyPositioned
    // and resolves the drop target against those bounds.
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
            modifier = Modifier.width(OrdinalWidth),
            text = (exercise.position + 1).toString(),
            style = AppUi.typography.mono.meta,
            color = AppUi.colors.textDim,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.exerciseName,
                style = AppUi.typography.text.body.copy(
                    fontWeight = FontWeight.SemiBold,
                    // Strikethrough shares the text colour; the mockup's separate
                    // `text-decoration-color: --dim` has no Compose equivalent on one Text.
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
            // `.plan-line` exists only on the collapsed card (§2.5): open, the rows below say
            // the same thing in full. A skipped card states the fact instead of a summary.
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
            // `.chev` — a bare 18dp glyph in `textDim`, `margin-top:4px`. Not a button and it
            // never rotates; the open card simply does not draw it. Decorative: the whole
            // header is the toggle target, so the glyph carries no semantics of its own.
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

/** `.cbody > .sets{padding:0 12px 8px}`, rows split by `--hair` rules drawn by the container. */
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
                        onWeightChange = { raw -> onWeightChange(set.setUuid, raw) },
                        onRepsChange = { raw -> onRepsChange(set.setUuid, raw) },
                        onPrTagClick = onPrTagClick,
                        modifier = Modifier.reorderableColumnItem(
                            state = reorderState,
                            key = set.setUuid,
                            index = index,
                        ),
                        // Long-press the trailing drag-handle icon to start a drag.
                        // Skipped exercises stay read-only — handle is rendered for
                        // visual consistency but the gesture detector is disabled so a
                        // mis-targeted long-press cannot rewrite the historical record.
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

/** `.chead .ord { width: 16px }` — fixed so field columns align across ordinals 1-9 and 10+. */
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
