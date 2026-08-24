// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.personal_record

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.database.session.PersonalRecordRow
import io.github.stslex.workeeper.core.data.database.session.SessionDao
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType.Companion.toData
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class PersonalRecordRepositoryImpl @Inject internal constructor(
    private val sessionDao: SessionDao,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : PersonalRecordRepository {

    override suspend fun getPersonalRecord(
        exerciseUuid: String,
    ): PersonalRecordDataModel? = withContext(ioDispatcher) {
        sessionDao.getPersonalRecord(Uuid.parse(exerciseUuid))?.toData()
    }

    override fun observePersonalRecord(
        exerciseUuid: String,
    ): Flow<PersonalRecordDataModel?> = sessionDao
        .observePersonalRecord(Uuid.parse(exerciseUuid))
        .map { it?.toData() }
        .flowOn(ioDispatcher)

    override fun observePersonalRecordsBatch(
        exerciseUuids: Set<String>,
    ): Flow<Map<String, PersonalRecordDataModel>> {
        if (exerciseUuids.isEmpty()) return flowOf(emptyMap())
        return sessionDao
            .observePersonalRecordsBatch(exerciseUuids.map(Uuid::parse))
            .map { rows -> rows.toBestPerExercise() }
            .flowOn(ioDispatcher)
    }

    override fun observePrSetUuids(
        exerciseUuids: Set<String>,
    ): Flow<Set<String>> {
        if (exerciseUuids.isEmpty()) return flowOf(emptySet())
        return sessionDao
            .observePersonalRecordsBatch(exerciseUuids.map(Uuid::parse))
            .map { rows ->
                rows.toBestPerExercise()
                    .values
                    .map { it.setUuid }
                    .toSet()
            }
            .flowOn(ioDispatcher)
    }

    /**
     * The DAO already returns only eligible candidates, best-first within each exercise group,
     * so picking the holder is `.first()` per group and nothing else. Do not reintroduce a
     * filter here: eligibility lives in SQL, and a second copy of half the rule in this module
     * is exactly how the batch path came to disagree with the single-exercise path.
     */
    private fun List<PersonalRecordRow>.toBestPerExercise(): Map<String, PersonalRecordDataModel> =
        groupBy { it.exerciseUuid.toString() }
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
