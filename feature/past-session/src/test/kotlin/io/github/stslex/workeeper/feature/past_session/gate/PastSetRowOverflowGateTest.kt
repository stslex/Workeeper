// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.gate

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
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import io.github.stslex.workeeper.feature.past_session.ui.components.PastSetEditRow
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * The set-field overflow gate for the past-session weighted edit row
 * (set-field-column-headers.md §6). Same design as `LiveSetRowOverflowGateTest`:
 * closed-loop slot capture (R1), measurement-only harness (R2).
 */
internal class PastSetRowOverflowGateTest {

    @Test
    fun weightedRowValuesFitTheirSlots() {
        val gate = OverflowGateSdk()
        gate.setup()
        val cells = mutableListOf<GateCell>()
        try {
            for (scale in ASSERTED_FONT_SCALES) {
                gate.setFontScale(scale)
                for (glyphs in GLYPH_CLASSES) {
                    val set = PastSetUiModel(
                        setUuid = "gate-set",
                        performedExerciseUuid = "gate-exercise",
                        position = 0,
                        type = SetTypeUiModel.WORK,
                        weightInput = WEIGHT_INPUTS.getValue(glyphs),
                        repsInput = REPS_INPUTS.getValue(glyphs),
                        weightError = false,
                        repsError = false,
                        isPersonalRecord = false,
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
                        row = "PastSetEditRow",
                        column = "weight",
                        glyphs = glyphs,
                        fontScale = scale,
                        slotWidthPx = weight.first,
                        textWidthPx = gate.measureTextWidthPx(set.weightInput, weight.second),
                    )
                    cells += GateCell(
                        row = "PastSetEditRow",
                        column = "reps",
                        glyphs = glyphs,
                        fontScale = scale,
                        slotWidthPx = reps.first,
                        textWidthPx = gate.measureTextWidthPx(set.repsInput, reps.second),
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
        assertAll(
            cells.map { cell ->
                {
                    val limit = KNOWN_LIMITS["${cell.column}/${cell.glyphs}@${cell.fontScale}"]
                    if (limit != null) {
                        assertTrue(
                            cell.overflows,
                            "ledgered cell fits now ($limit) — update spec §7: ${cell.describe()}",
                        )
                    } else {
                        assertFalse(cell.overflows, cell.describe())
                    }
                }
            },
        )
    }

    /**
     * The production row at the in-app content width — the card body shares the live
     * screen's edge and card paddings (`PastSessionScreen.kt` screenEdge,
     * `PastExerciseCard.kt` CardBody `Space.md`), so the context arithmetic is identical.
     */
    @Composable
    private fun RowAtInAppWidth(
        set: PastSetUiModel,
        onWeightSlot: (Int, TextStyle) -> Unit,
        onRepsSlot: (Int, TextStyle) -> Unit,
    ) {
        val contentWidth = with(LocalDensity.current) {
            val consumedPx = 2 * (AppDimension.screenEdge + AppDimension.Space.md).roundToPx()
            (GOLDEN_DEVICE.screenWidth - consumedPx).toDp()
        }
        Box(modifier = Modifier.width(contentWidth)) {
            PastSetEditRow(
                set = set,
                isWeighted = true,
                onWeightChange = {},
                onRepsChange = {},
                onPrTagClick = {},
                weightSlotProbe = onWeightSlot,
                repsSlotProbe = onRepsSlot,
            )
        }
    }

    private companion object {
        // The full R11 matrix, asserted; see LiveSetRowOverflowGateTest's companion note.
        val ASSERTED_FONT_SCALES = listOf(1.0f, 1.3f, 1.6f, 2.0f)
        val GLYPH_CLASSES = listOf(1, 2, 3, 5)
        val WEIGHT_INPUTS = mapOf(1 to "5", 2 to "55", 3 to "555", 5 to "102.5")
        val REPS_INPUTS = mapOf(1 to "5", 2 to "12", 3 to "555", 5 to "55555")

        /**
         * The spec §7 ledger (R10/R11) — the past row's slice. weight/5@2.0 left the
         * ledger when R13's compact inset paid its +6px deficit with 8dp to spare; the
         * inverted assertion caught the resolution, as designed.
         */
        val KNOWN_LIMITS = mapOf(
            "reps/5@1.6" to "DEFERRED to domain cap (B-8), follow-up PR",
            "reps/5@2.0" to "DEFERRED to domain cap (B-8), follow-up PR",
        )
    }
}
