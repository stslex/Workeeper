// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.training

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseDao
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class TrainingExerciseRepositoryImpl @Inject internal constructor(
    private val dao: TrainingExerciseDao,
    private val transition: DbTransitionRunner,
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
    ) = transition.mutate {
        dao.updatePlanSets(
            trainingUuid = Uuid.parse(trainingUuid),
            exerciseUuid = Uuid.parse(exerciseUuid),
            planSets = PlanSetsConverter.toJson(planSets),
        )
    }

    override suspend fun attachExercise(
        trainingUuid: String,
        exerciseUuid: String,
        planSets: List<PlanSetDataModel>?,
    ) = transition.mutate {
        val trainingId = Uuid.parse(trainingUuid)
        val nextPosition = (dao.getMaxPosition(trainingId) ?: -1) + 1
        dao.insert(
            TrainingExerciseEntity(
                trainingUuid = trainingId,
                exerciseUuid = Uuid.parse(exerciseUuid),
                position = nextPosition,
                planSets = PlanSetsConverter.toJson(planSets),
            ),
        )
    }

    override suspend fun detachExercise(
        trainingUuid: String,
        exerciseUuid: String,
    ) = transition.mutate {
        dao.deleteByTrainingAndExercise(
            trainingUuid = Uuid.parse(trainingUuid),
            exerciseUuid = Uuid.parse(exerciseUuid),
        )
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
