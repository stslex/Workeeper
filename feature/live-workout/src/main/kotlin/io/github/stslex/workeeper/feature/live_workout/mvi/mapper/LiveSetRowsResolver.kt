// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.mapper

import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveExerciseUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList

/**
 * Resolves the visible-row list for each exercise card. Priority is
 * `performed > draft > plan > fallback`. Live workout's exercise card renders
 * `LiveExerciseUiModel.visibleSets` directly — UI never merges sources, never sees
 * `State.DraftKey`.
 *
 * Every state transition that touches `performedSets`, `planSets`, or `setDrafts`
 * must funnel through `State.withVisibleSets()` (directly, or via
 * `LiveWorkoutMapper.withPresentation`) so the resolver stays in sync.
 */
internal object LiveSetRowsResolver {

    fun resolveVisibleSets(
        exercise: LiveExerciseUiModel,
        drafts: ImmutableMap<State.DraftKey, LiveSetUiModel>,
    ): ImmutableList<LiveSetUiModel> {
        val draftPositions = drafts.keys
            .filter { it.performedExerciseUuid == exercise.performedExerciseUuid }
            .map { it.position }
        val total = maxOf(
            exercise.planSets.size,
            exercise.performedSets.size,
            (draftPositions.maxOrNull() ?: -1) + 1,
        )
        if (total == 0) return EMPTY
        val performedByPos = exercise.performedSets.associateBy { it.position }
        return (0 until total).map { position ->
            val performed = performedByPos[position]
            val draft = drafts[State.DraftKey(exercise.performedExerciseUuid, position)]
            val plan = exercise.planSets.getOrNull(position)
            when {
                performed != null -> performed
                draft != null -> draft
                plan != null -> LiveSetUiModel(
                    position = position,
                    weight = plan.weight,
                    reps = plan.reps,
                    type = plan.type,
                    isDone = false,
                )

                else -> LiveSetUiModel(
                    position = position,
                    weight = null,
                    reps = 0,
                    type = SetTypeUiModel.WORK,
                    isDone = false,
                )
            }
        }.toImmutableList()
    }

    fun State.withVisibleSets(): State {
        val refreshed = exercises.map { exercise ->
            exercise.copy(visibleSets = resolveVisibleSets(exercise, setDrafts))
        }.toImmutableList()
        return copy(exercises = refreshed)
    }

    private val EMPTY: ImmutableList<LiveSetUiModel> =
        kotlinx.collections.immutable.persistentListOf()
}
