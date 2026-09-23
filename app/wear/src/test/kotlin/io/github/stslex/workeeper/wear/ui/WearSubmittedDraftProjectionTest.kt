// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import io.github.stslex.workeeper.wear.state.CommandDraft
import io.github.stslex.workeeper.wear.state.CommandIssueResult
import io.github.stslex.workeeper.wear.state.CommandStatus
import io.github.stslex.workeeper.wear.state.ReducerTestFixtures
import io.github.stslex.workeeper.wear.state.WatchWorkoutReducer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class WearSubmittedDraftProjectionTest {
    @Test
    fun unresolvedSubmittedValuesAreNotMarkedUnsentEvenAfterAuthorityExpires() {
        val reducer = submittedDraft()
        val sending = WearSurfaceMapper.map(reducer.state)
        assertEquals(CompletionUnavailableReason.COMMAND_IN_FLIGHT, sending.completionUnavailableReason)
        assertEquals(12, sending.reps)
        assertEquals(null, sending.weightHundredthsKg)
        assertFalse(sending.hasUnsubmittedDraft, "Sending values were already submitted; they are not unsent")
        assertFalse(sending.controlsEnabled)
        reducer.onTransportTimeout(ReducerTestFixtures.id(2), 2L)
        assertEquals(WearSurfaceKind.RETRYABLE_ERROR, WearSurfaceMapper.map(reducer.state).kind)
        assertFalse(WearSurfaceMapper.map(reducer.state).hasUnsubmittedDraft)
        assertTrue(reducer.issueTimeoutRetry(ReducerTestFixtures.id(3), 3L) is CommandIssueResult.Issued)
        assertFalse(WearSurfaceMapper.map(reducer.state).hasUnsubmittedDraft)

        reducer.expireAuthority(120_000L)
        val expired = WearSurfaceMapper.map(reducer.state)
        assertEquals(CompletionUnavailableReason.REFRESH_REQUIRED, expired.completionUnavailableReason)
        assertEquals(12, expired.reps)
        assertEquals(null, expired.weightHundredthsKg)
        assertFalse(expired.hasUnsubmittedDraft, "Authority expiry does not undo command submission")
        val submitted = requireNotNull(reducer.state.command)
        val awaiting = WearSurfaceMapper.map(
            reducer.state.copy(command = submitted.copy(status = CommandStatus.AWAITING_RETRY_AUTHORITY)),
        )
        assertEquals(12, awaiting.reps)
        assertFalse(
            awaiting.hasUnsubmittedDraft,
            "An unresolved command retains submitted values while awaiting authority",
        )
    }

    @Test
    fun differentDraftOrCommandIdentityAndClosedCommandsKeepUnsentMarker() {
        val state = submittedDraft().state
        val submitted = requireNotNull(state.command)
        val unsubmitted = listOf(
            state.copy(draft = CommandDraft(13, null)),
            state.copy(draft = CommandDraft(12, 10_000)),
            state.copy(command = submitted.copy(source = submitted.source.copy(sessionRevision = 2L))),
            state.copy(command = submitted.copy(target = submitted.target.copy(setPosition = 1))),
            state.copy(command = null),
        ) + listOf(CommandStatus.TERMINAL, CommandStatus.SOURCE_INVALIDATED, CommandStatus.ABANDONED).map { status ->
            state.copy(command = submitted.copy(status = status))
        }
        unsubmitted.forEach { candidate ->
            val model = WearSurfaceMapper.map(candidate)
            assertEquals(candidate.draft?.reps, model.reps)
            assertEquals(candidate.draft?.weightHundredthsKg, model.weightHundredthsKg)
            assertTrue(
                model.hasUnsubmittedDraft,
                "Only the same unresolved submitted payload may hide the unsent marker",
            )
        }
        assertFalse(WearSurfaceMapper.map(state.copy(draft = null)).hasUnsubmittedDraft)
    }

    private fun submittedDraft(): WatchWorkoutReducer = WatchWorkoutReducer().apply {
        val request = ReducerTestFixtures.id(1)
        issueHandshake(request, 0L)
        assertTrue(receiveSnapshot(request, ReducerTestFixtures.active(), 0L).accepted)
        assertTrue(
            issueCommand(
                ReducerTestFixtures.id(2),
                1L,
                ReducerTestFixtures.fingerprint(reps = 12, weight = null),
            ) is CommandIssueResult.Issued,
        )
    }
}
