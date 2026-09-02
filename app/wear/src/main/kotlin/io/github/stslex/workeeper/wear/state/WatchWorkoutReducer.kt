// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.FingerprintPurpose
import io.github.stslex.workeeper.core.wear.protocol.FingerprintV1
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.ProtocolPairingValidator
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import kotlin.math.max
import kotlin.math.min

internal data class SnapshotReduction(
    val accepted: Boolean,
    val effectiveMutationWindowMs: Long?,
)

/** Single watch-process authority owner shared by Tile, controller, cache, and transport. */
@Suppress("TooManyFunctions") // Authority and command transitions stay in one auditable state owner.
internal class WatchWorkoutReducer {

    var state: WatchReducerState = WatchReducerState()
        private set

    private var latestIssuedGeneration: Long = 0L
    private var admittedMeta: AdmittedSnapshotMeta? = null
    private var admittedSnapshot: SnapshotData? = null
    private val requests = linkedMapOf<CanonicalUuid, RequestToken>()
    private val commands = linkedMapOf<CanonicalUuid, LogicalCommand>()

    fun issueHandshake(correlationId: CanonicalUuid, issuedAtElapsedRealtimeMs: Long): RequestToken {
        require(issuedAtElapsedRealtimeMs >= 0L)
        latestIssuedGeneration = Math.addExact(latestIssuedGeneration, 1L)
        retireAttemptAuthority()
        invalidateCommandForNewAuthorityRequest()
        val token = RequestToken(
            correlationId = correlationId,
            generation = latestIssuedGeneration,
            issuedAtElapsedRealtimeMs = issuedAtElapsedRealtimeMs,
            operation = RequestOperation.HANDSHAKE,
            commandId = null,
        )
        register(token)
        state = state.copy(refreshRequired = false)
        return token
    }

    fun issueCommand(
        correlationId: CanonicalUuid,
        issuedAtElapsedRealtimeMs: Long,
        fingerprintCommand: FingerprintCommand,
    ): CommandIssueResult {
        val available = state.authority as? LocalMutationAuthority.Available
            ?: return CommandIssueResult.Rejected
        if (issuedAtElapsedRealtimeMs !in 0 until available.effectiveDeadlineMs) {
            expireAuthority(issuedAtElapsedRealtimeMs)
            return CommandIssueResult.Rejected
        }
        if (!fingerprintMatchesAuthority(fingerprintCommand, available)) {
            return CommandIssueResult.Rejected
        }
        val current = state.command
        if (current != null && current.status !in TERMINAL_COMMAND_STATUSES) {
            return CommandIssueResult.Rejected
        }

        latestIssuedGeneration = Math.addExact(latestIssuedGeneration, 1L)
        val fingerprint = FingerprintV1.fingerprint(
            fingerprintCommand,
            FingerprintPurpose.DELIVERY_ATTEMPT,
        )
        val token = RequestToken(
            correlationId = correlationId,
            generation = latestIssuedGeneration,
            issuedAtElapsedRealtimeMs = issuedAtElapsedRealtimeMs,
            operation = RequestOperation.COMMAND_INITIAL,
            commandId = fingerprintCommand.commandId,
        )
        val command = LogicalCommand(
            commandId = fingerprintCommand.commandId,
            fingerprintCommand = fingerprintCommand,
            source = available.source,
            target = available.target,
            draft = CommandDraft(
                reps = fingerprintCommand.reps,
                weightHundredthsKg = fingerprintCommand.weightHundredthsKg,
            ),
            localGeneration = latestIssuedGeneration,
            attemptsIssued = 1,
            timedOutCorrelations = emptySet(),
            consumedOutcomeCorrelations = emptySet(),
            status = CommandStatus.IN_FLIGHT,
        )
        commands[command.commandId] = command
        trimCommands()
        register(token)
        state = state.copy(
            authority = LocalMutationAuthority.AttemptBound(
                commandId = command.commandId,
                attemptFingerprint = fingerprint,
                leaseId = available.leaseId,
                leaseGeneration = available.leaseGeneration,
                source = available.source,
                target = available.target,
                effectiveDeadlineMs = available.effectiveDeadlineMs,
                attemptsIssued = 1,
                retryReady = false,
            ),
            draft = command.draft,
            command = command,
        )
        return CommandIssueResult.Issued(token)
    }

