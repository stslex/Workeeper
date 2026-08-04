// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.components.setbar.AppSetBar
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.tooltip.AppTooltip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import kotlinx.collections.immutable.ImmutableList

// Weight column gets a slightly wider weight so the kg input stays legible at small
// widths; reps stays at flex 1 so the row balances at typical phone sizes.
private const val WEIGHT_COLUMN_FLEX = 1.2f

/**
 * Pure-Composable body of the plan editor — the set rows and the card foot that grows and
 * shrinks them. Hosted by the full-screen `PlanEditorScreen` route and, inline, by the exercise
 * create-flow. All state lives upstream; field changes emit [PlanEditorBodyAction] back to the
 * parent, which maps them to store actions.
 *
 * ## v3: this is `#s-past`'s card, and nothing here is a new form (extraction §7.5)
 *
 * `.card` / `.sets` / `.set` / `.field` / `.tchip` are all drawn already, and **an authored plan
 * row is a logged row, not a third kind of row.** Three things changed with the ruling:
 *
 *  1. **Add and remove live in the card's foot** — [AppSetBar], the drawn `.setbar`. A set row
 *     carries no `✕` (removal is «− подход», which drops the last set) and **no host draws an add
 *     button outside this composable**: one pair of opposite actions, in one place. It also means
 *     the empty draft has somewhere to grow from without the caller supplying it.
 *  2. **Values render in the normal colour** — `isLogged`, which is `textPrimary` on the plain
 *     field and `#s-past`'s own inline override on every ordinary row. Passing none of
 *     `isDone / isRecord / isLogged / isError` falls to `textTertiary`, which draws a number the
 *     user typed as "not yet entered". `isLogged` is REUSED rather than given a fourth flag of
 *     its own: a boolean resolving to the same colour is a rename, and a rename is the mutation
 *     no gate can catch (§27).
 *  3. **The rows sit on a card**, `surfaceTier1` at `Radius.medium`, because `.setbar`'s top rule
 *     is the foot of something and a rule with nothing above it is a line in the air.
 *
 * When [scrollable] is `false`, the internal `verticalScroll` and capped height are dropped so the
 * body lays out naturally inside an outer scroll container — used by the exercise create-flow,
 * which embeds it in an already-scrollable form.
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
        PlanCard(
            draft = draft,
            isWeighted = isWeighted,
            onAction = onAction,
            setTypeTooltipText = setTypeTooltipText,
            scrollable = scrollable,
        )
    }
}

@Composable
private fun PlanCard(
    draft: ImmutableList<PlanSetUiModel>,
    isWeighted: Boolean,
    onAction: (PlanEditorBodyAction) -> Unit,
    setTypeTooltipText: String?,
    scrollable: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimension.Radius.medium))
            .background(AppUi.colors.surfaceTier1),
    ) {
        val rowsModifier = if (scrollable) {
            Modifier
                .fillMaxWidth()
                .heightIn(max = SCROLLABLE_ROWS_MAX_HEIGHT)
                .verticalScroll(rememberScrollState())
        } else {
            Modifier.fillMaxWidth()
        }
        if (draft.isEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppDimension.cardPadding)
                    .testTag("PlanEditorBodyEmpty"),
                text = stringResource(R.string.core_ui_kit_plan_editor_empty_hint),
                style = AppUi.typography.text.body,
                color = AppUi.colors.textSecondary,
            )
        } else {
            Column(
                modifier = rowsModifier.padding(AppDimension.cardPadding),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            ) {
                draft.forEachIndexed { index, set ->
                    PlanEditorRow(
                        index = index,
                        item = set,
                        isWeighted = isWeighted,
                        setTypeTooltipText = setTypeTooltipText,
                        onAction = onAction,
                    )
                }
            }
        }
        AppSetBar(
            addLabel = stringResource(R.string.core_ui_kit_setbar_add),
            removeLabel = stringResource(R.string.core_ui_kit_setbar_remove),
            onAdd = { onAction(PlanEditorBodyAction.OnAddSet) },
            // The foot removes the LAST set. The action keeps its row index because the reducer
            // is shared with paths that address a row directly — what this drops is the per-row
            // control, not the ability to address a row.
            onRemove = { onAction(PlanEditorBodyAction.OnSetRemove(draft.lastIndex)) },
            removeEnabled = draft.isNotEmpty(),
        )
    }
}

@Composable
private fun PlanEditorRow(
    index: Int,
    item: PlanSetUiModel,
    isWeighted: Boolean,
    setTypeTooltipText: String?,
    onAction: (PlanEditorBodyAction) -> Unit,
) {
    var typeMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("PlanEditorBodyRow_$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Text(
            modifier = Modifier.widthIn(min = ORDINAL_MIN_WIDTH),
            text = "${index + 1}.",
            style = AppUi.typography.text.body,
            color = AppUi.colors.textTertiary,
        )
        if (isWeighted) {
            AppNumberInput(
                modifier = Modifier
                    .weight(WEIGHT_COLUMN_FLEX)
                    .testTag("PlanEditorBodyRowWeight_$index"),
                value = item.weight?.formatPlain().orEmpty(),
                onValueChange = { raw ->
                    onAction(PlanEditorBodyAction.OnSetWeightChange(index, raw.toDoubleOrNull()))
                },
                decimals = 2,
                suffix = stringResource(R.string.core_ui_kit_plan_editor_unit_kg),
                isLogged = true,
            )
        }
        AppNumberInput(
            modifier = Modifier
                .weight(1f)
                .testTag("PlanEditorBodyRowReps_$index"),
            value = item.reps.takeIf { it > 0 }?.toString().orEmpty(),
            onValueChange = { raw ->
                onAction(PlanEditorBodyAction.OnSetRepsChange(index, raw.toIntOrNull() ?: 0))
            },
            decimals = 0,
            suffix = stringResource(R.string.core_ui_kit_plan_editor_unit_reps),
            isLogged = true,
        )
        Box {
            val chipBox: @Composable () -> Unit = {
                Box(
                    modifier = Modifier
                        .clip(AppUi.shapes.small)
                        .clickable { typeMenuOpen = true }
                        .padding(
                            horizontal = AppDimension.Space.xxs,
                            vertical = AppDimension.Space.xxs,
                        )
                        .testTag("PlanEditorBodyRowType_$index"),
                ) {
                    AppSetTypeChip(type = item.type.toUiKitType())
                }
            }
            if (setTypeTooltipText != null) {
                AppTooltip(text = setTypeTooltipText) { chipBox() }
            } else {
                chipBox()
            }
            DropdownMenu(
                expanded = typeMenuOpen,
                onDismissRequest = { typeMenuOpen = false },
                containerColor = AppUi.colors.surfaceTier2,
            ) {
                SetTypeUiModel.entries.forEach { type ->
                    DropdownMenuItem(
                        modifier = Modifier.testTag("PlanEditorBodyTypeOption_${type.name}"),
                        text = {
                            Text(
                                text = stringResource(type.labelRes),
                                style = AppUi.typography.text.body,
                                color = AppUi.colors.textPrimary,
                            )
                        },
                        onClick = {
                            onAction(PlanEditorBodyAction.OnSetTypeChange(index, type))
                            typeMenuOpen = false
                        },
                    )
                }
            }
        }
    }
}

/** The row's `.set-i` column — wide enough for a two-digit ordinal plus its full stop. */
private val ORDINAL_MIN_WIDTH = 22.dp

/** The scrollable host's cap: the route embeds the body above its own action bar. */
private val SCROLLABLE_ROWS_MAX_HEIGHT = 360.dp

internal fun Double.formatPlain(): String = if (this % 1.0 == 0.0) {
    toLong().toString()
} else {
    toString()
}
