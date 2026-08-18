// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.gate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import io.github.stslex.workeeper.core.ui.kit.components.button.AppCheckmarkButtonTouchSize
import io.github.stslex.workeeper.core.ui.kit.components.setrow.SetColumnHeader
import io.github.stslex.workeeper.core.ui.kit.components.setrow.SetRowGeometry
import io.github.stslex.workeeper.core.ui.kit.golden.GOLDEN_DEVICE
import io.github.stslex.workeeper.core.ui.kit.golden.OverflowGateSdk
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveSetRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.math.abs

/**
 * R14/R17's alignment assertion (set-field-column-headers.md §7a), in the layoutlib stack.
 *
 * TWO claims, both necessary:
 *  - EDGES (R17, the contract itself): each header label's rendered left edge equals its
 *    column's value left edge. Width equality alone was falsified inside this PR — the R9
 *    threshold moved the field inset to 8dp while the header label stayed at 12dp, and
 *    every width stayed equal through the 4dp drift. Only the edges see that class.
 *  - GUTTER (R14): the header's index gutter equals the row's index column — necessary
 *    and not sufficient; kept because a gutter drift at 10+ sets moves every edge at once
 *    and this assert names the culprit directly.
 *
 * Asserted at ONE set and at TEN sets — the count where the index text outgrows the 12dp
 * minimum. The drift scenarios are REAL in this stack and in production: layoutlib lays
 * "10" at ~15dp (0.6em × 12.5sp arithmetic agrees). The first cut of this test ran under
 * Robolectric and passed vacuously — an instrument defect, not a font fact (it laid a
 * 3-digit index 10.5dp under the same arithmetic, i.e. it did not render Plex Mono at
 * all); the growth precondition below turns a defective instrument into a loud failure.
 *
 * Both known-negatives are proven in the PR record: a hardcoded 12dp header gutter reds
 * the gutter assert at 10 sets; a header label inset diverging from the rows' reds the
 * edge assert with widths untouched.
 */
internal class SetColumnAlignmentGateTest {

    @Test
    fun headerLabelsSitOnTheirColumns() {
        val gate = OverflowGateSdk()
        gate.setup()
        val samples = mutableListOf<Sample>()
        try {
            for (setCount in SET_COUNTS) {
                val capture = Capture()
                gate.render { AlignedPair(setCount = setCount, capture = capture) }
                samples += capture.toSample(setCount)
            }
        } finally {
            gate.teardown()
        }
        samples.forEach {
            println(
                "alignment: ${it.setCount} sets -> gutter ${it.gutterPx}px / index ${it.indexPx}px, " +
                    "weight label ${it.weightLabelLeft} vs field ${it.weightFieldLeft}, " +
                    "reps label ${it.repsLabelLeft} vs field ${it.repsFieldLeft}",
            )
        }
        check(samples.size == SET_COUNTS.size) { "gate ran over ${samples.size} counts" }
        // Precondition: the ten-set index column must outgrow the one-set minimum in this
        // stack, or the gutter-drift scenario is unreachable here and a pass is vacuous.
        check(samples.last().indexPx > samples.first().indexPx) {
            "index column did not grow (${samples.first().indexPx} -> ${samples.last().indexPx}px)" +
                " — defective instrument, not a passing gate"
        }
        assertAll(
            samples.flatMap { s ->
                listOf(
                    {
                        assertEquals(
                            s.indexPx,
                            s.gutterPx,
                            "at ${s.setCount} sets the header gutter (${s.gutterPx}px) " +
                                "drifted from the row index column (${s.indexPx}px)",
                        )
                    },
                    {
                        assertTrue(
                            abs(s.weightLabelLeft - s.weightFieldLeft) <= EDGE_TOLERANCE_PX,
                            "at ${s.setCount} sets the WEIGHT label left (${s.weightLabelLeft}px) " +
                                "drifted from its value left (${s.weightFieldLeft}px)",
                        )
                    },
                    {
                        assertTrue(
                            abs(s.repsLabelLeft - s.repsFieldLeft) <= EDGE_TOLERANCE_PX,
                            "at ${s.setCount} sets the REPS label left (${s.repsLabelLeft}px) " +
                                "drifted from its value left (${s.repsFieldLeft}px)",
                        )
                    },
                )
            },
        )
    }

