// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.past_session.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.SetRepository
import io.github.stslex.workeeper.core.exercise.session.model.SessionDetailDataModel
import javax.inject.Inject

@ViewModelScoped
internal class PastSessionInteractorImpl @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val setRepository: SetRepository,
) : PastSessionInteractor {

    override suspend fun getSessionDetail(
        sessionUuid: String,
    ): SessionDetailDataModel? = sessionRepository.getSessionDetail(sessionUuid)

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
