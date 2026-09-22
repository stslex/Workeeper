// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.wear.state.CommandIssueResult
import io.github.stslex.workeeper.wear.state.CommandStatus
import io.github.stslex.workeeper.wear.state.LocalMutationAuthority
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.WatchWorkoutReducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WearRefreshRequiredCompletionTest {

    @Test
    fun rejectedUnsolicitedSnapshotBlocksOtherwiseValidIdleCompletion() {
        val rejectedSnapshots = listOf(
            ReducerTestFixtures.active(databaseEpoch = ReducerTestFixtures.otherEpoch, revision = 5),
            ReducerTestFixtures.active(session = ReducerTestFixtures.sessionB, revision = 5),
            ReducerTestFixtures.active(revision = 2, leaseGeneration = 3),
        )
        val scenarios = listOf(false, true).flatMap { terminalCommand ->
            rejectedSnapshots.map { snapshot -> terminalCommand to snapshot }
        }
        scenarios.forEach { (terminalCommand, snapshot) ->
            val reducer = activeReducer(terminalCommand)
            val before = reducer.state
            assertTrue(before.authority is LocalMutationAuthority.Available)
            assertFalse(before.refreshRequired)
            assertTrue(WearSurfaceMapper.map(before).completeEnabled)
            assertNull(WearSurfaceMapper.map(before).completionUnavailableReason)

            val reduction = reducer.receiveUnsolicited(snapshot)

            assertFalse(reduction.accepted)
            assertNull(reduction.effectiveMutationWindowMs)
            assertEquals(before.display, reducer.state.display)
            assertEquals(before.authority, reducer.state.authority)
            assertTrue(reducer.state.authority is LocalMutationAuthority.Available)
            assertEquals(before.command, reducer.state.command)
            assertTrue(reducer.state.refreshRequired)
            val model = WearSurfaceMapper.map(reducer.state)
            assertTrue(model.controlsEnabled)
            assertFalse(
                model.completeEnabled,
                "A rejected unsolicited snapshot must block completion while refresh is required",
            )
            assertEquals(CompletionUnavailableReason.REFRESH_REQUIRED, model.completionUnavailableReason)
        }
    }

    private fun activeReducer(terminalCommand: Boolean): WatchWorkoutReducer = WatchWorkoutReducer().apply {
        val handshake = ReducerTestFixtures.id(901)
        issueHandshake(handshake, 1_000)
        assertTrue(receiveSnapshot(handshake, ReducerTestFixtures.active(revision = 3), 1_000).accepted)
        if (terminalCommand) {
            val correlation = ReducerTestFixtures.id(902)
            val issued = issueCommand(correlation, 1_001, ReducerTestFixtures.fingerprint(revision = 3))
            assertTrue(issued is CommandIssueResult.Issued)
            receiveCommandResponse(
                ReducerTestFixtures.response(
                    correlationId = correlation,
                    outcome = CompleteCommandOutcome.Applied,
                    replacement = ReducerTestFixtures.active(revision = 4, leaseGeneration = 2, targetPosition = 1),
                ),
                1_002,
            )
            assertEquals(CommandStatus.TERMINAL, state.command?.status)
        } else {
            assertNull(state.command)
        }
    }
}
