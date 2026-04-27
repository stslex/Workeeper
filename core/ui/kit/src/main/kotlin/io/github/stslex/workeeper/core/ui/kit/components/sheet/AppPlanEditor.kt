// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.setchip.SetType
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

// Weight column gets a slightly wider weight so the kg input stays legible at small
// widths; reps stays at flex 1 so the row balances at typical phone sizes.
private const val WEIGHT_COLUMN_FLEX = 1.2f

/**
 * Modal bottom sheet for editing a plan as an ordered list of sets. Stateless — every
 * field change emits an [AppPlanEditorAction] back to the parent store, which is the
 * single source of truth for the draft. The component does NOT own any discard state;
 * the parent decides whether to show a confirm dialog when [AppPlanEditorAction.OnDismiss]
 * fires.
 *
 * @param state render data — exercise name + current draft sets
 * @param isWeighted when false, the weight column is hidden and rendered sets carry
 * `weight = null`
 * @param onAction callback for every UI action; the parent translates into its own MVI
 * action surface
 */
@Composable
fun AppPlanEditor(
    state: AppPlanEditorState,
    isWeighted: Boolean,
    onAction: (AppPlanEditorAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        modifier = modifier.testTag("AppPlanEditor"),
        onDismiss = { onAction(AppPlanEditorAction.OnDismiss) },
    ) {
        PlanEditorHeader(exerciseName = state.exerciseName)
        Spacer(Modifier.height(AppDimension.Space.md))
        PlanEditorBody(
            draft = state.draft,
            isWeighted = isWeighted,
            onAction = onAction,
        )
        Spacer(Modifier.height(AppDimension.Space.md))
        AppButton.Tertiary(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("AppPlanEditorAddSet"),
            text = stringResource(R.string.core_ui_kit_plan_editor_add_set),
            onClick = { onAction(AppPlanEditorAction.OnAddSet) },
            size = AppButtonSize.MEDIUM,
        )
        Spacer(Modifier.height(AppDimension.Space.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton.Tertiary(
                modifier = Modifier.testTag("AppPlanEditorCancel"),
                text = stringResource(R.string.core_ui_kit_action_cancel),
                onClick = { onAction(AppPlanEditorAction.OnDismiss) },
                size = AppButtonSize.MEDIUM,
            )
            AppButton.Primary(
                modifier = Modifier
                    .weight(1f)
                    .testTag("AppPlanEditorSave"),
                text = stringResource(R.string.core_ui_kit_plan_editor_save),
                onClick = { onAction(AppPlanEditorAction.OnSave) },
                size = AppButtonSize.MEDIUM,
            )
        }
    }
}

@Composable
private fun PlanEditorHeader(exerciseName: String) {
    val title = if (exerciseName.isBlank()) {
        stringResource(R.string.core_ui_kit_plan_editor_title_default)
    } else {
        stringResource(R.string.core_ui_kit_plan_editor_title_format, exerciseName)
    }
    Text(
        text = title,
        style = AppUi.typography.titleLarge,
        color = AppUi.colors.textPrimary,
    )
    Text(
        modifier = Modifier.padding(top = AppDimension.Space.xs),
        text = stringResource(R.string.core_ui_kit_plan_editor_subtitle),
        style = AppUi.typography.bodySmall,
        color = AppUi.colors.textTertiary,
    )
}

@Composable
private fun ColumnScope.PlanEditorBody(
    draft: ImmutableList<PlanSetDataModel>,
    isWeighted: Boolean,
    onAction: (AppPlanEditorAction) -> Unit,
) {
    if (draft.isEmpty()) {
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AppDimension.Space.md)
                .testTag("AppPlanEditorEmpty"),
            text = stringResource(R.string.core_ui_kit_plan_editor_empty_hint),
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textSecondary,
        )
        return
    }
    // Plain Column rather than LazyColumn so each row's TextField is anchored to a
    // stable composition slot — LazyColumn would re-key on PlanSetDataModel.hashCode
    // changes and tear down the row each character, dismissing the soft keyboard.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        draft.forEachIndexed { index, set ->
            PlanEditorRow(
                index = index,
                item = set,
                isWeighted = isWeighted,
                onAction = onAction,
            )
        }
    }
}

