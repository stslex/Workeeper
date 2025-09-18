package io.github.stslex.workeeper.core.exercise.training

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.database.training.TrainingDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single
class TrainingRepositoryImpl(
    private val dao: TrainingDao,
    private val appDispatcher: AppDispatcher
) : TrainingRepository {

    override fun getAll(): List<TrainingDataModel> {
        return dao.getAll().map { it.toData() }
    }

    override fun getTrainings(query: String): Flow<PagingData<TrainingDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = { dao.getAll(query) }
    ).flow
        .map { pagingData ->
            pagingData.map { it.toData() }
        }
        .flowOn(appDispatcher.io)

    override suspend fun addTraining(training: TrainingDataModel) {
        withContext(appDispatcher.io) {
            dao.add(training.toEntity())
        }
    }

    override suspend fun updateTraining(training: TrainingChangeDataModel) {
        withContext(appDispatcher.io) {
            dao.add(training.toEntity())
        }
    }

    override suspend fun removeTraining(uuid: String) {
        withContext(appDispatcher.io) {
            dao.delete(Uuid.parse(uuid))
        }
    }

    override suspend fun getTraining(
        uuid: String
    ): TrainingDataModel? = withContext(appDispatcher.io) {
        dao.get(Uuid.parse(uuid))?.toData()
    }

    override suspend fun removeAll(uuids: List<String>) = withContext(appDispatcher.io) {
        dao.deleteAll(uuids.map(Uuid::parse))
    }

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false
        )
    }
}