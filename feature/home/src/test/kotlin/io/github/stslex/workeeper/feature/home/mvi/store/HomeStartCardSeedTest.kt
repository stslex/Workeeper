// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.mvi.store

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The seed itself, asserted where no composition, no clock and no test harness can get
 * between the claim and the answer.
 *
 * `State.init` used to hand the card `StartCardModeUi.WEEK`. That is not a default, it is an
 * announcement: the mode is persisted (HS6), so on every cold start the card's head named a
 * mode nobody had read yet — the same defect the Settings sheet had, on a more prominent
 * surface and on every launch rather than in a window after a tap.
 *
 * `HomeStartCardModeLabelTest` makes the rendering half of this claim and needs a Compose
 * environment to do it. This one needs nothing, and that is the point: if that suite ever has
 * to be quarantined, the invariant still has a witness — one that cannot pass for a reason
 * nobody can name.
 *
 * `State.init` is called directly rather than through `emptyPagingState()`: the subject is the
 * production factory, and a test helper is exactly the kind of thing that could grow a `copy`
 * and answer for it.
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
