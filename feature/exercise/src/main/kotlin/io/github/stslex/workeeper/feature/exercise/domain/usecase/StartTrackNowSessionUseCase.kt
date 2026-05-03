// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.feature.exercise.R
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class StartTrackNowSessionUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val sessionRepository: SessionRepository,
    private val resourceWrapper: ResourceWrapper,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) {

    suspend operator fun invoke(exerciseUuid: String): String = withContext(defaultDispatcher) {
        val exercise = exerciseRepository.getExercise(exerciseUuid)
        val trainingName = exercise?.name?.takeIf { it.isNotBlank() }
            ?: resourceWrapper.getString(R.string.feature_exercise_track_now_default_training_name)
        // Shared adhoc-session helper — same code path as v2.3 Quick start. Replaces the
        // older two-step Training upsert + session start that left orphan training rows
        // when Track Now was cancelled (the cancel path only deleted the session).
        sessionRepository.createAdhocSession(
            name = trainingName,
            exerciseUuids = listOf(exerciseUuid),
        ).sessionUuid
    }
}
