// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import kotlinx.collections.immutable.ImmutableList

/**
 * Pure-Composable body of the plan editor: the type toggle above [PlanSetCard]. All state lives
 * upstream; field changes emit [PlanEditorBodyAction] back to the parent.
 */
@Composable
fun PlanEditorBody(
    draft: ImmutableList<PlanSetUiModel>,
    isWeighted: Boolean,
    onAction: (PlanEditorBodyAction) -> Unit,
    modifier: Modifier = Modifier,
    setTypeTooltipText: String? = null,
    scrollable: Boolean = true,
    onTypeChange: ((ExerciseTypeUiModel) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The null is the exclusion: a host that may not edit the type supplies no handler.
        if (onTypeChange != null) {
            TypeToggle(
                selected = if (isWeighted) {
                    ExerciseTypeUiModel.WEIGHTED
                } else {
                    ExerciseTypeUiModel.WEIGHTLESS
                },
                onSelect = onTypeChange,
                modifier = Modifier.padding(bottom = AppDimension.Space.sm),
            )
        }
        PlanSetCard(
            plan = draft,
            isWeighted = isWeighted,
            setTypeTooltipText = setTypeTooltipText,
            scrollable = scrollable,
            onAction = onAction,
        )
    }
}
