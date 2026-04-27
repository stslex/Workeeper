// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.dialog.AppDialog
import io.github.stslex.workeeper.core.ui.kit.components.input.AppNumberInput
import io.github.stslex.workeeper.core.ui.kit.components.setchip.AppSetTypeChip
import io.github.stslex.workeeper.core.ui.kit.components.setchip.SetType
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

private const val DEFAULT_REPS = 5

// Weight column gets a slightly wider weight so the kg input stays legible at small
// widths; reps stays at flex 1 so the row balances at typical phone sizes.
private const val WEIGHT_COLUMN_FLEX = 1.2f

/**
 * Modal bottom sheet for editing a plan as an ordered list of sets. Used by Edit training
 * (per training_exercise.plan_sets) and Edit exercise (for last_adhoc_sets).
 *
 * The sheet keeps its own draft state so the parent only learns about the new plan when
 * the user taps Save. An empty plan (zero sets) saves as `null` so the data layer stores
 * "no plan yet" canonically.
 *
 * @param exerciseName name shown in the title; pass blank to use the default "Plan" title
 * @param isWeighted when false, the weight column is hidden and saved sets carry `weight = null`
 * @param initialSets the existing plan, or null/empty for a fresh plan
 * @param onSave invoked with the new plan (null when empty) once the user confirms
 * @param onDismiss invoked when the user cancels or swipes the sheet down
 */
