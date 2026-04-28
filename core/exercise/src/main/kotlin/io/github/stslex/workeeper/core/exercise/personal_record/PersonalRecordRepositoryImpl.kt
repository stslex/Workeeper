// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.exercise.personal_record

import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.database.session.PersonalRecordRow
import io.github.stslex.workeeper.core.database.session.SessionDao
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataType.Companion.toData
import kotlinx.coroutines.CoroutineDispatcher
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
