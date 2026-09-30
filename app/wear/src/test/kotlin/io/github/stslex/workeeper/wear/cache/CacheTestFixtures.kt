// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.cache

import io.github.stslex.workeeper.core.wear.protocol.ActiveTarget
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.core.wear.protocol.WearProtocolCodec
import java.io.IOException

internal object CacheTestFixtures {

    val databaseEpoch = uuid("bbbbbbbb-0000-4000-8000-000000000001")
    val sessionUuid = uuid("bbbbbbbb-0000-4000-8000-000000000002")
    val exerciseUuid = uuid("bbbbbbbb-0000-4000-8000-000000000003")
    val correlationId = uuid("bbbbbbbb-0000-4000-8000-000000000004")
    val leaseId = uuid("bbbbbbbb-0000-4000-8000-000000000005")

    fun snapshot(revision: Long = 5): SnapshotData = SnapshotData(
        databaseEpoch = databaseEpoch,
        payload = SnapshotPayload.ActiveWithTarget(
            sessionUuid = sessionUuid,
            sessionRevision = revision,
            trainingName = BoundedDisplayName.Value("Training"),
            completedExercises = 0,
            totalExercises = 1,
            target = ActiveTarget(
                performedExerciseUuid = exerciseUuid,
                exerciseName = BoundedDisplayName.Value("Exercise"),
                setPosition = 0,
                setOrdinal = 1,
                totalSets = 1,
                reps = 10,
                weightHundredthsKg = 5_000,
                exerciseType = ExerciseTypeWire.WEIGHTED,
                setType = SetTypeWire.WORK,
            ),
            mutationAuthority = MutationAuthority.Granted(
                mutationLeaseId = leaseId,
                mutationLeaseGeneration = 8,
                leaseRemainingAtPhoneSendMs = WearProtocol.MAX_MUTATION_WINDOW_MS,
            ),
        ),
    )

    fun encodedSnapshot(revision: Long = 5): ByteArray = WearProtocolCodec.encode(
        ActiveWorkoutSnapshotResponse(
            schemaVersion = WearProtocol.SCHEMA_VERSION,
            correlationId = correlationId,
            snapshot = snapshot(revision),
        ),
    )

    fun uuid(value: String): CanonicalUuid = CanonicalUuid.parse(value)
}

internal class FakeAtomicStorage(initial: ByteArray? = null) : AtomicRecordStorage {
    var bytes: ByteArray? = initial?.copyOf()
    var deleteCalls: Int = 0
    var failDelete: Boolean = false
    var crashCut: CrashCut? = null

    override fun read(): ByteArray? = bytes?.copyOf()

    override fun replace(bytes: ByteArray) {
        when (crashCut) {
            CrashCut.TEMP_WRITE,
            CrashCut.DURABLE_SYNC,
            -> throw IOException("simulated pre-publish crash")
            CrashCut.AFTER_ATOMIC_PUBLISH -> {
                this.bytes = bytes.copyOf()
                throw IOException("simulated post-publish crash")
            }
            null -> this.bytes = bytes.copyOf()
        }
    }

    override fun delete(): Boolean {
        deleteCalls += 1
        if (!failDelete) bytes = null
        return !failDelete
    }
}

internal enum class CrashCut {
    TEMP_WRITE,
    DURABLE_SYNC,
    AFTER_ATOMIC_PUBLISH,
}
