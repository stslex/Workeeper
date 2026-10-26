// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.strings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * kit's filler on its own (kmp-phase-7-16-live-workout-feature.md, B4.1): Compose resources'
 * positional rule, nothing more. [StringTemplatesParityTest] holds it against the real resolver.
 */
internal class StringTemplatesTest {

    @Test
    fun fillsPositionalPlaceholdersInOrder() {
        assertEquals("2 of 3 exercises", "%1\$d of %2\$d exercises".fillTemplate(2, 3))
    }

    @Test
    fun aPlaceholderMayRepeatOrComeOutOfOrder() {
        assertEquals("b, a, b", "%2\$s, %1\$s, %2\$s".fillTemplate("a", "b"))
    }

    @Test
    fun aTemplateWithoutPositionalPlaceholdersComesBackVerbatim() {
        assertEquals("Set added", "Set added".fillTemplate(5))
        assertEquals("%d sets", "%d sets".fillTemplate(3))
    }

    @Test
    fun argumentsPrintWithToStringAndInsertLiterally() {
        assertEquals("102.5 × 5", "%1\$s × %2\$d".fillTemplate(102.5, 5))
        assertEquals("“\$1 \\ name” removed", "“%1\$s” removed".fillTemplate("\$1 \\ name"))
    }

    @Test
    fun aMissingArgumentThrowsAsComposeResourcesDoes() {
        assertThrows<IndexOutOfBoundsException> { "%2\$d of %1\$d".fillTemplate(1) }
    }
}
