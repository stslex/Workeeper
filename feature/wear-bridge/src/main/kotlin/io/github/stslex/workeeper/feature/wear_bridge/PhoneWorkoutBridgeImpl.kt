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
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CommandValidation
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandRouting
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetRequest
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.EnvelopeTooLargeException
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.FingerprintPurpose
import io.github.stslex.workeeper.core.wear.protocol.FingerprintV1
import io.github.stslex.workeeper.core.wear.protocol.FingerprintValue
import io.github.stslex.workeeper.core.wear.protocol.GetActiveWorkoutRequest
import io.github.stslex.workeeper.core.wear.protocol.ImmutableTypeField
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.ProtocolRejectionReason
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.UnsupportedFingerprintVersionException
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
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
    private val mutationWriter: WearSetMutationWriter,
) : PhoneWorkoutBridge {

    override val transportStatus: Set<WearPayloadTransportStatus> = setOf(
        WearPayloadTransportStatus.PRIVACY_DISCLOSURE_REQUIRED,
        WearPayloadTransportStatus.TRANSPORT_POLICY_REQUIRED,
    )

    private val coordinatorMutex = Mutex()
    private val snapshotResponses =
        BoundedResponseMap<SnapshotRequestKey, ActiveWorkoutSnapshotResponse>()
    private val commandResponses =
        BoundedResponseMap<CommandRequestKey, CompleteCurrentSetResponse>()

    override suspend fun getActiveWorkout(
        authenticatedSourceNodeId: String,
        request: GetActiveWorkoutRequest,
    ): ActiveWorkoutSnapshotResponse = coordinatorMutex.withLock {
        requireRequest(request.schemaVersion, authenticatedSourceNodeId)
        val key = SnapshotRequestKey(authenticatedSourceNodeId, request.correlationId)
        snapshotResponses[key]?.let { return@withLock it }
        val prepared = transition.mutate {
            val epoch = currentEpoch()
            prepareSnapshotResponse(
                sourceNodeId = authenticatedSourceNodeId,
                correlationId = request.correlationId,
                base = snapshotBuilder.build(epoch),
            )
        }
        prepared.lease?.let { candidate ->
            leaseStore.publishIfCurrent(database, transition, candidate)
        }
        snapshotResponses.put(key, prepared.response)
        prepared.response
    }

    override suspend fun completeCurrentSet(
        authenticatedSourceNodeId: String,
        request: CompleteCurrentSetRequest,
    ): CompleteCurrentSetResponse = coordinatorMutex.withLock {
        requireRequest(request.schemaVersion, authenticatedSourceNodeId)
        val fingerprintCommand = request.toFingerprintCommand(authenticatedSourceNodeId)
        val stableFingerprint = FingerprintV1.fingerprint(
            fingerprintCommand,
            FingerprintPurpose.STABLE_INTENT,
        )
        val attemptFingerprint = FingerprintV1.fingerprint(
            fingerprintCommand,
            FingerprintPurpose.DELIVERY_ATTEMPT,
        )
        val key = CompleteRequestKey(
            sourceNodeId = authenticatedSourceNodeId,
            correlationId = request.correlationId,
            attemptFingerprint = attemptFingerprint,
        )
        commandResponses[key]?.let { return@withLock it }
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
        prepared.retire?.let { retired ->
            leaseStore.retireMatching(
                sourceNodeId = retired.sourceNodeId,
                sessionUuid = retired.sessionUuid,
                leaseId = retired.leaseId,
                leaseGeneration = retired.leaseGeneration,
            )
        }
        prepared.lease?.let { candidate ->
            leaseStore.publishIfCurrent(database, transition, candidate)
        }
        commandResponses.put(key, prepared.response)
        prepared.response
    }

    override suspend fun protocolRejected(
        authenticatedSourceNodeId: String,
        routing: CompleteCommandRouting,
        reason: ProtocolRejectionReason,
    ): CompleteCurrentSetResponse = coordinatorMutex.withLock {
        requireRequest(routing.schemaVersion, authenticatedSourceNodeId)
        val key = ProtocolRejectedRequestKey(
            sourceNodeId = authenticatedSourceNodeId,
            routing = routing,
            reason = reason,
        )
        commandResponses[key]?.let { return@withLock it }
        val response = transition {
            readOnlyCommandResponse(
                correlationId = routing.correlationId,
                commandId = routing.commandId,
                outcome = CompleteCommandOutcome.ProtocolRejected(reason),
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
        response
    }

    // Keep the security-sensitive gateway order visible and contiguous with specification §5.2.
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun processCommand(
        sourceNodeId: String,
        request: CompleteCurrentSetRequest,
        stableFingerprint: FingerprintValue,
        attemptFingerprint: FingerprintValue,
    ): PreparedCommandResponse {
        val epoch = currentEpoch()
        val base = snapshotBuilder.build(epoch)
        val retirement = request.retirement(sourceNodeId)
        if (request.databaseEpoch != epoch) {
            return prepareCommandResponse(
                sourceNodeId,
                request,
                CompleteCommandOutcome.StaleRevision,
                base,
                retirement,
            )
        }

        val payload = base.payload
        if (payload is SnapshotPayload.NoSession) {
            return PreparedCommandResponse(
                response = readOnlyCommandResponse(
                    request.correlationId,
                    request.commandId,
                    CompleteCommandOutcome.NoActiveSession,
                    base,
                ),
                retire = retirement,
            )
        }
        val activeIdentity = requireNotNull(payload.sessionIdentityOrNull())
        if (activeIdentity.first != request.sessionUuid) {
            return prepareCommandResponse(
                sourceNodeId,
                request,
                CompleteCommandOutcome.StaleRevision,
                base,
                retirement,
            )
        }

        val sync = requireNotNull(database.wearSyncDao.getActiveSessionSync())
        val receiptOutcome = receiptOutcome(sync, epoch, request, attemptFingerprint)
        if (receiptOutcome != null) {
            return when (receiptOutcome) {
                CompleteCommandOutcome.AlreadyApplied -> prepareCommandResponse(
                    sourceNodeId,
                    request,
                    receiptOutcome,
                    base,
                    retirement,
                )
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
            return prepareCommandResponse(
                sourceNodeId,
                request,
                CompleteCommandOutcome.StaleRevision,
                base,
                retirement,
            )
        }

        val performed = database.performedExerciseDao.getBySession(sync.sessionUuid)
            .firstOrNull { it.uuid.toString() == request.body.performedExerciseUuid.value }
        if (performed == null || performed.skipped) {
            return prepareCommandResponse(
                sourceNodeId,
                request,
                CompleteCommandOutcome.TargetChanged,
                base,
                retirement,
            )
        }

        val leaseAdmission = leaseStore.admit(
            sourceNodeId = sourceNodeId,
            commandId = request.commandId,
            databaseEpoch = request.databaseEpoch,
            sessionUuid = request.sessionUuid,
            sessionRevision = request.sessionRevision,
            performedExerciseUuid = request.body.performedExerciseUuid,
            setPosition = request.body.setPosition,
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
                CompleteCommandOutcome.AuthorizationExpired,
                base,
                retirement,
            )
            LeaseAdmission.CommandFingerprintMismatch -> return PreparedCommandResponse(
                response = readOnlyCommandResponse(
                    request.correlationId,
                    request.commandId,
                    CompleteCommandOutcome.ProtocolRejected(
                        ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
                    ),
                    base,
                ),
                retire = retirement,
            )
            LeaseAdmission.Accepted -> Unit
        }

        val targetPayload = payload as? SnapshotPayload.ActiveWithTarget
        val target = targetPayload?.target
        val existing = database.setDao.getByPerformedAndPosition(
            performedExerciseUuid = Uuid.parse(request.body.performedExerciseUuid.value),
            position = request.body.setPosition,
        )
        if (!request.matchesCanonicalTarget(target, existing != null)) {
            return prepareCommandResponse(
                sourceNodeId,
                request,
                CompleteCommandOutcome.TargetChanged,
                base,
                retirement,
            )
        }
        val canonicalTarget = requireNotNull(target)
        if (canonicalTarget.exerciseType != request.body.exerciseType) {
            return unavailableTargetRejection(
                request,
                CompleteCommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.EXERCISE_TYPE),
                base,
                retirement,
            )
        }
        if (canonicalTarget.setType != request.body.setType) {
            return unavailableTargetRejection(
                request,
                CompleteCommandOutcome.ImmutableTypeMismatch(ImmutableTypeField.SET_TYPE),
                base,
                retirement,
            )
        }
        CommandValidation.validate(request.body)?.let { invalid ->
            return unavailableTargetRejection(request, invalid, base, retirement)
        }

        mutationWriter.write(
            WearSetWrite(
                uuid = Uuid.random(),
                performedExerciseUuid = Uuid.parse(request.body.performedExerciseUuid.value),
                position = request.body.setPosition,
                reps = request.body.reps,
                weight = request.body.weightHundredthsKg?.toDouble()?.div(HUNDREDTHS_PER_KG),
                type = request.body.setType.toEntity(),
            ),
        )
        val committedSync = requireNotNull(database.wearSyncDao.getSessionSync(sync.sessionUuid))
        check(committedSync.revision > sync.revision) { "Wear mutation did not advance revision" }
        val committedBase = snapshotBuilder.build(epoch)
        val prepared = prepareCommandResponse(
            sourceNodeId,
            request,
            CompleteCommandOutcome.Applied,
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
        return prepared
    }

    private suspend fun receiptOutcome(
        sync: io.github.stslex.workeeper.core.data.database.wear.SessionWearSyncRow,
        epoch: CanonicalUuid,
        request: CompleteCurrentSetRequest,
        attemptFingerprint: FingerprintValue,
    ): CompleteCommandOutcome? {
        val storedBytes = sync.receiptAttemptFingerprint ?: return null
        val stored = try {
            FingerprintValue.parse(storedBytes)
        } catch (_: UnsupportedFingerprintVersionException) {
            return CompleteCommandOutcome.ProtocolRejected(
                ProtocolRejectionReason.UNSUPPORTED_FINGERPRINT_VERSION,
            )
        } catch (_: IllegalArgumentException) {
            return CompleteCommandOutcome.ProtocolRejected(
                ProtocolRejectionReason.UNSUPPORTED_FINGERPRINT_VERSION,
            )
        }
        if (sync.receiptCommandId != request.commandId.value) return null
        if (!stored.constantTimeEquals(attemptFingerprint)) {
            return CompleteCommandOutcome.ProtocolRejected(
                ProtocolRejectionReason.COMMAND_FINGERPRINT_MISMATCH,
            )
        }
        return if (sync.receiptDatabaseEpoch == epoch.value && sync.receiptRevision == sync.revision) {
            CompleteCommandOutcome.AlreadyApplied
        } else {
            null
        }
    }

    private suspend fun prepareAfterWriteFailure(
        sourceNodeId: String,
        request: CompleteCurrentSetRequest,
        stableFingerprint: ByteArray,
    ): PreparedCommandResponse {
        val epoch = currentEpoch()
        val base = snapshotBuilder.build(epoch)
        val retirement = request.retirement(sourceNodeId)
        val identity = base.payload.sessionIdentityOrNull()
        val target = base.payload as? SnapshotPayload.ActiveWithTarget
        val outcome = when {
            request.databaseEpoch != epoch -> CompleteCommandOutcome.StaleRevision
            identity == null -> CompleteCommandOutcome.NoActiveSession
            identity.first != request.sessionUuid || identity.second != request.sessionRevision ->
                CompleteCommandOutcome.StaleRevision
            target == null -> CompleteCommandOutcome.TargetChanged
            target.target.performedExerciseUuid != request.body.performedExerciseUuid ||
                target.target.setPosition != request.body.setPosition ->
                CompleteCommandOutcome.TargetChanged
            else -> CompleteCommandOutcome.RetryableTemporaryFailure
        }
        if (outcome == CompleteCommandOutcome.NoActiveSession) {
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
                outcome == CompleteCommandOutcome.RetryableTemporaryFailure
            },
        )
    }

    private suspend fun unavailableTargetRejection(
        request: CompleteCurrentSetRequest,
        outcome: CompleteCommandOutcome,
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

    private suspend fun prepareSnapshotResponse(
        sourceNodeId: String,
        correlationId: CanonicalUuid,
        base: SnapshotData,
    ): PreparedSnapshotResponse {
        val target = base.payload as? SnapshotPayload.ActiveWithTarget
            ?: return PreparedSnapshotResponse(fitSnapshotResponse(correlationId, base))
        val lease = allocateCandidate(sourceNodeId, base, target)
        val granted = base.withGrantedAuthority(lease)
        val candidate = ActiveWorkoutSnapshotResponse(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = correlationId,
            snapshot = granted,
        )
        if (!fits(candidate)) {
            return PreparedSnapshotResponse(
                fitSnapshotResponse(correlationId, snapshotBuilder.payloadTooLarge(base)),
            )
        }
        persistLeaseGeneration(target, lease)
        return PreparedSnapshotResponse(candidate, lease)
    }

    private suspend fun prepareCommandResponse(
        sourceNodeId: String,
        request: CompleteCurrentSetRequest,
        outcome: CompleteCommandOutcome,
        base: SnapshotData,
        retirement: LeaseRetirement,
        retryStableFingerprint: ByteArray? = null,
    ): PreparedCommandResponse {
        val target = base.payload as? SnapshotPayload.ActiveWithTarget
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
            target = target,
            retryCommandId = request.commandId.takeIf { retryStableFingerprint != null },
            retryStableFingerprint = retryStableFingerprint,
        )
        val candidate = CompleteCurrentSetResponse(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = request.correlationId,
            commandId = request.commandId,
            outcome = outcome,
            replacement = base.withGrantedAuthority(lease),
        )
        if (!fits(candidate)) {
            val boundedOutcome = if (outcome == CompleteCommandOutcome.RetryableTemporaryFailure) {
                CompleteCommandOutcome.TargetChanged
            } else {
                outcome
            }
            return PreparedCommandResponse(
                response = readOnlyCommandResponse(
                    request.correlationId,
                    request.commandId,
                    boundedOutcome,
                    snapshotBuilder.payloadTooLarge(base),
                ),
                retire = retirement,
            )
        }
        persistLeaseGeneration(target, lease)
        return PreparedCommandResponse(candidate, lease, retirement)
    }

    private suspend fun persistLeaseGeneration(
        target: SnapshotPayload.ActiveWithTarget,
        lease: PendingMutationLease,
    ) {
        check(
            database.wearSyncDao.incrementLeaseGeneration(
                sessionUuid = Uuid.parse(target.sessionUuid.value),
                revision = target.sessionRevision,
            ) == 1,
        ) { "Durable Wear lease generation could not be allocated" }
        val persisted = requireNotNull(
            database.wearSyncDao.getSessionSync(Uuid.parse(target.sessionUuid.value)),
        )
        check(persisted.leaseGeneration == lease.leaseGeneration) {
            "Durable Wear lease generation was not strictly ordered"
        }
    }

    private suspend fun allocateCandidate(
        sourceNodeId: String,
        base: SnapshotData,
        target: SnapshotPayload.ActiveWithTarget,
        retryCommandId: CanonicalUuid? = null,
        retryStableFingerprint: ByteArray? = null,
    ): PendingMutationLease {
        val sync = requireNotNull(
            database.wearSyncDao.getSessionSync(Uuid.parse(target.sessionUuid.value)),
        )
        check(sync.revision == target.sessionRevision)
        val nextGeneration = Math.addExact(sync.leaseGeneration, 1L)
        val issuedAt = clock.elapsedRealtimeMs()
        return PendingMutationLease(
            sourceNodeId = sourceNodeId,
            sessionUuid = target.sessionUuid,
            databaseEpoch = base.databaseEpoch,
            sessionRevision = target.sessionRevision,
            performedExerciseUuid = target.target.performedExerciseUuid,
            setPosition = target.target.setPosition,
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

    private fun fitSnapshotResponse(
        correlationId: CanonicalUuid,
        base: SnapshotData,
    ): ActiveWorkoutSnapshotResponse {
        val candidate = ActiveWorkoutSnapshotResponse(
            WearProtocol.SCHEMA_VERSION,
            correlationId,
            base,
        )
        if (fits(candidate)) return candidate
        val fallback = candidate.copy(snapshot = snapshotBuilder.payloadTooLarge(base))
        check(fits(fallback)) { "PayloadTooLarge snapshot fallback exceeded the protocol envelope" }
        return fallback
    }

    private fun readOnlyCommandResponse(
        correlationId: CanonicalUuid,
        commandId: CanonicalUuid,
        outcome: CompleteCommandOutcome,
        base: SnapshotData,
    ): CompleteCurrentSetResponse {
        val response = CompleteCurrentSetResponse(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = correlationId,
            commandId = commandId,
            outcome = outcome,
            replacement = base.withUnavailableAuthority(),
        )
        check(fits(response)) { "Bounded read-only command response exceeded the protocol envelope" }
        return response
    }

    private suspend fun currentEpoch(): CanonicalUuid = CanonicalUuid.parse(
        requireNotNull(database.wearSyncDao.getDatabaseMetadata()).databaseEpoch,
    )

    private fun fits(envelope: io.github.stslex.workeeper.core.wear.protocol.WearEnvelope): Boolean =
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

    private fun CompleteCurrentSetRequest.matchesCanonicalTarget(
        target: io.github.stslex.workeeper.core.wear.protocol.ActiveTarget?,
        targetRowAlreadyExists: Boolean,
    ): Boolean {
        if (target == null) return false
        if (targetRowAlreadyExists) return false
        if (target.performedExerciseUuid != body.performedExerciseUuid) return false
        return target.setPosition == body.setPosition
    }

    private data class SnapshotRequestKey(
        val sourceNodeId: String,
        val correlationId: CanonicalUuid,
    )

    private sealed interface CommandRequestKey

    private data class CompleteRequestKey(
        val sourceNodeId: String,
        val correlationId: CanonicalUuid,
        val attemptFingerprint: FingerprintValue,
    ) : CommandRequestKey

    private data class ProtocolRejectedRequestKey(
        val sourceNodeId: String,
        val routing: CompleteCommandRouting,
        val reason: ProtocolRejectionReason,
    ) : CommandRequestKey

    private data class PreparedSnapshotResponse(
        val response: ActiveWorkoutSnapshotResponse,
        val lease: PendingMutationLease? = null,
    )

    private data class PreparedCommandResponse(
        val response: CompleteCurrentSetResponse,
        val lease: PendingMutationLease? = null,
        val retire: LeaseRetirement? = null,
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

private fun CompleteCurrentSetRequest.toFingerprintCommand(sourceNodeId: String): FingerprintCommand =
    FingerprintCommand(
        sourceNodeId = sourceNodeId,
        schemaVersion = schemaVersion,
        commandId = commandId,
        databaseEpoch = databaseEpoch,
        sessionUuid = sessionUuid,
        sessionRevision = sessionRevision,
        performedExerciseUuid = body.performedExerciseUuid,
        setPosition = body.setPosition,
        reps = body.reps,
        weightHundredthsKg = body.weightHundredthsKg,
        exerciseType = body.exerciseType,
        setType = body.setType,
        mutationLeaseId = mutationLeaseId,
        mutationLeaseGeneration = mutationLeaseGeneration,
    )

private fun CompleteCurrentSetRequest.retirement(sourceNodeId: String) =
    LeaseRetirement(
        sourceNodeId = sourceNodeId,
        sessionUuid = sessionUuid,
        leaseId = mutationLeaseId,
        leaseGeneration = mutationLeaseGeneration,
    )

private fun SnapshotData.withUnavailableAuthority(): SnapshotData = copy(
    payload = when (val current = payload) {
        is SnapshotPayload.ActiveWithTarget -> current.copy(
            mutationAuthority = MutationAuthority.Unavailable(
                io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
                    .FRESH_HANDSHAKE_REQUIRED,
            ),
        )
        else -> current
    },
)

private fun SnapshotData.withGrantedAuthority(lease: PendingMutationLease): SnapshotData = copy(
    payload = (payload as SnapshotPayload.ActiveWithTarget).copy(
        mutationAuthority = MutationAuthority.Granted(
            mutationLeaseId = lease.leaseId,
            mutationLeaseGeneration = lease.leaseGeneration,
            leaseRemainingAtPhoneSendMs = lease.leaseRemainingAtPhoneSendMs,
        ),
    ),
)

private fun SetTypeWire.toEntity(): SetTypeEntity = when (this) {
    SetTypeWire.WARM -> SetTypeEntity.WARM
    SetTypeWire.WORK -> SetTypeEntity.WORK
    SetTypeWire.FAIL -> SetTypeEntity.FAIL
    SetTypeWire.DROP -> SetTypeEntity.DROP
}
