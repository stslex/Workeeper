// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.domain

import androidx.paging.PagingData
import androidx.paging.map
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.archive.di.ArchiveScope
import io.github.stslex.workeeper.feature.archive.domain.mapper.ArchivedItemDomainMapper.toDomain
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Inject
@SingleIn(ArchiveScope::class)
internal class ArchiveInteractorImpl(
    private val exerciseRepository: ExerciseRepository,
    private val trainingRepository: TrainingRepository,
    // Qualified: Metro reads the javax @DefaultDispatcher (via includeJavax interop), so this
    // resolves to the (CoroutineDispatcher + @DefaultDispatcher) bound instance — no strip.
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ArchiveInteractor {

    override fun observeArchivedExerciseCount(): Flow<Int> = exerciseRepository
        .observeArchivedCount()
        .flowOn(defaultDispatcher)

    override fun observeArchivedTrainingCount(): Flow<Int> = trainingRepository
        .observeArchivedCount()
        .flowOn(defaultDispatcher)

    override fun pagedArchivedExercises(): Flow<PagingData<ArchivedItem.Exercise>> =
        exerciseRepository
            .pagedArchived()
            .map { pagingData ->
                pagingData.map { exercise ->
                    ArchivedItem.Exercise(
                        uuid = exercise.uuid,
                        name = exercise.name,
                        tags = exerciseRepository.getLabels(exercise.uuid),
                        archivedAt = exercise.archivedAt ?: exercise.timestamp,
                        type = exercise.type.toDomain(),
                    )
                }
            }
            .flowOn(defaultDispatcher)

    override fun pagedArchivedTrainings(): Flow<PagingData<ArchivedItem.Training>> =
        trainingRepository
            .pagedArchived()
            .map { pagingData ->
                pagingData.map { training ->
                    ArchivedItem.Training(
                        uuid = training.uuid,
                        name = training.name,
                        tags = training.labels,
                        archivedAt = training.archivedAt ?: training.timestamp,
                        exerciseCount = training.exerciseUuids.size,
                    )
                }
            }
            .flowOn(defaultDispatcher)

    override suspend fun restoreExercise(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.restore(uuid) }
    }

    override suspend fun restoreTraining(uuid: String) {
        withContext(defaultDispatcher) { trainingRepository.restore(uuid) }
    }

    override suspend fun reArchiveExercise(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.archive(uuid) }
    }

    override suspend fun reArchiveTraining(uuid: String) {
        withContext(defaultDispatcher) { trainingRepository.archive(uuid) }
    }

    override suspend fun countExerciseSessions(
        uuid: String,
    ): Int = withContext(defaultDispatcher) {
        exerciseRepository.countSessionsUsing(uuid)
    }

    override suspend fun countTrainingSessions(
        uuid: String,
    ): Int = withContext(defaultDispatcher) {
        trainingRepository.countSessionsUsing(uuid)
    }

    override suspend fun permanentlyDeleteExercise(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.permanentDelete(uuid) }
    }

    override suspend fun permanentlyDeleteTraining(uuid: String) {
        withContext(defaultDispatcher) { trainingRepository.permanentDelete(uuid) }
    }
}
