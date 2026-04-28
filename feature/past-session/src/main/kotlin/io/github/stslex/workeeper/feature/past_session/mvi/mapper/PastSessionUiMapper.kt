// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.core.time.formatElapsedDuration
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.exercise.session.model.PerformedExerciseDetailDataModel
import io.github.stslex.workeeper.core.exercise.session.model.SessionDetailDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.past_session.R
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastExerciseUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSessionUiModel
import io.github.stslex.workeeper.feature.past_session.mvi.model.PastSetUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal fun SessionDetailDataModel.toUi(
    resourceWrapper: ResourceWrapper,
): PastSessionUiModel {
    val totalSets = exercises.sumOf { it.sets.size }
    val activeExercises = exercises.count { !it.skipped }
    val volume = computeVolume(exercises)
    val finishedAtLabel = resourceWrapper.formatMediumDate(finishedAt)
    val durationLabel = formatElapsedDuration(finishedAt - startedAt)
    val totalsLabel = buildTotalsLabel(resourceWrapper, activeExercises, totalSets)
    val volumeLabel = volume?.let {
        resourceWrapper.getString(R.string.feature_past_session_volume_label, formatWeight(it))
    }
    val trainingName = if (isAdhoc) {
        resourceWrapper.getString(R.string.feature_past_session_adhoc_label)
    } else {
        trainingName
    }
    return PastSessionUiModel(
        trainingName = trainingName,
        isAdhoc = isAdhoc,
        finishedAtAbsoluteLabel = finishedAtLabel,
        durationLabel = durationLabel,
        totalsLabel = totalsLabel,
        volumeLabel = volumeLabel,
        exercises = exercises.toUiList(),
    )
}

private fun computeVolume(exercises: List<PerformedExerciseDetailDataModel>): Double? {
    val total = exercises
        .asSequence()
        .filter { it.exerciseType == ExerciseTypeDataModel.WEIGHTED }
        .flatMap { it.sets.asSequence() }
        .filter { it.type == SetsDataType.WORK || it.type == SetsDataType.FAIL }
        .mapNotNull { set -> set.weight?.let { weight -> weight * set.reps } }
        .sum()
    return total.takeIf { it > 0.0 }
}

private fun buildTotalsLabel(
    resourceWrapper: ResourceWrapper,
    exerciseCount: Int,
    setCount: Int,
): String {
    val exercises = resourceWrapper.getQuantityString(
        R.plurals.feature_past_session_exercises_count,
        exerciseCount,
        exerciseCount,
    )
    val sets = resourceWrapper.getQuantityString(
        R.plurals.feature_past_session_sets_count,
        setCount,
        setCount,
    )
    return resourceWrapper.getString(
        R.string.feature_past_session_totals_format,
        exercises,
        sets,
    )
}

private fun List<PerformedExerciseDetailDataModel>.toUiList(): ImmutableList<PastExerciseUiModel> =
    sortedBy { it.position }
        .map { exercise ->
            PastExerciseUiModel(
                performedExerciseUuid = exercise.performedExerciseUuid,
                exerciseName = exercise.exerciseName,
                position = exercise.position,
                skipped = exercise.skipped,
                isWeighted = exercise.exerciseType == ExerciseTypeDataModel.WEIGHTED,
                sets = exercise.sets.toUiSets(exercise.performedExerciseUuid),
            )
        }
        .toImmutableList()

private fun List<SetsDataModel>.toUiSets(
    performedExerciseUuid: String,
): ImmutableList<PastSetUiModel> = this
    .mapIndexed { index, set ->
        PastSetUiModel(
            setUuid = set.uuid,
            performedExerciseUuid = performedExerciseUuid,
            position = index,
            type = set.type.toUi(),
            weightInput = set.weight?.let(::formatWeight).orEmpty(),
            repsInput = set.reps.takeIf { it > 0 }?.toString().orEmpty(),
            weightError = false,
            repsError = false,
        )
    }
    .toImmutableList()

private fun SetsDataType.toUi(): SetTypeUiModel = when (this) {
    SetsDataType.WARM -> SetTypeUiModel.WARMUP
    SetsDataType.WORK -> SetTypeUiModel.WORK
    SetsDataType.FAIL -> SetTypeUiModel.FAILURE
    SetsDataType.DROP -> SetTypeUiModel.DROP
}

internal fun SetTypeUiModel.toSetsDataType(): SetsDataType = when (this) {
    SetTypeUiModel.WARMUP -> SetsDataType.WARM
    SetTypeUiModel.WORK -> SetsDataType.WORK
    SetTypeUiModel.FAILURE -> SetsDataType.FAIL
    SetTypeUiModel.DROP -> SetsDataType.DROP
}

private const val WEIGHT_DECIMAL_FACTOR = 10.0

private fun formatWeight(weight: Double): String {
    val rounded = (weight * WEIGHT_DECIMAL_FACTOR).toLong() / WEIGHT_DECIMAL_FACTOR
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
