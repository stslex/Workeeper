// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.personal_record

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.database.session.PersonalRecordRow
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType.Companion.toData
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
