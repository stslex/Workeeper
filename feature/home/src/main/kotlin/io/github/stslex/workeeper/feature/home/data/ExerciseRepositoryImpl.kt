package io.github.stslex.workeeper.feature.home.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.database.exercise.ExerciseDao
import io.github.stslex.workeeper.feature.home.data.model.ExerciseDataModel
import io.github.stslex.workeeper.feature.home.data.model.toData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
class ExerciseRepositoryImpl(
    private val dao: ExerciseDao,
    appDispatcher: AppDispatcher
) : ExerciseRepository {

    override val exercises: Flow<PagingData<ExerciseDataModel>> = Pager(
        config = pagingConfig,
        pagingSourceFactory = dao::getAll
    ).flow
        .map { pagingData ->
            pagingData.map { it.toData() }
        }
        .flowOn(appDispatcher.io)

    companion object {

        private val pagingConfig = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false
        )
    }
}