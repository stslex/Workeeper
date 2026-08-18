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

/**
 * R14's alignment assertion (set-field-column-headers.md §7a), in the layoutlib stack.
 *
 * The header label's left edge equals its column's value left edge iff the header's index
 * GUTTER equals the row's index COLUMN — the gap and the label inset on both sides are the
 * same tokens (`Space.sm`, `SetRowGeometry.compactFieldInset`), so the gutter is the one
 * free variable. This gate captures both sides' laid-out widths through test-only size
 * probes and asserts them equal at ONE set and at TEN sets — the case where a hardcoded
 * header gutter drifts from rows whose index text has outgrown the 12dp minimum.
 *
 * Layoutlib, not Robolectric, deliberately: measured, Robolectric's font stack lays a
 * three-digit `mono.meta` index under the 12dp minimum, so the gutter never grows there
 * and a bounds-based assert passes vacuously (the first cut of this test did exactly
 * that). Layoutlib measures "10" at ~15dp and the drift scenario is real. The growth
 * precondition below turns any stack that cannot reproduce growth into a loud failure.
 *
 * `SetsColumn`'s wiring (resolving once and passing the same value to header and rows) is
 * pinned by the `exerciseTenSets` canary golden; this gate pins the components' geometry
 * contract itself.
 */
internal class SetColumnAlignmentGateTest {

    @Test
    fun headerGutterEqualsRowIndexColumn() {
        val gate = OverflowGateSdk()
        gate.setup()
        val samples = mutableListOf<Sample>()
        try {
            for (setCount in SET_COUNTS) {
                var gutterPx = -1
                var indexPx = -1
                gate.render {
                    AlignedPair(
                        setCount = setCount,
                        onGutter = { gutterPx = it },
                        onIndexColumn = { indexPx = it },
                    )
                }
                check(gutterPx >= 0) { "header gutter probe never fired at $setCount sets" }
                check(indexPx >= 0) { "row index probe never fired at $setCount sets" }
                samples += Sample(setCount, gutterPx, indexPx)
            }
        } finally {
            gate.teardown()
        }
        samples.forEach {
            println("alignment: ${it.setCount} sets -> gutter ${it.gutterPx}px, index ${it.indexPx}px")
        }
        check(samples.size == SET_COUNTS.size) { "gate ran over ${samples.size} counts" }
        // Precondition: the ten-set row's index column must actually outgrow the one-set
        // minimum in this stack, or the drift scenario is unreachable and a pass is vacuous.
        check(samples.last().indexPx > samples.first().indexPx) {
            "index column did not grow (${samples.first().indexPx} -> ${samples.last().indexPx}px)" +
                " — this stack cannot reproduce the drift scenario"
        }
        assertAll(
            samples.map { sample ->
                {
                    assertEquals(
                        sample.indexPx,
                        sample.gutterPx,
                        "at ${sample.setCount} sets the header gutter (${sample.gutterPx}px) " +
                            "drifted from the row index column (${sample.indexPx}px)",
                    )
                }
            },
        )
        assertTrue(samples.isNotEmpty())
    }

    /**
     * The header and the WIDEST row (the last: its index label is the set count itself)
     * exactly as `SetsColumn` composes them: one resolution, both consumers.
     */
    @Composable
    private fun AlignedPair(
        setCount: Int,
        onGutter: (Int) -> Unit,
        onIndexColumn: (Int) -> Unit,
    ) {
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
                    indexGutterProbe = onGutter,
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
                    indexColumnProbe = onIndexColumn,
                )
            }
        }
    }

    private data class Sample(val setCount: Int, val gutterPx: Int, val indexPx: Int)

    private companion object {
        val SET_COUNTS = listOf(1, 10)
    }
}
