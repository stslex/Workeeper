// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import android.net.Uri
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.SaveResult
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor.TrackNowConflict
import io.github.stslex.workeeper.feature.exercise.domain.usecase.ArchiveExerciseUseCase
import io.github.stslex.workeeper.feature.exercise.domain.usecase.DeleteSessionUseCase
import io.github.stslex.workeeper.feature.exercise.domain.usecase.ResolveTrackNowConflictUseCase
import io.github.stslex.workeeper.feature.exercise.domain.usecase.StartTrackNowSessionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Suppress("LongParameterList", "TooManyFunctions")
@ViewModelScoped
internal class ExerciseInteractorImpl @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val tagRepository: TagRepository,
    private val imageStorage: ImageStorage,
    private val personalRecordRepository: PersonalRecordRepository,
    private val archiveExerciseUseCase: ArchiveExerciseUseCase,
    private val resolveTrackNowConflictUseCase: ResolveTrackNowConflictUseCase,
    private val startTrackNowSessionUseCase: StartTrackNowSessionUseCase,
    private val deleteSessionUseCase: DeleteSessionUseCase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ExerciseInteractor {

    override suspend fun getExercise(
        uuid: String,
    ): ExerciseDataModel? = withContext(defaultDispatcher) {
        exerciseRepository.getExercise(uuid)
    }

    override suspend fun getLabels(
        exerciseUuid: String,
    ): List<String> = withContext(defaultDispatcher) {
        exerciseRepository.getLabels(exerciseUuid)
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

    override fun observePersonalRecord(
        exerciseUuid: String,
        type: ExerciseTypeDataModel,
    ): Flow<PersonalRecordDataModel?> = personalRecordRepository
        .observePersonalRecord(exerciseUuid, type)

    override suspend fun saveExercise(
        snapshot: ExerciseChangeDataModel,
    ): SaveResult = withContext(defaultDispatcher) {
        when (exerciseRepository.saveItem(snapshot)) {
            ExerciseRepository.SaveResult.Success -> SaveResult.Success(snapshot.uuid)
            ExerciseRepository.SaveResult.DuplicateName -> SaveResult.DuplicateName
        }
    }

    override suspend fun createTag(name: String): TagDataModel = withContext(defaultDispatcher) {
        tagRepository.add(name)
    }

    override suspend fun archive(uuid: String): ArchiveResult = archiveExerciseUseCase(uuid)

    override suspend fun restore(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.restore(uuid) }
    }

    override suspend fun canPermanentlyDelete(
        uuid: String,
    ): Boolean = withContext(defaultDispatcher) {
        exerciseRepository.canPermanentlyDeleteImmediately(uuid)
    }

    override suspend fun permanentlyDelete(uuid: String) {
        withContext(defaultDispatcher) { exerciseRepository.permanentDelete(uuid) }
    }

    override suspend fun getAdhocPlan(
        uuid: String,
    ): List<PlanSetDataModel>? = withContext(defaultDispatcher) {
        exerciseRepository.getAdhocPlan(uuid)
    }

    override suspend fun setAdhocPlan(uuid: String, plan: List<PlanSetDataModel>?) {
        withContext(defaultDispatcher) {
            exerciseRepository.setAdhocPlan(uuid, plan)
        }
    }

    override suspend fun clearWeightsFromAllPlansForExercise(uuid: String) {
        withContext(defaultDispatcher) {
            exerciseRepository.clearWeightsFromAllPlansForExercise(uuid)
        }
    }

    override suspend fun saveImage(
        uri: Uri,
        exerciseUuid: String,
    ): ImageSaveResult = imageStorage.saveImage(uri, exerciseUuid)

    override suspend fun createTempCaptureUri(): Uri = imageStorage.createTempCaptureUri()

    override suspend fun deleteImageFile(path: String): Boolean = imageStorage.deleteImage(path)

    override suspend fun resolveTrackNowConflict(): TrackNowConflict = resolveTrackNowConflictUseCase()

    override suspend fun startTrackNowSession(
        exerciseUuid: String,
    ): String = startTrackNowSessionUseCase(exerciseUuid)

    override suspend fun deleteSession(sessionUuid: String) {
        deleteSessionUseCase(sessionUuid)
    }
}
