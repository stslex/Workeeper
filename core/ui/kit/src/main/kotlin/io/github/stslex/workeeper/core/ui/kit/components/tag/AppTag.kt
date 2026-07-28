// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.tag

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * The mockup's `.tag` (extraction §3.2 / §4.4) — a body-rung pill on the resting-card tier,
 * distinct from [AppTagChip]'s caption-rung chips (the edit-form vocabulary).
 *
 * Resting: `--sec` fill, `--meta` label, no border. Selected (`.tag.on`, the chart's range
 * chips): `--raise` fill, `--max` label, a 1dp `hair-s` ring — mapped to [AppUi]'s
 * `borderDefault`, the lifted hair-s that clears 3:1 the way every other control outline
 * does. Geometry: padding 8×13px → 8dp/12dp, radius 10px → the 8dp rung.
 *
 * [onClick] is optional because the exercise-detail row is display-only
 * (`cursor:default` in the mockup) while the chart's range chips are interactive.
 */
@Composable
fun AppTag(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(AppDimension.Radius.small)
    Text(
        modifier = modifier
            .clip(shape)
            .background(
                color = if (selected) AppUi.colors.surfaceTier4 else AppUi.colors.surfaceTier1,
                shape = shape,
            )
            .then(
                if (selected) {
                    Modifier.border(
                        width = AppDimension.Border.small,
                        color = AppUi.colors.borderDefault,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(
                horizontal = AppDimension.Space.md,
                vertical = AppDimension.Space.sm,
            ),
        text = label,
        style = AppUi.typography.text.body,
        color = if (selected) AppUi.colors.textPrimary else AppUi.colors.textTertiary,
        maxLines = 1,
    )
}

@Preview
@Composable
private fun AppTagPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        AppTag(label = "верх")
    }
}

@Preview
@Composable
private fun AppTagSelectedPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        AppTag(label = "Всё", selected = true, onClick = {})
    }
}
