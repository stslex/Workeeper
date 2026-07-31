// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
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
 * So the card this row used to be — `shapes.medium` on `surfaceTier1`, inset by the list's gutter,
 * with a `LazyRow` of tag chips and the date on its own third line — becomes the same full-bleed
 * 88dp ruled row the other two already are. Three regions are pure delta and carry no new argument:
 * the container, the two-line clamped `titleMedium` name, and the single non-wrapping `mono.meta`
 * line. §26 "Meta-line order" is why the tag chips go: they are rejected in-row on every payload,
 * and the tags survive as text at the tail of the meta line.
 *
 * ## What this row does NOT resolve
 *
 * **The trailing slot is deliberately untouched, and it is the screen's open question.** `#s-list`
 * gives a row one 20px slot holding a chevron, a check, or nothing; this row carries *two* live
 * verbs — a `Restore` button and an overflow whose single item is permanent delete — and both were
 * verified reachable before the question was framed, so it does not collapse to one (unlike B23's
 * dialog on the sibling, which had no producer at all). The drawn archive row carries a **chevron**,
 * i.e. it navigates, and says nothing about either verb.
 *
 * That is a §0.1 decision for the owner, not a delta to apply, so the two affordances are left
 * exactly as they were. Everything around them is rebuilt. See `archive-delta.md` §2.1 for the three
 * readings and the argument against each.
 *
 * One consequence worth stating rather than discovering: while the affordances stay, this row is
 * **taller than 88dp in practice** and its trailing region is not the drawn slot. The `heightIn`
 * minimum is the drawn one; the row is not yet the drawn row, and it cannot be until §2.1 is ruled.
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
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AppDimension.rowHeight)
                .padding(horizontal = AppDimension.screenEdge),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
            ) {
                Text(
                    modifier = Modifier.testTag("ArchivedItemName_${item.uuid}"),
                    text = item.name,
                    style = AppUi.typography.titleMedium,
                    color = AppUi.colors.textPrimary,
                    maxLines = NAME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.testTag("ArchivedItemMeta_${item.uuid}"),
                    text = metaLine,
                    style = AppUi.typography.mono.meta,
                    color = AppUi.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // UNRESOLVED — archive-delta §2.1. Two verbs against a one-slot skeleton; left as-is
            // on purpose rather than picked. Do not "tidy" this into the drawn 20dp slot without
            // that ruling: collapsing it silently answers a §0.1 question in a refactor.
            TrailingAffordances(
                item = item,
                onRestore = onRestore,
                onPermanentDelete = onPermanentDelete,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = AppDimension.borderHairline,
                color = AppUi.colors.borderSubtle,
            )
        }
    }
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

private const val NAME_MAX_LINES = 2

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
