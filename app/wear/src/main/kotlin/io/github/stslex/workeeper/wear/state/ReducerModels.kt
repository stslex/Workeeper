// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.FingerprintValue
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody

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

/** What a watch command aims at: a set to complete, or an exercise to skip or return (§7.1). */
internal sealed interface CommandTarget {
    val performedExerciseUuid: CanonicalUuid

    // The target is the selection's twin: its toString names the type only (§7.4).
    data class CompleteSet(val key: TargetKey) : CommandTarget {
        override val performedExerciseUuid: CanonicalUuid get() = key.performedExerciseUuid

        override fun toString(): String = "CommandTarget.CompleteSet"
    }

    data class SetSkipped(override val performedExerciseUuid: CanonicalUuid, val skipped: Boolean) : CommandTarget {
        override fun toString(): String = "CommandTarget.SetSkipped"
    }

    companion object {
        fun of(body: WatchCommandBody): CommandTarget = when (body) {
            is WatchCommandBody.CompleteSet -> CompleteSet(TargetKey(body.performedExerciseUuid, body.setPosition))
            is WatchCommandBody.SetExerciseSkipped -> SetSkipped(body.performedExerciseUuid, body.skipped)
        }
    }
}

internal sealed interface WatchDisplayState {
    data object Loading : WatchDisplayState
    data class NoSession(val databaseEpoch: CanonicalUuid) : WatchDisplayState
    data class Active(
        val snapshot: SnapshotData,
        val freshness: ActiveFreshness,
    ) : WatchDisplayState
    data class PayloadTooLarge(val snapshot: SnapshotData) : WatchDisplayState
    data class ProtocolMismatch(val reason: ProtocolRejectionReason?) : WatchDisplayState
}

internal enum class ActiveFreshness {
    FRESH,
    REFRESH_REQUIRED,
    STALE,
    DISCONNECTED,
}

internal sealed interface LocalMutationAuthority {
    /** A session lease: any command the admitted outline allows, at its revision (V3). */
    data class Available(
        val leaseId: CanonicalUuid,
        val leaseGeneration: Long,
        val source: WorkoutSourceVersion,
        val effectiveDeadlineMs: Long,
    ) : LocalMutationAuthority

    data class AttemptBound(
        val commandId: CanonicalUuid,
        val attemptFingerprint: FingerprintValue,
        val leaseId: CanonicalUuid,
        val leaseGeneration: Long,
        val source: WorkoutSourceVersion,
        /** The command's target, for the in-flight display only. */
        val target: CommandTarget,
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
    val target: CommandTarget,
    /** The values a `complete_set` sends; null for a skip or a return. */
    val draft: CommandDraft?,
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
    is SnapshotPayload.Active -> WorkoutSourceVersion(
        databaseEpoch = databaseEpoch,
        identity = ActiveIdentity.Session(state.sessionUuid),
        sessionRevision = state.sessionRevision,
    )
    is SnapshotPayload.PayloadTooLarge -> WorkoutSourceVersion(
        databaseEpoch = databaseEpoch,
        identity = ActiveIdentity.Session(state.sessionUuid),
        sessionRevision = state.sessionRevision,
    )
}

/**
 * True when this snapshot is the outline a command was built on: the same source, which the database
 * triggers make the same outline (§7.1). A `PayloadTooLarge` snapshot never matches.
 */
internal fun SnapshotData.matchesCommandSource(source: WorkoutSourceVersion): Boolean =
    payload !is SnapshotPayload.PayloadTooLarge && sourceVersion() == source

/** The admitted outline's `Active` payload, or null for every other display. */
internal fun WatchDisplayState.activeOutlineOrNull(): SnapshotPayload.Active? =
    (this as? WatchDisplayState.Active)?.snapshot?.payload as? SnapshotPayload.Active
