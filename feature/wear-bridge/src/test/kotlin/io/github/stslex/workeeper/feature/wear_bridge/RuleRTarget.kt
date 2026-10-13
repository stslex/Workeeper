// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.wear_bridge

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.ExerciseTypeWire
import io.github.stslex.workeeper.core.wear.protocol.OutlineRules
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.SetTypeWire
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.WatchCommandBody
import io.github.stslex.workeeper.core.wear.protocol.exercise

/** Rule R's set of an outline: the target Phase 1 named (wear-phase-2-exercise-list.md §5.3). */
internal data class RuleRTarget(
    val performedExerciseUuid: CanonicalUuid,
    val setPosition: Int,
    val setOrdinal: Int,
    val totalSets: Int,
    val reps: Int,
    val weightHundredthsKg: Int?,
    val exerciseType: ExerciseTypeWire,
    val setType: SetTypeWire,
)

/** Rule R's `Open` set of this outline; fails when rule R selects no `Open` set. */
internal val SnapshotPayload.Active.target: RuleRTarget
    get() {
        val selection = requireNotNull(OutlineRules.defaultSelection(this)) { "rule R selects nothing" }
        val exercise = requireNotNull(exercise(selection.performedExerciseUuid))
        val set = exercise.sets.first { it.position == selection.setPosition } as OutlineSet.Open
        return RuleRTarget(
            performedExerciseUuid = exercise.performedExerciseUuid,
            setPosition = set.position,
            setOrdinal = exercise.sets.indexOf(set) + 1,
            totalSets = exercise.sets.size,
            reps = set.reps,
            weightHundredthsKg = set.weightHundredthsKg,
            exerciseType = exercise.exerciseType,
            setType = set.setType,
        )
    }

/** A `complete_set` of rule R's set with its planned values. */
internal fun SnapshotPayload.Active.completeTargetBody(): WatchCommandBody.CompleteSet = WatchCommandBody.CompleteSet(
    performedExerciseUuid = target.performedExerciseUuid,
    setPosition = target.setPosition,
    reps = target.reps,
    weightHundredthsKg = target.weightHundredthsKg,
    exerciseType = target.exerciseType,
    setType = target.setType,
)

/** An `Active` outline where rule R selects nothing: the workout is complete (§5.3). */
internal fun SnapshotPayload.isWorkoutComplete(): Boolean =
    this is SnapshotPayload.Active && OutlineRules.isWorkoutComplete(this)
