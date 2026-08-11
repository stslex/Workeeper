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
 * Pure-Composable body of the plan editor — the set rows and the card foot that grows and
 * shrinks them, with the type toggle above when the host may change the type. Hosted by the
 * full-screen `PlanEditorScreen` route and, inline, by the exercise create-flow. All state lives
 * upstream; field changes emit [PlanEditorBodyAction] back to the parent, which maps them to
 * store actions.
 *
 * ## The card is [PlanSetCard] and is not drawn here
 *
 * The rows, the `.setbar` foot and the `.tchip` picker all live in that component, which the
 * exercise read screen draws too (`v3-editors.md` ED2, D-OPEN-6). What is left here is the one
 * thing the read screen has no business with: the type.
 *
 * When [scrollable] is `false`, the card's internal `verticalScroll` and capped height are
 * dropped so the body lays out naturally inside an outer scroll container — used by the exercise
 * create-flow, which embeds it in an already-scrollable form.
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
        // THE NULL IS THE EXCLUSION. A host that may not edit the type supplies no handler and
        // gets no toggle — which is how `Mode.PerformedExercise` keeps not rendering it: type
        // lives on the parent exercise and is not editable through a training-scoped editor, so
        // that mode passes null. The rule is carried by the signature rather than by a `when` on
        // a mode the body cannot see.
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
