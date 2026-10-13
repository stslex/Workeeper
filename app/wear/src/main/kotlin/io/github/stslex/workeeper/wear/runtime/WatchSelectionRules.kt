// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.runtime

import io.github.stslex.workeeper.core.wear.protocol.CanonicalUuid
import io.github.stslex.workeeper.core.wear.protocol.OutlineRules
import io.github.stslex.workeeper.core.wear.protocol.OutlineSet
import io.github.stslex.workeeper.core.wear.protocol.OutlineStatus
import io.github.stslex.workeeper.core.wear.protocol.SnapshotPayload
import io.github.stslex.workeeper.core.wear.protocol.exercise
import io.github.stslex.workeeper.wear.state.isSelectable

/**
 * wear-phase-2-exercise-list.md §7.2: the selection on a newly admitted outline. [current] is null
 * when there is no selection or the outline belongs to another session.
 *
 * 1. no selection, or an outline of another session: rule R;
 * 2. the selected exercise is gone, skipped or finished: rule R;
 * 3. the selected position is still `Open` or `Blocked`: kept;
 * 4. otherwise (the set is `Done`, its position is gone, or the exercise had no sets and now has
 *    some): the first incomplete set of the same exercise.
 */
internal fun reconcileSelection(current: WatchSelection?, outline: SnapshotPayload.Active): WatchSelection? {
    current ?: return OutlineRules.defaultSelection(outline)
    val exercise = outline.exercise(current.performedExerciseUuid)
    if (exercise == null || OutlineRules.status(exercise) in LEFT_EXERCISES) {
        return OutlineRules.defaultSelection(outline)
    }
    val set = exercise.sets.firstOrNull { it.position == current.setPosition }
    if (set is OutlineSet.Open || set is OutlineSet.Blocked) return current
    return WatchSelection(exercise.performedExerciseUuid, OutlineRules.firstIncompleteSet(exercise)?.position)
}

/** Opening [uuid] from the list (§7.2): the first incomplete set of an `OPEN` or `NO_SETS` exercise. */
internal fun SnapshotPayload.Active.exerciseSelection(uuid: CanonicalUuid): WatchSelection? {
    val exercise = exercise(uuid) ?: return null
    if (OutlineRules.status(exercise) in LEFT_EXERCISES) return null
    return WatchSelection(uuid, OutlineRules.firstIncompleteSet(exercise)?.position)
}

/** Picking a set on Sets (§7.2): an `Open` or `Blocked` set of an exercise that is still open. */
internal fun SnapshotPayload.Active.setSelection(uuid: CanonicalUuid, position: Int): WatchSelection? =
    WatchSelection(uuid, position).takeIf(::isSelectable)

/** Exercises the selection leaves: nothing is left to do on them from the watch's controller. */
private val LEFT_EXERCISES = setOf(OutlineStatus.FINISHED, OutlineStatus.SKIPPED)
