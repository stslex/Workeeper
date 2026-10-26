// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.strings

import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_active_session_conflict_body_format
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_discard_session_confirm_title_format
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * SP1: kit's filler against the real resolver on kit's real catalog. Each row compares the
 * resolver's own formatting with the filler applied to the same template read verbatim. The
 * Russian half is [StringTemplatesParityRuTest]: the Robolectric JUnit 5 extension forbids
 * method-level `@Config`.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class StringTemplatesParityTest {

    @Test
    fun fillsTheEnglishCatalogAsTheResolverFormats() {
        assertTemplatesFillAsTheResolverFormats()
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru")
internal class StringTemplatesParityRuTest {

    @Test
    fun fillsTheRussianCatalogAsTheResolverFormats() {
        assertTemplatesFillAsTheResolverFormats()
    }
}

private fun assertTemplatesFillAsTheResolverFormats() {
    val resolver = ComposeStringResolver()
    val discard = Res.string.core_ui_kit_discard_session_confirm_title_format
    val conflict = Res.string.core_ui_kit_active_session_conflict_body_format
    val rows = runBlocking {
        listOf(
            resolver.getString(discard, "Push Day") to
                resolver.getString(discard).fillTemplate("Push Day"),
            resolver.getString(conflict, "Push Day", "12 min") to
                resolver.getString(conflict).fillTemplate("Push Day", "12 min"),
            resolver.getString(conflict, 3, 4) to
                resolver.getString(conflict).fillTemplate(3, 4),
        )
    }
    assertAll(
        { assertEquals(rows[0].first, rows[0].second, "row 1: one argument") },
        { assertEquals(rows[1].first, rows[1].second, "row 2: two String arguments") },
        { assertEquals(rows[2].first, rows[2].second, "row 3: the same template, two Int arguments") },
    )
}
