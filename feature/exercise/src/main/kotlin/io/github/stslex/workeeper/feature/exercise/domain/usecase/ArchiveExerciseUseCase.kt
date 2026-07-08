// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import io.github.stslex.workeeper.feature.exercise.domain.model.ArchiveResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Inject
@SingleIn(ExerciseScope::class)
internal class ArchiveExerciseUseCase(
    private val exerciseRepository: ExerciseRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(uuid: String): ArchiveResult = withContext(defaultDispatcher) {
        val activeTrainings = exerciseRepository.getActiveTrainingsUsing(uuid)
        if (activeTrainings.isNotEmpty()) {
            ArchiveResult.Blocked(activeTrainings)
        } else {
            exerciseRepository.archive(uuid)
            ArchiveResult.Success
        }
    }
}
