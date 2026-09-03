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
 * The rail degradation ladder, rendered. OVERALL is only reachable at narrow widths, so
 * [narrowRailIsOverall] constrains the rail. `RailDetailTest` pins the levels themselves.
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
        // A skipped group must not look like an unfilled track; a record segment reads molten.
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
        // GUARD: the one-off underline draws below the band; without the bottom padding the
        // shrink canvas clips the thing under test.
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
