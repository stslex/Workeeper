// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import androidx.sqlite.SQLiteException
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.session.ExternalSessionWrite
import io.github.stslex.workeeper.core.data.exercise.session.ExternalSessionWrites
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CommandRouting
import io.github.stslex.workeeper.core.wear.protocol.CommandValidation
import io.github.stslex.workeeper.core.wear.protocol.EnvelopeTooLargeException
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.FingerprintPurpose
import io.github.stslex.workeeper.core.wear.protocol.FingerprintV2
import io.github.stslex.workeeper.core.wear.protocol.FingerprintValue
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.ImmutableTypeField
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.OutlineRules
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.UnsupportedFingerprintVersionException
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandRequest
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandResponse
import io.github.stslex.workeeper.core.wear.protocol.WearEnvelope
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import io.github.stslex.workeeper.core.wear.protocol.exercise
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedHashMap
import kotlin.uuid.Uuid

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class PhoneWorkoutBridgeImpl @Inject internal constructor(
    private val database: AppDatabase,
    private val transition: DbTransitionRunner,
    private val snapshotBuilder: PhoneWorkoutSnapshotBuilder,
    private val leaseStore: WearMutationLeaseStore,
    private val clock: PhoneMonotonicClock,
    private val writers: WearMutationWriters,
    private val knownRevisions: WatchKnownRevisions,
    private val externalSessionWrites: ExternalSessionWrites,
) : PhoneWorkoutBridge {

    /** Empty: no owner gate is open (wear-phase-1-active-workout-tile.md §6.1). */
    override val transportStatus: Set<WearPayloadTransportStatus> = emptySet()

    private val coordinatorMutex = Mutex()
    private val snapshotResponses =
        BoundedResponseMap<SnapshotRequestKey, ActiveWorkoutSnapshotResponse>()
    private val commandResponses =
        BoundedResponseMap<CommandRequestKey, WatchCommandResponse>()

    override suspend fun getActiveWorkout(
        authenticatedSourceNodeId: String,
        request: GetActiveWorkoutRequest,
    ): ActiveWorkoutSnapshotResponse = coordinatorMutex.withLock {
        requireRequest(request.schemaVersion, authenticatedSourceNodeId)
        val key = SnapshotRequestKey(authenticatedSourceNodeId, request.correlationId)
        snapshotResponses[key]?.let { return@withLock knownRevisions.shown(authenticatedSourceNodeId, it) }
        val prepared = transition.mutate {
            val epoch = currentEpoch()
            prepareSnapshotResponse(
                sourceNodeId = authenticatedSourceNodeId,
                correlationId = request.correlationId,
                base = snapshotBuilder.build(epoch),
            )
        }
        val response = publishSnapshotOrRefreshReadOnly(request.correlationId, prepared)
        snapshotResponses.put(key, response)
        knownRevisions.shown(authenticatedSourceNodeId, response)
    }

    override suspend fun executeCommand(
        authenticatedSourceNodeId: String,
        request: WatchCommandRequest,
    ): WatchCommandResponse = coordinatorMutex.withLock {
        requireRequest(request.schemaVersion, authenticatedSourceNodeId)
        val fingerprintCommand = request.toFingerprintCommand(authenticatedSourceNodeId)
        val stableFingerprint = FingerprintV2.fingerprint(
            fingerprintCommand,
            FingerprintPurpose.STABLE_INTENT,
        )
        val attemptFingerprint = FingerprintV2.fingerprint(
            fingerprintCommand,
            FingerprintPurpose.DELIVERY_ATTEMPT,
        )
        val key = CommandAttemptKey(
            sourceNodeId = authenticatedSourceNodeId,
            correlationId = request.correlationId,
            attemptFingerprint = attemptFingerprint,
        )
        commandResponses[key]?.let { return@withLock knownRevisions.shown(authenticatedSourceNodeId, it) }
        val prepared = try {
            transition.mutate {
                processCommand(
                    sourceNodeId = authenticatedSourceNodeId,
                    request = request,
                    stableFingerprint = stableFingerprint,
                    attemptFingerprint = attemptFingerprint,
                )
            }
        } catch (_: SQLiteException) {
            transition.mutate {
                prepareAfterWriteFailure(
                    sourceNodeId = authenticatedSourceNodeId,
                    request = request,
                    stableFingerprint = stableFingerprint.encoded,
                )
            }
        }
        // wear-live-sync.md §6.4, wear-phase-2-exercise-list.md §6.3: only after mutate returned an
        // Applied write; in commit order, because this runs under coordinatorMutex.
        prepared.applied?.let(externalSessionWrites::publish)
        prepared.retire?.let { retired ->
            leaseStore.retireMatching(
                sourceNodeId = retired.sourceNodeId,
                sessionUuid = retired.sessionUuid,
                leaseId = retired.leaseId,
                leaseGeneration = retired.leaseGeneration,
            )
        }
        val response = publishCommandOrRefreshReadOnly(prepared)
        commandResponses.put(key, response)
        knownRevisions.shown(authenticatedSourceNodeId, response)
    }

    override suspend fun protocolRejected(
        authenticatedSourceNodeId: String,
        routing: CommandRouting,
        reason: ProtocolRejectionReason,
    ): WatchCommandResponse = coordinatorMutex.withLock {
        requireRequest(routing.schemaVersion, authenticatedSourceNodeId)
        val key = ProtocolRejectedRequestKey(
            sourceNodeId = authenticatedSourceNodeId,
            routing = routing,
            reason = reason,
        )
        commandResponses[key]?.let { return@withLock knownRevisions.shown(authenticatedSourceNodeId, it) }
        val response = transition {
            readOnlyCommandResponse(
                correlationId = routing.correlationId,
                commandId = routing.commandId,
                outcome = CommandOutcome.ProtocolRejected(reason),
                base = snapshotBuilder.build(currentEpoch()),
            )
        }
        leaseStore.retireMatching(
            sourceNodeId = authenticatedSourceNodeId,
            sessionUuid = routing.sessionUuid,
            leaseId = routing.mutationLeaseId,
            leaseGeneration = routing.mutationLeaseGeneration,
        )
        commandResponses.put(key, response)
        knownRevisions.shown(authenticatedSourceNodeId, response)
    }

    // Keep the security-sensitive gateway order visible and contiguous with specification §5.4.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun processCommand(
        sourceNodeId: String,
        request: WatchCommandRequest,
        stableFingerprint: FingerprintValue,
        attemptFingerprint: FingerprintValue,
    ): PreparedCommandResponse {
        val epoch = currentEpoch()
        val base = snapshotBuilder.build(epoch)
        val retirement = request.retirement(sourceNodeId)
        if (request.databaseEpoch != epoch) {
            return prepareCommandResponse(sourceNodeId, request, CommandOutcome.StaleRevision, base, retirement)
        }

        val payload = base.payload
        if (payload is SnapshotPayload.NoSession) {
            return PreparedCommandResponse(
                response = readOnlyCommandResponse(
                    request.correlationId,
                    request.commandId,
                    CommandOutcome.NoActiveSession,
                    base,
                ),
                retire = retirement,
            )
        }
        val activeIdentity = requireNotNull(payload.sessionIdentityOrNull())
        if (activeIdentity.first != request.sessionUuid) {
            return prepareCommandResponse(sourceNodeId, request, CommandOutcome.StaleRevision, base, retirement)
        }

        // Bound to the session the snapshot just resolved. Nothing enforces that only one session
        // is IN_PROGRESS, and an independent unordered `LIMIT 1` over that predicate could answer
        // with a different row — leaving the outline from one session while the revision, the
        // receipt and the performed lookup below all came from another.
        val sync = requireNotNull(
            database.wearSyncDao.getSessionSync(Uuid.parse(activeIdentity.first.value)),
        )
        val receiptOutcome = receiptOutcome(sync, epoch, request, attemptFingerprint)
        if (receiptOutcome != null) {
            return when (receiptOutcome) {
                CommandOutcome.AlreadyApplied ->
                    prepareCommandResponse(sourceNodeId, request, receiptOutcome, base, retirement)
                else -> PreparedCommandResponse(
                    response = readOnlyCommandResponse(
                        request.correlationId,
                        request.commandId,
                        receiptOutcome,
                        base,
                    ),
                    retire = retirement,
                )
            }
        }
        if (sync.revision != request.sessionRevision) {
            return prepareCommandResponse(sourceNodeId, request, CommandOutcome.StaleRevision, base, retirement)
        }

        val performedExerciseUuid = Uuid.parse(request.body.performedExerciseUuid.value)
        val performed = database.performedExerciseDao.getBySession(sync.sessionUuid)
            .firstOrNull { it.uuid == performedExerciseUuid }
        if (performed == null) {
            return prepareCommandResponse(sourceNodeId, request, CommandOutcome.TargetChanged, base, retirement)
        }

        val leaseAdmission = leaseStore.admit(
            sourceNodeId = sourceNodeId,
            commandId = request.commandId,
            databaseEpoch = request.databaseEpoch,
            sessionUuid = request.sessionUuid,
            sessionRevision = request.sessionRevision,
            leaseId = request.mutationLeaseId,
            leaseGeneration = request.mutationLeaseGeneration,
            stableFingerprint = stableFingerprint,
            attemptFingerprint = attemptFingerprint,
            admittedAtPhoneElapsedRealtimeMs = clock.elapsedRealtimeMs(),
        )
        when (leaseAdmission) {
            LeaseAdmission.AuthorizationExpired -> return prepareCommandResponse(
                sourceNodeId,
                request,
                CommandOutcome.AuthorizationExpired,
                base,
                retirement,
            )
            LeaseAdmission.CommandFingerprintMismatch -> return PreparedCommandResponse(
                response = readOnlyCommandResponse(
                    request.correlationId,
                    request.commandId,
                    CommandOutcome.ProtocolRejected(ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH),
                    base,
                ),
                retire = retirement,
            )
            LeaseAdmission.Accepted -> Unit
        }

        when (val rejection = bodyRejection(request.body, payload)) {
            null -> Unit
            CommandOutcome.TargetChanged ->
                return prepareCommandResponse(sourceNodeId, request, rejection, base, retirement)
            else -> return unavailableTargetRejection(request, rejection, base, retirement)
        }

        val applied = write(request.body, sync.sessionUuid)
        val committedSync = requireNotNull(database.wearSyncDao.getSessionSync(sync.sessionUuid))
        check(committedSync.revision > sync.revision) { "Wear mutation did not advance revision" }
        val committedBase = snapshotBuilder.build(epoch)
        val prepared = prepareCommandResponse(
            sourceNodeId,
            request,
            CommandOutcome.Applied,
            committedBase,
            retirement,
        )
        check(
            database.wearSyncDao.storeReceipt(
                sessionUuid = sync.sessionUuid,
                commandId = request.commandId.value,
                attemptFingerprint = attemptFingerprint.encoded,
                databaseEpoch = epoch.value,
                revision = committedSync.revision,
            ) == 1,
        ) { "Wear receipt did not bind to committed revision" }
        // Published by executeCommand once this transaction has committed (§6.3).
        return prepared.copy(applied = applied)
    }

    /**
     * §5.4 step 8: why the outline at the request's revision refuses [body], or null when it may be
     * written. Only `complete_set` checks types and values.
     */
    private fun bodyRejection(body: WatchCommandBody, payload: SnapshotPayload): CommandOutcome? {
        if (!OutlineRules.allows(payload, body)) return CommandOutcome.TargetChanged
        if (body !is WatchCommandBody.CompleteSet) return null
        val exercise = requireNotNull((payload as SnapshotPayload.Active).exercise(body.performedExerciseUuid))
        val set = exercise.sets.first { it.position == body.setPosition } as OutlineSet.Open
        return when {
            exercise.exerciseType != body.exerciseType ->
                CommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.EXERCISE_TYPE)
            set.setType != body.setType -> CommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.SET_TYPE)
            else -> CommandValidation.validate(body)
        }
    }

    /** Writes [body] inside the command's transaction; returns what the screen is told after it commits. */
    private suspend fun write(body: WatchCommandBody, sessionUuid: Uuid): ExternalSessionWrite {
        val performedExerciseUuid = Uuid.parse(body.performedExerciseUuid.value)
        return when (body) {
            is WatchCommandBody.CompleteSet -> {
                val written = WearSetWrite(
                    uuid = Uuid.random(),
                    performedExerciseUuid = performedExerciseUuid,
                    position = body.setPosition,
                    reps = body.reps,
                    weight = body.weightHundredthsKg?.toDouble()?.div(HUNDREDTHS_PER_KG),
                    type = body.setType.toEntity(),
                )
                writers.set.write(written)
                written.toExternal(sessionUuid)
            }
            is WatchCommandBody.SetExerciseSkipped -> {
                writers.skip.write(performedExerciseUuid, body.skipped)
                ExternalSessionWrite.SkipChanged(
                    sessionUuid = sessionUuid.toString(),
                    performedExerciseUuid = performedExerciseUuid.toString(),
                    skipped = body.skipped,
                )
            }
        }
    }

    /**
     * P4: a stored receipt counts only for the same command id; only then is its fingerprint parsed,
     * so a receipt another command left, in any encoding, never rejects this one.
     */
    private fun receiptOutcome(
        sync: io.github.stslex.workeeper.core.data.database.wear.SessionWearSyncRow,
        epoch: CanonicalUuid,
        request: WatchCommandRequest,
        attemptFingerprint: FingerprintValue,
    ): CommandOutcome? {
        val storedBytes = sync.receiptAttemptFingerprint ?: return null
        if (sync.receiptCommandId != request.commandId.value) return null
        val stored = try {
            FingerprintValue.parse(storedBytes)
        } catch (_: UnsupportedFingerprintVersionException) {
            return CommandOutcome.ProtocolRejected(ProtocolRejectionReason.UNSUPPORTED_FINGERPRINT_VERSION)
        } catch (_: IllegalArgumentException) {
            return CommandOutcome.ProtocolRejected(ProtocolRejectionReason.UNSUPPORTED_FINGERPRINT_VERSION)
        }
        if (!stored.constantTimeEquals(attemptFingerprint)) {
            return CommandOutcome.ProtocolRejected(ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH)
        }
        return if (sync.receiptDatabaseEpoch == epoch.value && sync.receiptRevision == sync.revision) {
            CommandOutcome.AlreadyApplied
        } else {
            null
        }
    }

    private suspend fun prepareAfterWriteFailure(
        sourceNodeId: String,
        request: WatchCommandRequest,
        stableFingerprint: ByteArray,
    ): PreparedCommandResponse {
        val epoch = currentEpoch()
        val base = snapshotBuilder.build(epoch)
        val retirement = request.retirement(sourceNodeId)
        val identity = base.payload.sessionIdentityOrNull()
        val outcome = when {
            request.databaseEpoch != epoch -> CommandOutcome.StaleRevision
            identity == null -> CommandOutcome.NoActiveSession
            identity.first != request.sessionUuid || identity.second != request.sessionRevision ->
                CommandOutcome.StaleRevision
            !OutlineRules.allows(base.payload, request.body) -> CommandOutcome.TargetChanged
            else -> CommandOutcome.RetryableTemporaryFailure
        }
        if (outcome == CommandOutcome.NoActiveSession) {
            return PreparedCommandResponse(
                readOnlyCommandResponse(request.correlationId, request.commandId, outcome, base),
                retire = retirement,
            )
        }
        return prepareCommandResponse(
            sourceNodeId = sourceNodeId,
            request = request,
            outcome = outcome,
            base = base,
            retirement = retirement,
            retryStableFingerprint = stableFingerprint.takeIf {
                outcome == CommandOutcome.RetryableTemporaryFailure
            },
        )
    }

    private fun unavailableTargetRejection(
        request: WatchCommandRequest,
        outcome: CommandOutcome,
        base: SnapshotData,
        retirement: LeaseRetirement,
    ): PreparedCommandResponse = PreparedCommandResponse(
        response = readOnlyCommandResponse(
            request.correlationId,
            request.commandId,
            outcome,
            base.withUnavailableAuthority(),
        ),
        retire = retirement,
    )

    /** §6.2: a lease goes with every `Active` outline, whatever its exercises' states. */
    private suspend fun prepareSnapshotResponse(
        sourceNodeId: String,
        correlationId: CanonicalUuid,
        base: SnapshotData,
    ): PreparedSnapshotResponse {
        val active = base.payload as? SnapshotPayload.Active
            ?: return PreparedSnapshotResponse(snapshotResponse(correlationId, base))
        val lease = allocateCandidate(sourceNodeId, base, active)
        val candidate = snapshotResponse(correlationId, base.withGrantedAuthority(lease))
        persistLeaseGeneration(active, lease)
        return PreparedSnapshotResponse(candidate, lease)
    }

    private suspend fun prepareCommandResponse(
        sourceNodeId: String,
        request: WatchCommandRequest,
        outcome: CommandOutcome,
        base: SnapshotData,
        retirement: LeaseRetirement,
        retryStableFingerprint: ByteArray? = null,
    ): PreparedCommandResponse {
        val active = base.payload as? SnapshotPayload.Active
            ?: return PreparedCommandResponse(
                response = readOnlyCommandResponse(
                    request.correlationId,
                    request.commandId,
                    outcome,
                    base,
                ),
                retire = retirement,
            )
        val lease = allocateCandidate(
            sourceNodeId = sourceNodeId,
            base = base,
            active = active,
            retryCommandId = request.commandId.takeIf { retryStableFingerprint != null },
            retryStableFingerprint = retryStableFingerprint,
        )
        val candidate = WatchCommandResponse(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = request.correlationId,
            commandId = request.commandId,
            outcome = outcome,
            replacement = base.withGrantedAuthority(lease),
        )
        // GUARD: the builder kept this outline inside the budget of §5.7, so it fits (F27).
        check(fits(candidate)) { "Granted command response exceeded the protocol envelope" }
        persistLeaseGeneration(active, lease)
        return PreparedCommandResponse(candidate, lease, retirement)
    }

    private suspend fun persistLeaseGeneration(
        active: SnapshotPayload.Active,
        lease: PendingMutationLease,
    ) {
        check(
            database.wearSyncDao.incrementLeaseGeneration(
                sessionUuid = Uuid.parse(active.sessionUuid.value),
                revision = active.sessionRevision,
            ) == 1,
        ) { "Durable Wear lease generation could not be allocated" }
        val persisted = requireNotNull(
            database.wearSyncDao.getSessionSync(Uuid.parse(active.sessionUuid.value)),
        )
        check(persisted.leaseGeneration == lease.leaseGeneration) {
            "Durable Wear lease generation was not strictly ordered"
        }
    }

    private suspend fun allocateCandidate(
        sourceNodeId: String,
        base: SnapshotData,
        active: SnapshotPayload.Active,
        retryCommandId: CanonicalUuid? = null,
        retryStableFingerprint: ByteArray? = null,
    ): PendingMutationLease {
        val sync = requireNotNull(
            database.wearSyncDao.getSessionSync(Uuid.parse(active.sessionUuid.value)),
        )
        check(sync.revision == active.sessionRevision)
        val nextGeneration = Math.addExact(sync.leaseGeneration, 1L)
        val issuedAt = clock.elapsedRealtimeMs()
        return PendingMutationLease(
            sourceNodeId = sourceNodeId,
            sessionUuid = active.sessionUuid,
            databaseEpoch = base.databaseEpoch,
            sessionRevision = active.sessionRevision,
            leaseId = CanonicalUuid.random(),
            leaseGeneration = nextGeneration,
            leaseRemainingAtPhoneSendMs = WearProtocol.MAX_MUTATION_WINDOW_MS,
            expiresAtPhoneElapsedRealtimeMs = Math.addExact(
                issuedAt,
                WearProtocol.MAX_MUTATION_WINDOW_MS,
            ),
            retryCommandId = retryCommandId,
            retryStableFingerprint = retryStableFingerprint,
        )
    }

    /** GUARD: [base] comes from the builder, inside the budget of §5.7, so this fits (F27). */
    private fun snapshotResponse(
        correlationId: CanonicalUuid,
        base: SnapshotData,
    ): ActiveWorkoutSnapshotResponse {
        val response = ActiveWorkoutSnapshotResponse(
            WearProtocol.SCHEMA_VERSION,
            correlationId,
            base,
        )
        check(fits(response)) { "Bounded snapshot response exceeded the protocol envelope" }
        return response
    }

    private suspend fun publishSnapshotOrRefreshReadOnly(
        correlationId: CanonicalUuid,
        prepared: PreparedSnapshotResponse,
    ): ActiveWorkoutSnapshotResponse {
        val lease = prepared.lease ?: return prepared.response
        if (leaseStore.publishIfCurrent(database, transition, lease)) return prepared.response
        return transition {
            snapshotResponse(
                correlationId = correlationId,
                base = snapshotBuilder.build(currentEpoch()).withUnavailableAuthority(),
            )
        }
    }

    private suspend fun publishCommandOrRefreshReadOnly(
        prepared: PreparedCommandResponse,
    ): WatchCommandResponse {
        val lease = prepared.lease ?: return prepared.response
        if (leaseStore.publishIfCurrent(database, transition, lease)) return prepared.response
        return transition {
            val base = snapshotBuilder.build(currentEpoch())
            readOnlyCommandResponse(
                correlationId = prepared.response.correlationId,
                commandId = prepared.response.commandId,
                outcome = when (prepared.response.outcome) {
                    CommandOutcome.RetryableTemporaryFailure,
                    CommandOutcome.TargetChanged,
                    -> lease.terminalOutcomeAfterPublicationLoss(base)
                    else -> prepared.response.outcome
                },
                base = base,
            )
        }
    }

    /**
     * §5.5: an outcome that needs an `Active` replacement but gets `PayloadTooLarge` is sent as
     * `TargetChanged`. GUARD: the budget of §5.7 makes this `check` hold (F27).
     */
    private fun readOnlyCommandResponse(
        correlationId: CanonicalUuid,
        commandId: CanonicalUuid,
        outcome: CommandOutcome,
        base: SnapshotData,
    ): WatchCommandResponse {
        val response = WatchCommandResponse(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = correlationId,
            commandId = commandId,
            outcome = if (base.payload is SnapshotPayload.PayloadTooLarge && outcome.needsActiveReplacement()) {
                CommandOutcome.TargetChanged
            } else {
                outcome
            },
            replacement = base.withUnavailableAuthority(),
        )
        check(fits(response)) { "Bounded read-only command response exceeded the protocol envelope" }
        return response
    }

    private suspend fun currentEpoch(): CanonicalUuid = CanonicalUuid.parse(
        requireNotNull(database.wearSyncDao.getDatabaseMetadata()).databaseEpoch,
    )

    private fun fits(envelope: WearEnvelope): Boolean =
        try {
            WearProtocolCodec.encode(envelope)
            true
        } catch (_: EnvelopeTooLargeException) {
            false
        }

    private fun requireRequest(schemaVersion: Int, sourceNodeId: String) {
        require(schemaVersion == WearProtocol.SCHEMA_VERSION)
        require(sourceNodeId.isNotBlank())
    }

    private data class SnapshotRequestKey(
        val sourceNodeId: String,
        val correlationId: CanonicalUuid,
    )

    private sealed interface CommandRequestKey

    private data class CommandAttemptKey(
        val sourceNodeId: String,
        val correlationId: CanonicalUuid,
        val attemptFingerprint: FingerprintValue,
    ) : CommandRequestKey

    private data class ProtocolRejectedRequestKey(
        val sourceNodeId: String,
        val routing: CommandRouting,
        val reason: ProtocolRejectionReason,
    ) : CommandRequestKey

    private data class PreparedSnapshotResponse(
        val response: ActiveWorkoutSnapshotResponse,
        val lease: PendingMutationLease? = null,
    )

    private data class PreparedCommandResponse(
        val response: WatchCommandResponse,
        val lease: PendingMutationLease? = null,
        val retire: LeaseRetirement? = null,
        /** What an Applied command wrote; null on every other path. */
        val applied: ExternalSessionWrite? = null,
    )

    private class BoundedResponseMap<K, V> {
        private val values = LinkedHashMap<K, V>(MAX_DEDUPLICATED_RESPONSES, LOAD_FACTOR, true)

        operator fun get(key: K): V? = values[key]

        fun put(key: K, value: V) {
            values[key] = value
            while (values.size > MAX_DEDUPLICATED_RESPONSES) {
                values.remove(values.keys.first())
            }
        }
    }

    private companion object {
        const val HUNDREDTHS_PER_KG: Double = 100.0
        const val MAX_DEDUPLICATED_RESPONSES: Int = 32
        const val LOAD_FACTOR: Float = 0.75f
    }
}

