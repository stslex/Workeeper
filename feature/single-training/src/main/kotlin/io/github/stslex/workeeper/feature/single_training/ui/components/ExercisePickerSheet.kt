// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.mvi.model.PickerExerciseItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Suppress("LongParameterList")
@Composable
internal fun ExercisePickerSheet(
    query: String,
    results: ImmutableList<PickerExerciseItem>,
    selectedUuids: ImmutableList<String>,
    onSearchChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBottomSheet(
        modifier = modifier.testTag("ExercisePickerSheet"),
        onDismiss = onDismiss,
    ) {
        Text(
            text = stringResource(R.string.feature_training_picker_title),
            style = AppUi.typography.titleLarge,
            color = AppUi.colors.textPrimary,
        )
        Spacer(Modifier.height(AppDimension.Space.sm))
        AppTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ExercisePickerSearch"),
            value = query,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.feature_training_picker_search_placeholder),
            leadingIcon = Icons.Default.Search,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Spacer(Modifier.height(AppDimension.Space.md))
        if (results.isEmpty()) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppDimension.Space.md)
                    .testTag("ExercisePickerEmpty"),
                text = stringResource(R.string.feature_training_picker_empty),
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
            ) {
                items(items = results, key = { it.uuid }) { item ->
                    PickerRow(
                        item = item,
                        isSelected = item.uuid in selectedUuids,
                        onToggle = { onToggle(item.uuid) },
                    )
                }
            }
        }
        Spacer(Modifier.height(AppDimension.Space.lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppButton.Tertiary(
                modifier = Modifier.testTag("ExercisePickerCancel"),
                text = stringResource(R.string.feature_training_picker_cancel),
                onClick = onDismiss,
                size = AppButtonSize.MEDIUM,
            )
            AppButton.Primary(
                modifier = Modifier
                    .weight(1f)
                    .testTag("ExercisePickerConfirm"),
                text = stringResource(
                    R.string.feature_training_picker_add_format,
                    selectedUuids.size,
                ),
                onClick = onConfirm,
                enabled = selectedUuids.isNotEmpty(),
                size = AppButtonSize.MEDIUM,
            )
        }
    }
}

@Composable
private fun PickerRow(
    item: PickerExerciseItem,
    isSelected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier2)
            .clickable(onClick = onToggle)
            .padding(AppDimension.Space.md)
            .testTag("ExercisePickerRow_${item.uuid}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
        ) {
            Text(
                text = item.name,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textPrimary,
            )
            if (item.tags.isNotEmpty()) {
                Text(
                    text = item.tagsLabel,
                    style = AppUi.typography.bodySmall,
                    color = AppUi.colors.textTertiary,
                )
            }
        }
        Checkbox(
            checked = isSelected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(
                checkedColor = AppUi.colors.accent,
                uncheckedColor = AppUi.colors.borderStrong,
                checkmarkColor = AppUi.colors.onAccent,
            ),
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ExercisePickerSheetPreview() {
    AppTheme {
        ExercisePickerSheet(
            query = "",
            results = persistentListOf(
                PickerExerciseItem(
                    uuid = "1",
                    name = "Bench press",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf("Push", "Chest"),
                ),
                PickerExerciseItem(
                    uuid = "2",
                    name = "Pull-up",
                    type = ExerciseTypeUiModel.WEIGHTLESS,
                    tags = persistentListOf("Pull"),
                ),
            ),
            selectedUuids = persistentListOf("1"),
            onSearchChange = {},
            onToggle = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
