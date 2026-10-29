// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.ui.kit.components.rail.RailGroup
import io.github.stslex.workeeper.core.ui.kit.components.rail.RailSegment
import io.github.stslex.workeeper.feature.live_workout.mvi.model.ExerciseStatusUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Projects session state onto the progress rail (§8): one group per exercise, one segment per
 * visible row, so the rail's denominator matches the cards below it.
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
