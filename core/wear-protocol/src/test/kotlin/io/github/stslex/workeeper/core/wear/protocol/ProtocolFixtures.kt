// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

internal object ProtocolFixtures {

    val correlationId = uuid("aaaaaaaa-0000-4000-8000-000000000001")
    val commandId = uuid("aaaaaaaa-0000-4000-8000-000000000002")
    val databaseEpoch = uuid("aaaaaaaa-0000-4000-8000-000000000003")
    val sessionUuid = uuid("aaaaaaaa-0000-4000-8000-000000000004")
    val exerciseUuid = uuid("aaaaaaaa-0000-4000-8000-000000000005")
    val leaseId = uuid("aaaaaaaa-0000-4000-8000-000000000006")
    val secondExerciseUuid = uuid("aaaaaaaa-0000-4000-8000-000000000007")
    val skippedExerciseUuid = uuid("aaaaaaaa-0000-4000-8000-000000000008")

    val granted = MutationAuthority.Granted(
        mutationLeaseId = leaseId,
        mutationLeaseGeneration = 7,
        leaseRemainingAtPhoneSendMs = WearProtocol.MAX_MUTATION_WINDOW_MS,
    )
    val unavailable = MutationAuthority.Unavailable(MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED)

    /** Squat: one done set, one open set at position 1, one blocked; a skipped and a set-less exercise after. */
    fun outline(): List<OutlineExercise> = listOf(
        OutlineExercise(
            performedExerciseUuid = exerciseUuid,
            name = BoundedDisplayName.Value("Squat"),
            exerciseType = ExerciseTypeWire.WEIGHTED,
            skipped = false,
            sets = listOf(
                OutlineSet.Done(position = 0, setType = SetTypeWire.WARM, reps = 10, weightHundredthsKg = 6_000),
                OutlineSet.Open(position = 1, setType = SetTypeWire.WORK, reps = 8, weightHundredthsKg = 10_000),
                OutlineSet.Blocked(position = 3, setType = SetTypeWire.DROP, field = NumericField.WEIGHT),
            ),
        ),
        OutlineExercise(
            performedExerciseUuid = skippedExerciseUuid,
            name = BoundedDisplayName.Omitted(OmissionReason.TOO_LARGE),
            exerciseType = ExerciseTypeWire.WEIGHTLESS,
            skipped = true,
            sets = listOf(
                OutlineSet.Open(position = 0, setType = SetTypeWire.WORK, reps = 12, weightHundredthsKg = null),
            ),
        ),
        OutlineExercise(
            performedExerciseUuid = secondExerciseUuid,
            name = BoundedDisplayName.Value("Plank"),
            exerciseType = ExerciseTypeWire.WEIGHTLESS,
            skipped = false,
            sets = emptyList(),
        ),
    )

    fun activePayload(
        authority: MutationAuthority = granted,
        exercises: List<OutlineExercise> = outline(),
    ) = SnapshotPayload.Active(
        sessionUuid = sessionUuid,
        sessionRevision = 11,
        trainingName = BoundedDisplayName.Value("Strength"),
        exercises = exercises,
        mutationAuthority = authority,
    )

    fun snapshot(payload: SnapshotPayload = activePayload()) = SnapshotData(
        databaseEpoch = databaseEpoch,
        payload = payload,
    )

    fun command(body: WatchCommandBody = commandBody()) = WatchCommandRequest(
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
    ) = WatchCommandBody.CompleteSet(
        performedExerciseUuid = exerciseUuid,
        setPosition = 1,
        reps = reps,
        weightHundredthsKg = weightHundredthsKg,
        exerciseType = exerciseType,
        setType = SetTypeWire.WORK,
    )

    fun skipBody(skipped: Boolean = true) = WatchCommandBody.SetExerciseSkipped(
        performedExerciseUuid = exerciseUuid,
        skipped = skipped,
    )

    fun commandResponse(outcome: CommandOutcome, replacement: SnapshotData) = WatchCommandResponse(
        schemaVersion = WearProtocol.SCHEMA_VERSION,
        correlationId = correlationId,
        commandId = commandId,
        outcome = outcome,
        replacement = replacement,
    )

    fun uuid(value: String): CanonicalUuid = CanonicalUuid.parse(value)
}
