// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetLayout
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The mockup's `sh-pick` (extraction §4.9): the window — scrim, grab, dismissal — wrapped at
 * this level, the content split out so it stays goldenable (the window is outside the gate,
 * §10.4).
 */
@Composable
internal fun ExercisePickerSheet(
    items: ImmutableList<ExercisePickerItemUiModel>,
    selectedUuid: String?,
    onDismiss: () -> Unit,
    onItemSelect: (String) -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        ExercisePickerSheetContent(
            items = items,
            selectedUuid = selectedUuid,
            onItemSelect = onItemSelect,
        )
    }
}

/**
 * `sh-pick`'s drawing: the h3 title, then `.mitem` rows — `--body` at the body rung, the
 * selected one `.mitem.on`: `--max` at 500 with the trailing `.chev`-weight check
 * (`M4 12.5l5 5L20 7`, `AppIcons.Check`). The old build put the exercise-type label under
 * the selected row instead; the mockup's selection mark is the check, and the check ships.
 */
@Composable
internal fun ExercisePickerSheetContent(
    items: ImmutableList<ExercisePickerItemUiModel>,
    selectedUuid: String?,
    onItemSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSheetLayout(
        modifier = modifier,
        title = stringResource(R.string.feature_exercise_chart_picker_title),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimension.Space.md)
                .heightIn(max = PICKER_LIST_MAX_HEIGHT)
                .testTag("ExerciseChartPickerList"),
        ) {
            items(items, key = { it.uuid }) { item ->
                PickerRow(
                    item = item,
                    selected = item.uuid == selectedUuid,
                    onClick = { onItemSelect(item.uuid) },
                )
            }
        }
    }
}

/** `.mitem` — padding `15px 4px` → 16/4, body rung, radius 12px → 8 (no 12 rung). */
@Composable
private fun PickerRow(
    item: ExercisePickerItemUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimension.Radius.small))
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppDimension.Space.xs,
                vertical = AppDimension.Space.lg,
            )
            .testTag("ExerciseChartPickerItem_${item.uuid}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(end = AppDimension.Space.md),
            text = item.name,
            style = if (selected) {
                AppUi.typography.text.body.copy(fontWeight = FontWeight.Medium)
            } else {
                AppUi.typography.text.body
            },
            color = if (selected) AppUi.colors.textPrimary else AppUi.colors.textSecondary,
            maxLines = 1,
        )
        if (selected) {
            Icon(
                modifier = Modifier
                    .size(AppDimension.iconSm)
                    .testTag("ExerciseChartPickerCheck"),
                imageVector = AppIcons.Check,
                contentDescription = null,
                tint = AppUi.colors.textPrimary,
            )
        }
    }
}

/** The old build's cap, kept: the sheet lists recents, not the whole library. */
private val PICKER_LIST_MAX_HEIGHT = 360.dp

@Preview
@Composable
private fun ExercisePickerSheetContentDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Row(modifier = Modifier.background(AppUi.colors.surfaceTier3)) {
            ExercisePickerSheetContent(
                items = persistentListOf(
                    ExercisePickerItemUiModel("a", "разведение ног", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("b", "жим платформы (узко)", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("c", "румынская тяга", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("d", "подтягивания", ExerciseTypeUiModel.WEIGHTLESS),
                ),
                selectedUuid = "a",
                onItemSelect = {},
            )
        }
    }
}

@Preview
@Composable
private fun ExercisePickerSheetContentLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Row(modifier = Modifier.background(AppUi.colors.surfaceTier3)) {
            ExercisePickerSheetContent(
                items = persistentListOf(
                    ExercisePickerItemUiModel("a", "разведение ног", ExerciseTypeUiModel.WEIGHTED),
                    ExercisePickerItemUiModel("b", "подтягивания", ExerciseTypeUiModel.WEIGHTLESS),
                ),
                selectedUuid = "a",
                onItemSelect = {},
            )
        }
    }
}
