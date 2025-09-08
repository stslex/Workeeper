package io.github.stslex.workeeper.core.exercise.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.exercise.data.model.ChangeExerciseDataModel
import io.github.stslex.workeeper.core.exercise.data.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.data.model.toData
import io.github.stslex.workeeper.core.exercise.data.model.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single
internal class ExerciseRepositoryImpl(
    private val dao: ExerciseDao,
    private val appDispatcher: AppDispatcher
) : ExerciseRepository {

    override val exercises: Flow<PagingData<ExerciseDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::getAll
    ).flow
        .map { pagingData ->
            pagingData.map { it.toData() }
        }
        .flowOn(appDispatcher.io)

    override fun getExercises(query: String): Flow<PagingData<ExerciseDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = { dao.getAll(query) }
    ).flow
        .map { pagingData ->
            pagingData.map { it.toData() }
        }
        .flowOn(appDispatcher.io)

    override suspend fun saveItem(item: ChangeExerciseDataModel) {
        withContext(appDispatcher.io) {
            dao.create(item.toEntity())
        }
    }

    override suspend fun deleteItem(uuid: String) {
        withContext(appDispatcher.io) {
            dao.delete(Uuid.parse(uuid))
        }
    }

    override suspend fun searchItems(query: String): List<ExerciseDataModel> = withContext(appDispatcher.io) {
        dao.searchUniqueExclude(query).map { it.toData() }
    }

    override suspend fun deleteAllItems(uuids: List<Uuid>) {
        withContext(appDispatcher.io) {
            dao.delete(uuids)
        }
    }

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false
        )
    }
}