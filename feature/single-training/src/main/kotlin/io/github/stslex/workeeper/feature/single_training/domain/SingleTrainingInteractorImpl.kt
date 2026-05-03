// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.single_training.domain.mapper.toData
import io.github.stslex.workeeper.feature.single_training.domain.mapper.toDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ActiveSessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.ArchiveResult
import io.github.stslex.workeeper.feature.single_training.domain.model.PickerExercise
import io.github.stslex.workeeper.feature.single_training.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.StartSessionConflict
import io.github.stslex.workeeper.feature.single_training.domain.model.TagDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingChangeDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingDomain
import io.github.stslex.workeeper.feature.single_training.domain.model.TrainingExerciseDetail
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

@Suppress("TooManyFunctions", "LongParameterList")
@ViewModelScoped
internal class SingleTrainingInteractorImpl @Inject constructor(
    private val trainingRepository: TrainingRepository,
    private val trainingExerciseRepository: TrainingExerciseRepository,
    private val exerciseRepository: ExerciseRepository,
    private val tagRepository: TagRepository,
    private val sessionRepository: SessionRepository,
    private val sessionConflictResolver: SessionConflictResolver,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : SingleTrainingInteractor {

    override suspend fun getTraining(
        uuid: String,
    ): TrainingDomain? = withContext(defaultDispatcher) {
        trainingRepository.getTraining(uuid)?.toDomain()
    }

    override suspend fun getTrainingExercises(
        trainingUuid: String,
    ): List<TrainingExerciseDetail> = withContext(defaultDispatcher) {
        val rows = trainingExerciseRepository.getRowsForTraining(trainingUuid)
        if (rows.isEmpty()) return@withContext emptyList()
        val exercises = exerciseRepository
            .getExercisesByUuid(rows.map { it.exerciseUuid })
            .associateBy { it.uuid }
        rows.mapNotNull { row ->
            val exercise = exercises[row.exerciseUuid] ?: return@mapNotNull null
            TrainingExerciseDetail(
                exercise = exercise.toDomain(),
                position = row.position,
                planSets = row.planSets?.map { it.toDomain() },
                labels = exerciseRepository.getLabels(exercise.uuid),
            )
        }
    }

    override suspend fun getLabels(
        exerciseUuid: String,
    ): List<String> = withContext(defaultDispatcher) {
        exerciseRepository.getLabels(exerciseUuid)
    }

    override suspend fun getRecentSessions(
        trainingUuid: String,
        limit: Int,
    ): List<SessionDomain> = withContext(defaultDispatcher) {
        sessionRepository.getRecentFinishedByTraining(trainingUuid, limit).map { it.toDomain() }
    }

    override fun observeAvailableTags(): Flow<List<TagDomain>> = tagRepository
        .observeAll()
        .map { tags -> tags.map { it.toDomain() } }
        .flowOn(defaultDispatcher)

    override suspend fun saveTraining(snapshot: TrainingChangeDomain) {
        withContext(defaultDispatcher) {
            trainingRepository.updateTraining(snapshot.toData())
        }
    }

    override suspend fun createTag(name: String): TagDomain = withContext(defaultDispatcher) {
        tagRepository.add(name).toDomain()
    }

    override suspend fun archive(uuid: String): ArchiveResult = withContext(defaultDispatcher) {
        val active = sessionRepository.getAnyActiveSession()
        if (active != null && active.trainingUuid == uuid) {
            ArchiveResult.Blocked(reason = "active_session")
        } else {
            trainingRepository.archive(uuid)
            ArchiveResult.Success
        }
    }

    override suspend fun permanentlyDelete(uuid: String) {
        withContext(defaultDispatcher) {
            trainingRepository.permanentDelete(uuid)
        }
    }

    override suspend fun canPermanentlyDelete(
        uuid: String,
    ): Boolean = withContext(defaultDispatcher) {
        val active = sessionRepository.getAnyActiveSession()
        active?.trainingUuid != uuid &&
            trainingRepository.countSessionsUsing(uuid) == 0
    }

    override fun observeAnyActiveSession(): Flow<ActiveSessionDomain?> = sessionRepository
        .observeAnyActiveSession()
        .map { it?.toDomain() }
        .flowOn(defaultDispatcher)

    override suspend fun setPlanForExercise(
        trainingUuid: String,
        exerciseUuid: String,
        plan: List<PlanSetDomain>?,
    ) {
        withContext(defaultDispatcher) {
            trainingExerciseRepository.setPlan(trainingUuid, exerciseUuid, plan?.map { it.toData() })
        }
    }

    override suspend fun searchExercisesForPicker(
        query: String,
        excludeUuids: Set<String>,
    ): List<PickerExercise> = withContext(defaultDispatcher) {
        exerciseRepository
            .searchActiveExercises(query, excludeUuids)
            .map { exercise ->
                PickerExercise(
                    exercise = exercise.toDomain(),
                    labels = exerciseRepository.getLabels(exercise.uuid),
                )
            }
    }

    override suspend fun resolveExercises(
        uuids: List<String>,
    ): List<PickerExercise> = withContext(defaultDispatcher) {
        exerciseRepository
            .getExercisesByUuid(uuids)
            .map { exercise ->
                PickerExercise(
                    exercise = exercise.toDomain(),
                    labels = exerciseRepository.getLabels(exercise.uuid),
                )
            }
    }

    override suspend fun resolveStartSessionConflict(
        requestedTrainingUuid: String,
    ): StartSessionConflict = withContext(defaultDispatcher) {
        sessionConflictResolver.resolve(requestedTrainingUuid).toDomain()
    }

    override suspend fun deleteSession(sessionUuid: String) {
        withContext(defaultDispatcher) {
            sessionRepository.deleteSession(sessionUuid)
        }
    }
}
