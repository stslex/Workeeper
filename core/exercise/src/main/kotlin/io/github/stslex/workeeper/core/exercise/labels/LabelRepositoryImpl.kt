package io.github.stslex.workeeper.core.exercise.labels

import io.github.stslex.workeeper.core.core.coroutine.dispatcher.IODispatcher
import io.github.stslex.workeeper.core.database.trainingLabels.TrainingLabelDao
import io.github.stslex.workeeper.core.exercise.labels.model.LabelDataModel
import io.github.stslex.workeeper.core.exercise.labels.model.toData
import io.github.stslex.workeeper.core.exercise.labels.model.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LabelRepositoryImpl @Inject constructor(
    private val dao: TrainingLabelDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : LabelRepository {

    override suspend fun getAll(): List<LabelDataModel> = withContext(ioDispatcher) {
        dao.getAll().map { it.toData() }
    }

    override suspend fun remove(label: String) {
        withContext(ioDispatcher) {
            dao.delete(label)
        }
    }

    override suspend fun add(label: LabelDataModel) {
        withContext(ioDispatcher) {
            dao.add(label.toEntity())
        }
    }
}