@Composable
fun AppPlanEditor(
    exerciseName: String,
    isWeighted: Boolean,
    initialSets: List<PlanEditorSet>?,
    onSave: (List<PlanEditorSet>?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialSafe = remember(initialSets) { initialSets.orEmpty() }
    // The sheet is short-lived and parent state already persists the canonical plan, so a
    // plain `remember` is enough — no Saver needed across config changes.
    var draft by remember(initialSafe) { mutableStateOf(initialSafe.toList()) }
    var typeMenuIndex by remember { mutableStateOf<Int?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    val isDirty = draft != initialSafe

    val attemptDismiss: () -> Unit = {
        if (isDirty) showDiscardDialog = true else onDismiss()
    }

    AppBottomSheet(
        modifier = modifier.testTag("AppPlanEditor"),
        onDismiss = attemptDismiss,
    ) {
        PlanEditorHeader(exerciseName = exerciseName)
        Spacer(Modifier.height(AppDimension.Space.md))
        PlanEditorBody(
            draft = draft,
            isWeighted = isWeighted,
            typeMenuIndex = typeMenuIndex,
            onTypeMenuOpen = { typeMenuIndex = it },
            onTypeMenuDismiss = { typeMenuIndex = null },
            onWeightChange = { index, value ->
                draft = draft.toMutableList().apply {
                    this[index] = this[index].copy(weight = value.toDoubleOrNull())
                }
            },
            onRepsChange = { index, value ->
                draft = draft.toMutableList().apply {
                    val reps = value.toIntOrNull() ?: 0
                    this[index] = this[index].copy(reps = reps.coerceAtLeast(0))
                }
            },
            onTypeSelect = { index, type ->
                draft = draft.toMutableList().apply {
                    this[index] = this[index].copy(type = type)
                }
                typeMenuIndex = null
            },
            onRemove = { index ->
                draft = draft.toMutableList().apply { removeAt(index) }
            },
        )
        Spacer(Modifier.height(AppDimension.Space.md))
        AppButton.Tertiary(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("AppPlanEditorAddSet"),
            text = stringResource(R.string.core_ui_kit_plan_editor_add_set),
            onClick = {
                draft = draft + buildNewSet(draft, isWeighted)
            },
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
                onClick = attemptDismiss,
                size = AppButtonSize.MEDIUM,
            )
            AppButton.Primary(
                modifier = Modifier
                    .weight(1f)
                    .testTag("AppPlanEditorSave"),
                text = stringResource(R.string.core_ui_kit_plan_editor_save),
                onClick = { onSave(draft.takeIf { it.isNotEmpty() }) },
                size = AppButtonSize.MEDIUM,
            )
        }
    }

    if (showDiscardDialog) {
        AppDialog(
            title = stringResource(R.string.core_ui_kit_plan_editor_discard_title),
            body = stringResource(R.string.core_ui_kit_plan_editor_discard_body),
            confirmLabel = stringResource(R.string.core_ui_kit_plan_editor_discard_confirm),
            destructive = true,
            dismissLabel = stringResource(R.string.core_ui_kit_action_cancel),
            onConfirm = {
                showDiscardDialog = false
                onDismiss()
            },
            onDismiss = { showDiscardDialog = false },
        )
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

@Suppress("LongParameterList")
@Composable
private fun ColumnScope.PlanEditorBody(
    draft: List<PlanEditorSet>,
    isWeighted: Boolean,
    typeMenuIndex: Int?,
    onTypeMenuOpen: (Int) -> Unit,
    onTypeMenuDismiss: () -> Unit,
    onWeightChange: (Int, String) -> Unit,
    onRepsChange: (Int, String) -> Unit,
    onTypeSelect: (Int, SetType) -> Unit,
    onRemove: (Int) -> Unit,
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
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        items(items = draft, key = { it.hashCode() + draft.indexOf(it) }) { item ->
            val index = draft.indexOf(item)
            PlanEditorRow(
                index = index,
                item = item,
                isWeighted = isWeighted,
                isMenuOpen = typeMenuIndex == index,
                onTypeMenuOpen = { onTypeMenuOpen(index) },
                onTypeMenuDismiss = onTypeMenuDismiss,
                onWeightChange = { onWeightChange(index, it) },
                onRepsChange = { onRepsChange(index, it) },
                onTypeSelect = { onTypeSelect(index, it) },
                onRemove = { onRemove(index) },
            )
        }
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun PlanEditorRow(
    index: Int,
    item: PlanEditorSet,
    isWeighted: Boolean,
    isMenuOpen: Boolean,
    onTypeMenuOpen: () -> Unit,
    onTypeMenuDismiss: () -> Unit,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onTypeSelect: (SetType) -> Unit,
    onRemove: () -> Unit,
) {
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
                onValueChange = onWeightChange,
                decimals = 2,
                suffix = stringResource(R.string.core_ui_kit_plan_editor_unit_kg),
            )
        }
        AppNumberInput(
            modifier = Modifier
                .weight(1f)
                .testTag("AppPlanEditorRowReps_$index"),
            value = item.reps.takeIf { it > 0 }?.toString().orEmpty(),
            onValueChange = onRepsChange,
            decimals = 0,
            suffix = stringResource(R.string.core_ui_kit_plan_editor_unit_reps),
        )
        Box {
            Box(
                modifier = Modifier
                    .clip(AppUi.shapes.small)
                    .clickable(onClick = onTypeMenuOpen)
                    .padding(horizontal = AppDimension.Space.xxs, vertical = AppDimension.Space.xxs)
                    .testTag("AppPlanEditorRowType_$index"),
            ) {
                AppSetTypeChip(type = item.type)
            }
            DropdownMenu(
                expanded = isMenuOpen,
                onDismissRequest = onTypeMenuDismiss,
                containerColor = AppUi.colors.surfaceTier2,
            ) {
                SetType.entries.forEach { type ->
                    DropdownMenuItem(
                        modifier = Modifier.testTag("AppPlanEditorTypeOption_${type.name}"),
                        text = {
                            Text(
                                text = stringResource(type.labelRes),
                                style = AppUi.typography.bodyMedium,
                                color = AppUi.colors.textPrimary,
                            )
                        },
                        onClick = { onTypeSelect(type) },
                    )
                }
            }
        }
        IconButton(
            modifier = Modifier
                .size(AppDimension.heightXs)
                .testTag("AppPlanEditorRowRemove_$index"),
            onClick = onRemove,
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

private val SetType.labelRes: Int
    get() = when (this) {
        SetType.WARMUP -> R.string.core_ui_kit_plan_editor_set_type_warmup
        SetType.WORK -> R.string.core_ui_kit_plan_editor_set_type_work
        SetType.FAIL -> R.string.core_ui_kit_plan_editor_set_type_failure
        SetType.DROP -> R.string.core_ui_kit_plan_editor_set_type_drop
    }

private fun buildNewSet(draft: List<PlanEditorSet>, isWeighted: Boolean): PlanEditorSet {
    val previous = draft.lastOrNull()
    return if (previous != null) {
        previous.copy(type = SetType.WORK, weight = if (isWeighted) previous.weight else null)
    } else {
        PlanEditorSet(
            weight = if (isWeighted) null else null,
            reps = DEFAULT_REPS,
            type = SetType.WORK,
        )
    }
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
            exerciseName = "Bench Press",
            isWeighted = true,
            initialSets = listOf(
                PlanEditorSet(60.0, 10, SetType.WARMUP),
                PlanEditorSet(80.0, 8, SetType.WORK),
                PlanEditorSet(100.0, 5, SetType.WORK),
                PlanEditorSet(85.0, 6, SetType.FAIL),
            ),
            onSave = {},
            onDismiss = {},
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
            exerciseName = "Bench Press",
            isWeighted = true,
            initialSets = null,
            onSave = {},
            onDismiss = {},
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
            exerciseName = "Pull-up",
            isWeighted = false,
            initialSets = listOf(
                PlanEditorSet(null, 8, SetType.WORK),
                PlanEditorSet(null, 6, SetType.WORK),
                PlanEditorSet(null, 4, SetType.DROP),
            ),
            onSave = {},
            onDismiss = {},
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
            exerciseName = "",
            isWeighted = false,
            initialSets = emptyList(),
            onSave = {},
            onDismiss = {},
        )
    }
}
