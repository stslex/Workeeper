// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.FingerprintValue
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload

internal sealed interface ActiveIdentity {
    data object NoSession : ActiveIdentity
    data class Session(val sessionUuid: CanonicalUuid) : ActiveIdentity
}

internal data class WorkoutSourceVersion(
    val databaseEpoch: CanonicalUuid,
    val identity: ActiveIdentity,
    val sessionRevision: Long?,
)

internal data class TargetKey(
    val performedExerciseUuid: CanonicalUuid,
    val setPosition: Int,
)

internal sealed interface WatchDisplayState {
    data object Loading : WatchDisplayState
    data class NoSession(val databaseEpoch: CanonicalUuid) : WatchDisplayState
    data class Active(
        val snapshot: SnapshotData,
        val freshness: ActiveFreshness,
    ) : WatchDisplayState
    data class PhoneActionRequired(val snapshot: SnapshotData) : WatchDisplayState
    data class WorkoutComplete(val snapshot: SnapshotData) : WatchDisplayState
    data class ProtocolMismatch(val reason: ProtocolRejectionReason?) : WatchDisplayState
}

internal enum class ActiveFreshness {
    FRESH,
    REFRESH_REQUIRED,
    STALE,
    DISCONNECTED,
}

internal sealed interface LocalMutationAuthority {
    data class Available(
        val leaseId: CanonicalUuid,
        val leaseGeneration: Long,
        val source: WorkoutSourceVersion,
        val target: TargetKey,
        val effectiveDeadlineMs: Long,
    ) : LocalMutationAuthority

    data class AttemptBound(
        val commandId: CanonicalUuid,
        val attemptFingerprint: FingerprintValue,
        val leaseId: CanonicalUuid,
        val leaseGeneration: Long,
        val source: WorkoutSourceVersion,
        val target: TargetKey,
        val effectiveDeadlineMs: Long,
        val attemptsIssued: Int,
        val retryReady: Boolean,
    ) : LocalMutationAuthority

    data object Retired : LocalMutationAuthority
}

internal data class CommandDraft(
    val reps: Int,
    val weightHundredthsKg: Int?,
)

internal data class LogicalCommand(
    val commandId: CanonicalUuid,
    val fingerprintCommand: FingerprintCommand,
    val source: WorkoutSourceVersion,
    val target: TargetKey,
    val draft: CommandDraft,
    val localGeneration: Long,
    val attemptsIssued: Int,
    val timedOutCorrelations: Set<CanonicalUuid>,
    val consumedOutcomeCorrelations: Set<CanonicalUuid>,
    val status: CommandStatus,
)

internal enum class CommandStatus {
    IN_FLIGHT,
    TIMED_OUT_RETRYABLE,
    AWAITING_RETRY_AUTHORITY,
    RETRY_READY,
    SOURCE_INVALIDATED,
    TERMINAL,
    ABANDONED,
}

internal sealed interface ReducerEvent {
    data object ConfirmationHaptic : ReducerEvent
    data object ErrorHaptic : ReducerEvent
    data class FieldError(val field: NumericField) : ReducerEvent
    data class ProtocolError(val reason: ProtocolRejectionReason?) : ReducerEvent
}

internal data class WatchReducerState(
    val display: WatchDisplayState = WatchDisplayState.Loading,
    val authority: LocalMutationAuthority = LocalMutationAuthority.Retired,
    val draft: CommandDraft? = null,
    val command: LogicalCommand? = null,
    val events: List<ReducerEvent> = emptyList(),
    val refreshRequired: Boolean = false,
)

internal data class RequestToken(
    val correlationId: CanonicalUuid,
    val generation: Long,
    val issuedAtElapsedRealtimeMs: Long,
    val operation: RequestOperation,
    val commandId: CanonicalUuid?,
)

internal enum class RequestOperation {
    HANDSHAKE,
    COMMAND_INITIAL,
    COMMAND_RETRY,
}

internal sealed interface CommandIssueResult {
    data class Issued(val token: RequestToken) : CommandIssueResult
    data object Rejected : CommandIssueResult
}

internal fun SnapshotData.sourceVersion(): WorkoutSourceVersion = when (val state = payload) {
    is SnapshotPayload.NoSession -> WorkoutSourceVersion(
        databaseEpoch = databaseEpoch,
        identity = ActiveIdentity.NoSession,
        sessionRevision = null,
    )
    is SnapshotPayload.ActiveWithTarget -> WorkoutSourceVersion(
        databaseEpoch = databaseEpoch,
        identity = ActiveIdentity.Session(state.sessionUuid),
        sessionRevision = state.sessionRevision,
    )
    is SnapshotPayload.PhoneActionRequired -> WorkoutSourceVersion(
        databaseEpoch = databaseEpoch,
        identity = ActiveIdentity.Session(state.sessionUuid),
        sessionRevision = state.sessionRevision,
    )
    is SnapshotPayload.WorkoutComplete -> WorkoutSourceVersion(
        databaseEpoch = databaseEpoch,
        identity = ActiveIdentity.Session(state.sessionUuid),
        sessionRevision = state.sessionRevision,
    )
}

internal fun SnapshotData.targetKeyOrNull(): TargetKey? =
    (payload as? SnapshotPayload.ActiveWithTarget)?.target?.let { target ->
        TargetKey(target.performedExerciseUuid, target.setPosition)
    }
