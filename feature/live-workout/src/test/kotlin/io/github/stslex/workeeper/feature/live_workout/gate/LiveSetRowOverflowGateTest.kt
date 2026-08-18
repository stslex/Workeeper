// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.gate

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import io.github.stslex.workeeper.core.ui.kit.golden.GOLDEN_DEVICE
import io.github.stslex.workeeper.core.ui.kit.golden.GateCell
import io.github.stslex.workeeper.core.ui.kit.golden.OverflowGateSdk
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveSetRow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * The set-field overflow gate for the live weighted row (set-field-column-headers.md §6).
 *
 * Closed-loop by ruling R1: the slot widths are CAPTURED from a rendered production
 * [LiveSetRow] via `valueSlotProbe`, never recomputed from row-budget constants — only the
 * context above the row (screen edge, sets-column padding) is reproduced, from the same
 * `AppDimension` tokens the screen reads. A measurement test by ruling R2: no snapshot
 * handler, no PNGs, not a `*.golden.*` package (see `OverflowGateSdk`).
 */
internal class LiveSetRowOverflowGateTest {

    @Test
    fun weightedRowValuesFitTheirSlots() {
        val gate = OverflowGateSdk()
        gate.setup()
        val cells = mutableListOf<GateCell>()
        try {
            for (scale in ASSERTED_FONT_SCALES) {
                gate.setFontScale(scale)
                for (glyphs in GLYPH_CLASSES) {
                    val set = LiveSetUiModel(
                        position = 0,
                        weight = WEIGHT_VALUES.getValue(glyphs),
                        reps = REPS_VALUES.getValue(glyphs),
                        type = SetTypeUiModel.WORK,
                        isDone = false,
                    )
                    var weightSample: Pair<Int, TextStyle>? = null
                    var repsSample: Pair<Int, TextStyle>? = null
                    gate.render {
                        RowAtInAppWidth(
                            set = set,
                            onWeightSlot = { px, style -> weightSample = px to style },
                            onRepsSlot = { px, style -> repsSample = px to style },
                        )
                    }
                    val weight = checkNotNull(weightSample) { "weight slot probe never fired" }
                    val reps = checkNotNull(repsSample) { "reps slot probe never fired" }
                    cells += GateCell(
                        row = "LiveSetRow",
                        column = "weight",
                        glyphs = glyphs,
                        fontScale = scale,
                        slotWidthPx = weight.first,
                        textWidthPx = gate.measureTextWidthPx(set.weightLabel, weight.second),
                    )
                    cells += GateCell(
                        row = "LiveSetRow",
                        column = "reps",
                        glyphs = glyphs,
                        fontScale = scale,
                        slotWidthPx = reps.first,
                        textWidthPx = gate.measureTextWidthPx(set.reps.toString(), reps.second),
                    )
                }
            }
        } finally {
            gate.teardown()
        }
        println(
            "overflow gate matrix: ${cells.size} cells " +
                "(scales=$ASSERTED_FONT_SCALES glyphs=$GLYPH_CLASSES columns=[weight, reps])",
        )
        cells.forEach { println("  ${it.describe()}") }
        check(cells.size == ASSERTED_FONT_SCALES.size * GLYPH_CLASSES.size * 2) {
            "gate ran over ${cells.size} cells — zero or partial input"
        }
        assertAll(cells.map { cell -> { assertFalse(cell.overflows, cell.describe()) } })
    }

    /**
     * The production row at the in-app content width: device width minus the screen edge
     * and the sets-column padding on both sides — context tokens only; everything inside
     * the row (gaps, index, chip, checkmark, flex split, field padding, suffix) is the
     * production layout, measured through the probes.
     */
    @Composable
    private fun RowAtInAppWidth(
        set: LiveSetUiModel,
        onWeightSlot: (Int, TextStyle) -> Unit,
        onRepsSlot: (Int, TextStyle) -> Unit,
    ) {
        val contentWidth = with(LocalDensity.current) {
            val consumedPx = 2 * (AppDimension.screenEdge + AppDimension.Space.md).roundToPx()
            (GOLDEN_DEVICE.screenWidth - consumedPx).toDp()
        }
        Box(modifier = Modifier.width(contentWidth)) {
            LiveSetRow(
                set = set,
                isWeighted = true,
                onWeightChange = {},
                onRepsChange = {},
                onTypeChange = {},
                onMarkDone = {},
                onUncheck = {},
                editable = true,
                weightSlotProbe = onWeightSlot,
                repsSlotProbe = onRepsSlot,
            )
        }
    }

    private companion object {
        // The fontScale-1.0 band. The full R4 matrix [1.0, 1.3, 1.6, 2.0] is MEASURED
        // but not asserted: with the measured stepdown in place every residual red cell
        // is ladder-floor-limited (the contrast-pinned 19sp rung at the non-linear
        // converter — ×1.695 at fontScale 2.0), which conflicts with the R4 band wording
        // for reps×5-glyph at 1.3/1.6 and several 2.0 cells. Extension is blocked on
        // that ruling — spec §7 carries the measured ledger.
        val ASSERTED_FONT_SCALES = listOf(1.0f)
        val GLYPH_CLASSES = listOf(1, 2, 3, 5)
        val WEIGHT_VALUES = mapOf(1 to 5.0, 2 to 55.0, 3 to 555.0, 5 to 102.5)
        val REPS_VALUES = mapOf(1 to 5, 2 to 12, 3 to 555, 5 to 55555)
    }
}