    fun receiveSnapshot(
        correlationId: CanonicalUuid,
        snapshot: SnapshotData,
        receivedAtElapsedRealtimeMs: Long,
    ): SnapshotReduction {
        val token = requests[correlationId]
            ?.takeIf { it.operation == RequestOperation.HANDSHAKE }
            ?: return SnapshotReduction(accepted = false, effectiveMutationWindowMs = null)
        requests.remove(correlationId)
        return applySnapshot(
            snapshot = snapshot,
            token = token,
            receivedAtElapsedRealtimeMs = receivedAtElapsedRealtimeMs,
            unsolicited = false,
        )
    }

    fun receiveUnsolicited(snapshot: SnapshotData): SnapshotReduction {
        val reduction = applySnapshot(
            snapshot = snapshot,
            token = null,
            receivedAtElapsedRealtimeMs = 0L,
            unsolicited = true,
        )
        state = state.copy(refreshRequired = true)
        return reduction
    }

    fun receiveCommandResponse(
        response: CompleteCurrentSetResponse,
        receivedAtElapsedRealtimeMs: Long,
    ) {
        val token = requests[response.correlationId]
            ?.takeIf { it.operation != RequestOperation.HANDSHAKE }
            ?.takeIf { it.commandId == response.commandId }
            ?: return
        val existing = commands[response.commandId] ?: return
        requests.remove(response.correlationId)
        if (response.correlationId in existing.consumedOutcomeCorrelations) return
        if (existing.status in CLOSED_COMMAND_STATUSES) {
            commands[existing.commandId] = existing.copy(
                consumedOutcomeCorrelations = existing.consumedOutcomeCorrelations + response.correlationId,
            )
            return
        }

        if (!responsePairingIsValid(response, existing)) {
            closeAsProtocolMismatch(existing, response.correlationId, reason = null)
            return
        }

        retireMatchingAttempt(existing.commandId)
        var command = existing.copy(
            consumedOutcomeCorrelations = existing.consumedOutcomeCorrelations + response.correlationId,
        )
        command = reduceOutcomeBeforeSnapshot(command, response.outcome)
        commands[command.commandId] = command
        updateVisibleCommand(command)

        val reduction = applySnapshot(
            snapshot = response.replacement,
            token = token,
            receivedAtElapsedRealtimeMs = receivedAtElapsedRealtimeMs,
            unsolicited = false,
        )
        command = commands[command.commandId] ?: command
        command = reduceOutcomeAfterSnapshot(command, response.outcome, reduction.accepted)
        commands[command.commandId] = command
        updateVisibleCommand(command)
        if (command.status in CLOSED_COMMAND_STATUSES) {
            removeRequestsForCommand(command.commandId)
        }

        if (!reduction.accepted && response.outcome.requiresConvergence()) {
            state = state.copy(refreshRequired = true)
        }
        val protocolRejected = response.outcome as? CompleteCommandOutcome.ProtocolRejected
        if (protocolRejected != null) {
            state = state.copy(
                display = WatchDisplayState.ProtocolMismatch(protocolRejected.reason),
                authority = LocalMutationAuthority.Retired,
                draft = null,
                refreshRequired = !reduction.accepted,
            )
        }
    }

