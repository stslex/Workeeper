// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session.model

import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel

/**
 * One-shot hierarchical view of a finished session, used by the Past session detail screen.
 * Carries enough info to render the header (training name, timestamps), the per-exercise
 * breakdown (with `exerciseType` so the UI knows whether to show the weight column), and
 * the underlying sets ready for inline edit.
 */
data class SessionDetailDataModel(
    val sessionUuid: String,
    val trainingUuid: String,
    val trainingName: String,
    val isAdhoc: Boolean,
    val startedAt: Long,
    val finishedAt: Long,
    val exercises: List<PerformedExerciseDetailDataModel>,
)

data class PerformedExerciseDetailDataModel(
    val performedExerciseUuid: String,
    val exerciseUuid: String,
    val exerciseName: String,
    val exerciseType: ExerciseTypeDataModel,
    val position: Int,
    val skipped: Boolean,
    val sets: List<SetsDataModel>,
)
