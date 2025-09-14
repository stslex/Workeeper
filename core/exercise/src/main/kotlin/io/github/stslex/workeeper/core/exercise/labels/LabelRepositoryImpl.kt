package io.github.stslex.workeeper.core.exercise.labels

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.AppDispatcher
import io.github.stslex.workeeper.core.database.trainingLabels.TrainingLabelDao
import io.github.stslex.workeeper.core.exercise.labels.model.LabelDataModel
import io.github.stslex.workeeper.core.exercise.labels.model.toData
import io.github.stslex.workeeper.core.exercise.labels.model.toEntity
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single

@Single
class LabelRepositoryImpl(
    private val dao: TrainingLabelDao,
    private val appDispatcher: AppDispatcher
) : LabelRepository {

    override suspend fun getAll(): List<LabelDataModel> = withContext(appDispatcher.io) {
        dao.getAll().map { it.toData() }
    }

    override suspend fun remove(label: String) {
        withContext(appDispatcher.io) {
            dao.delete(label)
        }
    }

    override suspend fun add(label: LabelDataModel) {
        withContext(appDispatcher.io) {
            dao.add(label.toEntity())
        }
    }
}