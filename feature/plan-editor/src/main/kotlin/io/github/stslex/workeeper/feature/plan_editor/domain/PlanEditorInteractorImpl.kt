// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.feature.plan_editor.domain.mapper.PlanEditorDomainMapper.isWeighted
import io.github.stslex.workeeper.feature.plan_editor.domain.mapper.PlanEditorDomainMapper.toData
import io.github.stslex.workeeper.feature.plan_editor.domain.mapper.PlanEditorDomainMapper.toDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanSetDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ViewModelScoped
internal class PlanEditorInteractorImpl @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val trainingExerciseRepository: TrainingExerciseRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : PlanEditorInteractor {

    override suspend fun loadPlan(
        exerciseUuid: String,
        trainingUuid: String?,
    ): PlanEditorLoadResult = withContext(defaultDispatcher) {
        val exercise = exerciseRepository.getExercise(exerciseUuid)
            ?: return@withContext PlanEditorLoadResult.NotFound
        val plan = if (trainingUuid.isNullOrBlank()) {
            exerciseRepository.getAdhocPlan(exerciseUuid).orEmpty()
        } else {
            trainingExerciseRepository.getPlan(trainingUuid, exerciseUuid).orEmpty()
        }
        PlanEditorLoadResult.Success(
            exerciseName = exercise.name,
            isWeighted = exercise.type.isWeighted(),
            plan = plan.map { it.toDomain() },
        )
    }

    override suspend fun savePlan(
        exerciseUuid: String,
        trainingUuid: String?,
        plan: List<PlanSetDomain>?,
    ) {
        withContext(defaultDispatcher) {
            val data = plan?.map { it.toData() }
            if (trainingUuid.isNullOrBlank()) {
                exerciseRepository.setAdhocPlan(exerciseUuid, data)
            } else {
                trainingExerciseRepository.setPlan(trainingUuid, exerciseUuid, data)
            }
        }
    }
}
