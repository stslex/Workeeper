// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorScope
import io.github.stslex.workeeper.feature.plan_editor.domain.mapper.PlanEditorDomainMapper.toData
import io.github.stslex.workeeper.feature.plan_editor.domain.mapper.PlanEditorDomainMapper.toDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanSetDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

@Inject
@SingleIn(PlanEditorScope::class)
internal class PlanEditorInteractorImpl(
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
            type = exercise.type.toDomain(),
            plan = plan.map { it.toDomain() },
        )
    }

    override suspend fun savePlan(
        exerciseUuid: String,
        trainingUuid: String?,
        type: ExerciseTypeDomain,
        plan: List<PlanSetDomain>?,
    ) {
        withContext(defaultDispatcher) {
            val data = plan?.map { it.toData() }
            if (trainingUuid.isNullOrBlank()) {
                // Mode.Exercise — PlanEditor owns the type for the parent exercise. Persist
                // the type first so a concurrent reader who hits last_adhoc_sets sees the
                // matching `type` column. When the type flips to WEIGHTLESS, also wipe
                // weights from every other plan_sets row that references this exercise so
                // weighted plan values do not survive the type change.
                exerciseRepository.setExerciseType(exerciseUuid, type.toData())
                if (type == ExerciseTypeDomain.WEIGHTLESS) {
                    exerciseRepository.clearWeightsFromAllPlansForExercise(exerciseUuid)
                }
                exerciseRepository.setAdhocPlan(exerciseUuid, data)
            } else {
                // Mode.PerformedExercise — type lives on the parent exercise and isn't
                // editable from a training-scoped editor. Only the plan_sets row changes.
                trainingExerciseRepository.setPlan(trainingUuid, exerciseUuid, data)
            }
        }
    }
}
