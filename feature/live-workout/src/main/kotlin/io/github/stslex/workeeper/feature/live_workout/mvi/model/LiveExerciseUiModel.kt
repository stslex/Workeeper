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
    /**
     * Resolved visible-row list with priority `performed > draft > plan > fallback`.
     * Computed by `LiveSetRowsResolver` and refreshed on every state mutation that
     * touches `performedSets`, `planSets`, or `State.setDrafts`. UI components render
     * this list directly and must never merge sources themselves.
     */
    val visibleSets: ImmutableList<LiveSetUiModel> = persistentListOf(),
    /**
     * Whether this exercise is in the saved training plan (§6.2). `false` marks a **one-off**:
     * real work that counts toward progress, deliberately absent from the template.
     *
     * Not `is_adhoc` — see `LiveExerciseDomain.isPlanAttached` for the two axes and the
     * breaking case that separates them. Defaults to `true` so a fixture that does not care
     * about the axis reads as an ordinary plan exercise.
     */
    val isPlanAttached: Boolean = true,
    /** Template description; gates the `.mini.info` button and fills `sh-desc` (§1.5/§1.9). */
    val description: String? = null,
)
