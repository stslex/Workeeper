// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toData
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
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

    override suspend fun upsert(
        performedExerciseUuid: String,
        position: Int,
        weight: Double?,
        reps: Int,
        type: SetsDataType,
    ) {
        withContext(ioDispatcher) {
            val parent = Uuid.parse(performedExerciseUuid)
            val existing = dao.getByPerformedAndPosition(parent, position)
            val entity = SetEntity(
                uuid = existing?.uuid ?: Uuid.random(),
                performedExerciseUuid = parent,
                position = position,
                reps = reps,
                weight = weight,
                type = type.toEntity(),
            )
            dao.insert(entity)
        }
    }

    override suspend fun deleteByPerformedAndPosition(
        performedExerciseUuid: String,
        position: Int,
    ) {
        withContext(ioDispatcher) {
            dao.deleteByPerformedAndPosition(Uuid.parse(performedExerciseUuid), position)
        }
    }

    override suspend fun deleteAllForPerformedExercise(performedExerciseUuid: String) {
        withContext(ioDispatcher) {
            dao.deleteAllForPerformedExercise(Uuid.parse(performedExerciseUuid))
        }
    }

    override suspend fun hasAnyForPerformed(
        performedExerciseUuid: String,
    ): Boolean = withContext(ioDispatcher) {
        dao.hasAnyForPerformed(Uuid.parse(performedExerciseUuid))
    }

    override suspend fun countByPerformedExercise(
        performedExerciseUuid: String,
    ): Int = withContext(ioDispatcher) {
        dao.countByPerformedExercise(Uuid.parse(performedExerciseUuid))
    }
}
