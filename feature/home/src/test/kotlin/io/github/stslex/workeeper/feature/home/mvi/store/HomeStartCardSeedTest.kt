// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.store

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * `State.init` seeds no start-card mode and no body, asserted with no composition in the way.
 * The production factory is called directly, never through `emptyPagingState()`.
 */
internal class HomeStartCardSeedTest {

    @Test
    fun `init seeds no mode and no body`() {
        val state = HomeStore.State.init(
            pagingUiState = PagingUiState { flowOf(PagingData.empty<RecentSessionItem>()) },
        )

        assertNull(state.startCardMode, "State.init must not seed a start-card mode")
        assertNull(state.startCardBody, "State.init must not seed a start-card readout")
    }
}
