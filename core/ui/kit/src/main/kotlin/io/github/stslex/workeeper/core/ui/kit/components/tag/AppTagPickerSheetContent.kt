// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
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

/**
 * The tag picker's sheet (ED7): search field · the dictionary as selectable chips, a tap
 * toggles immediately · «+ Создать «X»» only when no exact match exists · «Готово».
 *
 * The window is `AppBottomSheet` at the call site so this drawing stays goldenable —
 * `ModalBottomSheet` composes into its own window, outside Paparazzi's one-window model
 * (`AppConfirmSheetContent`'s own split, for the same reason).
 *
 * Selection applies LIVE — a toggle lands in the caller's draft the moment it is tapped — so
 * «Готово» only closes the sheet, and the scrim and drag reach the same place. The dictionary
 * write behind «Создать» is also immediate (the repository's own contract); what stays
 * uncommitted until Save is only the LINK to the record being edited.
 *
 * The counter «N из 10» is deliberately NOT here: the limit belongs to `feature/exercise`
 * alone, and its head renders it (`v3-editors.md` §3.2 — a counter where no limit exists
 * is a lie).
 */
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
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
                title = stringResource(R.string.core_ui_kit_tag_create_format, searchQuery),
                onClick = { onTagCreate(searchQuery) },
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
