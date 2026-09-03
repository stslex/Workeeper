// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import kotlinx.serialization.Serializable

/**
 * Payload returned from a `Screen.PlanEditor.Draft` session to its caller, JSON-encoded in the
 * previous backstack entry's `SavedStateHandle`; the caller persists on its own Save.
 */
@Serializable
data class PlanDraftResult(
    val type: ExerciseTypeUiModel,
    val plan: List<PlanSetUiModel>,
)
