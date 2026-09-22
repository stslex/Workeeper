// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.Bidi
import java.util.Locale

internal class WearValueFormatterTest {

    @Test
    fun `explicit English Russian and Arabic locales control both value strings`() {
        val cases = listOf(
            Locale.US to WearFormattedValues("128", "72.53"),
            Locale.forLanguageTag("ru-RU") to WearFormattedValues("128", "72,53"),
            Locale.forLanguageTag("ar-EG") to WearFormattedValues("١٢٨", "٧٢٫٥٣"),
        )

        cases.forEach { (locale, expected) ->
            assertEquals(expected, WearValueFormatter.format(128, 7_253, locale), locale.toLanguageTag())
        }
    }

    @Test
    fun `weight formatting preserves hundredths without fixed trailing zeros or grouping`() {
        val cases = mapOf(0 to "0", 1 to "0.01", 250 to "2.5", 10_000 to "100", 99_999 to "999.99")
        cases.forEach { (weight, expected) ->
            assertEquals(WearFormattedValues("999", expected), WearValueFormatter.format(999, weight, Locale.US))
        }
    }

    @Test
    fun `absent values remain absent for the localized UI fallback`() {
        assertEquals(WearFormattedValues(null, null), WearValueFormatter.format(null, null, Locale.US))
        assertEquals(WearFormattedValues("0", null), WearValueFormatter.format(0, null, Locale.US))
    }

    @Test
    fun `localized numeric runs preserve digit order inside an RTL paragraph`() {
        val values = WearValueFormatter.format(128, 7_253, Locale.forLanguageTag("ar-EG"))
        listOf(requireNotNull(values.reps), requireNotNull(values.weight)).forEach { number ->
            val paragraph = Bidi("الوزن $number", Bidi.DIRECTION_RIGHT_TO_LEFT)
            val start = paragraph.length - number.length
            number.forEachIndexed { offset, character ->
                if (character.isDigit()) {
                    assertEquals(0, paragraph.getLevelAt(start + offset) % 2, "digits retain numeric reading order")
                }
            }
        }
    }

    @Test
    fun `model copies recompute labels when values or selected locale change`() {
        val original = WearSurfaceModel(
            kind = WearSurfaceKind.ACTIVE,
            reps = 8,
            weightHundredthsKg = 10_000,
            selectedLocale = Locale.US,
        )
        val edited = original.copy(reps = 12, weightHundredthsKg = 7_253)
        val translated = edited.copy(selectedLocale = Locale.forLanguageTag("ar-EG"))
        val cleared = translated.copy(weightHundredthsKg = null)

        assertEquals(WearFormattedValues("8", "100"), original.formattedValues)
        assertEquals(WearFormattedValues("12", "72.53"), edited.formattedValues)
        assertEquals(WearFormattedValues("١٢", "٧٢٫٥٣"), translated.formattedValues)
        assertNull(cleared.formattedValues.weight)
        assertEquals("١٢", cleared.formattedValues.reps)
        assertTrue(translated != edited, "selected locale participates in presentation equality")
    }
}
