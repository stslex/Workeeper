// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.domain

import io.github.stslex.workeeper.feature.plan_editor.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanSetDomain

internal interface PlanEditorInteractor {

    /**
     * Loads exercise metadata + initial plan. When [trainingUuid] is null the plan is read
     * from `exercise_table.last_adhoc_sets`; otherwise from
     * `training_exercise_table.plan_sets` for the (training, exercise) pair. The exercise's
     * own `type` is always returned (it's the source of truth for both
     * `last_adhoc_sets` and any plan_sets row in a training, since training-exercise rows
     * inherit shape from the parent exercise).
     */
    suspend fun loadPlan(exerciseUuid: String, trainingUuid: String?): PlanEditorLoadResult

    /**
     * Persists [plan] (or null to clear) to the appropriate backing store, plus the
     * [type] when [trainingUuid] is null (i.e. Mode.Exercise — PlanEditor owns the type
     * for the parent exercise). For Mode.PerformedExercise the [type] is ignored — the
     * type lives on the parent exercise and isn't editable through a training-scoped
     * editor.
     *
     * When [type] flips a Mode.Exercise from WEIGHTED to WEIGHTLESS the implementation
     * also wipes weights from every plan_sets row that references this exercise so
     * weighted plan values do not survive the type change.
     */
    suspend fun savePlan(
        exerciseUuid: String,
        trainingUuid: String?,
        type: ExerciseTypeDomain,
        plan: List<PlanSetDomain>?,
    )
}
