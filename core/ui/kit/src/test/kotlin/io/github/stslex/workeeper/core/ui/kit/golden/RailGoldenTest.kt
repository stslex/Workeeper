// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.rail.AppProgressRail
import io.github.stslex.workeeper.core.ui.kit.components.rail.RailGroup
import io.github.stslex.workeeper.core.ui.kit.components.rail.RailSegment
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The §8 degradation ladder, rendered.
 *
 * The four exercise x set combinations are the mockup's own degradation toggles. **Measured:
 * at the golden's 392dp width they reach only two of the three levels** — 2x4 and 5x4 are
 * SETS, and both 8x4 and 16x5 are EXERCISES. That is not an artefact of the golden canvas;
 * the same four land the same way at the mockup's own 412px rail width. Reaching OVERALL at
 * full width would take 24 exercises, which is past any real session.
 *
 * So OVERALL is a narrow-width state, and [narrowRailIsOverall] covers it by constraining the
 * rail rather than by inflating the data — which is also the honest picture of what that level
 * is for. `RailDetailTest` pins the levels themselves; these prove what they look like.
 */
internal class RailGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun railTwoByFour(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { Rail(rail(exercises = 2, sets = 4, filled = 3)) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun railFiveByFour(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { Rail(rail(exercises = 5, sets = 4, filled = 9)) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun railEightByFour(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { Rail(rail(exercises = 8, sets = 4, filled = 14)) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun railSixteenByFive(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { Rail(rail(exercises = 16, sets = 5, filled = 33)) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun narrowRailIsOverall(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) {
            Box(modifier = Modifier.padding(AppDimension.Space.lg)) {
                Box(modifier = Modifier.width(NARROW_RAIL_WIDTH)) {
                    AppProgressRail(groups = rail(exercises = 16, sets = 5, filled = 33))
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun railWithSkippedAndRecord(theme: GoldenTheme, testInfo: TestInfo) {
        // A skipped group renders as an outline rather than an unfilled track — the two must
        // not look alike — and a record segment resolves to `molten` rather than `max` (§9).
        goldenSubject(testInfo, theme) {
            Rail(
                listOf(
                    group(sets = 4, filled = 4),
                    group(sets = 4, filled = 2, recordAt = 1),
                    group(sets = 4, filled = 0, skipped = true),
                ).toImmutableList(),
            )
        }
    }
    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun railWithOneOffUnderline(theme: GoldenTheme, testInfo: TestInfo) {
        // `.grp.temp::after` — the dashed `dim` underline beneath a one-off group (§6.2),
        // finally read from `RailGroup.isOneOff` (the flag was carried and never rendered).
        // The underline draws 4dp BELOW the band (in the railmeta gap on screen); the
        // shrink canvas needs that room reserved or it clips the very thing under test.
        goldenSubject(testInfo, theme) {
            Box(modifier = Modifier.padding(bottom = 8.dp)) {
                AppProgressRail(
                    groups = kotlinx.collections.immutable.persistentListOf(
                        RailGroup(
                            segments = kotlinx.collections.immutable.persistentListOf(
                                RailSegment(isFilled = true),
                                RailSegment(isFilled = false),
                            ),
                        ),
                        RailGroup(
                            segments = kotlinx.collections.immutable.persistentListOf(
                                RailSegment(isFilled = true),
                                RailSegment(isFilled = false),
                            ),
                            isOneOff = true,
                        ),
                    ),
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun Rail(groups: ImmutableList<RailGroup>) {
    Box(modifier = Modifier.padding(AppDimension.Space.lg)) {
        AppProgressRail(groups = groups)
    }
}

/** Matches `RailDetailTest.NARROW_WIDTH`; the two must move together or the set drifts. */
private val NARROW_RAIL_WIDTH = 120.dp

private fun rail(exercises: Int, sets: Int, filled: Int): ImmutableList<RailGroup> {
    var remaining = filled
    return (0 until exercises).map {
        val take = minOf(remaining, sets)
        remaining -= take
        group(sets = sets, filled = take)
    }.toImmutableList()
}

private fun group(
    sets: Int,
    filled: Int,
    skipped: Boolean = false,
    recordAt: Int? = null,
): RailGroup = RailGroup(
    segments = (0 until sets).map { index ->
        RailSegment(isFilled = index < filled, isRecord = index == recordAt)
    }.toImmutableList(),
    isSkipped = skipped,
)
