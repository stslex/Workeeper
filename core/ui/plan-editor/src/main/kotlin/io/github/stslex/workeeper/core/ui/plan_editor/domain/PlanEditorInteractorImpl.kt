// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.domain

import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.ui.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.core.ui.plan_editor.domain.model.PlanSetDomain
import io.github.stslex.workeeper.core.ui.plan_editor.domain.model.SetTypeDomain
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
            isWeighted = exercise.type == ExerciseTypeDataModel.WEIGHTED,
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

    private fun PlanSetDataModel.toDomain(): PlanSetDomain = PlanSetDomain(
        weight = weight,
        reps = reps,
        type = type.toDomain(),
    )

    private fun PlanSetDomain.toData(): PlanSetDataModel = PlanSetDataModel(
        weight = weight,
        reps = reps,
        type = type.toData(),
    )

    private fun SetTypeDataModel.toDomain(): SetTypeDomain = when (this) {
        SetTypeDataModel.WARMUP -> SetTypeDomain.WARMUP
        SetTypeDataModel.WORK -> SetTypeDomain.WORK
        SetTypeDataModel.FAILURE -> SetTypeDomain.FAILURE
        SetTypeDataModel.DROP -> SetTypeDomain.DROP
    }

    private fun SetTypeDomain.toData(): SetTypeDataModel = when (this) {
        SetTypeDomain.WARMUP -> SetTypeDataModel.WARMUP
        SetTypeDomain.WORK -> SetTypeDataModel.WORK
        SetTypeDomain.FAILURE -> SetTypeDataModel.FAILURE
        SetTypeDomain.DROP -> SetTypeDataModel.DROP
    }
}
