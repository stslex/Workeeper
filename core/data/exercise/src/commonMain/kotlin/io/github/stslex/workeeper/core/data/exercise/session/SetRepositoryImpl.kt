// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.common.DbTransitionRunner
import io.github.stslex.workeeper.core.data.database.session.SetDao
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toData
import io.github.stslex.workeeper.core.data.exercise.exercise.model.toEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

@Suppress("TooManyFunctions")
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class SetRepositoryImpl @Inject internal constructor(
    private val dao: SetDao,
    private val transition: DbTransitionRunner,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : SetRepository {

    override suspend fun getByPerformedExercise(
        performedExerciseUuid: String,
    ): List<SetsDataModel> = withContext(ioDispatcher) {
        dao.getByPerformedExercise(Uuid.parse(performedExerciseUuid)).map { it.toData() }
    }

    override suspend fun getByPerformedExercises(
        performedExerciseUuids: List<String>,
    ): Map<String, List<SetsDataModel>> = withContext(ioDispatcher) {
        if (performedExerciseUuids.isEmpty()) return@withContext emptyMap()
        val parsed = performedExerciseUuids.map { Uuid.parse(it) }
        dao.getByPerformedExercises(parsed)
            .groupBy { it.performedExerciseUuid.toString() }
            .mapValues { (_, sets) -> sets.map { it.toData() } }
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
        set: SetsDataModel,
    ) = transition.mutate {
        val entity = set.toEntity(
            performedExerciseUuid = Uuid.parse(performedExerciseUuid),
        )
        dao.upsertByTarget(
            uuid = entity.uuid,
            performedExerciseUuid = entity.performedExerciseUuid,
            position = entity.position,
            reps = entity.reps,
            weight = entity.weight,
            type = entity.type,
        )
    }

    override suspend fun update(
        performedExerciseUuid: String,
        set: SetsDataModel,
    ) = transition.mutate {
        dao.update(
            set.toEntity(
                performedExerciseUuid = Uuid.parse(performedExerciseUuid),
            ),
        )
    }

    override suspend fun reorderSets(
        performedExerciseUuid: String,
        orderedSetUuids: List<String>,
    ) {
        if (orderedSetUuids.isEmpty()) return
        val performedUuid = Uuid.parse(performedExerciseUuid)
        val orderedUuids = orderedSetUuids.map(Uuid::parse)
        require(orderedUuids.distinct().size == orderedUuids.size) {
            "Set order must not contain duplicate UUIDs"
        }
        transition.mutate {
            val currentUuids = dao.getByPerformedExercise(performedUuid).map { it.uuid }
            require(
                currentUuids.size == orderedUuids.size &&
                    currentUuids.toSet() == orderedUuids.toSet(),
            ) {
                "Set order must contain every set belonging to the performed exercise exactly once"
            }
            // The unique target index is immediate in SQLite. First move every selected row
            // outside the canonical non-negative range, then assign the requested positions.
            // Both passes share the database transition, so readers observe neither phase.
            orderedUuids.forEachIndexed { index, setUuid ->
                dao.updatePosition(setUuid, Int.MIN_VALUE + index)
            }
            orderedUuids.forEachIndexed { index, setUuid ->
                dao.updatePosition(setUuid, index)
            }
        }
    }

    override suspend fun delete(uuid: String) = transition.mutate {
        dao.delete(Uuid.parse(uuid))
    }

    override suspend fun upsert(
        performedExerciseUuid: String,
        position: Int,
        weight: Double?,
        reps: Int,
        type: SetsDataType,
    ) = transition.mutate {
        dao.upsertByTarget(
            uuid = Uuid.random(),
            performedExerciseUuid = Uuid.parse(performedExerciseUuid),
            position = position,
            reps = reps,
            weight = weight,
            type = type.toEntity(),
        )
    }

    override suspend fun deleteByPerformedAndPosition(
        performedExerciseUuid: String,
        position: Int,
    ) = transition.mutate {
        dao.deleteByPerformedAndPosition(Uuid.parse(performedExerciseUuid), position)
    }

    override suspend fun deleteAllForPerformedExercise(performedExerciseUuid: String) = transition.mutate {
        dao.deleteAllForPerformedExercise(Uuid.parse(performedExerciseUuid))
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
