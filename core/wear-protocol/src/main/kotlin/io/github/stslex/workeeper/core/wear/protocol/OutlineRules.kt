// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.wear.protocol

/** An exercise's state in a session outline (wear-phase-2-exercise-list.md §5.3). */
enum class OutlineStatus {
    /** It has an `Open` or `Blocked` set. */
    OPEN,

    /** Not skipped, at least one set, and every set `Done`. */
    FINISHED,
    SKIPPED,

    /** Not skipped and no set at all. */
    NO_SETS,
}

/**
 * One exercise of an outline and, for an exercise with sets, one of its incomplete sets: the watch's
 * selection, and what rule R gives. [setPosition] is null for an exercise without sets.
 */
data class OutlineSelection(val performedExerciseUuid: CanonicalUuid, val setPosition: Int?) {

    /** GUARD: names its type only; the selection is never logged (§9). */
    override fun toString(): String = "OutlineSelection"
}

/** Finished exercises out of the exercises that are not skipped (P13). */
data class ExerciseProgress(val finished: Int, val total: Int)

/**
 * The pure rules both sides read an outline by (wear-phase-2-exercise-list.md §5.3): the phone's
 * command validation, and the watch's selection, eligibility and pairing.
 */
object OutlineRules {

    fun isFinished(exercise: OutlineExercise): Boolean =
        !exercise.skipped && exercise.sets.isNotEmpty() && exercise.sets.all { it is OutlineSet.Done }

    fun status(exercise: OutlineExercise): OutlineStatus = when {
        exercise.skipped -> OutlineStatus.SKIPPED
        isFinished(exercise) -> OutlineStatus.FINISHED
        exercise.sets.isEmpty() -> OutlineStatus.NO_SETS
        else -> OutlineStatus.OPEN
    }

    /** The `Open` or `Blocked` set with the lowest position, or null. */
    fun firstIncompleteSet(exercise: OutlineExercise): OutlineSet? =
        exercise.sets.firstOrNull { it !is OutlineSet.Done }

    /**
     * Rule R (Phase 1 §4 over the outline): the first exercise in order that is `OPEN` or `NO_SETS`,
     * on its first incomplete set, or on no position for `NO_SETS`. An exercise without sets stops
     * the search (P12). None: no selection.
     */
    fun defaultSelection(outline: SnapshotPayload.Active): OutlineSelection? {
        val exercise = outline.exercises.firstOrNull { it.isPending() } ?: return null
        return OutlineSelection(exercise.performedExerciseUuid, firstIncompleteSet(exercise)?.position)
    }

    /** No exercise is `OPEN` or `NO_SETS`; an empty outline included. */
    fun isWorkoutComplete(outline: SnapshotPayload.Active): Boolean = outline.exercises.none { it.isPending() }

    fun exerciseProgress(outline: SnapshotPayload.Active): ExerciseProgress = ExerciseProgress(
        finished = outline.exercises.count(::isFinished),
        total = outline.exercises.count { !it.skipped },
    )

    /** The exercise exists, is not skipped, and has an `Open` set at [setPosition]. */
    fun completable(payload: SnapshotPayload, performedExerciseUuid: CanonicalUuid, setPosition: Int): Boolean {
        val exercise = (payload as? SnapshotPayload.Active)?.exercise(performedExerciseUuid) ?: return false
        return !exercise.skipped && exercise.sets.any { it.position == setPosition && it is OutlineSet.Open }
    }

    /** The exercise exists and its skip flag differs from [skipped]. */
    fun skippable(payload: SnapshotPayload, performedExerciseUuid: CanonicalUuid, skipped: Boolean): Boolean {
        val exercise = (payload as? SnapshotPayload.Active)?.exercise(performedExerciseUuid) ?: return false
        return exercise.skipped != skipped
    }

    /** What a command [body] asks is still possible on [payload]: [completable] or [skippable]. */
    fun allows(payload: SnapshotPayload, body: WatchCommandBody): Boolean = when (body) {
        is WatchCommandBody.CompleteSet -> completable(payload, body.performedExerciseUuid, body.setPosition)
        is WatchCommandBody.SetExerciseSkipped -> skippable(payload, body.performedExerciseUuid, body.skipped)
    }

    private fun OutlineExercise.isPending(): Boolean = status(this).let {
        it == OutlineStatus.OPEN || it == OutlineStatus.NO_SETS
    }
}

/** The exercise of this outline with [performedExerciseUuid], or null. */
fun SnapshotPayload.Active.exercise(performedExerciseUuid: CanonicalUuid): OutlineExercise? =
    exercises.firstOrNull { it.performedExerciseUuid == performedExerciseUuid }
