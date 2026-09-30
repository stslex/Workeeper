// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import android.os.SystemClock
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.FingerprintValue
import java.security.MessageDigest
import java.util.LinkedHashMap
import kotlin.uuid.Uuid

fun interface PhoneMonotonicClock {
    fun elapsedRealtimeMs(): Long
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SystemPhoneMonotonicClock @Inject constructor() : PhoneMonotonicClock {
    override fun elapsedRealtimeMs(): Long = SystemClock.elapsedRealtime()
}

internal data class PendingMutationLease(
    val sourceNodeId: String,
    val sessionUuid: CanonicalUuid,
    val databaseEpoch: CanonicalUuid,
    val sessionRevision: Long,
    val performedExerciseUuid: CanonicalUuid,
    val setPosition: Int,
    val leaseId: CanonicalUuid,
    val leaseGeneration: Long,
    val leaseRemainingAtPhoneSendMs: Long,
    val expiresAtPhoneElapsedRealtimeMs: Long,
    val retryCommandId: CanonicalUuid? = null,
    val retryStableFingerprint: ByteArray? = null,
)

internal sealed interface LeaseAdmission {
    data object Accepted : LeaseAdmission
    data object AuthorizationExpired : LeaseAdmission
    data object CommandFingerprintMismatch : LeaseAdmission
}

/** One bounded, process-only authority slot per source node and phone session. */
@SingleIn(AppScope::class)
internal class WearMutationLeaseStore @Inject constructor(
    transition: DbTransitionRunner,
) {

    private val lock = Any()
    private val slots = mutableMapOf<SlotKey, ActiveLease>()
    private val seenCommands = LinkedHashMap<CommandKey, Unit>(
        MAX_SEEN_COMMANDS,
        LOAD_FACTOR,
        true,
    )

    init {
        transition.addAfterMutationCommitListener(::retireAll)
    }

    fun publish(candidate: PendingMutationLease): Boolean = synchronized(lock) {
        val key = SlotKey(candidate.sourceNodeId, candidate.sessionUuid)
        val current = slots[key]
        if (current != null && current.pending.leaseGeneration >= candidate.leaseGeneration) {
            return@synchronized false
        }
        slots[key] = ActiveLease(
            pending = candidate,
            binding = candidate.retryCommandId?.let { commandId ->
                AttemptBinding(
                    commandId = commandId,
                    stableFingerprint = requireNotNull(candidate.retryStableFingerprint).copyOf(),
                    attemptFingerprint = null,
                    deliveries = 0,
                    acceptsSuccessorAttempt = true,
                )
            },
        )
        true
    }

    fun admit(
        sourceNodeId: String,
        commandId: CanonicalUuid,
        databaseEpoch: CanonicalUuid,
        sessionUuid: CanonicalUuid,
        sessionRevision: Long,
        performedExerciseUuid: CanonicalUuid,
        setPosition: Int,
        leaseId: CanonicalUuid,
        leaseGeneration: Long,
        stableFingerprint: FingerprintValue,
        attemptFingerprint: FingerprintValue,
        admittedAtPhoneElapsedRealtimeMs: Long,
    ): LeaseAdmission = synchronized(lock) {
        val key = SlotKey(sourceNodeId, sessionUuid)
        val active = slots[key] ?: return@synchronized LeaseAdmission.AuthorizationExpired
        val presentation = LeasePresentation(
            databaseEpoch = databaseEpoch,
            sessionRevision = sessionRevision,
            performedExerciseUuid = performedExerciseUuid,
            setPosition = setPosition,
            leaseId = leaseId,
            leaseGeneration = leaseGeneration,
            admittedAtPhoneElapsedRealtimeMs = admittedAtPhoneElapsedRealtimeMs,
        )
        if (!active.pending.matches(presentation)) {
            return@synchronized LeaseAdmission.AuthorizationExpired
        }
        val commandKey = CommandKey(sourceNodeId, sessionUuid, commandId)
        if (!active.pending.isFreshAt(presentation.admittedAtPhoneElapsedRealtimeMs)) {
            rememberCommand(commandKey)
            return@synchronized LeaseAdmission.AuthorizationExpired
        }
        active.admitCommand(
            commandKey = commandKey,
            stableFingerprint = stableFingerprint.encoded,
            attemptFingerprint = attemptFingerprint.encoded,
        )
    }

    private fun ActiveLease.admitCommand(
        commandKey: CommandKey,
        stableFingerprint: ByteArray,
        attemptFingerprint: ByteArray,
    ): LeaseAdmission {
        val binding = binding
        if (binding == null) {
            if (seenCommands.containsKey(commandKey)) {
                return LeaseAdmission.CommandFingerprintMismatch
            }
            this.binding = AttemptBinding(
                commandId = commandKey.commandId,
                stableFingerprint = stableFingerprint.copyOf(),
                attemptFingerprint = attemptFingerprint.copyOf(),
                deliveries = 1,
                acceptsSuccessorAttempt = false,
            )
            rememberCommand(commandKey)
            return LeaseAdmission.Accepted
        }
        if (binding.commandId != commandKey.commandId) {
            return LeaseAdmission.AuthorizationExpired
        }
        if (!MessageDigest.isEqual(binding.stableFingerprint, stableFingerprint)) {
            return LeaseAdmission.CommandFingerprintMismatch
        }
        val boundAttempt = binding.attemptFingerprint
        if (boundAttempt == null && binding.acceptsSuccessorAttempt) {
            binding.attemptFingerprint = attemptFingerprint.copyOf()
            binding.deliveries = 1
            binding.acceptsSuccessorAttempt = false
            rememberCommand(commandKey)
            return LeaseAdmission.Accepted
        }
        if (boundAttempt == null || !MessageDigest.isEqual(boundAttempt, attemptFingerprint)) {
            return LeaseAdmission.CommandFingerprintMismatch
        }
        if (binding.deliveries >= MAX_DELIVERIES_PER_ATTEMPT) {
            return LeaseAdmission.AuthorizationExpired
        }
        binding.deliveries++
        return LeaseAdmission.Accepted
    }

    private fun PendingMutationLease.matches(presentation: LeasePresentation): Boolean {
        if (databaseEpoch != presentation.databaseEpoch) return false
        if (sessionRevision != presentation.sessionRevision) return false
        if (performedExerciseUuid != presentation.performedExerciseUuid) return false
        if (setPosition != presentation.setPosition) return false
        if (leaseId != presentation.leaseId) return false
        if (leaseGeneration != presentation.leaseGeneration) return false
        return true
    }

    private fun PendingMutationLease.isFreshAt(elapsedRealtimeMs: Long): Boolean =
        elapsedRealtimeMs < expiresAtPhoneElapsedRealtimeMs

    private fun rememberCommand(key: CommandKey) {
        seenCommands[key] = Unit
        while (seenCommands.size > MAX_SEEN_COMMANDS) {
            seenCommands.remove(seenCommands.keys.first())
        }
    }

    fun retireMatching(
        sourceNodeId: String,
        sessionUuid: CanonicalUuid,
        leaseId: CanonicalUuid,
        leaseGeneration: Long,
    ) = synchronized(lock) {
        val key = SlotKey(sourceNodeId, sessionUuid)
        val current = slots[key] ?: return@synchronized
        if (current.pending.leaseId == leaseId &&
            current.pending.leaseGeneration == leaseGeneration
        ) {
            slots.remove(key)
        }
    }

    fun retireSession(sourceNodeId: String, sessionUuid: CanonicalUuid) {
        synchronized(lock) {
            slots.remove(SlotKey(sourceNodeId, sessionUuid))
        }
    }

    private fun retireAll() {
        synchronized(lock) {
            slots.clear()
        }
    }

    internal fun activeLeaseForTest(
        sourceNodeId: String,
        sessionUuid: CanonicalUuid,
    ): PendingMutationLease? = synchronized(lock) {
        slots[SlotKey(sourceNodeId, sessionUuid)]?.pending
    }

    private data class SlotKey(
        val sourceNodeId: String,
        val sessionUuid: CanonicalUuid,
    )

    private data class CommandKey(
        val sourceNodeId: String,
        val sessionUuid: CanonicalUuid,
        val commandId: CanonicalUuid,
    )

    private data class ActiveLease(
        val pending: PendingMutationLease,
        var binding: AttemptBinding?,
    )

    private data class LeasePresentation(
        val databaseEpoch: CanonicalUuid,
        val sessionRevision: Long,
        val performedExerciseUuid: CanonicalUuid,
        val setPosition: Int,
        val leaseId: CanonicalUuid,
        val leaseGeneration: Long,
        val admittedAtPhoneElapsedRealtimeMs: Long,
    )

    private data class AttemptBinding(
        val commandId: CanonicalUuid,
        val stableFingerprint: ByteArray,
        var attemptFingerprint: ByteArray?,
        var deliveries: Int,
        var acceptsSuccessorAttempt: Boolean,
    )

    private companion object {
        const val MAX_DELIVERIES_PER_ATTEMPT: Int = 2
        const val MAX_SEEN_COMMANDS: Int = 64
        const val LOAD_FACTOR: Float = 0.75f
    }
}

/**
 * Publishes only while the durable source tuple is still current. The publication happens inside
 * a writer transition, so a later phone mutation must run after it and retire the slot through the
 * post-commit listener; a mutation that won the preceding gap makes this candidate ineligible.
 */
internal suspend fun WearMutationLeaseStore.publishIfCurrent(
    database: AppDatabase,
    transition: DbTransitionRunner,
    candidate: PendingMutationLease,
): Boolean = transition {
    val metadata = database.wearSyncDao.getDatabaseMetadata()
        ?: return@transition false
    val sync = database.wearSyncDao.getSessionSync(Uuid.parse(candidate.sessionUuid.value))
        ?: return@transition false
    if (metadata.databaseEpoch != candidate.databaseEpoch.value ||
        sync.revision != candidate.sessionRevision ||
        sync.leaseGeneration != candidate.leaseGeneration
    ) {
        return@transition false
    }
    publish(candidate)
}
