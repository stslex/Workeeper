// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListItem
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise_chart.R
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun ExercisePickerSheet(
    items: ImmutableList<ExercisePickerItemUiModel>,
    selectedUuid: String?,
    onDismiss: () -> Unit,
    onItemSelect: (String) -> Unit,
) {
    AppBottomSheet(onDismiss = onDismiss) {
        Text(
            modifier = Modifier.padding(bottom = AppDimension.Space.md),
            text = stringResource(R.string.feature_exercise_chart_picker_title),
            style = AppUi.typography.titleMedium,
            color = AppUi.colors.textPrimary,
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .testTag("ExerciseChartPickerList"),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        ) {
            items(items, key = { it.uuid }) { item ->
                AppListItem(
                    modifier = Modifier.testTag("ExerciseChartPickerItem_${item.uuid}"),
                    headline = item.name,
                    supportingText = if (item.uuid == selectedUuid) {
                        stringResource(item.type.labelRes)
                    } else {
                        null
                    },
                    onClick = { onItemSelect(item.uuid) },
                )
            }
        }
    }
}

@Preview
@Composable
private fun ExercisePickerSheetLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier1)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            Text(
                text = "Select exercise",
                style = AppUi.typography.titleMedium,
                color = AppUi.colors.textPrimary,
            )
            persistentListOf(
                ExercisePickerItemUiModel("a", "Bench press", ExerciseTypeUiModel.WEIGHTED),
                ExercisePickerItemUiModel("b", "Pull-ups", ExerciseTypeUiModel.WEIGHTLESS),
            ).forEach {
                AppListItem(headline = it.name)
            }
        }
    }
}

@Preview
@Composable
private fun ExercisePickerSheetDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier1)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            Text(
                text = "Select exercise",
                style = AppUi.typography.titleMedium,
                color = AppUi.colors.textPrimary,
            )
            persistentListOf(
                ExercisePickerItemUiModel("a", "Bench press", ExerciseTypeUiModel.WEIGHTED),
                ExercisePickerItemUiModel("b", "Pull-ups", ExerciseTypeUiModel.WEIGHTLESS),
            ).forEach {
                AppListItem(headline = it.name)
            }
        }
    }
}
