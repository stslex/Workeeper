package io.github.stslex.workeeper.feature.charts.domain.calculator

import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.feature.charts.domain.model.SingleChartDomainModel

internal interface ChartDomainCalculator {

    fun init(
        startTimestamp: Long,
        endTimestamp: Long,
    )

    suspend fun mapTrainings(
        startTimestamp: Long,
        endTimestamp: Long,
        trainings: List<TrainingDataModel>,
        getExercises: suspend (uuids: List<String>) -> List<ExerciseDataModel>,
    ): List<SingleChartDomainModel>

    suspend fun mapExercises(
        startTimestamp: Long,
        endTimestamp: Long,
        exercises: List<ExerciseDataModel>,
    ): List<SingleChartDomainModel>
}
