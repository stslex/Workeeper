// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.topbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The v3 `.topbar` (extraction §1.2): a 48dp-rung row of hanging icon buttons with an
 * optional title, sitting directly on `surfaceTier0` — no surface of its own, no elevation.
 *
 * Geometry, derived rather than transcribed (§0.2):
 * - `.icon-btn` is 44×44 in the mockup → the 48dp rung.
 * - `.lead{margin-left:-12px}` hangs the touch target into the gutter so the **glyph**, not
 *   the button, aligns with the 16dp content edge. With a 21dp glyph centred in 48dp the
 *   inset is 13.5dp, so the row's own edge padding is `xxs` (2dp): glyph lands at 15.5dp —
 *   within half a dp of the gutter, on-ladder. The mockup's arithmetic is the same trick at
 *   its own sizes (20px gutter − 12px hang + 11.5px inset = 19.5px).
 * - `min-height:60px` resolves as 48dp button + 2×4dp vertical padding = 56dp (`heightLg`).
 *
 * The session screen passes no [title]; per §1.2 its centre is an empty spacer. Screens that
 * do title themselves (past-session, exercise, settings) get the `.topbar h1` treatment —
 * `text.section` at heading weight; the h1's declared −.015em tracking is deliberately not
 * reproduced (spec B4: `text.title` is the only tracked rung).
 */
@Composable
fun AppTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    navigation: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = AppDimension.heightLg)
            .padding(
                horizontal = AppDimension.Space.xxs,
                vertical = AppDimension.Space.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        navigation()
        if (title != null) {
            Text(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = AppDimension.Space.xs),
                text = title,
                style = AppUi.typography.text.section,
                color = AppUi.colors.textPrimary,
                maxLines = 1,
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        actions()
    }
}

/**
 * The v3 `.icon-btn` (extraction §1.2): 48dp touch target, 21dp stroked glyph, resting tint
 * `textTertiary` (`--meta`). The mockup's hover state — `background:--sec; color:--max` —
 * maps onto the **pressed** state here, animated on the `fast` token like the CSS's 140ms
 * transitions; there is no hover on touch hardware, and a Material ripple would be a
 * different treatment than the one drawn.
 *
 * Radius: the mockup draws 12px, a value the `Radius` ladder does not have (extraction §0.5
 * reports the missing rung). Rounded **down** to `Radius.small` (8dp): every small control
 * on this screen (`.ordchip` 8px, `.mini` 9px, `.tchip` 9px) lands on the 8dp rung, and an
 * inner radius should stay tighter than the 16dp cards it sits amongst.
 */
@Composable
fun AppIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyphSize: Dp = TOPBAR_GLYPH_SIZE,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (isPressed) AppUi.colors.surfaceTier1 else Color.Transparent,
        animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.out),
        label = "icon-btn-bg",
    )
    val tint by animateColorAsState(
        targetValue = if (isPressed) AppUi.colors.textPrimary else AppUi.colors.textTertiary,
        animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.out),
        label = "icon-btn-tint",
    )
    Box(
        modifier = modifier
            .size(AppDimension.iconXl)
            .clip(RoundedCornerShape(AppDimension.Radius.small))
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(glyphSize),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

/** The mockup's 21×21 top-bar glyph — a component treatment, kept literal like stroke widths. */
private val TOPBAR_GLYPH_SIZE: Dp = 21.dp
