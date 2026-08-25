// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.mvi.store

import androidx.paging.PagingData
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingListItemUi
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore.State
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The gate on the empty state's blank-start CTA: offering it while a session runs inserts a
 * second `IN_PROGRESS` row and orphans one of them (B27).
 */
internal class StartBlankGateTest {

    private fun state(hasActiveSession: Boolean): State = State(
        pagingUiState = PagingUiState { flowOf(PagingData.empty<TrainingListItemUi>()) },
        availableTags = kotlinx.collections.immutable.persistentListOf(),
        activeTagFilter = kotlinx.collections.immutable.persistentSetOf(),
        selectionMode = State.SelectionMode.Off,
        pendingBulkDelete = null,
        hasActiveSession = hasActiveSession,
    )

    @Test
    fun `no workout running — the drawn pair is whole`() {
        assertTrue(
            state(hasActiveSession = false).showStartBlank,
            "with nothing running the empty state must offer both CTAs; the pair is contract",
        )
    }

    @Test
    fun `a workout is running — the blank-start CTA withdraws`() {
        assertFalse(
            state(hasActiveSession = true).showStartBlank,
            "offering blank-start while a session runs inserts a SECOND IN_PROGRESS session and " +
                "orphans one of them (B27)",
        )
    }

    /**
     * The flag arrives asynchronously, so `init` must not pick the value that offers the CTA:
     * withholding it for a frame costs nothing, offering it for a frame costs a session.
     */
    @Test
    fun `before the first emission the CTA is withheld, not offered`() {
        val initial = State.init(
            pagingUiState = PagingUiState { flowOf(PagingData.empty<TrainingListItemUi>()) },
        )
        assertEquals(true, initial.hasActiveSession)
        assertFalse(
            initial.showStartBlank,
            "the pre-emission default must withhold the CTA; a wrong `false` starts a session",
        )
    }
}
