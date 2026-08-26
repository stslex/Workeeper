// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.switch.AppSwitch
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * A sheet menu row. Lighter geometry than [AppSheetMenuContent], whose taller section rows
 * belong to list screens.
 */
@Composable
fun AppSheetItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    destructive: Boolean = false,
) {
    val tint = if (destructive) AppUi.colors.status.error else AppUi.colors.textSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimension.Radius.small))
            .clickable(onClick = onClick)
            .padding(
                horizontal = AppDimension.Space.xs,
                vertical = AppDimension.Space.lg,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        icon?.let {
            Icon(
                modifier = Modifier.size(SHEET_ITEM_GLYPH),
                imageVector = it,
                contentDescription = null,
                tint = tint,
            )
        }
        Text(
            text = title,
            style = AppUi.typography.text.body,
            color = tint,
        )
    }
}

/** A titled switch row: title over a supporting line, switch trailing. */
@Composable
fun AppSheetSwitchRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppDimension.Radius.small))
            .clickable { onCheckedChange(!checked) }
            .padding(
                horizontal = AppDimension.Space.xs,
                vertical = AppDimension.Space.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.lg),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppUi.typography.text.body,
                color = AppUi.colors.textPrimary,
            )
            Text(
                modifier = Modifier.padding(top = AppDimension.Space.xxs),
                text = supporting,
                style = AppUi.typography.mono.meta,
                color = AppUi.colors.textTertiary,
            )
        }
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun AppSheetSeparator(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(vertical = AppDimension.Space.xs),
        thickness = AppDimension.Border.small,
        color = AppUi.colors.borderSubtle,
    )
}

private val SHEET_ITEM_GLYPH = 19.dp
