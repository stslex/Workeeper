// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppCheckmarkButton
import io.github.stslex.workeeper.core.ui.kit.components.button.SetDoneMark
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The done-marker, as a §10.2 transient **pair** plus the gate that a pair alone cannot carry.
 *
 * ## Why three cases and not two
 *
 * `markRest` and `markMidTransition` are the pair: at rest a 2dp-ringed circle with no tick, and
 * part-way through, a part-grown squircle with a *partly stroked* tick. A lone mid-transition
 * golden would assert nothing — the frame of a morph that plays correctly and the frame of one
 * that plays always are the same frame.
 *
 * `markDone` and `markDoneRecord` are the other half, and they are deliberately driven through
 * the REAL [AppCheckmarkButton] rather than the stateless mark. Paparazzi renders a single frame
 * of a fresh composition, so a component whose tick animation fired on first composition would be
 * caught here mid-stroke or undrawn. A fully drawn tick in this image is the assertion that the
 * stroke is gated to the false->true transition — the §10.2 defect that already shipped once in
 * this arc, stated as a picture rather than as a comment.
 *
 * The stateless [SetDoneMark] exists precisely so the middle frame is reachable: a transient can
 * only be captured deterministically if the frame is an argument.
 */
internal class SetDoneMarkGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun markRest(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            Specimen {
                SetDoneMark(
                    closedFraction = 0f,
                    tickProgress = 0f,
                    fill = Color.Transparent,
                    ring = AppUi.colors.borderStrong,
                    tick = AppUi.colors.onAccent,
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun markMidTransition(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            Specimen {
                val plate = AppUi.colors.accent
                SetDoneMark(
                    closedFraction = MID_GEOMETRY,
                    // Behind the geometry by the mockup's own 60ms delay: at this point in the
                    // morph the plate is filling and the tick has only started.
                    tickProgress = MID_TICK,
                    fill = lerp(Color.Transparent, plate, MID_GEOMETRY),
                    ring = lerp(AppUi.colors.borderStrong, plate, MID_GEOMETRY),
                    tick = AppUi.colors.onAccent,
                )
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun markDone(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            Specimen {
                AppCheckmarkButton(isDone = true, enabled = true, onToggle = {})
            }
        }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun markDoneRecord(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme, surface = { AppUi.colors.surfaceTier1 }) {
            Specimen {
                AppCheckmarkButton(isDone = true, enabled = true, isRecord = true, onToggle = {})
            }
        }
    }
}

@Composable
private fun Specimen(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(AppDimension.Space.lg)) { content() }
}

/** Part-way through the morph: grown, rounded, not yet a plate. */
private const val MID_GEOMETRY = 0.5f

/** The tick trails the plate by the 60ms delay, so it is barely started at the halfway point. */
private const val MID_TICK = 0.3f
