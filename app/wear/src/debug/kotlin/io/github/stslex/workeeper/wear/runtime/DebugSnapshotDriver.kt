// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.ActiveTarget
import io.github.stslex.workeeper.core.wear.protocol.ActiveWorkoutSnapshotResponse
import io.github.stslex.workeeper.core.wear.protocol.BoundedDisplayName
import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.MutationAuthority
import io.github.stslex.workeeper.core.wear.protocol.MutationUnavailableReason
import io.github.stslex.workeeper.core.wear.protocol.OmissionReason
import io.github.stslex.workeeper.core.wear.protocol.PhoneActionReason
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotData
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WearProtocol
import io.github.stslex.workeeper.wear.cache.ElapsedRealtimeClock
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import io.github.stslex.workeeper.wear.ui.WearSurfaceKind
import io.github.stslex.workeeper.wear.ui.WearSurfaceModel

/** Debug-only source. Every snapshot passes through the real correlation/admission/cache path. */
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

    private fun active(model: WearSurfaceModel, granted: Boolean): SnapshotPayload.ActiveWithTarget {
        leaseGeneration += 1
        return SnapshotPayload.ActiveWithTarget(
            sessionUuid = session,
            sessionRevision = revision,
            trainingName = model.trainingName.displayName(),
            completedExercises = model.completedExercises ?: 0,
            totalExercises = model.totalExercises ?: 1,
            target = ActiveTarget(
                performedExerciseUuid = exercise,
                exerciseName = model.exerciseName.displayName(),
                setPosition = requireNotNull(model.setOrdinal) - 1,
                setOrdinal = requireNotNull(model.setOrdinal),
                totalSets = requireNotNull(model.totalSets),
                reps = requireNotNull(model.reps),
                weightHundredthsKg = model.weightHundredthsKg,
                exerciseType = if (model.weighted) ExerciseTypeWire.WEIGHTED else ExerciseTypeWire.WEIGHTLESS,
                setType = SetTypeWire.WORK,
            ),
            mutationAuthority = if (granted) {
                MutationAuthority.Granted(ids.nextId(), leaseGeneration, WearProtocol.MAX_MUTATION_WINDOW_MS)
            } else {
                MutationAuthority.Unavailable(MutationUnavailableReason.FRESH_HANDSHAKE_REQUIRED)
            },
        )
    }

    private fun phoneAction(model: WearSurfaceModel): SnapshotPayload.PhoneActionRequired? {
        val reason = when (model.kind) {
            WearSurfaceKind.PHONE_ACTION_NO_SETS ->
                PhoneActionReason.NoSetRows(exercise, model.exerciseName.displayName())
            WearSurfaceKind.PHONE_ACTION_UNSUPPORTED -> PhoneActionReason.UnsupportedNumericValues(
                requireNotNull(model.fieldError),
                exercise,
                model.exerciseName.displayName(),
            )
            WearSurfaceKind.PAYLOAD_TOO_LARGE -> PhoneActionReason.PayloadTooLarge
            else -> return null
        }
        revision += 1
        current = null
        return SnapshotPayload.PhoneActionRequired(session, revision, reason)
    }

    private fun String?.displayName(): BoundedDisplayName =
        this?.let(BoundedDisplayName::from) ?: BoundedDisplayName.Omitted(OmissionReason.TOO_LARGE)

    private fun terminal(): Boolean {
        val previous = current ?: return false
        revision += 1
        return publish(
            SnapshotPayload.WorkoutComplete(
                sessionUuid = session,
                sessionRevision = revision,
                trainingName = previous.trainingName.displayName(),
                completedExercises = previous.totalExercises ?: 1,
                totalExercises = previous.totalExercises ?: 1,
            ),
        ).also { if (it) current = null }
    }

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
