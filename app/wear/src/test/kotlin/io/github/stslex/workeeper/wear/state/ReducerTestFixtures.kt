// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.state

import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.CommandOutcome
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.FingerprintCommand
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
import io.github.stslex.workeeper.core.wear.protocol.OutlineExercise
import io.github.stslex.workeeper.core.wear.protocol.OutlineRules
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandResponse
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

    /** The outline's second exercise: one open set, after the one rule R selects. */
    val laterExercise = uuid("cccccccc-0000-4000-8000-000000000009")

    /**
     * An outline whose rule R set is [targetPosition] of [targetExercise]: done sets before it, one
     * open set after it, then [laterExercise] with one open set.
     */
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
        payload = SnapshotPayload.Active(
            sessionUuid = session,
            sessionRevision = revision,
            trainingName = BoundedDisplayName.Value("Training"),
            exercises = listOf(
                outlineExercise(
                    targetExercise,
                    List(targetPosition) { done(it) } + open(targetPosition) + open(targetPosition + 1),
                ),
                outlineExercise(laterExercise, listOf(open(0))),
            ),
            mutationAuthority = if (unavailable) {
                MutationAuthority.Unavailable(MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED)
            } else {
                MutationAuthority.Granted(leaseId, leaseGeneration, remainingMs)
            },
        ),
    )

    fun outlineExercise(
        uuid: CanonicalUuid,
        sets: List<OutlineSet>,
        skipped: Boolean = false,
    ) = OutlineExercise(uuid, BoundedDisplayName.Value("Exercise"), ExerciseTypeWire.WEIGHTED, skipped, sets)

    fun open(position: Int) = OutlineSet.Open(position, SetTypeWire.WORK, reps = 8, weightHundredthsKg = 10_000)

    fun done(position: Int) = OutlineSet.Done(position, SetTypeWire.WORK, reps = 8, weightHundredthsKg = 10_000)

    /** Rule R's set of an outline whose selection is an `Open` set, with its one-based ordinal. */
    fun ruleROpenSet(snapshot: SnapshotData): Pair<Int, OutlineSet.Open> {
        val payload = snapshot.payload as SnapshotPayload.Active
        val selection = requireNotNull(OutlineRules.defaultSelection(payload))
        val sets = payload.exercises.single { it.performedExerciseUuid == selection.performedExerciseUuid }.sets
        val index = sets.indexOfFirst { it.position == selection.setPosition }
        return index + 1 to sets[index] as OutlineSet.Open
    }

    /** The same outline with rule R's `Open` set planned at [reps]. */
    fun withRuleRReps(snapshot: SnapshotData, reps: Int): SnapshotData {
        val payload = snapshot.payload as SnapshotPayload.Active
        val selection = requireNotNull(OutlineRules.defaultSelection(payload))
        val exercises = payload.exercises.map { exercise ->
            if (exercise.performedExerciseUuid != selection.performedExerciseUuid) return@map exercise
            exercise.copy(
                sets = exercise.sets.map { set ->
                    if (set is OutlineSet.Open && set.position == selection.setPosition) set.copy(reps = reps) else set
                },
            )
        }
        return snapshot.copy(payload = payload.copy(exercises = exercises))
    }

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
        body = WatchCommandBody.CompleteSet(
            performedExerciseUuid = exercise,
            setPosition = position,
            reps = reps,
            weightHundredthsKg = weight,
            exerciseType = ExerciseTypeWire.WEIGHTED,
            setType = SetTypeWire.WORK,
        ),
        mutationLeaseId = leaseId,
        mutationLeaseGeneration = leaseGeneration,
    )

    fun response(
        correlationId: CanonicalUuid,
        outcome: CommandOutcome,
        replacement: SnapshotData,
        command: CanonicalUuid = commandId,
    ) = WatchCommandResponse(
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
