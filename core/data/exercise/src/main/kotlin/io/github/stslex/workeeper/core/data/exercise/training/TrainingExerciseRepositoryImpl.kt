// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.training

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Singleton
internal class TrainingExerciseRepositoryImpl @Inject constructor(
    private val dao: TrainingExerciseDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : TrainingExerciseRepository {

    override suspend fun getPlan(
        trainingUuid: String,
        exerciseUuid: String,
    ): List<PlanSetDataModel>? = withContext(ioDispatcher) {
        val raw = dao.getPlanSets(Uuid.parse(trainingUuid), Uuid.parse(exerciseUuid))
        PlanSetsConverter.fromJson(raw)
    }

    override suspend fun getPlans(
        trainingUuid: String,
        exerciseUuids: List<String>,
    ): Map<String, List<PlanSetDataModel>?> = withContext(ioDispatcher) {
        if (exerciseUuids.isEmpty()) return@withContext emptyMap()
        val rows = dao.getPlanSetsBatch(
            trainingUuid = Uuid.parse(trainingUuid),
            exerciseUuids = exerciseUuids.map { Uuid.parse(it) },
        )
        rows.associate { row ->
            row.exerciseUuid.toString() to PlanSetsConverter.fromJson(row.planSets)
        }
    }

    override suspend fun setPlan(
        trainingUuid: String,
        exerciseUuid: String,
        planSets: List<PlanSetDataModel>?,
    ) {
        withContext(ioDispatcher) {
            dao.updatePlanSets(
                trainingUuid = Uuid.parse(trainingUuid),
                exerciseUuid = Uuid.parse(exerciseUuid),
                planSets = PlanSetsConverter.toJson(planSets),
            )
        }
    }

    override suspend fun getRowsForTraining(
        trainingUuid: String,
    ): List<TrainingExerciseRepository.TrainingExerciseRow> = withContext(ioDispatcher) {
        dao.getByTraining(Uuid.parse(trainingUuid)).map { row ->
            TrainingExerciseRepository.TrainingExerciseRow(
                exerciseUuid = row.exerciseUuid.toString(),
                position = row.position,
                planSets = PlanSetsConverter.fromJson(row.planSets),
            )
        }
    }
}
