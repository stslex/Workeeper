// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class StartTrackNowSessionUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val sessionRepository: SessionRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(
        exerciseUuid: String,
        defaultName: String,
    ): String = withContext(defaultDispatcher) {
        val exercise = exerciseRepository.getExercise(exerciseUuid)
        // Pass the raw exercise.name through to the adhoc-session helper. UI surfaces
        // that display this training name handle the blank/null case via stringResource.
        // Shared adhoc-session helper — same code path as v2.3 Quick start. Replaces the
        // older two-step Training upsert + session start that left orphan training rows
        // when Track Now was cancelled (the cancel path only deleted the session).
        sessionRepository.createAdhocSession(
            name = exercise?.name.orEmpty().ifBlank { defaultName },
            exerciseUuids = listOf(exerciseUuid),
        ).sessionUuid
    }
}