data class WearSetWrite(
    val uuid: Uuid,
    val performedExerciseUuid: Uuid,
    val position: Int,
    val reps: Int,
    val weight: Double?,
    val type: SetTypeEntity,
)

internal data class LeaseRetirement(
    val sourceNodeId: String,
    val sessionUuid: CanonicalUuid,
    val leaseId: CanonicalUuid,
    val leaseGeneration: Long,
)

fun interface WearSetMutationWriter {
    suspend fun write(value: WearSetWrite)
}

/** Writes one performed exercise's skip flag, inside the command's transaction (§6.3). */
fun interface WearSkipMutationWriter {
    suspend fun write(performedExerciseUuid: Uuid, skipped: Boolean)
}

/** The two command writers, one dependency of the bridge. */
@Inject
internal class WearMutationWriters(
    val set: WearSetMutationWriter,
    val skip: WearSkipMutationWriter,
)

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RoomWearSetMutationWriter @Inject internal constructor(
    private val database: AppDatabase,
) : WearSetMutationWriter {
    override suspend fun write(value: WearSetWrite) {
        database.setDao.upsertByTarget(
            uuid = value.uuid,
            performedExerciseUuid = value.performedExerciseUuid,
            position = value.position,
            reps = value.reps,
            weight = value.weight,
            type = value.type,
        )
    }
}

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RoomWearSkipMutationWriter @Inject internal constructor(
    private val database: AppDatabase,
) : WearSkipMutationWriter {
    override suspend fun write(performedExerciseUuid: Uuid, skipped: Boolean) {
        database.performedExerciseDao.setSkipped(performedExerciseUuid, skipped)
    }
}

