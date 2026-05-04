// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.domain

import io.github.stslex.workeeper.core.ui.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.core.ui.plan_editor.domain.model.PlanSetDomain

internal interface PlanEditorInteractor {

    /**
     * Loads exercise metadata + initial plan. When [trainingUuid] is null the plan is read
     * from `exercise_table.last_adhoc_sets`; otherwise from
     * `training_exercise_table.plan_sets` for the (training, exercise) pair.
     */
    suspend fun loadPlan(exerciseUuid: String, trainingUuid: String?): PlanEditorLoadResult

    /**
     * Persists [plan] (or null to clear) to the appropriate backing store.
     * Symmetric with [loadPlan]: when [trainingUuid] is null writes to
     * `last_adhoc_sets`; otherwise to `plan_sets` on `training_exercise_table`.
     */
    suspend fun savePlan(
        exerciseUuid: String,
        trainingUuid: String?,
        plan: List<PlanSetDomain>?,
    )
}
