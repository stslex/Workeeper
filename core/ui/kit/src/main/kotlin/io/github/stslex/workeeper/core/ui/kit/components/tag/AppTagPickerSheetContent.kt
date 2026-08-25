// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.input.AppTextField
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetItem
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/** The tag picker's sheet: search, dictionary chips, create row, done. Selection is live. */
@Composable
fun AppTagPickerSheetContent(
    selectedTagUuids: ImmutableSet<String>,
    availableTags: ImmutableList<AppTagItem>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onTagToggle: (String) -> Unit,
    onTagCreate: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedQuery = searchQuery.trim()
    val filtered = remember(searchQuery, availableTags) {
        tagPickerFiltered(searchQuery, availableTags)
    }
    val canCreate = remember(searchQuery, availableTags) {
        tagPickerCanCreate(searchQuery, availableTags)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.core_ui_kit_tag_sheet_title),
            style = AppUi.typography.titleLarge,
            color = AppUi.colors.textPrimary,
        )
        Spacer(Modifier.height(AppDimension.Space.sm))
        AppTextField(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(SEARCH_FIELD_TAG),
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = stringResource(R.string.core_ui_kit_tag_search_hint),
            leadingIcon = Icons.Default.Search,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Spacer(Modifier.height(AppDimension.Space.md))
        if (filtered.isNotEmpty()) {
            // Bounded and scrollable, or a tall dictionary walks the create row out of view.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = CHIP_AREA_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
            ) {
                filtered.forEach { tag ->
                    AppTagChip.Selectable(
                        modifier = Modifier.testTag("${AVAILABLE_TAG_PREFIX}${tag.uuid}"),
                        label = tag.name,
                        selected = tag.uuid in selectedTagUuids,
                        onSelectedChange = { onTagToggle(tag.uuid) },
                    )
                }
            }
        }
        if (canCreate) {
            AppSheetItem(
                modifier = Modifier.testTag(CREATE_ROW_TAG),
                title = stringResource(R.string.core_ui_kit_tag_create_format, normalizedQuery),
                onClick = { onTagCreate(normalizedQuery) },
            )
        }
        Spacer(Modifier.height(AppDimension.Space.lg))
        AppButton.Primary(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(DONE_BUTTON_TAG),
            text = stringResource(R.string.core_ui_kit_tag_done),
            onClick = onDone,
        )
    }
}

/** The dictionary filter, trimmed before matching so both predicates share one normalization. */
internal fun tagPickerFiltered(
    query: String,
    availableTags: ImmutableList<AppTagItem>,
): ImmutableList<AppTagItem> {
    val normalized = query.trim()
    return if (normalized.isEmpty()) {
        availableTags
    } else {
        availableTags.filter { it.name.startsWith(normalized, ignoreCase = true) }
            .toImmutableList()
    }
}

/** «+ Создать «X»» appears exactly when the TRIMMED query has no exact match. */
internal fun tagPickerCanCreate(
    query: String,
    availableTags: ImmutableList<AppTagItem>,
): Boolean {
    val normalized = query.trim()
    return normalized.isNotEmpty() &&
        availableTags.none { it.name.equals(normalized, ignoreCase = true) }
}

/** The exercise picker's own list bound (`ExercisePickerSheet`), for the same seat. */
private val CHIP_AREA_MAX_HEIGHT = 360.dp

/** Stable across both hosts, so a UI test does not need to know which editor opened it. */
const val SEARCH_FIELD_TAG: String = "AppTagPickerSearch"

/** See [SEARCH_FIELD_TAG]. */
const val CREATE_ROW_TAG: String = "AppTagPickerCreate"

/** See [SEARCH_FIELD_TAG]. */
const val DONE_BUTTON_TAG: String = "AppTagPickerDone"

/** See [SEARCH_FIELD_TAG]; suffixed with the tag's uuid. */
const val AVAILABLE_TAG_PREFIX: String = "AppTagPickerAvailable_"

@Preview
@Composable
private fun AppTagPickerSheetContentPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        AppTagPickerSheetContent(
            selectedTagUuids = persistentSetOf("t1"),
            availableTags = persistentListOf(
                AppTagItem(uuid = "t1", name = "Push"),
                AppTagItem(uuid = "t2", name = "Pull"),
                AppTagItem(uuid = "t3", name = "Legs"),
            ),
            searchQuery = "",
            onSearchQueryChange = {},
            onTagToggle = {},
            onTagCreate = {},
            onDone = {},
        )
    }
}
