// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.rail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.border.dashedBorder
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The session progress rail: one band of segments that degrades sets → exercises → overall by
 * available width. See documentation/feature-specs/v3-redesign-spec.md §8.
 */
@Composable
fun AppProgressRail(
    groups: ImmutableList<RailGroup>,
    modifier: Modifier = Modifier,
    meta: (@Composable (RailDetail) -> Unit)? = null,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val detail = RailDetail.resolve(availableWidth = maxWidth, groups = groups)
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(RAIL_HEIGHT)) {
                when (detail) {
                    RailDetail.SETS -> SegmentedRail(groups = groups, groupGap = GROUP_GAP_SETS)
                    RailDetail.EXERCISES -> SegmentedRail(
                        groups = groups.map { group -> group.collapsedToOneSegment() }
                            .toImmutableList(),
                        groupGap = GROUP_GAP_EXERCISES,
                    )

                    RailDetail.OVERALL -> OverallRail(groups = groups)
                }
            }
            // The slot receives the RESOLVED detail, so the label cannot disagree with the band.
            meta?.let { metaContent ->
                Spacer(modifier = Modifier.height(AppDimension.Space.md))
                metaContent(detail)
            }
        }
    }
}

/**
 * One exercise's worth of rail; [segments] are its sets, in order. [isSkipped] drops the group
 * from every progress denominator and renders an outline; [isOneOff] changes only the marker.
 */
@Immutable
data class RailGroup(
    val segments: ImmutableList<RailSegment>,
    val isSkipped: Boolean = false,
    val isOneOff: Boolean = false,
) {

    internal fun collapsedToOneSegment(): RailGroup = copy(
        segments = persistentListOf(
            RailSegment(
                // A collapsed group is filled only when every set in it is.
                isFilled = segments.isNotEmpty() && segments.all { it.isFilled },
                isRecord = segments.any { it.isRecord },
            ),
        ),
    )
}

/** One set. [isRecord] resolves the fill to `molten` instead of `max` (§9). */
@Immutable
data class RailSegment(
    val isFilled: Boolean,
    val isRecord: Boolean = false,
)

/** The three detail levels of §8, most detailed first. */
enum class RailDetail {
    SETS,
    EXERCISES,
    OVERALL,
    ;

    companion object {

        /**
         * Picks the most detailed level whose segments still meet their minimum width. Skipped
         * groups are outside the set-level arithmetic but still occupy an exercise-level slot.
         */
        fun resolve(availableWidth: Dp, groups: List<RailGroup>): RailDetail {
            if (groups.isEmpty()) return OVERALL
            val active = groups.filterNot { it.isSkipped }
            val groupCount = active.size.coerceAtLeast(1)
            val segmentCount = active.sumOf { it.segments.size }.coerceAtLeast(1)

            val setsGaps = GROUP_GAP_SETS * (groupCount - 1) +
                SEGMENT_GAP * (segmentCount - groupCount)
            if ((availableWidth - setsGaps) / segmentCount >= MIN_SEGMENT_WIDTH) return SETS

            val exerciseGaps = GROUP_GAP_EXERCISES * (groups.size - 1)
            if ((availableWidth - exerciseGaps) / groups.size >= MIN_GROUP_WIDTH) return EXERCISES

            return OVERALL
        }
    }
}

