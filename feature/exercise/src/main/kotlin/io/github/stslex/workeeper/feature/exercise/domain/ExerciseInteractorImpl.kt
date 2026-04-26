// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.ArchiveResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.uuid.Uuid

@ViewModelScoped
internal class ExerciseInteractorImpl @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val tagRepository: TagRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ExerciseInteractor {

    override suspend fun getExercise(
        uuid: String,
    ): ExerciseDataModel? = withContext(defaultDispatcher) {
        exerciseRepository.getExercise(uuid)
    }

    override suspend fun getRecentHistory(
        exerciseUuid: String,
        limit: Int,
    ): List<HistoryEntry> = withContext(defaultDispatcher) {
        exerciseRepository.getRecentHistory(exerciseUuid, limit)
    }

    override fun observeAvailableTags(): Flow<List<TagDataModel>> = tagRepository
        .observeAll()
        .flowOn(defaultDispatcher)

    override suspend fun saveExercise(
        snapshot: ExerciseChangeDataModel,
    ): String = withContext(defaultDispatcher) {
        val resolvedUuid = snapshot.uuid?.takeIf { it.isNotBlank() } ?: Uuid.random().toString()
        val toSave = snapshot.copy(uuid = resolvedUuid)
        exerciseRepository.saveItem(toSave)
        resolvedUuid
    }

    override suspend fun createTag(name: String): TagDataModel = withContext(defaultDispatcher) {
        tagRepository.add(name)
    }

    override suspend fun archive(uuid: String): ArchiveResult = withContext(defaultDispatcher) {
        val activeTrainings = exerciseRepository.getActiveTrainingsUsing(uuid)
        if (activeTrainings.isNotEmpty()) {
            ArchiveResult.Blocked(activeTrainings)
        } else {
            exerciseRepository.archive(uuid)
            ArchiveResult.Success
        }
    }

    override suspend fun restore(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.restore(uuid) }
    }
}
