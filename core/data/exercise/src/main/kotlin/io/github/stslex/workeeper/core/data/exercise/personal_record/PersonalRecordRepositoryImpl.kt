// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.personal_record

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.session.PersonalRecordRow
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType.Companion.toData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

@Singleton
internal class PersonalRecordRepositoryImpl @Inject constructor(
    private val sessionDao: SessionDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : PersonalRecordRepository {

    override suspend fun getPersonalRecord(
        exerciseUuid: String,
        type: ExerciseTypeDataModel,
    ): PersonalRecordDataModel? = withContext(ioDispatcher) {
        sessionDao
            .getPersonalRecord(
                exerciseUuid = Uuid.parse(exerciseUuid),
                isWeightless = type == ExerciseTypeDataModel.WEIGHTLESS,
            )
            ?.toData()
    }

    override fun observePersonalRecord(
        exerciseUuid: String,
        type: ExerciseTypeDataModel,
    ): Flow<PersonalRecordDataModel?> = sessionDao
        .observePersonalRecord(
            exerciseUuid = Uuid.parse(exerciseUuid),
            isWeightless = type == ExerciseTypeDataModel.WEIGHTLESS,
        )
        .map { it?.toData() }
        .flowOn(ioDispatcher)

    override fun observePersonalRecords(
        uuidsByType: Map<String, ExerciseTypeDataModel>,
    ): Flow<Map<String, PersonalRecordDataModel?>> {
        if (uuidsByType.isEmpty()) return flowOf(emptyMap())
        val perExerciseFlows: List<Flow<Pair<String, PersonalRecordDataModel?>>> =
            uuidsByType.map { (uuid, type) ->
                observePersonalRecord(uuid, type).map { uuid to it }
            }
        return combine(perExerciseFlows) { pairs -> pairs.toMap() }
    }

    override fun observePersonalRecordsBatch(
        uuidsByType: Map<String, ExerciseTypeDataModel>,
    ): Flow<Map<String, PersonalRecordDataModel>> {
        if (uuidsByType.isEmpty()) return flowOf(emptyMap())
        return sessionDao
            .observePersonalRecordsBatch(uuidsByType.keys.map(Uuid::parse))
            .map { rows -> rows.toBestPerExercise(uuidsByType) }
            .flowOn(ioDispatcher)
    }

    override fun observePrSetUuids(
        uuidsByType: Map<String, ExerciseTypeDataModel>,
    ): Flow<Set<String>> {
        if (uuidsByType.isEmpty()) return flowOf(emptySet())
        return sessionDao
            .observePersonalRecordsBatch(uuidsByType.keys.map(Uuid::parse))
            .map { rows ->
                rows.toBestPerExercise(uuidsByType)
                    .values
                    .map { it.setUuid }
                    .toSet()
            }
            .flowOn(ioDispatcher)
    }

    /**
     * The DAO returns all candidate sets ordered with the heaviest-first within each exercise
     * group. The `IN (:uuids)` query cannot encode the per-exercise `(:isWeightless = 1 OR
     * weight IS NOT NULL)` predicate, so we filter weighted exercises' weight-null rows here
     * — a weighted exercise's PR cannot be a weightless set.
     */
    private fun List<PersonalRecordRow>.toBestPerExercise(
        uuidsByType: Map<String, ExerciseTypeDataModel>,
    ): Map<String, PersonalRecordDataModel> = asSequence()
        .filter { row ->
            val type = uuidsByType[row.exerciseUuid.toString()] ?: return@filter false
            type == ExerciseTypeDataModel.WEIGHTLESS || row.weight != null
        }
        .groupBy { it.exerciseUuid.toString() }
        .mapValues { (_, group) -> group.first().toData() }

    private fun PersonalRecordRow.toData(): PersonalRecordDataModel = PersonalRecordDataModel(
        sessionUuid = sessionUuid.toString(),
        performedExerciseUuid = performedExerciseUuid.toString(),
        setUuid = setUuid.toString(),
        weight = weight,
        reps = reps,
        type = type.toData(),
        finishedAt = finishedAt,
    )
}