    fun onTransportTimeout(correlationId: CanonicalUuid, nowElapsedRealtimeMs: Long) {
        val token = requests[correlationId]
            ?.takeIf { it.operation != RequestOperation.HANDSHAKE }
            ?: return
        val commandId = token.commandId ?: return
        val command = commands[commandId] ?: return
        if (correlationId in command.consumedOutcomeCorrelations ||
            correlationId in command.timedOutCorrelations
        ) {
            return
        }
        val updated = if (command.attemptsIssued >= MAX_DELIVERY_ATTEMPTS) {
            retireMatchingAttempt(commandId)
            state = state.copy(refreshRequired = true)
            command.copy(
                timedOutCorrelations = command.timedOutCorrelations + correlationId,
                status = CommandStatus.ABANDONED,
            )
        } else {
            if (nowElapsedRealtimeMs >= authorityDeadline(commandId)) {
                retireMatchingAttempt(commandId)
                state = state.copy(refreshRequired = true)
                command.copy(
                    timedOutCorrelations = command.timedOutCorrelations + correlationId,
                    status = CommandStatus.ABANDONED,
                )
            } else {
                command.copy(
                    timedOutCorrelations = command.timedOutCorrelations + correlationId,
                    status = CommandStatus.TIMED_OUT_RETRYABLE,
                )
            }
        }
        commands[commandId] = updated
        updateVisibleCommand(updated)
        if (updated.status == CommandStatus.ABANDONED) {
            removeRequestsForCommand(commandId)
        }
        emit(ReducerEvent.ErrorHaptic)
    }

    fun issueTimeoutRetry(
        correlationId: CanonicalUuid,
        issuedAtElapsedRealtimeMs: Long,
    ): CommandIssueResult = issueRetry(
        correlationId = correlationId,
        issuedAtElapsedRealtimeMs = issuedAtElapsedRealtimeMs,
        requiredStatus = CommandStatus.TIMED_OUT_RETRYABLE,
        requireRetryReadyBinding = false,
    )

    fun issueTypedRetry(
        correlationId: CanonicalUuid,
        issuedAtElapsedRealtimeMs: Long,
    ): CommandIssueResult = issueRetry(
        correlationId = correlationId,
        issuedAtElapsedRealtimeMs = issuedAtElapsedRealtimeMs,
        requiredStatus = CommandStatus.RETRY_READY,
        requireRetryReadyBinding = true,
    )

    fun markDisconnected() {
        retireAttemptAuthority()
        val active = state.display as? WatchDisplayState.Active ?: return
        state = state.copy(display = active.copy(freshness = ActiveFreshness.DISCONNECTED))
    }

    fun expireAuthority(nowElapsedRealtimeMs: Long) {
        val deadline = when (val authority = state.authority) {
            is LocalMutationAuthority.Available -> authority.effectiveDeadlineMs
            is LocalMutationAuthority.AttemptBound -> authority.effectiveDeadlineMs
            is LocalMutationAuthority.Retired -> return
        }
        if (nowElapsedRealtimeMs < deadline) return
        state = state.copy(authority = LocalMutationAuthority.Retired)
        val active = state.display as? WatchDisplayState.Active ?: return
        state = state.copy(display = active.copy(freshness = ActiveFreshness.STALE))
    }

    fun drainEvents(): List<ReducerEvent> = state.events.also {
        state = state.copy(events = emptyList())
    }

    private fun issueRetry(
        correlationId: CanonicalUuid,
        issuedAtElapsedRealtimeMs: Long,
        requiredStatus: CommandStatus,
        requireRetryReadyBinding: Boolean,
    ): CommandIssueResult {
        val bound = state.authority as? LocalMutationAuthority.AttemptBound
            ?: return CommandIssueResult.Rejected
        val command = commands[bound.commandId]
            ?.takeIf { it.status == requiredStatus }
            ?: return CommandIssueResult.Rejected
        if (!retryIsCompatible(
                bound = bound,
                command = command,
                issuedAtElapsedRealtimeMs = issuedAtElapsedRealtimeMs,
                requireRetryReadyBinding = requireRetryReadyBinding,
                latestIssuedGeneration = latestIssuedGeneration,
            )
        ) {
            return CommandIssueResult.Rejected
        }
        val token = RequestToken(
            correlationId = correlationId,
            generation = command.localGeneration,
            issuedAtElapsedRealtimeMs = issuedAtElapsedRealtimeMs,
            operation = RequestOperation.COMMAND_RETRY,
            commandId = command.commandId,
        )
        val updated = command.copy(attemptsIssued = 2, status = CommandStatus.IN_FLIGHT)
        commands[command.commandId] = updated
        register(token)
        state = state.copy(
            authority = bound.copy(attemptsIssued = 2, retryReady = false),
            command = updated,
        )
        return CommandIssueResult.Issued(token)
    }

