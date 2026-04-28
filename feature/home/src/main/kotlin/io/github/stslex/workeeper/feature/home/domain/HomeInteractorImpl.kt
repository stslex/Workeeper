// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.exercise.session.model.RecentSessionDataModel
import io.github.stslex.workeeper.core.exercise.training.TrainingListItem
import io.github.stslex.workeeper.core.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

@ViewModelScoped
internal class HomeInteractorImpl @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val trainingRepository: TrainingRepository,
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
}
