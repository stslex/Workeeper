// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import kotlinx.collections.immutable.ImmutableList

@Composable
internal fun TagPickerInline(
    selectedTags: ImmutableList<TagUiModel>,
    availableTags: ImmutableList<TagUiModel>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTagToggle: (String) -> Unit,
    onTagRemove: (String) -> Unit,
    onTagCreate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedUuids = remember(selectedTags) { selectedTags.map { it.uuid }.toSet() }
    val filtered = remember(searchQuery, availableTags) {
        if (searchQuery.isBlank()) {
            availableTags
        } else {
            availableTags.filter { it.name.startsWith(searchQuery, ignoreCase = true) }
        }
    }
    val canCreate = remember(searchQuery, availableTags) {
        searchQuery.isNotBlank() &&
            availableTags.none { it.name.equals(searchQuery, ignoreCase = true) }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AppUi.colors.surfaceTier1, AppUi.shapes.medium)
            .padding(AppDimension.cardPadding)
            .testTag("ExerciseTagPicker"),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        if (selectedTags.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
            ) {
                selectedTags.forEach { tag ->
                    AppTagChip.Removable(
                        modifier = Modifier.testTag("ExerciseTagSelected_${tag.uuid}"),
                        label = tag.name,
                        onRemove = { onTagRemove(tag.uuid) },
                    )
                }
            }
        }
        AppTextField(
            modifier = Modifier.testTag("ExerciseTagSearch"),
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = stringResource(R.string.feature_exercise_edit_tag_search_placeholder),
            leadingIcon = Icons.Default.Search,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        ) {
            filtered.forEach { tag ->
                AppTagChip.Selectable(
                    modifier = Modifier.testTag("ExerciseTagAvailable_${tag.uuid}"),
                    label = tag.name,
                    selected = tag.uuid in selectedUuids,
                    onSelectedChange = { onTagToggle(tag.uuid) },
                )
            }
            if (canCreate) {
                AppButton.Tertiary(
                    modifier = Modifier.testTag("ExerciseTagCreate"),
                    text = stringResource(R.string.feature_exercise_edit_tag_create_format, searchQuery),
                    onClick = { onTagCreate(searchQuery) },
                    size = AppButtonSize.SMALL,
                )
            }
        }
    }
}