    private fun applySnapshot(
        snapshot: SnapshotData,
        token: RequestToken?,
        receivedAtElapsedRealtimeMs: Long,
        unsolicited: Boolean,
    ): SnapshotReduction {
        val generation = token?.generation ?: Long.MIN_VALUE
        val admission = SnapshotAdmissionPolicy.decide(
            current = admittedMeta,
            incoming = snapshot,
            incomingGeneration = generation,
            latestIssuedGeneration = latestIssuedGeneration,
            unsolicited = unsolicited,
        )
        if (admission == SnapshotAdmission.REJECT) {
            return SnapshotReduction(accepted = false, effectiveMutationWindowMs = null)
        }

        val active = snapshot.payload as? SnapshotPayload.ActiveWithTarget
        val granted = active?.mutationAuthority as? MutationAuthority.Granted
        val effectiveWindow = if (admission == SnapshotAdmission.AUTHORITY_ELIGIBLE &&
            granted != null && token != null
        ) {
            effectiveWindow(token, receivedAtElapsedRealtimeMs, granted.leaseRemainingAtPhoneSendMs)
        } else {
            null
        }
        val canInstallAuthority = effectiveWindow != null && effectiveWindow > 0L
        val deadline = effectiveWindow?.let { safeMonotonicAdd(receivedAtElapsedRealtimeMs, it) }
        val source = snapshot.sourceVersion()
        val leaseGeneration = granted?.mutationLeaseGeneration
        admittedMeta = AdmittedSnapshotMeta(source, generation, leaseGeneration)
        admittedSnapshot = snapshot

        val completeGrantedAuthority = canInstallAuthority && active != null &&
            granted != null && deadline != null
        state = state.copy(
            display = displayState(snapshot, canInstallAuthority),
            authority = if (completeGrantedAuthority) {
                val requiredGranted = requireNotNull(granted)
                LocalMutationAuthority.Available(
                    leaseId = requiredGranted.mutationLeaseId,
                    leaseGeneration = requiredGranted.mutationLeaseGeneration,
                    source = source,
                    target = requireNotNull(snapshot.targetKeyOrNull()),
                    effectiveDeadlineMs = requireNotNull(deadline),
                )
            } else {
                LocalMutationAuthority.Retired
            },
            refreshRequired = active?.mutationAuthority is MutationAuthority.Unavailable ||
                (granted != null && !canInstallAuthority),
        )
        invalidateObsoleteCommand(snapshot)
        return SnapshotReduction(
            accepted = true,
            effectiveMutationWindowMs = effectiveWindow?.takeIf { it > 0L },
        )
    }

    private fun displayState(snapshot: SnapshotData, mutable: Boolean): WatchDisplayState =
        when (snapshot.payload) {
            is SnapshotPayload.NoSession -> WatchDisplayState.NoSession(snapshot.databaseEpoch)
            is SnapshotPayload.ActiveWithTarget -> WatchDisplayState.Active(
                snapshot = snapshot,
                freshness = if (mutable) ActiveFreshness.FRESH else ActiveFreshness.REFRESH_REQUIRED,
            )
            is SnapshotPayload.PhoneActionRequired -> WatchDisplayState.PhoneActionRequired(snapshot)
            is SnapshotPayload.WorkoutComplete -> WatchDisplayState.WorkoutComplete(snapshot)
        }

    private fun effectiveWindow(token: RequestToken, receivedAt: Long, phoneRemaining: Long): Long? {
        if (receivedAt < token.issuedAtElapsedRealtimeMs) return null
        val rtt = receivedAt - token.issuedAtElapsedRealtimeMs
        val safeRemaining = max(0L, phoneRemaining - min(phoneRemaining, rtt))
        return min(WearProtocol.MAX_MUTATION_WINDOW_MS, safeRemaining).takeIf { it > 0L }
    }

