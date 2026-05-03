// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class DeleteSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val trainingRepository: TrainingRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(sessionUuid: String) {
        withContext(defaultDispatcher) {
            val session = sessionRepository.getById(sessionUuid) ?: return@withContext
            val training = trainingRepository.getTraining(session.trainingUuid)
            if (training?.isAdhoc == true) {
                sessionRepository.discardAdhocSession(
                    sessionUuid = sessionUuid,
                    trainingUuid = session.trainingUuid,
                )
            } else {
                sessionRepository.deleteSession(sessionUuid)
            }
        }
    }
}
