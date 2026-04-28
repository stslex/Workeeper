// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.SetRepository
import io.github.stslex.workeeper.feature.past_session.domain.PastSessionInteractor.DetailWithPrs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ViewModelScoped
internal class PastSessionInteractorImpl @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val setRepository: SetRepository,
    private val personalRecordRepository: PersonalRecordRepository,
    @IODispatcher private val ioDispatcher: CoroutineDispatcher,
) : PastSessionInteractor {

    /**
     * Fetches detail once at subscription and combines it with the live PR map. Detail edits
     * are reflected via optimistic state mutation in the handler; the PR flow re-emits when
     * the underlying `set_table` rows change so badges follow the current PR holder.
     */
    override fun observeDetailWithPrs(
        sessionUuid: String,
    ): Flow<DetailWithPrs?> = flow {
        val detail = sessionRepository.getSessionDetail(sessionUuid)
        if (detail == null) {
            emit(null)
            return@flow
        }
        val uuidsByType = detail.exercises.associate { it.exerciseUuid to it.exerciseType }
        emitAll(
            personalRecordRepository
                .observePersonalRecords(uuidsByType)
                .map { prMap -> DetailWithPrs(detail = detail, prMap = prMap) },
        )
    }.flowOn(ioDispatcher)

    override suspend fun updateSet(
        performedExerciseUuid: String,
        position: Int,
        set: SetsDataModel,
    ) {
        setRepository.update(performedExerciseUuid, position, set)
    }

    override suspend fun deleteSession(sessionUuid: String) {
        sessionRepository.deleteSession(sessionUuid)
    }
}
