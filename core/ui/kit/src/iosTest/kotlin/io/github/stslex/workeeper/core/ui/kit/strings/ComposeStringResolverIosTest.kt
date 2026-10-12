// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.strings

import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_action_cancel
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_active_session_conflict_body_format
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_discard_session_confirm_title_format
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** The EN column of kmp-string-resolver.md §5.1, resolved on Native. */
class ComposeStringResolverIosTest {

    @Test
    fun resolvesTheEnglishCatalog() {
        val resolver = ComposeStringResolver()
        val rows = runBlocking {
            listOf(
                resolver.getString(Res.string.core_ui_kit_action_cancel),
                resolver.getString(Res.string.core_ui_kit_discard_session_confirm_title_format, "Push Day"),
                resolver.getString(Res.string.core_ui_kit_active_session_conflict_body_format, "Push Day", "12 min"),
                resolver.getString(Res.string.core_ui_kit_discard_session_confirm_title_format),
            )
        }

        assertEquals(listOf("Cancel", "Delete Push Day?", "Push Day — 12 min", "Delete %1\$s?"), rows)
    }
}
