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
 * The v3 `.toast` (extraction §1.9): a floating `slab` panel with a hairline ring, a body-rung
 * message in `max`, and an uppercase mono **molten** action — the only place molten appears
 * outside the record surfaces, sanctioned by the mockup's own `Отменить` button. The heavy
 * `0 14px 40px` cast shadow ships in both themes; unlike the lifted surface, the toast floats
 * over dark content too, so the dark theme keeps a real shadow rather than an edge highlight.
 *
 * Presentation (placement, the 5s auto-dismiss, entrance motion) belongs to the host — this
 * is only the panel, which is exactly what makes it goldenable.
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

/** `.toast button{letter-spacing:.08em}` at the 12.5 meta rung. */
private val TOAST_ACTION_TRACKING = 1.sp

/** `box-shadow: 0 14px 40px rgba(0,0,0,.4)` as an elevation; both themes. */
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
