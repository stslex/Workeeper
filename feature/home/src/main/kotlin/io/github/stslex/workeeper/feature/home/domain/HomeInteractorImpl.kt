// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.session.SessionRepository
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ViewModelScoped
internal class HomeInteractorImpl @Inject constructor(
    private val sessionRepository: SessionRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : HomeInteractor {

    override fun observeActiveSession(): Flow<HomeStore.State.ActiveSessionInfo?> =
        sessionRepository.observeActiveSessionWithStats()
            .map { row ->
                row?.let {
                    HomeStore.State.ActiveSessionInfo(
                        sessionUuid = it.sessionUuid,
                        trainingUuid = it.trainingUuid,
                        trainingName = it.trainingName,
                        startedAt = it.startedAt,
                        doneCount = it.doneCount,
                        totalCount = it.totalCount,
                    )
                }
            }
            .flowOn(defaultDispatcher)
}
