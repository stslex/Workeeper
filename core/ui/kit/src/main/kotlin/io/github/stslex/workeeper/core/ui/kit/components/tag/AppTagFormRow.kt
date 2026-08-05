// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.tag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The form's tag row (ED7): the selected chips, each with `✕`, and the dashed «+ тег» chip
 * that opens the picker sheet ([AppTagPickerSheetContent]). Nothing else — no field labelled
 * with the section head's own word, no button row stacked over a text field; the search and
 * the dictionary live in the sheet.
 *
 * ONE component for both editors, which is this row's whole reason to be in the kit: the two
 * `internal` `TagPickerInline` copies it replaces differed only in test tags, two string ids,
 * one `@Suppress` and previews (`v3-editors.md` §2, B36).
 */
@Composable
fun AppTagFormRow(
    selectedTags: ImmutableList<AppTagItem>,
    onTagRemove: (String) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .testTag(FORM_ROW_TAG),
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        selectedTags.forEach { tag ->
            AppTagChip.Removable(
                modifier = Modifier.testTag("${SELECTED_TAG_PREFIX}${tag.uuid}"),
                label = tag.name,
                onRemove = { onTagRemove(tag.uuid) },
            )
        }
        AppTagChip.Add(
            modifier = Modifier.testTag(ADD_CHIP_TAG),
            onClick = onAddClick,
        )
    }
}

/** Stable across both hosts, so a UI test does not need to know which editor it is on. */
const val FORM_ROW_TAG: String = "AppTagFormRow"

/** See [FORM_ROW_TAG]. */
const val ADD_CHIP_TAG: String = "AppTagFormRowAdd"

/** See [FORM_ROW_TAG]; suffixed with the tag's uuid. */
const val SELECTED_TAG_PREFIX: String = "AppTagFormRowSelected_"

@Preview
@Composable
private fun AppTagFormRowPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        AppTagFormRow(
            selectedTags = persistentListOf(
                AppTagItem(uuid = "t1", name = "Push"),
                AppTagItem(uuid = "t2", name = "Chest"),
            ),
            onTagRemove = {},
            onAddClick = {},
        )
    }
}