    private fun reduceOutcomeBeforeSnapshot(
        command: LogicalCommand,
        outcome: CompleteCommandOutcome,
    ): LogicalCommand = when (outcome) {
        is CompleteCommandOutcome.Applied,
        is CompleteCommandOutcome.AlreadyApplied,
        -> command.terminal(clearDraft = true).also { emit(ReducerEvent.ConfirmationHaptic) }
        is CompleteCommandOutcome.StaleRevision,
        is CompleteCommandOutcome.TargetChanged,
        is CompleteCommandOutcome.NoActiveSession,
        -> command.terminal(clearDraft = true)
        is CompleteCommandOutcome.AuthorizationExpired ->
            command.copy(status = CommandStatus.TERMINAL).also { emit(ReducerEvent.ErrorHaptic) }
        is CompleteCommandOutcome.InvalidValues ->
            command.copy(status = CommandStatus.TERMINAL).also {
                emit(ReducerEvent.ErrorHaptic)
                emit(ReducerEvent.FieldError(outcome.field))
            }
        is CompleteCommandOutcome.ImmutableTypeMismatch ->
            command.terminal(clearDraft = true).also { emit(ReducerEvent.ErrorHaptic) }
        is CompleteCommandOutcome.RetryableTemporaryFailure ->
            command.copy(status = CommandStatus.AWAITING_RETRY_AUTHORITY)
                .also { emit(ReducerEvent.ErrorHaptic) }
        is CompleteCommandOutcome.ProtocolRejected ->
            command.terminal(clearDraft = true).also {
                emit(ReducerEvent.ErrorHaptic)
                emit(ReducerEvent.ProtocolError(outcome.reason))
            }
    }

    private fun reduceOutcomeAfterSnapshot(
        command: LogicalCommand,
        outcome: CompleteCommandOutcome,
        snapshotAccepted: Boolean,
    ): LogicalCommand = when (outcome) {
        is CompleteCommandOutcome.AuthorizationExpired -> {
            if (currentMatches(command)) command else command.terminal(clearDraft = true)
        }
        is CompleteCommandOutcome.InvalidValues -> {
            if (currentMatches(command)) command else command.terminal(clearDraft = true)
        }
        is CompleteCommandOutcome.RetryableTemporaryFailure -> {
            rebindRetryable(command, snapshotAccepted)
        }
        else -> command
    }

    private fun rebindRetryable(command: LogicalCommand, snapshotAccepted: Boolean): LogicalCommand {
        val available = state.authority as? LocalMutationAuthority.Available
        if (!retryableSuccessorMatches(snapshotAccepted, available, command)) {
            state = state.copy(
                authority = LocalMutationAuthority.Retired,
                refreshRequired = true,
            )
            return command.copy(status = CommandStatus.TERMINAL)
        }
        val successor = requireNotNull(available)
        val reboundSource = command.fingerprintCommand.copy(
            mutationLeaseId = successor.leaseId,
            mutationLeaseGeneration = successor.leaseGeneration,
        )
        val reboundFingerprint = FingerprintV1.fingerprint(
            reboundSource,
            FingerprintPurpose.DELIVERY_ATTEMPT,
        )
        state = state.copy(
            authority = LocalMutationAuthority.AttemptBound(
                commandId = command.commandId,
                attemptFingerprint = reboundFingerprint,
                leaseId = successor.leaseId,
                leaseGeneration = successor.leaseGeneration,
                source = successor.source,
                target = successor.target,
                effectiveDeadlineMs = successor.effectiveDeadlineMs,
                attemptsIssued = 1,
                retryReady = true,
            ),
        )
        return command.copy(
            fingerprintCommand = reboundSource,
            status = CommandStatus.RETRY_READY,
        )
    }

