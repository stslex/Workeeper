// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.strings

import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_cancel
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
 * The real resolver against kit's real catalog, outside composition (kmp-string-resolver.md
 * §5.1). The Russian half is [ComposeStringResolverRuTest]: the Robolectric JUnit 5 extension
 * forbids method-level `@Config`.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class ComposeStringResolverTest {

    @Test
    fun resolvesTheEnglishCatalog() {
        val rows = resolveTheFourRows()

        assertTheFourRows(
            expected = listOf("Cancel", "Delete Push Day?", "Push Day — 12 min", "Delete %1\$s?"),
            actual = rows,
        )
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru")
internal class ComposeStringResolverRuTest {

    @Test
    fun resolvesTheRussianCatalog() {
        val rows = resolveTheFourRows()

        assertTheFourRows(
            expected = listOf("Отмена", "Удалить «Push Day»?", "Push Day — 12 min", "Удалить «%1\$s»?"),
            actual = rows,
        )
    }
}

private fun resolveTheFourRows(): List<String> {
    val resolver = ComposeStringResolver()
    return runBlocking {
        listOf(
            resolver.getString(Res.string.core_ui_kit_action_cancel),
            resolver.getString(Res.string.core_ui_kit_discard_session_confirm_title_format, "Push Day"),
            resolver.getString(Res.string.core_ui_kit_active_session_conflict_body_format, "Push Day", "12 min"),
            resolver.getString(Res.string.core_ui_kit_discard_session_confirm_title_format),
        )
    }
}

private fun assertTheFourRows(expected: List<String>, actual: List<String>) {
    assertAll(
        { assertEquals(expected[0], actual[0], "row 1: plain string") },
        { assertEquals(expected[1], actual[1], "row 2: one argument") },
        { assertEquals(expected[2], actual[2], "row 3: two arguments, in order") },
        { assertEquals(expected[3], actual[3], "row 4: placeholder string read without arguments") },
    )
}
