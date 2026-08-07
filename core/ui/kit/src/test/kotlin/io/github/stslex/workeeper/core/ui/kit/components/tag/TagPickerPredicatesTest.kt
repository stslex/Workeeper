// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.tag

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The sheet's two predicates at their seam (§27: named so assertable). The trim is the
 * load-bearing half: the feature handlers trim before `createTag` and the repository
 * returns the EXISTING row for a known name, so an untrimmed comparison up here offers a
 * create that resolves to an already-selected tag and duplicates its chip.
 */
internal class TagPickerPredicatesTest {

    private val dictionary = persistentListOf(
        AppTagItem(uuid = "t1", name = "Push"),
        AppTagItem(uuid = "t2", name = "Pull"),
    )

    @Test
    fun `a padded exact match cannot create`() {
        assertFalse(tagPickerCanCreate(" Push ", dictionary))
    }

    @Test
    fun `a case-differing exact match cannot create either`() {
        assertFalse(tagPickerCanCreate("pUsH", dictionary))
    }

    @Test
    fun `a new name creates, padding stripped`() {
        assertTrue(tagPickerCanCreate("  Legs ", dictionary))
    }

    @Test
    fun `whitespace alone cannot create`() {
        assertFalse(tagPickerCanCreate("   ", dictionary))
    }

    @Test
    fun `the filter trims the same way the create predicate does`() {
        assertEquals(
            listOf("t1"),
            tagPickerFiltered(" Pus ", dictionary).map { it.uuid },
        )
    }

    @Test
    fun `a blank query filters nothing`() {
        assertEquals(dictionary, tagPickerFiltered("  ", dictionary))
    }
}
