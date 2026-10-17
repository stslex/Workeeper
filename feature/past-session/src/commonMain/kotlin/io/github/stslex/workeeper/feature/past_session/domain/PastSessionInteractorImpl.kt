// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.feature.past_session.di.PastSessionScope
import io.github.stslex.workeeper.feature.past_session.domain.mapper.PastSessionDomainMapper.toData
import io.github.stslex.workeeper.feature.past_session.domain.mapper.PastSessionDomainMapper.toDomain
import io.github.stslex.workeeper.feature.past_session.domain.model.DetailWithPrs
import io.github.stslex.workeeper.feature.past_session.domain.model.SetDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Inject
@SingleIn(PastSessionScope::class)
class PastSessionInteractorImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val setRepository: SetRepository,
    private val personalRecordRepository: PersonalRecordRepository,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : PastSessionInteractor {

    /**
     * Re-fetches detail on every PR re-emission so optimistic edits made through the input
     * handler aren't clobbered by a stale captured snapshot — the same `set_table` change
     * that triggers PR invalidation is the change that produced the latest detail. The
     * `uuidsByType` keyset is taken once from the initial load (exercises don't change for
     * a finished session) and reused across re-emissions.
     */
    override fun observeDetailWithPrs(
        sessionUuid: String,
    ): Flow<DetailWithPrs?> = flow {
        val initial = sessionRepository.getSessionDetail(sessionUuid)
        if (initial == null) {
            emit(null)
            return@flow
        }
        val exerciseUuids = initial.exercises.mapTo(mutableSetOf()) { it.exerciseUuid }
        emitAll(
            personalRecordRepository
                .observePrSetUuids(exerciseUuids)
                .map { prSetUuids ->
                    val fresh = sessionRepository.getSessionDetail(sessionUuid) ?: initial
                    DetailWithPrs(detail = fresh.toDomain(), prSetUuids = prSetUuids)
                },
        )
    }.flowOn(ioDispatcher)

    override suspend fun updateSet(
        performedExerciseUuid: String,
        set: SetDomain,
    ) {
        setRepository.update(performedExerciseUuid, set.toData())
    }

    override suspend fun reorderSets(
        performedExerciseUuid: String,
        orderedSetUuids: List<String>,
    ) {
        setRepository.reorderSets(performedExerciseUuid, orderedSetUuids)
    }

    override suspend fun deleteSession(sessionUuid: String) {
        sessionRepository.deleteSession(sessionUuid)
    }
}