internal fun WatchCommandRequest.toFingerprintCommand(
    sourceNodeId: String,
): FingerprintCommand = FingerprintCommand(
    sourceNodeId = sourceNodeId,
    schemaVersion = schemaVersion,
    commandId = commandId,
    databaseEpoch = databaseEpoch,
    sessionUuid = sessionUuid,
    sessionRevision = sessionRevision,
    body = body,
    mutationLeaseId = mutationLeaseId,
    mutationLeaseGeneration = mutationLeaseGeneration,
)

internal fun WatchCommandRequest.retirement(sourceNodeId: String) = LeaseRetirement(
    sourceNodeId = sourceNodeId,
    sessionUuid = sessionUuid,
    leaseId = mutationLeaseId,
    leaseGeneration = mutationLeaseGeneration,
)

internal fun SnapshotData.withUnavailableAuthority(): SnapshotData = copy(
    payload = when (val current = payload) {
        is SnapshotPayload.Active -> current.copy(
            mutationAuthority = MutationAuthority.Unavailable(
                io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
                    .FRESH_HANDSHAKE_REQUIRED,
            ),
        )
        else -> current
    },
)

internal fun SnapshotData.withGrantedAuthority(lease: PendingMutationLease): SnapshotData = copy(
    payload = (payload as SnapshotPayload.Active).copy(
        mutationAuthority = MutationAuthority.Granted(
            mutationLeaseId = lease.leaseId,
            mutationLeaseGeneration = lease.leaseGeneration,
            leaseRemainingAtPhoneSendMs = lease.leaseRemainingAtPhoneSendMs,
        ),
    ),
)

