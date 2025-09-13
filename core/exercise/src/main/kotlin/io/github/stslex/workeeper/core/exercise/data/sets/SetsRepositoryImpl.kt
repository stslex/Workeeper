package io.github.stslex.workeeper.core.exercise.data.sets

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.database.sets.SetsDao
import io.github.stslex.workeeper.core.exercise.data.model.SetsChangeDataModel
import io.github.stslex.workeeper.core.exercise.data.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.data.model.toData
import io.github.stslex.workeeper.core.exercise.data.model.toEntity
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single
class SetsRepositoryImpl(
    private val dao: SetsDao,
    private val appDispatcher: AppDispatcher
) : SetsRepository {

    override suspend fun getSets(
        exerciseUuid: String
    ): List<SetsDataModel> = withContext(appDispatcher.io) {
        dao.getSets(Uuid.parse(exerciseUuid))
            .map { it.toData() }
    }

    override suspend fun addSets(sets: List<SetsChangeDataModel>) {
        withContext(appDispatcher.io) {
            val sets = sets.map { it.toEntity() }
            dao.addAll(sets)
        }
    }

}