// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.setrow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import io.github.stslex.workeeper.core.ui.kit.golden.OverflowGateSdk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

/**
 * D2's proof by test, not by reasoning (set-field-column-headers.md §4): the header is one
 * `AnnotatedString` in one `Text`, so truncation must eat the unit — the string's tail —
 * before the name. Rendered through the Paparazzi measurement harness because Robolectric
 * is false-negative for text metrics (task brief, Phase 2).
 */
internal class SetColumnHeaderTest {

    @Test
    fun labelCasingAndSpanStructure() {
        val unitColor = Color.Red
        val weighted = buildSetColumnHeaderLabel(name = "вес", unit = "кг", unitColor = unitColor)
        assertEquals("ВЕС (КГ)", weighted.text)
        assertEquals(1, weighted.spanStyles.size)
        val span = weighted.spanStyles.single()
        assertEquals("(КГ)", weighted.text.substring(span.start, span.end))
        assertEquals(unitColor, span.item.color)

        val unitless = buildSetColumnHeaderLabel(name = "повторы", unit = null, unitColor = unitColor)
        assertEquals("ПОВТОРЫ", unitless.text)
        assertTrue(unitless.spanStyles.isEmpty())
    }

    @Test
    fun ellipsisEatsTheUnitBeforeTheName() {
        val label = buildSetColumnHeaderLabel(name = "вес", unit = "кг", unitColor = Color.Red)
        val fullText = label.text
        val name = "ВЕС"
        val unit = "(КГ)"

        val gate = OverflowGateSdk()
        gate.setup()
        val samples = mutableListOf<Pair<Int, TextLayoutResult>>()
        try {
            for (widthPx in CONSTRAINED_WIDTHS_PX) {
                var captured: TextLayoutResult? = null
                gate.render {
                    Box(
                        modifier = Modifier.width(
                            with(LocalDensity.current) { widthPx.toDp() },
                        ),
                    ) {
                        SetColumnHeaderLabel(
                            label = label,
                            onTextLayout = { captured = it },
                        )
                    }
                }
                samples += widthPx to checkNotNull(captured) {
                    "no text layout captured at ${widthPx}px"
                }
            }
        } finally {
            gate.teardown()
        }

        check(samples.size == CONSTRAINED_WIDTHS_PX.size) { "sweep ran over ${samples.size} widths" }
        val unitCutNameIntact = mutableListOf<Int>()
        assertAll(
            samples.map { (widthPx, layout) ->
                {
                    val visibleEnd = layout.getLineEnd(lineIndex = 0, visibleEnd = true)
                    val visible = fullText.take(visibleEnd)
                    if (layout.isLineEllipsized(0)) {
                        // The cut must come from the tail: the full unit may never survive
                        // a truncation (losing anything else first would mean the name went
                        // before the unit — the locked-decision-4 violation).
                        assertFalse(
                            visible.contains(unit),
                            "at ${widthPx}px the ellipsis kept the unit intact: \"$visible\"",
                        )
                        if (visible.startsWith(name)) unitCutNameIntact += widthPx
                    } else {
                        assertEquals(
                            fullText.length,
                            visibleEnd,
                            "at ${widthPx}px: not ellipsized but text is incomplete",
                        )
                    }
                }
            },
        )
        // The sweep must actually contain the interesting regime — unit truncated while the
        // name still reads in full — or the assertions above ran over nothing that matters.
        assertTrue(
            unitCutNameIntact.isNotEmpty(),
            "no sweep width produced an ellipsized label with the name intact " +
                "(widths=$CONSTRAINED_WIDTHS_PX)",
        )
        println(
            "ellipsis sweep: ${samples.size} widths, unit-cut-name-intact at " +
                "${unitCutNameIntact}px",
        )
    }

    private companion object {
        /** ~156px is the full "ВЕС (КГ)" advance at mono.caption on the golden device. */
        val CONSTRAINED_WIDTHS_PX = listOf(220, 150, 130, 110, 90, 70, 50)
    }
}
