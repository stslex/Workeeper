// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.model

import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Stable
data class LiveExerciseUiModel(
    val performedExerciseUuid: String,
    val exerciseUuid: String,
    val exerciseName: String,
    val exerciseType: ExerciseTypeUiModel,
    val position: Int,
    val status: ExerciseStatusUiModel,
    val statusLabel: String,
    val planSets: ImmutableList<PlanSetUiModel>,
    val performedSets: ImmutableList<LiveSetUiModel>,
    /** Resolved visible rows, priority `performed > draft > plan > fallback`; UI never merges. */
    val visibleSets: ImmutableList<LiveSetUiModel> = persistentListOf(),
    /** In the saved training plan (§6.2); not `is_adhoc` — see `LiveExerciseDomain`. */
    val isPlanAttached: Boolean = true,
    /** Template description; gates the `.mini.info` button and fills `sh-desc` (§1.5/§1.9). */
    val description: String? = null,
)
