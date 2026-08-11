// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.ui.kit.components.rail.RailGroup
import io.github.stslex.workeeper.core.ui.kit.components.rail.RailSegment
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Projects session state onto the progress rail (§8).
 *
 * One group per exercise, one segment per **visible** row. Visible rows are the list the user
 * actually sees, so the denominator on the rail is the denominator on screen — the rail cannot
 * claim a different total from the cards below it.
 *
 * Unfilled rows (§6.1) still occupy a segment: they are planned work not yet done, which is
 * exactly what an unfilled segment means. What §6.1 rules out is counting them as *completed*,
 * and [RailSegment.isFilled] is driven by `isDone` alone.
 */
internal object RailMapper {

    fun LiveWorkoutStore.State.toRailGroups(): ImmutableList<RailGroup> = exercises
        .map { exercise ->
            RailGroup(
                segments = exercise.visibleSets
                    .map { set ->
                        RailSegment(isFilled = set.isDone, isRecord = set.isPersonalRecord)
                    }
                    .toImmutableList(),
                isSkipped = exercise.status == ExerciseStatusUiModel.SKIPPED,
                isOneOff = !exercise.isPlanAttached,
            )
        }
        .toImmutableList()
}