@Composable
private fun SegmentedRail(groups: ImmutableList<RailGroup>, groupGap: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(),
        horizontalArrangement = Arrangement.spacedBy(groupGap),
    ) {
        groups.forEach { group ->
            // `.grp.temp::after` — dashed underline under a one-off group; skip wins over temp.
            val oneOffUnderline = group.isOneOff && !group.isSkipped
            val underline = AppUi.colors.textDim
            Row(
                modifier = Modifier
                    .weight(group.segments.size.coerceAtLeast(1).toFloat())
                    .let { base ->
                        if (oneOffUnderline) {
                            base.drawBehind {
                                val y = size.height + ONE_OFF_UNDERLINE_OFFSET.toPx()
                                drawLine(
                                    color = underline,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = ONE_OFF_UNDERLINE_STROKE.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(
                                        floatArrayOf(DASH_ON_PX.toPx(), DASH_OFF_PX.toPx()),
                                    ),
                                )
                            }
                        } else {
                            base
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(SEGMENT_GAP),
            ) {
                group.segments.forEach { segment ->
                    Segment(
                        segment = segment,
                        isSkipped = group.isSkipped,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun OverallRail(groups: ImmutableList<RailGroup>) {
    val active = groups.filterNot { it.isSkipped }.flatMap { it.segments }
    val filled = active.count { it.isFilled }
    val fraction by animateFloatAsState(
        targetValue = filled.toFloat() / active.size.coerceAtLeast(1),
        animationSpec = tween(durationMillis = RAIL_FILL_MS, easing = AppUi.motion.out),
        label = "rail-overall",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(SEGMENT_SHAPE)
            .background(AppUi.colors.surfaceTier4),
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .clip(SEGMENT_SHAPE)
                    // Always `max`, never molten: at overall granularity a record is not
                    // attributable to a segment.
                    .background(AppUi.colors.accent),
            )
        }
    }
}

@Composable
private fun Segment(segment: RailSegment, isSkipped: Boolean, modifier: Modifier = Modifier) {
    if (isSkipped) {
        // A skipped exercise is outside the denominator, so it reads as a dashed outline.
        Box(
            modifier = modifier
                .fillMaxHeight()
                .dashedBorder(
                    color = AppUi.colors.borderDefault,
                    cornerRadius = AppDimension.Radius.smallest,
                ),
        )
        return
    }
    // 420ms is a fourth duration outside the motion tokens, kept as drawn.
    val fillFraction by animateFloatAsState(
        targetValue = if (segment.isFilled) 1f else 0f,
        animationSpec = tween(durationMillis = RAIL_FILL_MS, easing = AppUi.motion.out),
        label = "rail-fill",
    )
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(SEGMENT_SHAPE)
            .background(AppUi.colors.surfaceTier4),
    ) {
        if (fillFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFraction)
                    .fillMaxHeight()
                    .clip(SEGMENT_SHAPE)
                    .background(
                        if (segment.isRecord) {
                            AppUi.colors.molten.solid
                        } else {
                            AppUi.colors.accent
                        },
                    ),
            )
        }
    }
}

/** Band height. 9px as drawn; not a rung on the ladder, kept rather than rounded down to 8dp. */
private val RAIL_HEIGHT: Dp = 9.dp

/** §8: 12px between exercise groups at set-level detail. Exact rung — `AppDimension.Space.md`. */
private val GROUP_GAP_SETS: Dp = AppDimension.Space.md

/** Groups sit closer once they are single segments, so the band still reads as one band. */
private val GROUP_GAP_EXERCISES: Dp = 6.dp

/** Between sets inside one exercise — tighter than the group gap, so grouping stays legible. */
private val SEGMENT_GAP: Dp = 3.dp

/**
 * UNVERIFIED — eyeballed in a desktop browser; drives the sets → exercises boundary.
 * See documentation/feature-specs/v3-step5-device-checklist.md.
 */
private val MIN_SEGMENT_WIDTH: Dp = 9.dp

/** UNVERIFIED — the exercises → overall boundary; same provenance as [MIN_SEGMENT_WIDTH]. */
private val MIN_GROUP_WIDTH: Dp = 11.dp

private val SEGMENT_SHAPE = RoundedCornerShape(AppDimension.Radius.smallest)

/** `.pill b{transition:width 420ms}` — outside the three motion tokens. */
private const val RAIL_FILL_MS = 420

/** `.grp.temp::after{bottom:-5px}` → 4dp below the band, on the ladder. */
private val ONE_OFF_UNDERLINE_OFFSET: Dp = 4.dp

private val ONE_OFF_UNDERLINE_STROKE: Dp = 1.dp

private val DASH_ON_PX: Dp = 3.dp

private val DASH_OFF_PX: Dp = 3.dp

@Preview
@Composable
private fun AppProgressRailSetsPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Box(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            AppProgressRail(groups = previewGroups(exercises = 2, sets = 4, filled = 3))
        }
    }
}

@Preview
@Composable
private fun AppProgressRailOverallPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Box(modifier = Modifier.background(AppUi.colors.surfaceTier0)) {
            AppProgressRail(groups = previewGroups(exercises = 16, sets = 5, filled = 20))
        }
    }
}

internal fun previewGroups(exercises: Int, sets: Int, filled: Int): ImmutableList<RailGroup> {
    var remaining = filled
    return (0 until exercises).map {
        RailGroup(
            segments = (0 until sets).map {
                val isFilled = remaining > 0
                if (isFilled) remaining--
                RailSegment(isFilled = isFilled)
            }.toImmutableList(),
        )
    }.toImmutableList()
}