    /**
     * The header and the WIDEST row (the last: its index label is the set count itself)
     * exactly as `SetsColumn` composes them: one resolution, both consumers.
     */
    @Composable
    private fun AlignedPair(setCount: Int, capture: Capture) {
        val contentWidth = with(LocalDensity.current) {
            val consumedPx = 2 * (AppDimension.screenEdge + AppDimension.Space.md).roundToPx()
            (GOLDEN_DEVICE.screenWidth - consumedPx).toDp()
        }
        Box(modifier = Modifier.width(contentWidth)) {
            Column {
                val indexColumnWidth = SetRowGeometry.resolveIndexColumnWidth(setCount)
                SetColumnHeader(
                    isWeighted = true,
                    indexColumnWidth = indexColumnWidth,
                    trailingWidth = SetRowGeometry.setTypeSlotWidth +
                        AppDimension.Space.sm +
                        AppCheckmarkButtonTouchSize,
                    indexGutterProbe = { capture.gutterPx = it },
                    labelLeftProbe = { cell, x ->
                        if (cell == 0) capture.weightLabelLeft = x else capture.repsLabelLeft = x
                    },
                )
                LiveSetRow(
                    set = LiveSetUiModel(
                        position = setCount - 1,
                        weight = 100.0,
                        reps = 5,
                        type = SetTypeUiModel.WORK,
                        isDone = false,
                    ),
                    isWeighted = true,
                    onWeightChange = {},
                    onRepsChange = {},
                    onTypeChange = {},
                    onMarkDone = {},
                    onUncheck = {},
                    editable = true,
                    indexColumnWidth = indexColumnWidth,
                    indexColumnProbe = { capture.indexPx = it },
                    weightLeftProbe = { capture.weightFieldLeft = it },
                    repsLeftProbe = { capture.repsFieldLeft = it },
                )
            }
        }
    }

    private class Capture {
        var gutterPx: Int = -1
        var indexPx: Int = -1
        var weightLabelLeft: Float = Float.NaN
        var repsLabelLeft: Float = Float.NaN
        var weightFieldLeft: Float = Float.NaN
        var repsFieldLeft: Float = Float.NaN

        fun toSample(setCount: Int): Sample {
            check(gutterPx >= 0) { "header gutter probe never fired at $setCount sets" }
            check(indexPx >= 0) { "row index probe never fired at $setCount sets" }
            check(!weightLabelLeft.isNaN()) { "weight label probe never fired at $setCount sets" }
            check(!repsLabelLeft.isNaN()) { "reps label probe never fired at $setCount sets" }
            check(!weightFieldLeft.isNaN()) { "weight field probe never fired at $setCount sets" }
            check(!repsFieldLeft.isNaN()) { "reps field probe never fired at $setCount sets" }
            return Sample(
                setCount = setCount,
                gutterPx = gutterPx,
                indexPx = indexPx,
                weightLabelLeft = weightLabelLeft,
                repsLabelLeft = repsLabelLeft,
                weightFieldLeft = weightFieldLeft,
                repsFieldLeft = repsFieldLeft,
            )
        }
    }

    private data class Sample(
        val setCount: Int,
        val gutterPx: Int,
        val indexPx: Int,
        val weightLabelLeft: Float,
        val repsLabelLeft: Float,
        val weightFieldLeft: Float,
        val repsFieldLeft: Float,
    )

    private companion object {
        val SET_COUNTS = listOf(1, 10)

        /** Two independent Rows accumulate px rounding; beyond one step is a drift. */
        const val EDGE_TOLERANCE_PX = 1.5f
    }
}
