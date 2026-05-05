// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.domain.model

internal sealed interface PlanEditorLoadResult {

    data class Success(
        val exerciseName: String,
        val isWeighted: Boolean,
        val plan: List<PlanSetDomain>,
    ) : PlanEditorLoadResult

    data object NotFound : PlanEditorLoadResult
}
