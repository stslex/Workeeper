// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import androidx.paging.PagingData
import androidx.paging.map
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.home.di.HomeScope
import io.github.stslex.workeeper.feature.home.domain.mapper.HomeDomainMapper.toDomain
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.RecentSessionDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.home.domain.model.TrainingListItemDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Suppress("LongParameterList")
@Inject
@SingleIn(HomeScope::class)
class HomeInteractorImpl(
    private val sessionRepository: SessionRepository,
    private val trainingRepository: TrainingRepository,
    private val sessionConflictResolver: SessionConflictResolver,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : HomeInteractor {

    override fun observeActiveSession(): Flow<ActiveSessionWithStatsDomain?> =
        sessionRepository.observeActiveSessionWithStats()
            .map { row -> row?.toDomain() }
            .flowOn(defaultDispatcher)

    override fun pagedRecent(): Flow<PagingData<RecentSessionDomain>> =
        sessionRepository.pagedRecentWithStats()
            .map { pagingData -> pagingData.map { it.toDomain() } }
            .flowOn(defaultDispatcher)

    override fun observeRecentTrainings(limit: Int): Flow<List<TrainingListItemDomain>> =
        trainingRepository.observeRecentTemplates(limit)
            .map { trainings -> trainings.map { it.toDomain() } }
            .flowOn(defaultDispatcher)

    override suspend fun resolveStartConflict(
        requestedTrainingUuid: String,
    ): StartSessionConflict = withContext(defaultDispatcher) {
        sessionConflictResolver.resolve(requestedTrainingUuid).toDomain()
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
