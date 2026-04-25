package io.github.stslex.workeeper.core.exercise.session

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.database.session.PerformedExerciseDao
import io.github.stslex.workeeper.core.exercise.session.model.PerformedExerciseDataModel
import io.github.stslex.workeeper.core.exercise.session.model.toData
import io.github.stslex.workeeper.core.exercise.session.model.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Singleton
internal class PerformedExerciseRepositoryImpl @Inject constructor(
    private val dao: PerformedExerciseDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : PerformedExerciseRepository {

    override suspend fun getBySession(
        sessionUuid: String,
    ): List<PerformedExerciseDataModel> = withContext(ioDispatcher) {
        dao.getBySession(Uuid.parse(sessionUuid)).map { it.toData() }
    }

    override suspend fun insert(rows: List<PerformedExerciseDataModel>) {
        withContext(ioDispatcher) {
            dao.insert(rows.map { it.toEntity() })
        }
    }

    override suspend fun setSkipped(uuid: String, skipped: Boolean) {
        withContext(ioDispatcher) {
            dao.setSkipped(Uuid.parse(uuid), skipped)
        }
    }
}
