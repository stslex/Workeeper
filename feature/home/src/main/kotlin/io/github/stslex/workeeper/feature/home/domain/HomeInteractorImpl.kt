// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingListItem
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Suppress("LongParameterList")
@ViewModelScoped
internal class HomeInteractorImpl @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val trainingRepository: TrainingRepository,
    private val sessionConflictResolver: SessionConflictResolver,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : HomeInteractor {

    override fun observeActiveSession(): Flow<SessionRepository.ActiveSessionWithStats?> =
        sessionRepository.observeActiveSessionWithStats()
            .flowOn(defaultDispatcher)

    override fun observeRecent(limit: Int): Flow<List<RecentSessionDataModel>> =
        sessionRepository.observeRecentWithStats(limit)
            .flowOn(defaultDispatcher)

    override fun observeRecentTrainings(limit: Int): Flow<List<TrainingListItem>> =
        trainingRepository.observeRecentTemplates(limit)
            .flowOn(defaultDispatcher)

    override suspend fun resolveStartConflict(
        requestedTrainingUuid: String,
    ): SessionConflictResolver.Resolution = withContext(defaultDispatcher) {
        sessionConflictResolver.resolve(requestedTrainingUuid)
    }

    override suspend fun getTrainingName(trainingUuid: String): String? = withContext(defaultDispatcher) {
        trainingRepository.getTraining(trainingUuid)?.name
    }

    override suspend fun deleteSession(sessionUuid: String) {
        withContext(defaultDispatcher) {
            sessionRepository.deleteSession(sessionUuid)
        }
    }
}
