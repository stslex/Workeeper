// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.rail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The session progress rail (v3 §8, §14).
 *
 * One horizontal band of segments that fills as the session is completed. It degrades by
 * available width through three levels — **sets → exercises → overall** — so the band never
 * becomes a row of slivers on a narrow screen or at a large font scale.
 *
 * ### Why the rule lives inside this component
 *
 * §8 requires `BoxWithConstraints` here rather than a `WindowSizeClass` computed at screen
 * level, for two reasons that are not interchangeable:
 *
 * - the question is local — do N segments fit in **this** rail — which is width at the layout
 *   point, not a property of the window;
 * - `MainActivity` absorbs `fontScale` without recreating the Activity, so a value computed
 *   once at screen level goes stale on a font-scale change while a value computed at the
 *   layout point does not.
 *
 * §8 also requires this to be exactly **one** component: "copies mean a drifting threshold".
 * The decision itself is [RailDetail.resolve], a pure function with no Compose dependency, so
 * it can be tested directly at every boundary instead of only through a screenshot — but it
 * has exactly one caller, which is this file.
 */
@Composable
fun AppProgressRail(
    groups: ImmutableList<RailGroup>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(RAIL_HEIGHT)) {
        val detail = RailDetail.resolve(availableWidth = maxWidth, groups = groups)
        when (detail) {
            RailDetail.SETS -> SegmentedRail(groups = groups, groupGap = GROUP_GAP_SETS)
            RailDetail.EXERCISES -> SegmentedRail(
                groups = groups.map { group -> group.collapsedToOneSegment() }.toImmutableList(),
                groupGap = GROUP_GAP_EXERCISES,
            )

            RailDetail.OVERALL -> OverallRail(groups = groups)
        }
    }
}

/**
 * One exercise's worth of rail. [segments] are its sets, in order.
 *
 * [isSkipped] excludes the group from every progress denominator (§6.1) and renders it as an
 * outline rather than a track. [isOneOff] marks a non-plan-attached exercise (§6.2); the work
 * is real and counts, so the segments render normally and only the marker differs.
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
                // A collapsed group reports the exercise as filled only when every set in it
                // is — a partially-done exercise reads as not-yet-done at this detail level,
                // which is the honest reading of "exercises" granularity.
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
         * Picks the most detailed level whose segments still meet their minimum width.
         *
         * Ported from the mockup's own `railMode()`, which is the only executable statement of
         * this rule that exists. Skipped groups are excluded from the set-level arithmetic
         * because they render as outlines rather than as tracks, but they still occupy a slot
         * at exercise level — which is why the two branches count different things.
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
            Row(
                modifier = Modifier.weight(group.segments.size.coerceAtLeast(1).toFloat()),
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .clip(SEGMENT_SHAPE)
            .background(AppUi.colors.surfaceTier4),
    ) {
        if (filled > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(filled.toFloat() / active.size.coerceAtLeast(1))
                    .fillMaxHeight()
                    .clip(SEGMENT_SHAPE)
                    .background(
                        if (active.any { it.isFilled && it.isRecord }) {
                            AppUi.colors.molten.solid
                        } else {
                            AppUi.colors.accent
                        },
                    ),
            )
        }
    }
}

@Composable
private fun Segment(segment: RailSegment, isSkipped: Boolean, modifier: Modifier = Modifier) {
    val base = modifier.fillMaxHeight().clip(SEGMENT_SHAPE)
    if (isSkipped) {
        // A skipped exercise is outside the denominator, so it reads as an empty outline
        // rather than as an unfilled track — the two must not look the same.
        Box(
            modifier = base.border(
                width = AppDimension.borderHairline,
                color = AppUi.colors.borderDefault,
                shape = SEGMENT_SHAPE,
            ),
        )
        return
    }
    Box(modifier = base.background(AppUi.colors.surfaceTier4)) {
        if (segment.isFilled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
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

/**
 * The rail's band height. §8 gives 9px; there is no 9dp rung on the `AppDimension` ladder and
 * §0.1's round-to-nearest would take it to 8dp. Kept at 9dp deliberately: the band is a
 * two-part shape (track plus fill) whose legibility is the whole point, and a 11% reduction is
 * a visual change rather than a rounding. Flagged for the same device check as
 * [MIN_SEGMENT_WIDTH].
 */
private val RAIL_HEIGHT: Dp = 9.dp

/** §8: 12px between exercise groups at set-level detail. Exact rung — `AppDimension.Space.md`. */
private val GROUP_GAP_SETS: Dp = AppDimension.Space.md

/** Groups sit closer once they are single segments, so the band still reads as one band. */
private val GROUP_GAP_EXERCISES: Dp = 6.dp

/** Between sets inside one exercise — tighter than the group gap, so grouping stays legible. */
private val SEGMENT_GAP: Dp = 3.dp

/**
 * **UNVERIFIED — measured in a desktop browser, never on a device.**
 *
 * The smallest segment that still reads as a segment rather than as a line. Taken from the
 * mockup's `railMode()`, where it was chosen by eye in Chrome at a desktop zoom level, on a
 * frame that is a viewport cap rather than a device width. Nothing here has been checked
 * against a real display, and the number drives the whole `sets → exercises` boundary: too
 * low and the rail degrades into slivers, too high and it drops detail that would have fit.
 *
 * **Waiting on:** the device checklist item "the 9dp segment threshold at each degradation
 * boundary, on a real device" — walk the 2x4 / 5x4 / 8x4 / 16x5 ladder on hardware at
 * fontScale 1.0 and 2.0 and record where the band stops being readable. Until that is done
 * this is a guess with a decimal point on it. Do not read it as measured.
 */
private val MIN_SEGMENT_WIDTH: Dp = 9.dp

/**
 * **UNVERIFIED — same provenance and same device check as [MIN_SEGMENT_WIDTH].**
 *
 * The `exercises → overall` boundary. Also from the mockup's `railMode()`. Higher than
 * [MIN_SEGMENT_WIDTH] because a group segment carries an entire exercise and a sliver reads as
 * noise rather than as information at that granularity — but the specific value has no more
 * evidence behind it than the other one does.
 */
private val MIN_GROUP_WIDTH: Dp = 11.dp

private val SEGMENT_SHAPE = RoundedCornerShape(AppDimension.Radius.smallest)

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
