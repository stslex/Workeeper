// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.start_mode.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The catalog's declaration order is load-bearing: `StartCardModeSheetContent` renders
 * `entries` as-is, and the arc's ruled sheet copy lists the four modes in exactly this
 * order with «Неделя» — the default (HS3) — leading. A reorder would silently reorder the
 * sheet.
 */
internal class StartCardModeCatalogTest {

    @Test
    fun `the catalog lists four modes in the ruled sheet order with default first`() {
        assertEquals(
            listOf(
                StartCardModeUi.WEEK,
                StartCardModeUi.DAYS_SINCE_LAST,
                StartCardModeUi.LAGGING_GROUPS,
                StartCardModeUi.FORGOTTEN_TRAINING,
            ),
            StartCardModeUi.entries.toList(),
        )
    }
}
