// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetRowsResolver.withVisibleSets
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import kotlinx.collections.immutable.toImmutableMap

/**
 * Single canonical entry point for draft seed lookup and draft update.
 *
 * `draft update = current visible row seed + changed field` is the invariant; this
 * helper enforces it. Every handler that creates or mutates a draft routes through
 * `updateSetDraft`, never assembles a `LiveSetUiModel` inline. Type chip clicks
 * preserve weight/reps; weight/reps edits preserve type. See
 * [documentation/feature-specs/live-workout.md → Set draft and visible row architecture]
 * for the rule.
 */
internal fun State.lookupSetDraftSeed(
    performedExerciseUuid: String,
    position: Int,
): LiveSetUiModel {
    val exercise = exercises.firstOrNull { it.performedExerciseUuid == performedExerciseUuid }
    val performed = exercise?.performedSets?.firstOrNull { it.position == position }
    if (performed != null) return performed
    val plan = exercise?.planSets?.getOrNull(position)
    return LiveSetUiModel(
        position = position,
        weight = plan?.weight,
        reps = plan?.reps ?: 0,
        type = plan?.type ?: SetTypeUiModel.WORK,
        isDone = false,
    )
}

internal fun State.updateSetDraft(
    performedExerciseUuid: String,
    position: Int,
    transform: (LiveSetUiModel) -> LiveSetUiModel,
): State {
    val key = State.DraftKey(performedExerciseUuid, position)
    val seed = setDrafts[key] ?: lookupSetDraftSeed(performedExerciseUuid, position)
    val updated = transform(seed)
    return copy(
        setDrafts = (setDrafts + (key to updated)).toImmutableMap(),
    ).withVisibleSets()
}
