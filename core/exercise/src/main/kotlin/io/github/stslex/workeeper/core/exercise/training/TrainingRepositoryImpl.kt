package io.github.stslex.workeeper.core.exercise.training

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.IODispatcher
import io.github.stslex.workeeper.core.database.training.TrainingDao
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single
class TrainingRepositoryImpl(
    private val dao: TrainingDao,
    @param:IODispatcher
    private val ioDispatcher: CoroutineDispatcher
) : TrainingRepository {

    override fun getTrainings(query: String): Flow<PagingData<TrainingDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = { dao.getAll(query) }
    ).flow
        .map { pagingData ->
            pagingData.map { it.toData() }
        }
        .flowOn(ioDispatcher)

    override suspend fun addTraining(training: TrainingDataModel) {
        withContext(ioDispatcher) {
            dao.add(training.toEntity())
        }
    }

    override suspend fun updateTraining(training: TrainingChangeDataModel) {
        withContext(ioDispatcher) {
            dao.add(training.toEntity())
        }
    }

    override suspend fun removeTraining(uuid: String) {
        withContext(ioDispatcher) {
            dao.delete(Uuid.parse(uuid))
        }
    }

    override suspend fun getTraining(
        uuid: String
    ): TrainingDataModel? = withContext(ioDispatcher) {
        dao.get(Uuid.parse(uuid))?.toData()
    }

    override fun subscribeForTraining(
        uuid: String
    ): Flow<TrainingDataModel> = dao
        .subscribeForTraining(Uuid.parse(uuid))
        .filterNotNull()
        .map { it.toData() }
        .flowOn(ioDispatcher)

    override suspend fun removeAll(uuids: List<String>) = withContext(ioDispatcher) {
        dao.deleteAll(uuids.map(Uuid::parse))
    }

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false
        )
    }
}