@Suppress("LongMethod")
@Composable
private fun PlanEditorRow(
    index: Int,
    item: PlanSetDataModel,
    isWeighted: Boolean,
    onAction: (AppPlanEditorAction) -> Unit,
) {
    var typeMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("AppPlanEditorRow_$index"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Text(
            modifier = Modifier.widthIn(min = 22.dp),
            text = "${index + 1}.",
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textTertiary,
        )
        if (isWeighted) {
            AppNumberInput(
                modifier = Modifier
                    .weight(WEIGHT_COLUMN_FLEX)
                    .testTag("AppPlanEditorRowWeight_$index"),
                value = item.weight?.formatPlain().orEmpty(),
                onValueChange = { raw ->
                    onAction(AppPlanEditorAction.OnSetWeightChange(index, raw.toDoubleOrNull()))
                },
                decimals = 2,
                suffix = stringResource(R.string.core_ui_kit_plan_editor_unit_kg),
            )
        }
        AppNumberInput(
            modifier = Modifier
                .weight(1f)
                .testTag("AppPlanEditorRowReps_$index"),
            value = item.reps.takeIf { it > 0 }?.toString().orEmpty(),
            onValueChange = { raw ->
                onAction(AppPlanEditorAction.OnSetRepsChange(index, raw.toIntOrNull() ?: 0))
            },
            decimals = 0,
            suffix = stringResource(R.string.core_ui_kit_plan_editor_unit_reps),
        )
        Box {
            Box(
                modifier = Modifier
                    .clip(AppUi.shapes.small)
                    .clickable { typeMenuOpen = true }
                    .padding(horizontal = AppDimension.Space.xxs, vertical = AppDimension.Space.xxs)
                    .testTag("AppPlanEditorRowType_$index"),
            ) {
                AppSetTypeChip(type = item.type.toKitChip())
            }
            DropdownMenu(
                expanded = typeMenuOpen,
                onDismissRequest = { typeMenuOpen = false },
                containerColor = AppUi.colors.surfaceTier2,
            ) {
                SetTypeDataModel.entries.forEach { type ->
                    DropdownMenuItem(
                        modifier = Modifier.testTag("AppPlanEditorTypeOption_${type.name}"),
                        text = {
                            Text(
                                text = stringResource(type.labelRes),
                                style = AppUi.typography.bodyMedium,
                                color = AppUi.colors.textPrimary,
                            )
                        },
                        onClick = {
                            onAction(AppPlanEditorAction.OnSetTypeChange(index, type))
                            typeMenuOpen = false
                        },
                    )
                }
            }
        }
        IconButton(
            modifier = Modifier
                .size(AppDimension.heightXs)
                .testTag("AppPlanEditorRowRemove_$index"),
            onClick = { onAction(AppPlanEditorAction.OnSetRemove(index)) },
        ) {
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(R.string.core_ui_kit_plan_editor_remove_set),
                tint = AppUi.colors.textTertiary,
            )
        }
    }
}

private fun SetTypeDataModel.toKitChip(): SetType = when (this) {
    SetTypeDataModel.WARMUP -> SetType.WARMUP
    SetTypeDataModel.WORK -> SetType.WORK
    SetTypeDataModel.FAILURE -> SetType.FAIL
    SetTypeDataModel.DROP -> SetType.DROP
}

private val SetTypeDataModel.labelRes: Int
    get() = when (this) {
        SetTypeDataModel.WARMUP -> R.string.core_ui_kit_plan_editor_set_type_warmup
        SetTypeDataModel.WORK -> R.string.core_ui_kit_plan_editor_set_type_work
        SetTypeDataModel.FAILURE -> R.string.core_ui_kit_plan_editor_set_type_failure
        SetTypeDataModel.DROP -> R.string.core_ui_kit_plan_editor_set_type_drop
    }

private fun Double.formatPlain(): String = if (this % 1.0 == 0.0) {
    toLong().toString()
} else {
    toString()
}

@Preview(name = "Weighted populated · Light", showBackground = true)
@Preview(
    name = "Weighted populated · Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppPlanEditorWeightedPopulatedPreview() {
    AppTheme {
        AppPlanEditor(
            state = AppPlanEditorState(
                exerciseName = "Bench Press",
                draft = listOf(
                    PlanSetDataModel(60.0, 10, SetTypeDataModel.WARMUP),
                    PlanSetDataModel(80.0, 8, SetTypeDataModel.WORK),
                    PlanSetDataModel(100.0, 5, SetTypeDataModel.WORK),
                    PlanSetDataModel(85.0, 6, SetTypeDataModel.FAILURE),
                ).toImmutableList(),
            ),
            isWeighted = true,
            onAction = {},
        )
    }
}

@Preview(name = "Weighted empty · Light", showBackground = true)
@Preview(
    name = "Weighted empty · Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppPlanEditorWeightedEmptyPreview() {
    AppTheme {
        AppPlanEditor(
            state = AppPlanEditorState(
                exerciseName = "Bench Press",
                draft = persistentListOf(),
            ),
            isWeighted = true,
            onAction = {},
        )
    }
}

@Preview(name = "Weightless populated · Light", showBackground = true)
@Preview(
    name = "Weightless populated · Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppPlanEditorWeightlessPopulatedPreview() {
    AppTheme {
        AppPlanEditor(
            state = AppPlanEditorState(
                exerciseName = "Pull-up",
                draft = listOf(
                    PlanSetDataModel(null, 8, SetTypeDataModel.WORK),
                    PlanSetDataModel(null, 6, SetTypeDataModel.WORK),
                    PlanSetDataModel(null, 4, SetTypeDataModel.DROP),
                ).toImmutableList(),
            ),
            isWeighted = false,
            onAction = {},
        )
    }
}

@Preview(name = "Weightless empty · Light", showBackground = true)
@Preview(
    name = "Weightless empty · Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppPlanEditorWeightlessEmptyPreview() {
    AppTheme {
        AppPlanEditor(
            state = AppPlanEditorState(
                exerciseName = "",
                draft = persistentListOf(),
            ),
            isWeighted = false,
            onAction = {},
        )
    }
}
