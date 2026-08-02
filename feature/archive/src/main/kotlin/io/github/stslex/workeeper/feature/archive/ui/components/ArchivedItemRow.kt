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
 * One archived row — `pass2d.html` `#s-list` `.row`, **the fourth payload**.
 *
 * ## Same skeleton as both siblings, and the drawing says so outright
 *
 * `#s-list`'s hint: "Скелет строки один — 88px, линейка снизу, имя и мета-строка, шеврон. Начинки
 * разные: поля у четырёх экранов не совпадают" — and its first frame draws all four, the last of
 * them this screen's:
 *
 * ```
 * Румынская тяга
 * упражнение · в архиве с 3 июля
 * ```
 *
 *
 * ## This row does not yet conform, and that is now scheduled rather than open
 *
 * §2.1 asked whether the drawn one-slot skeleton or this screen's two live verbs win. **It is RULED
 * (Ilya, §24.2 group A):** an archived item **opens** — read-only detail — so the drawn chevron is
 * true, this row takes the drawn 20dp slot like its three siblings, and restore and
 * permanent-delete come off it. Where they go is the one part still open, and it belongs to the
 * drawing.
 *
 * **What ships here is the pre-ruling row, deliberately.** Applying the ruling moves pixels — a
 * chevron appears, two affordances leave, and the row drops to the drawn 88dp, which it currently
 * exceeds because its trailing region is not the slot. The extraction this file is part of asserts
 * the opposite (every golden byte-identical), so conformance cannot land in the same change without
 * destroying the only claim that change makes. **It lands in the archive rebuild, after group A is
 * drawn**, and until then this row is the one consumer of [AppListRow] that does not use
 * `AppListRowSlot` — by schedule, not by exception.
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
        // SCHEDULED, not open — §2.1 is ruled (§24.2 group A): the row will take the drawn 20dp
        // slot with a chevron and these two verbs leave it. Not applied here because this change
        // asserts every golden stays byte-identical and applying it moves pixels; it lands with
        // the archive rebuild. Until then this stays un-slotted BY SCHEDULE — so do not wrap it in
        // `AppListRowSlot` piecemeal either: the slot arrives with the click and the destination
        // or not at all.
        content = {
            TrailingAffordances(
                item = item,
                onRestore = onRestore,
                onPermanentDelete = onPermanentDelete,
            )
        },
    )
}

/** The two contested verbs, unchanged. See [ArchivedItemRow]'s KDoc and `archive-delta.md` §2.1. */
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
