// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import kotlinx.serialization.Serializable

/**
 * Payload returned from a `Screen.PlanEditor.Draft` editor session back to the caller.
 *
 * Encoded as a JSON string and carried via `planEditorDraftResultAttr` in the previous
 * backstack entry's `SavedStateHandle`. The caller decodes the JSON and merges the
 * `(type, plan)` into its own local state — final persistence happens on the caller's
 * own Save click.
 *
 * Lives in `core/ui/plan-editor` rather than the feature module so PlanEditor (producer)
 * and the caller features (consumer) share the contract without cross-feature deps.
 */
@Serializable
data class PlanDraftResult(
    val type: ExerciseTypeUiModel,
    val plan: List<PlanSetUiModel>,
)