/** The lease's command cannot run now; another state of the session says why (§6.3, V3). */
internal fun PendingMutationLease.terminalOutcomeAfterPublicationLoss(
    base: SnapshotData,
): CommandOutcome {
    if (base.databaseEpoch != databaseEpoch) return CommandOutcome.StaleRevision
    val identity = base.payload.sessionIdentityOrNull()
        ?: return CommandOutcome.NoActiveSession
    if (identity.first != sessionUuid || identity.second != sessionRevision) {
        return CommandOutcome.StaleRevision
    }
    return CommandOutcome.AuthorizationExpired
}

/** §5.5: the outcomes a `PayloadTooLarge` replacement cannot carry. */
internal fun CommandOutcome.needsActiveReplacement(): Boolean = when (this) {
    is CommandOutcome.InvalidValues,
    is CommandOutcome.ImmutableTypeMismatch,
    is CommandOutcome.RetryableTemporaryFailure,
    -> true
    else -> false
}

internal fun SetTypeWire.toEntity(): SetTypeEntity = when (this) {
    SetTypeWire.WARM -> SetTypeEntity.WARM
    SetTypeWire.WORK -> SetTypeEntity.WORK
    SetTypeWire.FAIL -> SetTypeEntity.FAIL
    SetTypeWire.DROP -> SetTypeEntity.DROP
}

/**
 * The bridge's own map into the live-workout data type: core:data:exercise keeps its
 * SetTypeEntity mapping internal (wear-live-sync.md §6.4, Q4). Pinned over every value by a test.
 */
internal fun SetTypeEntity.toSetsDataType(): SetsDataType = when (this) {
    SetTypeEntity.WARM -> SetsDataType.WARM
    SetTypeEntity.WORK -> SetsDataType.WORK
    SetTypeEntity.FAIL -> SetsDataType.FAIL
    SetTypeEntity.DROP -> SetsDataType.DROP
}

/** The values exactly as written (wear-live-sync.md §6.4). */
internal fun WearSetWrite.toExternal(sessionUuid: Uuid): ExternalSessionWrite.SetMarked =
    ExternalSessionWrite.SetMarked(
        sessionUuid = sessionUuid.toString(),
        performedExerciseUuid = performedExerciseUuid.toString(),
        position = position,
        weight = weight,
        reps = reps,
        type = type.toSetsDataType(),
    )
