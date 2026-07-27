package io.github.stslex.workeeper.core.ui.kit.components.segmented

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The mockup's `.mseg` (`pass2d.html:166`), and — via `MetricToggle` — its `.tabs .ind` too.
 *
 * The selected segment is a **lifted surface**: `--slab` plus `--slabtop`
 * (`.mseg button.on{background:var(--slab);color:var(--max);box-shadow:var(--slabtop)}`). It is
 * one of the four things in the mockups that carry that signature, and it reads it through
 * [liftedSurface] rather than reimplementing it — see that modifier for why the mechanism
 * inverts by theme.
 *
 * Two consequences of the lift, both of them the mockup's own geometry rather than taste:
 *
 * - The track carries `padding: 3px` (`.mseg`), so the thumb is inset. Without it the thumb is
 *   flush with the track's clipped edge and the light theme's cast shadow has nowhere to fall —
 *   the lift would be invisible in exactly one theme, which is the failure mode this whole role
 *   exists to fix.
 * - The segments are separated by `gap: 3px` and **no rule**. The hairline dividers this
 *   component used to draw are siblings of the thumb, so a lifted thumb would have a seam
 *   running down its edge. The mockup draws air instead.
 */
@Composable
fun AppSegmentedControl(
    items: ImmutableList<String>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(AppDimension.heightXs)
            .clip(AppUi.shapes.small)
            .background(AppUi.colors.surfaceTier1)
            .padding(TRACK_PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TRACK_PADDING),
    ) {
        items.forEachIndexed { index, label ->
            val isSelected = index == selected
            val foreground = if (isSelected) AppUi.colors.accentTintedForeground else AppUi.colors.textTertiary
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .liftedSurface(shape = AppUi.shapes.small, lifted = isSelected)
                    .clip(AppUi.shapes.small)
                    .clickable { onSelectedChange(index) }
                    .padding(horizontal = AppDimension.Space.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = AppUi.typography.labelMedium,
                    color = foreground,
                )
            }
        }
    }
}

/** `.mseg{padding:3px}` and `.mseg{gap:3px}` — 3px, one rung, one value. */
private val TRACK_PADDING = AppDimension.Space.xs

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppSegmentedControlPreview() {
    AppTheme {
        var selected by remember { mutableIntStateOf(0) }
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            AppSegmentedControl(
                items = persistentListOf("Trainings", "Exercises"),
                selected = selected,
                onSelectedChange = { selected = it },
            )
        }
    }
}
