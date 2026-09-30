// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * wear-paired-transport.md §7.5 `protocolFailure`: a command closes through the existing
 * protocol-mismatch transition, and a handshake takes exactly its display, authority, draft and
 * event effects (Phase 1 §9: fail closed).
 */
internal class WatchWorkoutReducerProtocolFailureTest {

    @Test
    fun `a command's protocol failure closes it without retry`() {
        val reducer = activeReducer()
        val attempt = ReducerTestFixtures.id(70)
        reducer.issueCommand(attempt, 1, ReducerTestFixtures.fingerprint())

        reducer.protocolFailure(attempt)

        assertEquals(CommandStatus.TERMINAL, reducer.state.command?.status)
        assertFailedClosed(reducer)
        // The correlation is consumed: a late response for it changes nothing.
        reducer.receiveCommandResponse(
            ReducerTestFixtures.response(
                correlationId = attempt,
                outcome = CompleteCommandOutcome.Applied,
                replacement = ReducerTestFixtures.active(revision = 2),
            ),
            2,
        )
        assertFailedClosed(reducer)
    }

    @Test
    fun `a handshake's protocol failure has the command transition's effects`() {
        val commandReducer = activeReducer()
        val attempt = ReducerTestFixtures.id(71)
        commandReducer.issueCommand(attempt, 1, ReducerTestFixtures.fingerprint())
        commandReducer.protocolFailure(attempt)

        val handshakeReducer = activeReducer()
        val handshake = ReducerTestFixtures.id(72)
        handshakeReducer.issueHandshake(handshake, 1)
        handshakeReducer.protocolFailure(handshake)

        assertFailedClosed(handshakeReducer)
        assertEquals(commandReducer.state.display, handshakeReducer.state.display)
        assertEquals(commandReducer.state.authority, handshakeReducer.state.authority)
        assertEquals(commandReducer.state.refreshRequired, handshakeReducer.state.refreshRequired)
        assertEquals(commandReducer.state.events.last(), handshakeReducer.state.events.last())
    }

    private fun assertFailedClosed(reducer: WatchWorkoutReducer) {
        assertIs<WatchDisplayState.ProtocolMismatch>(reducer.state.display)
        assertIs<LocalMutationAuthority.Retired>(reducer.state.authority)
        assertNull(reducer.state.draft)
        assertTrue(reducer.state.refreshRequired)
        assertEquals(ReducerEvent.ProtocolError(reason = null), reducer.state.events.last())
    }

    private fun activeReducer(): WatchWorkoutReducer = WatchWorkoutReducer().also { reducer ->
        val correlation = ReducerTestFixtures.id(69)
        reducer.issueHandshake(correlation, 0)
        reducer.receiveSnapshot(correlation, ReducerTestFixtures.active(), 0)
    }
}
