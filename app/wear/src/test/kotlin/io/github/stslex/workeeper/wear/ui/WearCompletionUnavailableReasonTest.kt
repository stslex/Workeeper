// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.state.ActiveFreshness
import io.github.stslex.workeeper.wear.state.CommandDraft
import io.github.stslex.workeeper.wear.state.CommandIssueResult
import io.github.stslex.workeeper.wear.state.CommandStatus
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.WatchDisplayState
import io.github.stslex.workeeper.wear.state.WatchReducerState
import io.github.stslex.workeeper.wear.state.WatchWorkoutReducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WearCompletionUnavailableReasonTest {

    @Test
    fun `fresh actionable completion has no unavailable reason`() {
        val model = WearSurfaceMapper.map(activeReducer().state)

        assertTrue(model.completeEnabled)
        assertNull(model.completionUnavailableReason)
    }

    @Test
    fun `disconnection and stale state precede pending command and invalid fields`() {
        val pending = inFlightState()
        val display = pending.display as WatchDisplayState.Active
        val cases = mapOf(
            ActiveFreshness.DISCONNECTED to CompletionUnavailableReason.DISCONNECTED,
            ActiveFreshness.STALE to CompletionUnavailableReason.REFRESH_REQUIRED,
            ActiveFreshness.REFRESH_REQUIRED to CompletionUnavailableReason.REFRESH_REQUIRED,
        )

        cases.forEach { (freshness, expected) ->
            val model = WearSurfaceMapper.map(
                pending.copy(
                    display = display.copy(freshness = freshness),
                    draft = CommandDraft(0, -1),
                    authority = LocalMutationAuthority.Retired,
                ),
            )
            assertFalse(model.completeEnabled)
            assertEquals(expected, model.completionUnavailableReason, freshness.name)
        }
    }

    @Test
    fun `refresh request precedes command and numeric reasons even on a fresh display`() {
        val model = WearSurfaceMapper.map(
            inFlightState().copy(refreshRequired = true, draft = CommandDraft(0, -1)),
        )

        assertFalse(model.completeEnabled)
        assertEquals(CompletionUnavailableReason.REFRESH_REQUIRED, model.completionUnavailableReason)
    }

    @Test
    fun `in flight command takes precedence over attempt bound authority and invalid fields`() {
        val model = WearSurfaceMapper.map(inFlightState().copy(draft = CommandDraft(0, -1)))

        assertFalse(model.controlsEnabled)
        assertFalse(model.completeEnabled)
        assertEquals(CompletionUnavailableReason.COMMAND_IN_FLIGHT, model.completionUnavailableReason)
    }

    @Test
    fun `fresh display without mutation authority asks for refresh`() {
        val model = WearSurfaceMapper.map(
            activeReducer().state.copy(authority = LocalMutationAuthority.Retired, draft = CommandDraft(0, -1)),
        )

        assertEquals(WearSurfaceKind.ACTIVE, model.kind)
        assertFalse(model.completeEnabled)
        assertEquals(CompletionUnavailableReason.REFRESH_REQUIRED, model.completionUnavailableReason)
    }

    @Test
    fun `numeric reason follows protocol field precedence while draft editing stays enabled`() {
        val state = activeReducer().state
        val cases = listOf(
            CommandDraft(0, -1) to CompletionUnavailableReason.INVALID_REPS,
            CommandDraft(WearProtocol.MAX_WEAR_REPS + 1, 0) to CompletionUnavailableReason.INVALID_REPS,
            CommandDraft(1, -1) to CompletionUnavailableReason.INVALID_WEIGHT,
            CommandDraft(1, WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG + 1) to
                CompletionUnavailableReason.INVALID_WEIGHT,
        )

        cases.forEach { (draft, expected) ->
            val model = WearSurfaceMapper.map(state.copy(draft = draft))
            assertTrue(model.controlsEnabled)
            assertFalse(model.completeEnabled)
            assertEquals(expected, model.completionUnavailableReason)
        }
    }

    @Test
    fun `explicit null draft weight stays cleared instead of restoring the snapshot weight`() {
        val state = activeReducer().state
        val cleared = WearSurfaceMapper.map(state.copy(draft = CommandDraft(12, null)))

        assertEquals(12, cleared.reps)
        assertNull(cleared.weightHundredthsKg)
        assertNull(cleared.formattedValues.weight)
        assertTrue(cleared.completeEnabled)
        assertNull(cleared.completionUnavailableReason)
        assertEquals(10_000, WearSurfaceMapper.map(state.copy(draft = null)).weightHundredthsKg)
    }

    @Test
    fun `zero and protocol maximum values stay actionable`() {
        val state = activeReducer().state
        listOf(
            CommandDraft(1, 0),
            CommandDraft(WearProtocol.MAX_WEAR_REPS, WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG),
        ).forEach { draft ->
            val model = WearSurfaceMapper.map(state.copy(draft = draft))
            assertTrue(model.completeEnabled)
            assertNull(model.completionUnavailableReason)
        }
    }

    @Test
    fun `every command status has a reason when its active completion remains blocked`() {
        val state = inFlightState()
        val command = requireNotNull(state.command)
        CommandStatus.entries.forEach { status ->
            val model = WearSurfaceMapper.map(state.copy(command = command.copy(status = status)))
            if (model.controlsVisible && !model.completeEnabled) {
                assertNotNull(model.completionUnavailableReason, status.name)
            } else {
                assertNull(model.completionUnavailableReason, status.name)
            }
        }
    }

    @Test
    fun `synthetic target fixtures provide a reason exactly when completion is blocked`() {
        SyntheticSurfaceFixtures.allKinds().forEach { model ->
            if (model.controlsVisible && !model.completeEnabled) {
                assertNotNull(model.completionUnavailableReason, model.toString())
            } else {
                assertNull(model.completionUnavailableReason, model.toString())
            }
        }
    }

    private fun activeReducer(): WatchWorkoutReducer = WatchWorkoutReducer().apply {
        val request = ReducerTestFixtures.id(701)
        issueHandshake(request, 1_000)
        receiveSnapshot(request, ReducerTestFixtures.active(), 1_000)
    }

    private fun inFlightState(): WatchReducerState {
        val reducer = activeReducer()
        val issued = reducer.issueCommand(ReducerTestFixtures.id(702), 1_001, ReducerTestFixtures.fingerprint())
        assertTrue(issued is CommandIssueResult.Issued)
        return reducer.state
    }
}
