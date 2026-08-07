// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.mapper

import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.mvi.mapper.StartCardModeMapper.toDomain
import io.github.stslex.workeeper.feature.home.mvi.mapper.StartCardModeMapper.toUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Every arm pinned explicitly, both directions: a swapped arm here would persist one mode
 * and render another, and nothing downstream could tell — the handler launches the mapping
 * into a coroutine the handler tests do not execute, so this file is the mapping's gate.
 */
internal class StartCardModeMapperTest {

    @Test
    fun `every UI mode maps to its own domain twin`() {
        assertEquals(StartCardModeDomain.WEEK, StartCardModeUi.WEEK.toDomain())
        assertEquals(
            StartCardModeDomain.DAYS_SINCE_LAST,
            StartCardModeUi.DAYS_SINCE_LAST.toDomain(),
        )
        assertEquals(
            StartCardModeDomain.LAGGING_GROUPS,
            StartCardModeUi.LAGGING_GROUPS.toDomain(),
        )
        assertEquals(
            StartCardModeDomain.FORGOTTEN_TRAINING,
            StartCardModeUi.FORGOTTEN_TRAINING.toDomain(),
        )
    }

    @Test
    fun `every domain mode maps to its own UI twin`() {
        assertEquals(StartCardModeUi.WEEK, StartCardModeDomain.WEEK.toUi())
        assertEquals(
            StartCardModeUi.DAYS_SINCE_LAST,
            StartCardModeDomain.DAYS_SINCE_LAST.toUi(),
        )
        assertEquals(
            StartCardModeUi.LAGGING_GROUPS,
            StartCardModeDomain.LAGGING_GROUPS.toUi(),
        )
        assertEquals(
            StartCardModeUi.FORGOTTEN_TRAINING,
            StartCardModeDomain.FORGOTTEN_TRAINING.toUi(),
        )
    }

    @Test
    fun `the mapping round-trips over the whole catalog`() {
        StartCardModeUi.entries.forEach { mode ->
            assertEquals(mode, mode.toDomain().toUi())
        }
    }
}
