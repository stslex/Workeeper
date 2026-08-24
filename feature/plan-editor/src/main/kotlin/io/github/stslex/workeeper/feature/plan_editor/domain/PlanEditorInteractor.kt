// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.domain

import io.github.stslex.workeeper.feature.plan_editor.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanSetDomain

interface PlanEditorInteractor {

    /**
     * Loads exercise metadata plus the plan [trainingUuid] selects (null reads `last_adhoc_sets`).
     * The exercise's own `type` is always returned. See documentation/architecture.md.
     */
    suspend fun loadPlan(exerciseUuid: String, trainingUuid: String?): PlanEditorLoadResult

    /**
     * Persists [plan] (null clears) to the store [trainingUuid] selects. [type] is written only
     * when [trainingUuid] is null, and a flip to WEIGHTLESS wipes weights from this exercise.
     */
    suspend fun savePlan(
        exerciseUuid: String,
        trainingUuid: String?,
        type: ExerciseTypeDomain,
        plan: List<PlanSetDomain>?,
    )
}
