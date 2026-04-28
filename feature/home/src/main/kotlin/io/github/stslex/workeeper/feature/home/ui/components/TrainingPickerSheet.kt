// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.empty.AppEmptyState
import io.github.stslex.workeeper.core.ui.kit.components.loading.AppLoadingIndicator
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.mvi.model.PickerTrainingItem
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStore.State
import kotlinx.collections.immutable.persistentListOf

@Composable
internal fun TrainingPickerSheet(
    state: State.PickerState.Visible,
    onSelect: (String) -> Unit,
    onSeeAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppBottomSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("TrainingPickerSheet"),
    ) {
        Text(
            text = stringResource(R.string.feature_home_picker_title),
            style = AppUi.typography.titleLarge,
            color = AppUi.colors.textPrimary,
        )
        when {
            state.isLoading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimension.Space.xl),
                contentAlignment = Alignment.Center,
            ) { AppLoadingIndicator() }

            state.templates.isEmpty() -> AppEmptyState(
                headline = stringResource(R.string.feature_home_picker_empty),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            ) {
                items(items = state.templates, key = { it.trainingUuid }) { template ->
                    PickerRow(template = template, onClick = { onSelect(template.trainingUuid) })
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSeeAll)
                .padding(vertical = AppDimension.Space.md),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.feature_home_picker_see_all),
                style = AppUi.typography.labelLarge,
                color = AppUi.colors.accent,
            )
        }
    }
}

@Composable
private fun PickerRow(
    template: PickerTrainingItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = AppDimension.Space.sm),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        Text(
            text = template.name,
            style = AppUi.typography.titleMedium,
            color = AppUi.colors.textPrimary,
        )
        // TODO(tech-debt): UI mapping boundary — see documentation/tech-debt.md
        val subtitle = listOfNotNull(
            template.exerciseCountLabel,
            template.lastSessionRelativeLabel,
        ).joinToString(separator = " · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
        }
    }
}

@Preview(name = "Loaded — Light")
@Composable
private fun TrainingPickerSheetLoadedLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        TrainingPickerSheet(
            state = State.PickerState.Visible(
                templates = persistentListOf(
                    PickerTrainingItem(
                        trainingUuid = "t1",
                        name = "Push day",
                        exerciseCountLabel = "6 exercises",
                        lastSessionRelativeLabel = "3 days ago",
                    ),
                ),
                isLoading = false,
            ),
            onSelect = {},
            onSeeAll = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Loading — Dark")
@Composable
private fun TrainingPickerSheetLoadingDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        TrainingPickerSheet(
            state = State.PickerState.Visible(templates = persistentListOf(), isLoading = true),
            onSelect = {},
            onSeeAll = {},
            onDismiss = {},
        )
    }
}

@Preview(name = "Empty — Dark")
@Composable
private fun TrainingPickerSheetEmptyDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        TrainingPickerSheet(
            state = State.PickerState.Visible(templates = persistentListOf(), isLoading = false),
            onSelect = {},
            onSeeAll = {},
            onDismiss = {},
        )
    }
}
