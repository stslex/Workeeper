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
 * The gate on the empty state's blank-start CTA.
 *
 * ## What it is protecting, stated once so the assertions below are not mysterious
 *
 * «Начать пустую тренировку» routes to `Screen.LiveWorkout(sessionUuid = null, trainingUuid =
 * null)`. Both-null sends `CommonHandler.createSession` down its blank branch to
 * `LiveWorkoutInteractor.createAdhocSession`, which inserts a fresh ad-hoc training plus an
 * `IN_PROGRESS` session **unconditionally** — its sibling `startSession` guards against exactly
 * this ("Reuse any in-progress session for this training so re-entry from the Trainings tab does
 * not orphan an active session by spawning a parallel one"), and the blank branch does not.
 *
 * With a session already running that produces two `IN_PROGRESS` rows, and
 * `SessionDao.observeActive()` is `WHERE state = 'IN_PROGRESS' LIMIT 1` with no `ORDER BY` — so the
 * app follows an arbitrary one and the other is orphaned.
 *
 * The reachability is the part a reviewer cannot see from either file alone:
 * `TrainingDao.pagedActiveWithStats` filters `is_adhoc = 0`, so a running ad-hoc workout puts **no
 * row in this screen's list**. A user with no saved trainings and a blank workout in progress sees
 * an empty list, and the empty state offered them a second one.
 *
 * ## Why this is a state test and not a screen test
 *
 * The condition is a pure function of one flag, and §27's standing rule is that a selector driving
 * a surface gets asserted directly rather than photographed — a golden of the empty state cannot
 * see *which* actions it was given, and this action's absence is precisely the thing to gate.
 *
 * Both directions are asserted here by construction, and the mutation that proves the assertions
 * discriminate is recorded in the commit body: dropping `.takeIf { state.showStartBlank }` at the
 * call site leaves every other test in this module green.
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
     * The unsafe default is the one that *offers* the CTA, so `init` must not pick it.
     *
     * The flag arrives asynchronously from `observeActive()`. If the initial value were `false`,
     * every cold open would render one or more frames with the CTA live — tappable, and wrong
     * exactly when a session is running. Withholding it for a frame costs nothing; offering it for
     * a frame costs a session.
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
