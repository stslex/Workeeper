// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.CommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.ImmutableTypeField
import io.github.stslex.workeeper.core.wear.protocol.InvalidValueReason
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.OutlineRules
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WatchWorkoutReducerCommandTest {

    @Test
    fun `response-less timeout permits one exact retry before deadline and then abandons`() {
        val reducer = activeReducer()
        val firstAttempt = ReducerTestFixtures.id(30)
        val issue = assertIs<CommandIssueResult.Issued>(
            reducer.issueCommand(firstAttempt, 1, ReducerTestFixtures.fingerprint()),
        )
        assertIs<LocalMutationAuthority.AttemptBound>(reducer.state.authority)
        reducer.onTransportTimeout(firstAttempt, nowElapsedRealtimeMs = 119_999)
        assertEquals(CommandStatus.TIMED_OUT_RETRYABLE, reducer.state.command?.status)

        val retryId = ReducerTestFixtures.id(31)
        val retry = assertIs<CommandIssueResult.Issued>(
            reducer.issueTimeoutRetry(retryId, issuedAtElapsedRealtimeMs = 119_999),
        )
        assertEquals(issue.token.generation, retry.token.generation)
        assertEquals(2, assertIs<LocalMutationAuthority.AttemptBound>(reducer.state.authority).attemptsIssued)

        reducer.onTransportTimeout(retryId, nowElapsedRealtimeMs = 119_999)
        assertEquals(CommandStatus.ABANDONED, reducer.state.command?.status)
        assertIs<LocalMutationAuthority.Retired>(reducer.state.authority)
        assertTrue(reducer.state.refreshRequired)
    }

    @Test
    fun `deadline and intervening refresh both forbid old-lease timeout retry`() {
        val deadlineReducer = activeReducer()
        val first = ReducerTestFixtures.id(32)
        deadlineReducer.issueCommand(first, 1, ReducerTestFixtures.fingerprint())
        deadlineReducer.onTransportTimeout(first, nowElapsedRealtimeMs = 120_000)
        assertIs<CommandIssueResult.Rejected>(
            deadlineReducer.issueTimeoutRetry(ReducerTestFixtures.id(33), 120_000),
        )

        val refreshReducer = activeReducer()
        val command = ReducerTestFixtures.id(34)
        refreshReducer.issueCommand(command, 1, ReducerTestFixtures.fingerprint())
        refreshReducer.onTransportTimeout(command, 2)
        refreshReducer.issueHandshake(ReducerTestFixtures.id(35), 3)
        assertIs<CommandIssueResult.Rejected>(
            refreshReducer.issueTimeoutRetry(ReducerTestFixtures.id(36), 4),
        )
    }

    @Test
    fun `accepted refresh preserves a compatible draft but requires a new command identity`() {
        val reducer = activeReducer()
        val oldCorrelation = ReducerTestFixtures.id(46)
        reducer.issueCommand(oldCorrelation, 1, ReducerTestFixtures.fingerprint())
        reducer.onTransportTimeout(oldCorrelation, nowElapsedRealtimeMs = 2)

        val refresh = ReducerTestFixtures.id(47)
        reducer.issueHandshake(refresh, 3)
        reducer.receiveSnapshot(
            refresh,
            ReducerTestFixtures.active(
                leaseGeneration = 2,
                leaseId = ReducerTestFixtures.lease2,
            ),
            4,
        )

        assertEquals(CommandDraft(8, 10_000), reducer.state.draft)
        assertEquals(CommandStatus.SOURCE_INVALIDATED, reducer.state.command?.status)
        assertIs<CommandIssueResult.Rejected>(
            reducer.issueTimeoutRetry(ReducerTestFixtures.id(48), 5),
        )

        val newCommandId = ReducerTestFixtures.id(49)
        assertIs<CommandIssueResult.Issued>(
            reducer.issueCommand(
                ReducerTestFixtures.id(50),
                5,
                ReducerTestFixtures.fingerprint(
                    command = newCommandId,
                    leaseId = ReducerTestFixtures.lease2,
                    leaseGeneration = 2,
                ),
            ),
        )
        assertEquals(newCommandId, reducer.state.command?.commandId)
    }

    @Test
    fun `late validation outcome survives newer snapshot gating and keeps newer display`() {
        val reducer = activeReducer()
        val commandCorrelation = ReducerTestFixtures.id(51)
        reducer.issueCommand(
            commandCorrelation,
            1,
            ReducerTestFixtures.fingerprint(reps = 0),
        )

        val refresh = ReducerTestFixtures.id(52)
        reducer.issueHandshake(refresh, 2)
        reducer.receiveSnapshot(
            refresh,
            ReducerTestFixtures.active(
                revision = 2,
                leaseGeneration = 2,
                leaseId = ReducerTestFixtures.lease2,
                targetExercise = ReducerTestFixtures.exerciseB,
            ),
            3,
        )
        reducer.receiveCommandResponse(
            ReducerTestFixtures.response(
                correlationId = commandCorrelation,
                outcome = CommandOutcome.InvalidValues(
                    NumericField.REPS,
                    InvalidValueReason.BELOW_MINIMUM,
                ),
                replacement = ReducerTestFixtures.active(unavailable = true),
            ),
            4,
        )

        assertEquals(
            ReducerTestFixtures.exerciseB,
            OutlineRules.defaultSelection(activePayload(reducer))?.performedExerciseUuid,
        )
        assertNull(reducer.state.draft)
        assertEquals(CommandStatus.TERMINAL, reducer.state.command?.status)
        assertEquals(
            listOf(ReducerEvent.ErrorHaptic, ReducerEvent.FieldError(NumericField.REPS)),
            reducer.drainEvents(),
        )
    }

    @Test
    fun `applied outcome clears draft once while stale attached snapshot cannot replace newer display`() {
        val reducer = activeReducer()
        val commandCorrelation = ReducerTestFixtures.id(37)
        reducer.issueCommand(commandCorrelation, 1, ReducerTestFixtures.fingerprint())
        val refreshCorrelation = ReducerTestFixtures.id(38)
        reducer.issueHandshake(refreshCorrelation, 2)
        reducer.receiveSnapshot(
            refreshCorrelation,
            ReducerTestFixtures.active(revision = 2, leaseGeneration = 2),
            3,
        )

        val response = ReducerTestFixtures.response(
            correlationId = commandCorrelation,
            outcome = CommandOutcome.Applied,
            replacement = ReducerTestFixtures.active(
                revision = 1,
                leaseGeneration = 2,
                leaseId = ReducerTestFixtures.lease2,
            ),
        )
        reducer.receiveCommandResponse(response, 4)
        assertNull(reducer.state.draft)
        assertEquals(2, activePayload(reducer).sessionRevision)
        assertEquals(listOf(ReducerEvent.ConfirmationHaptic), reducer.drainEvents())

        reducer.receiveCommandResponse(response, 5)
        assertTrue(reducer.drainEvents().isEmpty())
    }

    @Test
    fun `same-source value rejection retains draft and requires fresh handshake`() {
        val reducer = activeReducer()
        val correlation = ReducerTestFixtures.id(39)
        reducer.issueCommand(correlation, 1, ReducerTestFixtures.fingerprint(reps = 0))
        val response = ReducerTestFixtures.response(
            correlationId = correlation,
            outcome = CommandOutcome.InvalidValues(
                NumericField.REPS,
                InvalidValueReason.BELOW_MINIMUM,
            ),
            replacement = ReducerTestFixtures.active(unavailable = true),
        )
        reducer.receiveCommandResponse(response, 2)

        assertEquals(CommandDraft(reps = 0, weightHundredthsKg = 10_000), reducer.state.draft)
        assertIs<LocalMutationAuthority.Retired>(reducer.state.authority)
        assertEquals(ActiveFreshness.REFRESH_REQUIRED, active(reducer).freshness)
        assertEquals(
            listOf(ReducerEvent.ErrorHaptic, ReducerEvent.FieldError(NumericField.REPS)),
            reducer.drainEvents(),
        )
    }

    @Test
    fun `immutable mismatch clears draft and emits no field error`() {
        val reducer = activeReducer()
        val correlation = ReducerTestFixtures.id(40)
        reducer.issueCommand(correlation, 1, ReducerTestFixtures.fingerprint())
        reducer.receiveCommandResponse(
            ReducerTestFixtures.response(
                correlation,
                CommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.EXERCISE_TYPE),
                ReducerTestFixtures.active(unavailable = true),
            ),
            2,
        )
        assertNull(reducer.state.draft)
        assertEquals(listOf(ReducerEvent.ErrorHaptic), reducer.drainEvents())
    }

    @Test
    fun `authorization expiry preserves compatible draft for a new command only`() {
        val reducer = activeReducer()
        val correlation = ReducerTestFixtures.id(41)
        reducer.issueCommand(correlation, 1, ReducerTestFixtures.fingerprint())
        reducer.receiveCommandResponse(
            ReducerTestFixtures.response(
                correlation,
                CommandOutcome.AuthorizationExpired,
                ReducerTestFixtures.active(
                    leaseGeneration = 2,
                    leaseId = ReducerTestFixtures.lease2,
                ),
            ),
            2,
        )
        assertEquals(CommandDraft(8, 10_000), reducer.state.draft)
        assertIs<LocalMutationAuthority.Available>(reducer.state.authority)
        assertEquals(CommandStatus.TERMINAL, reducer.state.command?.status)
    }

    @Test
    fun `typed retryable response rebinds successor fingerprint for attempt two`() {
        val reducer = activeReducer()
        val correlation = ReducerTestFixtures.id(42)
        reducer.issueCommand(correlation, 1, ReducerTestFixtures.fingerprint())
        reducer.receiveCommandResponse(
            ReducerTestFixtures.response(
                correlation,
                CommandOutcome.RetryableTemporaryFailure,
                ReducerTestFixtures.active(
                    leaseGeneration = 2,
                    leaseId = ReducerTestFixtures.lease2,
                ),
            ),
            2,
        )
        val rebound = assertIs<LocalMutationAuthority.AttemptBound>(reducer.state.authority)
        assertEquals(ReducerTestFixtures.lease2, rebound.leaseId)
        assertTrue(rebound.retryReady)
        assertEquals(CommandStatus.RETRY_READY, reducer.state.command?.status)
        assertIs<CommandIssueResult.Issued>(
            reducer.issueTypedRetry(ReducerTestFixtures.id(43), 3),
        )
    }

    @Test
    fun `protocol rejection clears command and cannot regain authority from replacement`() {
        val reducer = activeReducer()
        val correlation = ReducerTestFixtures.id(44)
        reducer.issueCommand(correlation, 1, ReducerTestFixtures.fingerprint())
        reducer.receiveCommandResponse(
            ReducerTestFixtures.response(
                correlation,
                CommandOutcome.ProtocolRejected(
                    ProtocolRejectionReason.INVALID_NUMERIC_ENCODING,
                ),
                ReducerTestFixtures.active(unavailable = true),
            ),
            2,
        )
        assertNull(reducer.state.draft)
        assertIs<WatchDisplayState.ProtocolMismatch>(reducer.state.display)
        assertIs<LocalMutationAuthority.Retired>(reducer.state.authority)
        assertFalse(reducer.state.refreshRequired)
        assertEquals(
            listOf(
                ReducerEvent.ErrorHaptic,
                ReducerEvent.ProtocolError(ProtocolRejectionReason.INVALID_NUMERIC_ENCODING),
            ),
            reducer.drainEvents(),
        )
    }

    @Test
    fun `invalid outcome replacement cross-pair closes protocol without applying snapshot`() {
        val reducer = activeReducer()
        val correlation = ReducerTestFixtures.id(45)
        reducer.issueCommand(correlation, 1, ReducerTestFixtures.fingerprint())
        reducer.receiveCommandResponse(
            ReducerTestFixtures.response(
                correlation,
                CommandOutcome.NoActiveSession,
                ReducerTestFixtures.active(),
            ),
            2,
        )
        assertIs<WatchDisplayState.ProtocolMismatch>(reducer.state.display)
        assertTrue(reducer.state.refreshRequired)
        assertNull(reducer.state.draft)
    }

    @Test
    fun `source invalidation outcomes enforce their complete contextual pairing matrix`() {
        val invalidCases = listOf(
            CommandOutcome.StaleRevision to ReducerTestFixtures.active(
                leaseGeneration = 2,
                leaseId = ReducerTestFixtures.lease2,
            ),
            CommandOutcome.TargetChanged to ReducerTestFixtures.active(
                session = ReducerTestFixtures.sessionB,
                revision = 0,
                leaseGeneration = 2,
                leaseId = ReducerTestFixtures.lease2,
                targetExercise = ReducerTestFixtures.exerciseB,
            ),
            CommandOutcome.TargetChanged to ReducerTestFixtures.active(
                leaseGeneration = 2,
                leaseId = ReducerTestFixtures.lease2,
            ),
            CommandOutcome.NoActiveSession to ReducerTestFixtures.noSession(
                ReducerTestFixtures.otherEpoch,
            ),
        )

        invalidCases.forEachIndexed { index, (outcome, replacement) ->
            val reducer = activeReducer()
            val correlation = ReducerTestFixtures.id(60 + index)
            reducer.issueCommand(correlation, 1, ReducerTestFixtures.fingerprint())

            reducer.receiveCommandResponse(
                ReducerTestFixtures.response(correlation, outcome, replacement),
                2,
            )

            assertIs<WatchDisplayState.ProtocolMismatch>(reducer.state.display, "case $index")
            assertNull(reducer.state.draft, "case $index")
            assertTrue(reducer.state.refreshRequired, "case $index")
        }
    }

    @Test
    fun `valid target changed pairing admits the same-source replacement target`() {
        val reducer = activeReducer()
        val correlation = ReducerTestFixtures.id(64)
        reducer.issueCommand(correlation, 1, ReducerTestFixtures.fingerprint())

        reducer.receiveCommandResponse(
            ReducerTestFixtures.response(
                correlationId = correlation,
                outcome = CommandOutcome.TargetChanged,
                replacement = ReducerTestFixtures.active(
                    leaseGeneration = 2,
                    leaseId = ReducerTestFixtures.lease2,
                    targetExercise = ReducerTestFixtures.exerciseB,
                ),
            ),
            2,
        )

        assertEquals(
            ReducerTestFixtures.exerciseB,
            OutlineRules.defaultSelection(activePayload(reducer))?.performedExerciseUuid,
        )
        assertEquals(CommandStatus.TERMINAL, reducer.state.command?.status)
        assertNull(reducer.state.draft)
    }

    /** wear-phase-2-exercise-list.md §7.1: the authority has no target; the admitted outline decides. */
    @Test
    fun `a command for any target the outline allows is issued and any other is rejected`() {
        val base = ReducerTestFixtures.fingerprint()
        val allowed = listOf(
            "rule R's set" to base,
            "a later open set" to ReducerTestFixtures.fingerprint(position = 1),
            "another exercise's open set" to
                ReducerTestFixtures.fingerprint(exercise = ReducerTestFixtures.laterExercise),
            "a skip" to base.copy(body = WatchCommandBody.SetExerciseSkipped(ReducerTestFixtures.laterExercise, true)),
        )
        val refused = listOf(
            "an unknown position" to ReducerTestFixtures.fingerprint(position = 5),
            "an exercise not in the outline" to
                ReducerTestFixtures.fingerprint(exercise = ReducerTestFixtures.exerciseB),
            "a return of a pending exercise" to
                base.copy(body = WatchCommandBody.SetExerciseSkipped(ReducerTestFixtures.exerciseA, false)),
        )
        allowed.forEachIndexed { index, (case, command) ->
            val issued = assertIs<CommandIssueResult.Issued>(
                activeReducer().issueCommand(ReducerTestFixtures.id(80 + index), 1, command),
                case,
            )
            assertEquals(command.commandId, issued.token.commandId, case)
            assertEquals(ReducerTestFixtures.id(80 + index), issued.token.correlationId, case)
        }
        refused.forEachIndexed { index, (case, command) ->
            assertIs<CommandIssueResult.Rejected>(
                activeReducer().issueCommand(ReducerTestFixtures.id(90 + index), 1, command),
                case,
            )
        }
    }

    private fun activeReducer(): WatchWorkoutReducer = WatchWorkoutReducer().also { reducer ->
        val correlation = ReducerTestFixtures.id(29)
        reducer.issueHandshake(correlation, 0)
        reducer.receiveSnapshot(correlation, ReducerTestFixtures.active(), 0)
    }

    private fun active(reducer: WatchWorkoutReducer): WatchDisplayState.Active =
        assertIs(reducer.state.display)

    private fun activePayload(reducer: WatchWorkoutReducer) =
        assertIs<SnapshotPayload.Active>(
            active(reducer).snapshot.payload,
        )
}
