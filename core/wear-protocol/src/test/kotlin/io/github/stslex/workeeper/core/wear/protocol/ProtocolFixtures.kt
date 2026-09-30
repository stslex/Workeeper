// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

internal object ProtocolFixtures {

    val correlationId = uuid("aaaaaaaa-0000-4000-8000-000000000001")
    val commandId = uuid("aaaaaaaa-0000-4000-8000-000000000002")
    val databaseEpoch = uuid("aaaaaaaa-0000-4000-8000-000000000003")
    val sessionUuid = uuid("aaaaaaaa-0000-4000-8000-000000000004")
    val exerciseUuid = uuid("aaaaaaaa-0000-4000-8000-000000000005")
    val leaseId = uuid("aaaaaaaa-0000-4000-8000-000000000006")

    fun activePayload(
        authority: MutationAuthority = MutationAuthority.Granted(
            mutationLeaseId = leaseId,
            mutationLeaseGeneration = 7,
            leaseRemainingAtPhoneSendMs = WearProtocol.MAX_MUTATION_WINDOW_MS,
        ),
    ) = SnapshotPayload.ActiveWithTarget(
        sessionUuid = sessionUuid,
        sessionRevision = 11,
        trainingName = BoundedDisplayName.Value("Strength"),
        completedExercises = 1,
        totalExercises = 3,
        target = ActiveTarget(
            performedExerciseUuid = exerciseUuid,
            exerciseName = BoundedDisplayName.Value("Squat"),
            setPosition = 1,
            setOrdinal = 2,
            totalSets = 4,
            reps = 8,
            weightHundredthsKg = 10_000,
            exerciseType = ExerciseTypeWire.WEIGHTED,
            setType = SetTypeWire.WORK,
        ),
        mutationAuthority = authority,
    )

    fun snapshot(payload: SnapshotPayload = activePayload()) = SnapshotData(
        databaseEpoch = databaseEpoch,
        payload = payload,
    )

    fun command(body: CompleteCurrentSetBody = commandBody()) = CompleteCurrentSetRequest(
        schemaVersion = WearProtocol.SCHEMA_VERSION,
        correlationId = correlationId,
        commandId = commandId,
        databaseEpoch = databaseEpoch,
        sessionUuid = sessionUuid,
        sessionRevision = 11,
        mutationLeaseId = leaseId,
        mutationLeaseGeneration = 7,
        body = body,
    )

    fun commandBody(
        reps: Int = 8,
        weightHundredthsKg: Int? = 10_000,
        exerciseType: ExerciseTypeWire = ExerciseTypeWire.WEIGHTED,
    ) = CompleteCurrentSetBody(
        performedExerciseUuid = exerciseUuid,
        setPosition = 1,
        reps = reps,
        weightHundredthsKg = weightHundredthsKg,
        exerciseType = exerciseType,
        setType = SetTypeWire.WORK,
    )

    fun uuid(value: String): CanonicalUuid = CanonicalUuid.parse(value)
}
