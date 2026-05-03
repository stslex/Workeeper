// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.domain

import android.net.Uri
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.images.model.ImageSaveResult
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.feature.exercise.domain.mapper.toData
import io.github.stslex.workeeper.feature.exercise.domain.mapper.toDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseChangeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.HistoryEntryDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PersonalRecordDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.SaveResult
import io.github.stslex.workeeper.feature.exercise.domain.model.TagDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.TrackNowConflict
import io.github.stslex.workeeper.feature.exercise.domain.usecase.ArchiveExerciseUseCase
import io.github.stslex.workeeper.feature.exercise.domain.usecase.DeleteSessionUseCase
import io.github.stslex.workeeper.feature.exercise.domain.usecase.ResolveTrackNowConflictUseCase
import io.github.stslex.workeeper.feature.exercise.domain.usecase.StartTrackNowSessionUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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
    ): ExerciseDomain? = withContext(defaultDispatcher) {
        exerciseRepository.getExercise(uuid)?.toDomain()
    }

    override suspend fun getLabels(
        exerciseUuid: String,
    ): List<String> = withContext(defaultDispatcher) {
        exerciseRepository.getLabels(exerciseUuid)
    }

    override suspend fun getRecentHistory(
        exerciseUuid: String,
        limit: Int,
    ): List<HistoryEntryDomain> = withContext(defaultDispatcher) {
        exerciseRepository.getRecentHistory(exerciseUuid, limit).map { it.toDomain() }
    }

    override fun observeAvailableTags(): Flow<List<TagDomain>> = tagRepository
        .observeAll()
        .map { tags -> tags.map { it.toDomain() } }
        .flowOn(defaultDispatcher)

    override fun observePersonalRecord(
        exerciseUuid: String,
        type: ExerciseTypeDomain,
    ): Flow<PersonalRecordDomain?> = personalRecordRepository
        .observePersonalRecord(exerciseUuid, type.toData())
        .map { record -> record?.toDomain() }

    override suspend fun saveExercise(
        snapshot: ExerciseChangeDomain,
    ): SaveResult = withContext(defaultDispatcher) {
        when (exerciseRepository.saveItem(snapshot.toData())) {
            ExerciseRepository.SaveResult.Success -> SaveResult.Success(snapshot.uuid)
            ExerciseRepository.SaveResult.DuplicateName -> SaveResult.DuplicateName
        }
    }

    override suspend fun createTag(name: String): TagDomain = withContext(defaultDispatcher) {
        tagRepository.add(name).toDomain()
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
    ): List<PlanSetDomain>? = withContext(defaultDispatcher) {
        exerciseRepository.getAdhocPlan(uuid)?.map { it.toDomain() }
    }

    override suspend fun setAdhocPlan(uuid: String, plan: List<PlanSetDomain>?) {
        withContext(defaultDispatcher) {
            exerciseRepository.setAdhocPlan(uuid, plan?.map { it.toData() })
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

    override suspend fun resolveTrackNowConflict(): TrackNowConflict =
        resolveTrackNowConflictUseCase()

    override suspend fun startTrackNowSession(
        exerciseUuid: String,
        defaultName: String,
    ): String = startTrackNowSessionUseCase(
        exerciseUuid = exerciseUuid,
        defaultName = defaultName,
    )

    override suspend fun deleteSession(sessionUuid: String) {
        deleteSessionUseCase(sessionUuid)
    }
}
