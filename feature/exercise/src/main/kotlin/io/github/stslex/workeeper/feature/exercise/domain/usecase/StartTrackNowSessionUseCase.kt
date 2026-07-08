// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain.usecase

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.feature.exercise.di.ExerciseScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Inject
@SingleIn(ExerciseScope::class)
internal class StartTrackNowSessionUseCase(
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