    private fun LogicalCommand.terminal(clearDraft: Boolean): LogicalCommand {
        if (clearDraft && state.command?.commandId == commandId) {
            state = state.copy(draft = null)
        }
        return copy(status = CommandStatus.TERMINAL)
    }

    private fun invalidateObsoleteCommand(snapshot: SnapshotData) {
        val visible = state.command ?: return
        val sourceMatches = snapshot.sourceVersion() == visible.source
        val targetMatches = snapshot.targetKeyOrNull() == visible.target
        if (visible.status == CommandStatus.SOURCE_INVALIDATED) {
            if (!sourceMatches || !targetMatches) {
                state = state.copy(draft = null)
            }
            return
        }
        if (visible.status in CLOSED_COMMAND_STATUSES) return
        if (sourceMatches && targetMatches) return
        val invalidated = visible.copy(status = CommandStatus.SOURCE_INVALIDATED)
        commands[visible.commandId] = invalidated
        state = state.copy(command = invalidated, draft = null)
    }

    private fun invalidateCommandForNewAuthorityRequest() {
        val visible = state.command ?: return
        if (visible.status in TERMINAL_COMMAND_STATUSES) return
        val invalidated = visible.copy(status = CommandStatus.SOURCE_INVALIDATED)
        commands[visible.commandId] = invalidated
        state = state.copy(command = invalidated)
    }

    private fun currentMatches(command: LogicalCommand): Boolean =
        admittedSnapshot?.sourceVersion() == command.source &&
            admittedSnapshot?.targetKeyOrNull() == command.target

    private fun responsePairingIsValid(
        response: CompleteCurrentSetResponse,
        command: LogicalCommand,
    ): Boolean {
        if (!ProtocolPairingValidator.isValid(response.outcome, response.replacement.payload)) {
            return false
        }
        val replacementSource = response.replacement.sourceVersion()
        return when (response.outcome) {
            is CompleteCommandOutcome.StaleRevision -> replacementSource != command.source
            is CompleteCommandOutcome.NoActiveSession ->
                replacementSource.databaseEpoch == command.source.databaseEpoch
            is CompleteCommandOutcome.TargetChanged ->
                replacementSource == command.source &&
                    response.replacement.targetKeyOrNull() != command.target
            else -> true
        }
    }

    private fun fingerprintMatchesAuthority(
        fingerprint: FingerprintCommand,
        authority: LocalMutationAuthority.Available,
    ): Boolean = fingerprint.databaseEpoch == authority.source.databaseEpoch &&
        fingerprint.sessionUuid == (authority.source.identity as? ActiveIdentity.Session)?.sessionUuid &&
        fingerprint.sessionRevision == authority.source.sessionRevision &&
        fingerprint.performedExerciseUuid == authority.target.performedExerciseUuid &&
        fingerprint.setPosition == authority.target.setPosition &&
        fingerprint.mutationLeaseId == authority.leaseId &&
        fingerprint.mutationLeaseGeneration == authority.leaseGeneration

    private fun retireMatchingAttempt(commandId: CanonicalUuid) {
        val bound = state.authority as? LocalMutationAuthority.AttemptBound ?: return
        if (bound.commandId == commandId) {
            state = state.copy(authority = LocalMutationAuthority.Retired)
        }
    }

    private fun retireAttemptAuthority() {
        if (state.authority !is LocalMutationAuthority.Retired) {
            state = state.copy(authority = LocalMutationAuthority.Retired)
        }
    }

    private fun authorityDeadline(commandId: CanonicalUuid): Long =
        (state.authority as? LocalMutationAuthority.AttemptBound)
            ?.takeIf { it.commandId == commandId }
            ?.effectiveDeadlineMs
            ?: Long.MIN_VALUE

    private fun closeAsProtocolMismatch(
        command: LogicalCommand,
        correlationId: CanonicalUuid,
        reason: io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason?,
    ) {
        val closed = command.copy(
            status = CommandStatus.TERMINAL,
            consumedOutcomeCorrelations = command.consumedOutcomeCorrelations + correlationId,
        )
        commands[command.commandId] = closed
        removeRequestsForCommand(command.commandId)
        retireMatchingAttempt(command.commandId)
        state = state.copy(
            display = WatchDisplayState.ProtocolMismatch(reason),
            authority = LocalMutationAuthority.Retired,
            draft = null,
            command = closed,
            refreshRequired = true,
        )
        emit(ReducerEvent.ProtocolError(reason))
    }

