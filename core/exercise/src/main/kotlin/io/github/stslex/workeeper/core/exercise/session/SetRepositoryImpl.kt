package io.github.stslex.workeeper.core.exercise.session

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.database.session.SetDao
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.toData
import io.github.stslex.workeeper.core.exercise.exercise.model.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Singleton
internal class SetRepositoryImpl @Inject constructor(
    private val dao: SetDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : SetRepository {

    override suspend fun getByPerformedExercise(
        performedExerciseUuid: String,
    ): List<SetsDataModel> = withContext(ioDispatcher) {
        dao.getByPerformedExercise(Uuid.parse(performedExerciseUuid)).map { it.toData() }
    }

    @Suppress("DEPRECATION")
    @Deprecated(
        message = "v5 plan-first model: prev-set hint comes from training_exercise.plan_sets" +
            " or exercise.last_adhoc_sets, not from history.",
    )
    override suspend fun getLastFinishedSet(
        exerciseUuid: String,
    ): SetsDataModel? = withContext(ioDispatcher) {
        dao.getLastFinishedSet(Uuid.parse(exerciseUuid))?.toData()
    }

    override suspend fun insert(
        performedExerciseUuid: String,
        position: Int,
        set: SetsDataModel,
    ) {
        withContext(ioDispatcher) {
            dao.insert(
                set.toEntity(
                    performedExerciseUuid = Uuid.parse(performedExerciseUuid),
                    position = position,
                ),
            )
        }
    }

    override suspend fun update(
        performedExerciseUuid: String,
        position: Int,
        set: SetsDataModel,
    ) {
        withContext(ioDispatcher) {
            dao.update(
                set.toEntity(
                    performedExerciseUuid = Uuid.parse(performedExerciseUuid),
                    position = position,
                ),
            )
        }
    }

    override suspend fun delete(uuid: String) {
        withContext(ioDispatcher) {
            dao.delete(Uuid.parse(uuid))
        }
    }
}
