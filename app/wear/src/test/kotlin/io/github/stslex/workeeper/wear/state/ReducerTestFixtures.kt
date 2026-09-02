// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.ActiveTarget
import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CompleteCommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.CompleteCurrentSetResponse
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol

internal object ReducerTestFixtures {

    val epoch = uuid("cccccccc-0000-4000-8000-000000000001")
    val otherEpoch = uuid("dddddddd-0000-4000-8000-000000000001")
    val sessionA = uuid("cccccccc-0000-4000-8000-000000000002")
    val sessionB = uuid("cccccccc-0000-4000-8000-000000000003")
    val exerciseA = uuid("cccccccc-0000-4000-8000-000000000004")
    val exerciseB = uuid("cccccccc-0000-4000-8000-000000000005")
    val lease1 = uuid("cccccccc-0000-4000-8000-000000000006")
    val lease2 = uuid("cccccccc-0000-4000-8000-000000000007")
    val commandId = uuid("cccccccc-0000-4000-8000-000000000008")

    @Suppress("LongParameterList") // Scenario builder keeps race dimensions explicit at call sites.
    fun active(
        databaseEpoch: CanonicalUuid = epoch,
        session: CanonicalUuid = sessionA,
        revision: Long = 1,
        leaseGeneration: Long = 1,
        leaseId: CanonicalUuid = lease1,
        remainingMs: Long = WearProtocol.MAX_MUTATION_WINDOW_MS,
        targetExercise: CanonicalUuid = exerciseA,
        targetPosition: Int = 0,
        unavailable: Boolean = false,
    ): SnapshotData = SnapshotData(
        databaseEpoch = databaseEpoch,
        payload = SnapshotPayload.ActiveWithTarget(
            sessionUuid = session,
            sessionRevision = revision,
            trainingName = BoundedDisplayName.Value("Training"),
            completedExercises = 0,
            totalExercises = 2,
            target = ActiveTarget(
                performedExerciseUuid = targetExercise,
                exerciseName = BoundedDisplayName.Value("Exercise"),
                setPosition = targetPosition,
                setOrdinal = targetPosition + 1,
                totalSets = targetPosition + 2,
                reps = 8,
                weightHundredthsKg = 10_000,
                exerciseType = ExerciseTypeWire.WEIGHTED,
                setType = SetTypeWire.WORK,
            ),
            mutationAuthority = if (unavailable) {
                MutationAuthority.Unavailable(MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED)
            } else {
                MutationAuthority.Granted(leaseId, leaseGeneration, remainingMs)
            },
        ),
    )

    fun noSession(databaseEpoch: CanonicalUuid = epoch): SnapshotData = SnapshotData(
        databaseEpoch = databaseEpoch,
        payload = SnapshotPayload.NoSession,
    )

    @Suppress("LongParameterList") // Mirrors every source/lease field varied by reducer tests.
    fun fingerprint(
        command: CanonicalUuid = commandId,
        databaseEpoch: CanonicalUuid = epoch,
        session: CanonicalUuid = sessionA,
        revision: Long = 1,
        exercise: CanonicalUuid = exerciseA,
        position: Int = 0,
        leaseId: CanonicalUuid = lease1,
        leaseGeneration: Long = 1,
        reps: Int = 8,
        weight: Int? = 10_000,
    ): FingerprintCommand = FingerprintCommand(
        sourceNodeId = "watch-node",
        schemaVersion = WearProtocol.SCHEMA_VERSION,
        commandId = command,
        databaseEpoch = databaseEpoch,
        sessionUuid = session,
        sessionRevision = revision,
        performedExerciseUuid = exercise,
        setPosition = position,
        reps = reps,
        weightHundredthsKg = weight,
        exerciseType = ExerciseTypeWire.WEIGHTED,
        setType = SetTypeWire.WORK,
        mutationLeaseId = leaseId,
        mutationLeaseGeneration = leaseGeneration,
    )

    fun response(
        correlationId: CanonicalUuid,
        outcome: CompleteCommandOutcome,
        replacement: SnapshotData,
        command: CanonicalUuid = commandId,
    ) = CompleteCurrentSetResponse(
        schemaVersion = WearProtocol.SCHEMA_VERSION,
        correlationId = correlationId,
        commandId = command,
        outcome = outcome,
        replacement = replacement,
    )

    fun id(number: Int): CanonicalUuid = uuid(
        "eeeeeeee-0000-4000-8000-${number.toString().padStart(12, '0')}",
    )

    fun uuid(value: String): CanonicalUuid = CanonicalUuid.parse(value)
}