    private fun updateVisibleCommand(command: LogicalCommand) {
        if (state.command?.commandId == command.commandId) {
            state = state.copy(command = command)
        }
    }

    private fun emit(event: ReducerEvent) {
        state = state.copy(events = state.events + event)
    }

    private fun register(token: RequestToken) {
        require(token.correlationId !in requests) { "Correlation IDs are single-use" }
        requests[token.correlationId] = token
        while (requests.size > MAX_OUTSTANDING_REQUESTS) {
            val removable = requests.entries.firstOrNull { (_, candidate) ->
                candidate.operation == RequestOperation.HANDSHAKE ||
                    candidate.commandId?.let { commandId ->
                        commands[commandId]?.status in TERMINAL_COMMAND_STATUSES
                    } == true
            } ?: error("Outstanding command correlation bound exceeded")
            requests.remove(removable.key)
        }
    }

    private fun removeRequestsForCommand(commandId: CanonicalUuid) {
        requests.entries.removeAll { (_, token) -> token.commandId == commandId }
    }

    private fun trimCommands() {
        while (commands.size > MAX_TRACKED_COMMANDS) {
            val removable = commands.entries.firstOrNull { (_, command) ->
                command.status in TERMINAL_COMMAND_STATUSES
            } ?: error("Tracked command bound exceeded")
            removeRequestsForCommand(removable.key)
            commands.remove(removable.key)
        }
    }

    private companion object {
        const val MAX_DELIVERY_ATTEMPTS = 2
        const val MAX_OUTSTANDING_REQUESTS = 64
        const val MAX_TRACKED_COMMANDS = 64
        val CLOSED_COMMAND_STATUSES = setOf(
            CommandStatus.TERMINAL,
            CommandStatus.ABANDONED,
        )
        val TERMINAL_COMMAND_STATUSES = setOf(
            CommandStatus.SOURCE_INVALIDATED,
        ) + CLOSED_COMMAND_STATUSES
    }
}

internal fun safeMonotonicAdd(left: Long, right: Long): Long? = runCatching {
    Math.addExact(left, right)
}.getOrNull()

internal fun retryIsCompatible(
    bound: LocalMutationAuthority.AttemptBound,
    command: LogicalCommand,
    issuedAtElapsedRealtimeMs: Long,
    requireRetryReadyBinding: Boolean,
    latestIssuedGeneration: Long,
): Boolean = bound.retryReady == requireRetryReadyBinding &&
    bound.attemptsIssued == 1 &&
    latestIssuedGeneration == command.localGeneration &&
    issuedAtElapsedRealtimeMs in 0 until bound.effectiveDeadlineMs

internal fun retryableSuccessorMatches(
    snapshotAccepted: Boolean,
    available: LocalMutationAuthority.Available?,
    command: LogicalCommand,
): Boolean = snapshotAccepted && available != null &&
    available.source == command.source &&
    available.target == command.target &&
    command.attemptsIssued == 1

internal fun CompleteCommandOutcome.requiresConvergence(): Boolean = when (this) {
    is CompleteCommandOutcome.Applied,
    is CompleteCommandOutcome.AlreadyApplied,
    is CompleteCommandOutcome.AuthorizationExpired,
    is CompleteCommandOutcome.StaleRevision,
    is CompleteCommandOutcome.TargetChanged,
    is CompleteCommandOutcome.NoActiveSession,
    is CompleteCommandOutcome.ProtocolRejected,
    -> true
    is CompleteCommandOutcome.InvalidValues,
    is CompleteCommandOutcome.ImmutableTypeMismatch,
    is CompleteCommandOutcome.RetryableTemporaryFailure,
    -> false
}
