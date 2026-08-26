// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The shared `#s-list` row skeleton: a ruled row with a clamped name, a meta line and a trailing
 * region. GUARD: [modifier] wraps row + rule, [rowModifier] only the ruled area — never swap them.
 */
@Composable
fun AppListRow(
    name: String,
    meta: String,
    nameTestTag: String,
    metaTestTag: String,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    rowModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(rowModifier)
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
                    modifier = Modifier.testTag(nameTestTag),
                    text = name,
                    style = AppUi.typography.titleMedium,
                    color = AppUi.colors.textPrimary,
                    maxLines = NAME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.testTag(metaTestTag),
                    text = meta,
                    style = AppUi.typography.mono.meta,
                    color = AppUi.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            content()
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = AppDimension.borderHairline,
                color = AppUi.colors.borderSubtle,
            )
        }
    }
}

/** The drawn 20px trailing slot: fixed width, so the text column never reflows on a toggle. */
@Composable
fun AppListRowSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.width(AppDimension.iconSm),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** Two lines, then ellipsis — the drawn `.row .name` clamp. */
private const val NAME_MAX_LINES = 2
