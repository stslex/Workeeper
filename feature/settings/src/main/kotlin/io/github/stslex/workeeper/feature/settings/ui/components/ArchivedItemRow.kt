// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.components.tag.AppTagChip
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.settings.R
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedItem

@Composable
internal fun ArchivedItemRow(
    item: ArchivedItem,
    archivedAtLabel: String,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .padding(AppDimension.cardPadding),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .testTag("ArchivedItemName_${item.uuid}"),
                text = item.name,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textPrimary,
            )
            AppButton.Tertiary(
                modifier = Modifier.testTag("ArchivedItemRestore_${item.uuid}"),
                text = stringResource(R.string.feature_settings_archive_restore),
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
                        contentDescription = stringResource(R.string.feature_settings_more),
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
                                text = stringResource(R.string.feature_settings_archive_delete_permanently),
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
        if (item.tags.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
                contentPadding = PaddingValues(vertical = AppDimension.Space.xxs),
            ) {
                items(items = item.tags, key = { tag -> tag }) { tag ->
                    AppTagChip.Static(label = tag)
                }
            }
        }
        Text(
            text = archivedAtLabel,
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
    }
}
