// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
import io.github.stslex.workeeper.core.wear.protocol.OmissionReason
import io.github.stslex.workeeper.core.wear.protocol.OutlineExercise
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel

/**
 * Debug-only source. Every snapshot passes through the real correlation/admission/cache path.
 *
 * Each fixture is published as a protocol v2 outline built so that rule R selects the fixture's set
 * and the surface mapper shows the fixture's surface (wear-phase-2-exercise-list.md §12.1 step 2):
 * the finished exercises first, then the selected one, then the rest of the session.
 */
internal class DebugSnapshotDriver(
    private val owner: WatchRuntimeOwner,
    private val ids: RuntimeIdSource,
    private val clock: DebugElapsedClock,
) {
    private val epoch = ids.nextId()
    private val session = ids.nextId()
    private val exercise = ids.nextId()
    private var revision = 0L
    private var leaseGeneration = 0L
    private var current: WearSurfaceModel? = null

    @Synchronized
    fun handle(scenario: String): Boolean = when (scenario) {
        "disconnect" -> true.also { owner.disconnected() }
        "expire" -> true.also {
            clock.advanceBy(WearProtocol.MAX_MUTATION_WINDOW_MS)
            owner.onWake()
        }
        "stop_ongoing" -> true.also {
            clock.advanceBy(WearProtocol.MAX_MUTATION_WINDOW_MS + DEBUG_UNCALIBRATED_RECONNECT_WINDOW_MS)
            owner.onWake()
        }
        "terminal" -> terminal()
        "no_session" -> publish(SnapshotPayload.NoSession).also { if (it) current = null }
        "refresh" -> current?.let { publish(active(it, granted = true)) } ?: false
        else -> showFixture(scenario.removePrefix("fixture:"))
    }

    private fun showFixture(id: String): Boolean {
        val model = SyntheticSurfaceFixtures.find(id) ?: return false
        if (model.kind == WearSurfaceKind.NO_SESSION) {
            return publish(SnapshotPayload.NoSession).also { if (it) current = null }
        }
        if (model.kind == WearSurfaceKind.WORKOUT_COMPLETE) {
            current = model
            return terminal()
        }
        phoneAction(model)?.let { return publish(it) }
        if (!model.controlsVisible || model.weightHundredthsKg?.let {
                it !in 0..WearProtocol.MAX_WEAR_WEIGHT_HUNDREDTHS_KG
            } == true
        ) {
            return false
        }
        current = model
        revision += 1
        val accepted = publish(active(model, granted = model.kind != WearSurfaceKind.REFRESH_REQUIRED))
        if (accepted && model.kind == WearSurfaceKind.DISCONNECTED) owner.disconnected()
        if (accepted && id == SyntheticSurfaceFixtures.COMMAND_IN_FLIGHT) owner.onAction(ControllerAction.CompleteSet)
        return accepted
    }

    /**
     * [model]'s `completedExercises` finished exercises, then its exercise with its set at
     * `setOrdinal` of `totalSets` open (the sets before it done), then open ones up to
     * `totalExercises`.
     */
    private fun active(model: WearSurfaceModel, granted: Boolean): SnapshotPayload.Active {
        leaseGeneration += 1
        val ordinal = requireNotNull(model.setOrdinal)
        val type = if (model.weighted) ExerciseTypeWire.WEIGHTED else ExerciseTypeWire.WEIGHTLESS
        val selected = OutlineExercise(
            performedExerciseUuid = exercise,
            name = model.exerciseName.displayName(),
            exerciseType = type,
            skipped = false,
            sets = List(requireNotNull(model.totalSets)) { position ->
                if (position < ordinal - 1) {
                    OutlineSet.Done(position, SetTypeWire.WORK, reps = null, weightHundredthsKg = null)
                } else {
                    OutlineSet.Open(position, SetTypeWire.WORK, requireNotNull(model.reps), model.weightHundredthsKg)
                }
            },
        )
        val finished = model.completedExercises ?: 0
        val total = model.totalExercises ?: 1
        return outline(
            trainingName = model.trainingName,
            exercises = List(finished) { finishedExercise(it) } + selected +
                List(total - finished - 1) { openExercise(finished + 1 + it) },
            authority = if (granted) {
                MutationAuthority.Granted(ids.nextId(), leaseGeneration, WearProtocol.MAX_MUTATION_WINDOW_MS)
            } else {
                MutationAuthority.Unavailable(MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED)
            },
        )
    }

    /** The first exercise asks for the phone: no sets, or a planned value outside the watch domain. */
    private fun phoneAction(model: WearSurfaceModel): SnapshotPayload? {
        val sets = when (model.kind) {
            WearSurfaceKind.PHONE_ACTION_NO_SETS -> emptyList()
            WearSurfaceKind.PHONE_ACTION_UNSUPPORTED ->
                listOf(OutlineSet.Blocked(0, SetTypeWire.WORK, requireNotNull(model.fieldError)))
            WearSurfaceKind.PAYLOAD_TOO_LARGE -> {
                revision += 1
                current = null
                return SnapshotPayload.PayloadTooLarge(session, revision)
            }
            else -> return null
        }
        revision += 1
        current = null
        val first = OutlineExercise(
            performedExerciseUuid = exercise,
            name = model.exerciseName.displayName(),
            exerciseType = ExerciseTypeWire.WEIGHTED,
            skipped = false,
            sets = sets,
        )
        return outline(model.trainingName, listOf(first), UNAVAILABLE)
    }

    private fun String?.displayName(): BoundedDisplayName =
        this?.let(BoundedDisplayName::from) ?: BoundedDisplayName.Omitted(OmissionReason.TOO_LARGE)

    /** Every exercise of the previous fixture finished: no selection, the workout is complete. */
    private fun terminal(): Boolean {
        val previous = current ?: return false
        revision += 1
        return publish(
            outline(
                trainingName = previous.trainingName,
                exercises = List(previous.totalExercises ?: 1) { finishedExercise(it) },
                authority = UNAVAILABLE,
            ),
        ).also { if (it) current = null }
    }

    private fun outline(
        trainingName: String?,
        exercises: List<OutlineExercise>,
        authority: MutationAuthority,
    ) = SnapshotPayload.Active(
        sessionUuid = session,
        sessionRevision = revision,
        trainingName = trainingName.displayName(),
        exercises = exercises,
        mutationAuthority = authority,
    )

    private fun finishedExercise(index: Int) = otherExercise(
        index,
        OutlineSet.Done(position = 0, setType = SetTypeWire.WORK, reps = null, weightHundredthsKg = null),
    )

    private fun openExercise(index: Int) = otherExercise(
        index,
        OutlineSet.Open(position = 0, setType = SetTypeWire.WORK, reps = 1, weightHundredthsKg = null),
    )

    /** An exercise the Phase 1 controller never names; its id only has to differ from the others. */
    private fun otherExercise(index: Int, set: OutlineSet) = OutlineExercise(
        performedExerciseUuid = CanonicalUuid.parse(
            OTHER_EXERCISE_PREFIX + index.toString().padStart(ID_SUFFIX_LENGTH, '0'),
        ),
        name = BoundedDisplayName.Omitted(OmissionReason.TOO_LARGE),
        exerciseType = ExerciseTypeWire.WEIGHTLESS,
        skipped = false,
        sets = listOf(set),
    )

    private fun publish(payload: SnapshotPayload): Boolean {
        val request = owner.issueHandshake()
        return owner.receiveSnapshot(
            ActiveWorkoutSnapshotResponse(
                WearProtocol.SCHEMA_VERSION,
                request.correlationId,
                SnapshotData(epoch, payload),
            ),
        )
    }

    private companion object {
        val UNAVAILABLE = MutationAuthority.Unavailable(MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED)
        const val OTHER_EXERCISE_PREFIX = "e0000000-0000-4000-8000-"
    }
}

/** Synthetic offset is a debug scenario control, never physical elapsed-time evidence. */
internal class DebugElapsedClock(private val source: ElapsedRealtimeClock) : ElapsedRealtimeClock {
    private var offsetMs = 0L

    @Synchronized
    override fun nowMs(): Long = Math.addExact(source.nowMs(), offsetMs)

    @Synchronized
    fun advanceBy(durationMs: Long) {
        require(durationMs >= 0L)
        offsetMs = Math.addExact(offsetMs, durationMs)
    }
}

internal class DebugRuntimeIds(seed: Long) : RuntimeIdSource {
    private var next = seed

    @Synchronized
    override fun nextId(): CanonicalUuid {
        next = Math.addExact(next, 1L)
        return CanonicalUuid.parse("f0000000-0000-4000-8000-${next.toString(ID_RADIX).padStart(ID_SUFFIX_LENGTH, '0')}")
    }
}

// Synthetic interval only. Release policy and platform removal tolerance await the physical-watch probe.
internal const val DEBUG_UNCALIBRATED_RECONNECT_WINDOW_MS = 240_000L

private const val ID_RADIX = 16
private const val ID_SUFFIX_LENGTH = 12
