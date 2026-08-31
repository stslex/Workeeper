// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.domain.model

sealed interface PlanEditorLoadResult {

    data class Success(
        val exerciseName: String,
        val type: ExerciseTypeDomain,
        val plan: List<PlanSetDomain>,
    ) : PlanEditorLoadResult

    data object NotFound : PlanEditorLoadResult
}
