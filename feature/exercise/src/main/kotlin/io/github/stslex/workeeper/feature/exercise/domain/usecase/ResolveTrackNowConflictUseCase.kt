// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.exercise.domain.mapper.toDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.TrackNowConflict
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class ResolveTrackNowConflictUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val trainingRepository: TrainingRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(): TrackNowConflict = withContext(defaultDispatcher) {
        val active = sessionRepository.getAnyActiveSession()
            ?: return@withContext TrackNowConflict.ProceedFresh
        val training = trainingRepository.getTraining(active.trainingUuid)
        TrackNowConflict.NeedsUserChoice(
            active = active.toDomain(),
            trainingName = training?.name?.takeIf { it.isNotBlank() },
        )
    }
}
