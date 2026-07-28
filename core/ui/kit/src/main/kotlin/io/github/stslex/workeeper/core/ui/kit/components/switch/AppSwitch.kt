// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.switch

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * The v3 `.sw` (extraction §1.9): a 46×28 capsule whose ON state is `max` — the achromatic
 * accent, not a hue. Knob 22dp at a 3dp inset, travelling 18dp on the `spring` curve
 * (geometry, so overshoot is legal — spec §5); track and knob colours ride `out`.
 *
 * Two measured substitutions, both the palette's standing corrections for `hair-s`:
 * the OFF track is [io.github.stslex.workeeper.core.ui.kit.theme.AppColors.borderDefault]
 * (the mockup's `--hair-s` measures 1.12–1.52:1 — invisible for a control that IS its
 * track), and the OFF knob keeps the mockup's `meta`.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track by animateColorAsState(
        targetValue = if (checked) AppUi.colors.accent else AppUi.colors.borderDefault,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "switch-track",
    )
    val knob by animateColorAsState(
        targetValue = if (checked) AppUi.colors.onAccent else AppUi.colors.textTertiary,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "switch-knob",
    )
    val travel by animateDpAsState(
        targetValue = if (checked) KNOB_TRAVEL else 0.dp,
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.spring),
        label = "switch-travel",
    )
    Box(
        modifier = modifier
            .size(width = TRACK_WIDTH, height = TRACK_HEIGHT)
            .clip(RoundedCornerShape(TRACK_RADIUS))
            .background(track)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(KNOB_INSET),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(x = travel.roundToPx(), y = 0) }
                .size(KNOB_SIZE)
                .clip(CircleShape)
                .background(knob),
        )
    }
}

private val TRACK_WIDTH = 46.dp
private val TRACK_HEIGHT = 28.dp
private val TRACK_RADIUS = 14.dp
private val KNOB_SIZE = 22.dp
private val KNOB_INSET = 3.dp

/** `translateX(18px)`. */
private val KNOB_TRAVEL = 18.dp

@Preview
@Composable
private fun AppSwitchPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        Row(modifier = Modifier.padding(AppDimension.Space.lg)) {
            AppSwitch(checked = false, onCheckedChange = {})
            AppSwitch(checked = true, onCheckedChange = {}, modifier = Modifier.padding(start = 16.dp))
        }
    }
}
