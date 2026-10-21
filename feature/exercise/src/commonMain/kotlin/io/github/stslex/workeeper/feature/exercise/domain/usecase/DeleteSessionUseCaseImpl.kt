// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** The production [DeleteSessionUseCase], bound in `ExerciseGraph` (S8). */
@Inject
@SingleIn(ExerciseScope::class)
class DeleteSessionUseCaseImpl internal constructor(
    private val sessionRepository: SessionRepository,
    private val trainingRepository: TrainingRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : DeleteSessionUseCase {

    override suspend operator fun invoke(sessionUuid: String) {
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
