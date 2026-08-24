// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListRow
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.archive.R
import io.github.stslex.workeeper.feature.archive.domain.model.ArchivedItem
import io.github.stslex.workeeper.feature.archive.domain.model.ExerciseTypeDomain

/**
 * One archived row — `#s-list` `.row`, the fourth payload. The one [AppListRow] consumer that is
 * not slotted yet; the slot lands with the archive rebuild. See `archive-delta.md` §2.1.
 */
@Composable
internal fun ArchivedItemRow(
    item: ArchivedItem,
    metaLine: String,
    showDivider: Boolean,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppListRow(
        modifier = modifier,
        name = item.name,
        nameTestTag = "ArchivedItemName_${item.uuid}",
        meta = metaLine,
        metaTestTag = "ArchivedItemMeta_${item.uuid}",
        showDivider = showDivider,
        // GUARD: un-slotted by schedule — do not wrap this in `AppListRowSlot` piecemeal.
        content = {
            TrailingAffordances(
                item = item,
                onRestore = onRestore,
                onPermanentDelete = onPermanentDelete,
            )
        },
    )
}

/** Restore plus the permanent-delete overflow. See `archive-delta.md` §2.1. */
@Composable
private fun TrailingAffordances(
    item: ArchivedItem,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppButton.Tertiary(
            modifier = Modifier.testTag("ArchivedItemRestore_${item.uuid}"),
            text = stringResource(R.string.feature_archive_action_restore),
            onClick = onRestore,
            size = AppButtonSize.SMALL,
        )
        Box {
            IconButton(
                modifier = Modifier
                    .size(AppDimension.heightXs)
                    .testTag("ArchivedItemMenu_${item.uuid}"),
                onClick = { menuExpanded = true },
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.iconSm),
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.feature_archive_action_more),
                    tint = AppUi.colors.textSecondary,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = AppUi.colors.surfaceTier2,
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.feature_archive_action_permanent_delete),
                            style = AppUi.typography.bodyMedium,
                            color = AppUi.colors.setType.failureForeground,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onPermanentDelete()
                    },
                )
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ArchivedItemRowPreview() {
    AppTheme {
        Column {
            ArchivedItemRow(
                item = ArchivedItem.Exercise(
                    uuid = "1",
                    name = "Румынская тяга",
                    tags = listOf("спина"),
                    archivedAt = 0L,
                    type = ExerciseTypeDomain.WEIGHTED,
                ),
                metaLine = "упражнение · в архиве с 3 июля · спина",
                showDivider = true,
                onRestore = {},
                onPermanentDelete = {},
            )
            ArchivedItemRow(
                item = ArchivedItem.Training(
                    uuid = "2",
                    name = "Верх (с подтягиваниями)",
                    tags = listOf(),
                    archivedAt = 0L,
                    exerciseCount = 8,
                ),
                metaLine = "тренировка · в архиве с 9 июля",
                showDivider = false,
                onRestore = {},
                onPermanentDelete = {},
            )
        }
    }
}
