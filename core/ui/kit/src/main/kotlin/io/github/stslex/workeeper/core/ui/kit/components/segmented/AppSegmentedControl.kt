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
 * The mockup's `.mseg` (`pass2d.html:165`), and — via `MetricToggle` — its `.tabs .ind` too. The
 * two agree on structure and differ on magnitude (`.mseg button` 32px inside 3px of track padding,
 * `pass2d.html:166`; `.tabs button` 44px inside 5px, `pass2d.html:138`/`:135`) — the numbers here
 * are `.mseg`'s.
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
 *   exists to fix. That padding sits **outside** the segment's height, not inside it — the track
 *   grows to carry it, see [TRACK_HEIGHT].
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
            .height(TRACK_HEIGHT)
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

/**
 * The track's outer height — **derived, not transcribed**, in the shape of [AppDimension.rowHeight].
 *
 * The mockup states the *button's* height and pads the track around it: `.mseg button{height:32px}`
 * (`pass2d.html:166`) inside `.mseg{padding:3px}` (`:165`), and neither box declares a height of
 * its own, so the track measures 32 + 2 x 3 = 38px. Here the padding is already rung-snapped
 * 3px -> 4dp ([TRACK_PADDING]), so the same sum lands one rung further along:
 *
 * ```
 *   AppDimension.heightXs   32   the segment — the mockup's own number
 *   + 2 x TRACK_PADDING      8   track padding, outside the segment rather than inside it
 *   --------------------------
 *                           40   = AppDimension.heightSm
 * ```
 *
 * Written as the sum and not as `heightSm` so that the derivation survives the next reader. Fix
 * the track at `heightXs` instead and the padding is taken *from* the segments: each
 * `fillMaxHeight` box measures 32 - 2 x 4 = 24dp, so the thumb, the label's font-scale headroom
 * and the clickable hit area all lose a quarter, and nothing in the file says why.
 *
 * Declared after [TRACK_PADDING] because a top-level `val` may not read one declared below it.
 */
private val TRACK_HEIGHT = AppDimension.heightXs + TRACK_PADDING * 2

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
