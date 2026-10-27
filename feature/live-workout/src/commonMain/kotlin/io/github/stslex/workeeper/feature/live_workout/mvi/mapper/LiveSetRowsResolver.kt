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
 * Resolves each card's visible-row list, priority `performed > draft > plan > fallback`.
 * GUARD: every transition touching those sources must funnel through `withVisibleSets()`.
 */
internal object LiveSetRowsResolver {

    fun resolveVisibleSets(
        exercise: LiveExerciseUiModel,
        drafts: ImmutableMap<State.DraftKey, LiveSetUiModel>,
        rowCountOverride: Int? = null,
    ): ImmutableList<LiveSetUiModel> {
        val performedTotal = exercise.performedSets
            .maxOfOrNull { it.position + 1 }
            ?: 0

        val draftTotal = drafts.keys
            .asSequence()
            .filter { it.performedExerciseUuid == exercise.performedExerciseUuid }
            .maxOfOrNull { it.position + 1 }
            ?: 0

        // The setbar's truncation (§6.4) pins the count exactly, floored at the highest
        // performed position so a logged row can never be hidden.
        val total = rowCountOverride?.coerceAtLeast(performedTotal)
            ?: maxOf(
                exercise.planSets.size,
                performedTotal,
                draftTotal,
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
            exercise.copy(
                visibleSets = resolveVisibleSets(
                    exercise = exercise,
                    drafts = setDrafts,
                    rowCountOverride = rowCountOverrides[exercise.performedExerciseUuid],
                ),
            )
        }.toImmutableList()
        return copy(exercises = refreshed)
    }

    private val EMPTY: ImmutableList<LiveSetUiModel> =
        kotlinx.collections.immutable.persistentListOf()
}
