// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.toast

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * The toast panel only: message plus an optional uppercase mono action. Placement, auto-dismiss
 * and entrance motion belong to the host.
 */
@Composable
fun AppToast(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(AppDimension.Radius.medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = TOAST_SHADOW_ELEVATION, shape = shape)
            .clip(shape)
            .background(AppUi.colors.surfaceTier2)
            .border(
                width = AppDimension.Border.small,
                color = AppUi.colors.borderSubtle,
                shape = shape,
            )
            .padding(
                horizontal = AppDimension.Space.lg,
                vertical = AppDimension.Space.md,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = message,
            style = AppUi.typography.text.body,
            color = AppUi.colors.textPrimary,
        )
        if (actionLabel != null && onAction != null) {
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimension.Radius.small))
                    .clickable(onClick = onAction)
                    .padding(
                        horizontal = AppDimension.Space.xs,
                        vertical = AppDimension.Space.xxs,
                    ),
                text = actionLabel.uppercase(),
                style = AppUi.typography.mono.meta.copy(letterSpacing = TOAST_ACTION_TRACKING),
                color = AppUi.colors.molten.text,
            )
        }
    }
}

private val TOAST_ACTION_TRACKING = 1.sp

// The toast floats over dark content, so both themes keep a real cast shadow.
private val TOAST_SHADOW_ELEVATION = 12.dp

@Preview
@Composable
private fun AppToastPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        AppToast(
            message = "«жим лёжа» удалено из плана",
            actionLabel = "Отменить",
            onAction = {},
            modifier = Modifier.padding(AppDimension.Space.lg),
        )
    }
}
