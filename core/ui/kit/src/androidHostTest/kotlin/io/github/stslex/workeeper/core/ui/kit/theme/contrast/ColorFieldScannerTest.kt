// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A scanner that silently returned an empty set would pass every completeness assertion. */
internal class ColorFieldScannerTest {

    private data class Fixture(
        val alpha: Color,
        val bravoBackground: Color,
        val nested: Nested,
        val label: String,
        val enabled: Boolean,
        val count: Int,
    )

    private data class Nested(val charlie: Color)

    private val fixture = Fixture(
        alpha = Color(0xFF112233),
        bravoBackground = Color(0xFF445566),
        nested = Nested(charlie = Color(0xFF778899)),
        label = "ignored",
        enabled = true,
        count = 7,
    )

    @Test
    fun `every declared Color is found and nothing else is`() {
        assertEquals(
            setOf("alpha", "bravoBackground"),
            ColorFieldScanner.colorFieldNames(Fixture::class.java),
        )
    }

    @Test
    fun `values are read back exactly, not defaulted`() {
        assertEquals(
            mapOf(
                "alpha" to Color(0xFF112233),
                "bravoBackground" to Color(0xFF445566),
            ),
            ColorFieldScanner.colorMap(fixture),
        )
    }

    @Test
    fun `nested groups are scanned separately rather than flattened`() {
        assertEquals(setOf("charlie"), ColorFieldScanner.colorFieldNames(Nested::class.java))
        assertEquals(mapOf("charlie" to Color(0xFF778899)), ColorFieldScanner.colorMap(fixture.nested))
    }

    /** The detector must be able to fail: a colour added to a type has to show up in the scan. */
    @Test
    fun `adding a Color to a type changes the scan result`() {
        val extended = ColorFieldScanner.colorFieldNames(FixtureWithExtraColor::class.java)
        val base = ColorFieldScanner.colorFieldNames(Fixture::class.java)

        assertTrue(extended.containsAll(base), "extended fixture lost a base colour: $extended")
        assertEquals(setOf("delta"), extended - base)
    }

    private data class FixtureWithExtraColor(
        val alpha: Color,
        val bravoBackground: Color,
        val delta: Color,
    )

    @Test
    fun `a type with no colours scans empty rather than throwing`() {
        assertEquals(emptySet<String>(), ColorFieldScanner.colorFieldNames(String::class.java))
    }
}
