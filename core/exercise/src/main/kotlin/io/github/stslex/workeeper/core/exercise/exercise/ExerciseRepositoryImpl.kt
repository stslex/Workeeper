package io.github.stslex.workeeper.core.exercise.exercise

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.IODispatcher
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.toData
import io.github.stslex.workeeper.core.exercise.exercise.model.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Singleton
internal class ExerciseRepositoryImpl @Inject constructor(
    private val dao: ExerciseDao,
    @IODispatcher private val bgDispatcher: CoroutineDispatcher,
) : ExerciseRepository {

    override val exercises: Flow<PagingData<ExerciseDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::getAll,
    ).flow
        .map { pagingData ->
            pagingData.map { it.toData() }
        }
        .flowOn(bgDispatcher)

    override suspend fun getExercisesByUuid(
        uuids: List<String>,
    ): List<ExerciseDataModel> = withContext(bgDispatcher) {
        dao
            .getByUuids(uuids.map(Uuid::parse))
            .orEmpty()
            .map { it.toData() }
    }

    override fun getExercises(query: String): Flow<PagingData<ExerciseDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = { dao.getAll(query) },
    ).flow
        .map { pagingData ->
            pagingData.map { it.toData() }
        }
        .flowOn(bgDispatcher)

    override suspend fun getExercise(
        uuid: String,
    ): ExerciseDataModel? = withContext(bgDispatcher) {
        dao.getExercise(Uuid.parse(uuid))?.toData()
    }

    override suspend fun getExercises(
        name: String,
        startDate: Long,
        endDate: Long,
    ): List<ExerciseDataModel> = withContext(bgDispatcher) {
        dao.getExercises(
            name = name,
            startDate = startDate,
            endDate = endDate,
        ).map { it.toData() }
    }

    override suspend fun saveItem(item: ExerciseChangeDataModel) = withContext(bgDispatcher) {
        dao.create(item.toEntity())
    }

    override suspend fun deleteItem(uuid: String) {
        withContext(bgDispatcher) {
            dao.delete(Uuid.Companion.parse(uuid))
        }
    }

    override suspend fun searchItems(query: String): List<ExerciseDataModel> =
        withContext(bgDispatcher) {
            dao.searchUniqueExclude(query).map { it.toData() }
        }

    override suspend fun deleteAllItems(uuids: List<Uuid>) {
        withContext(bgDispatcher) {
            dao.delete(uuids)
        }
    }

    override suspend fun deleteByTrainingUuid(trainingUuid: String) {
        withContext(bgDispatcher) {
            dao.deleteAllByTraining(Uuid.parse(trainingUuid))
        }
    }

    override suspend fun deleteByTrainingsUuids(trainingsUuids: List<String>) {
        withContext(bgDispatcher) {
            dao.deleteAllByTrainings(trainingsUuids.map(Uuid::parse))
        }
    }

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false,
        )
    }
}
