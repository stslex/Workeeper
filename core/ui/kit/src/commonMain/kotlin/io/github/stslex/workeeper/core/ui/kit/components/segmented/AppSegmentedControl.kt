package io.github.stslex.workeeper.core.ui.kit.components.segmented

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * The mockup's `.mseg`: a track whose selected segment is a [liftedSurface] thumb.
 * GUARD: keep the track padding and draw no dividers — either one kills the lift.
 */
@Composable
fun AppSegmentedControl(
    items: ImmutableList<String>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemModifier: (Int) -> Modifier = { Modifier },
) {
    Row(
        modifier = modifier
            .height(TRACK_HEIGHT)
            .clip(SEGMENT_SHAPE)
            .background(AppUi.colors.surfaceTier1)
            .padding(TRACK_PADDING)
            // One choice among N; without this the halves read to TalkBack as unrelated boxes.
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TRACK_PADDING),
    ) {
        items.forEachIndexed { index, label ->
            val isSelected = index == selected
            Box(
                modifier = itemModifier(index)
                    .weight(1f)
                    .fillMaxHeight()
                    .liftedSurface(shape = SEGMENT_SHAPE, lifted = isSelected)
                    .clip(SEGMENT_SHAPE)
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        onSelectedChange(index)
                    }
                    .padding(horizontal = AppDimension.Space.md),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = AppUi.typography.labelMedium,
                    color = if (isSelected) {
                        AppUi.colors.textPrimary
                    } else {
                        AppUi.colors.textTertiary
                    },
                )
            }
        }
    }
}

/** `.mseg{padding:3px}` and `.mseg{gap:3px}` — 3px, one rung, one value. */
private val TRACK_PADDING = AppDimension.Space.xs

/** `.mseg button{border-radius:8px}` — Radius.small, NOT the theme's `shapes.small` (6dp). */
private val SEGMENT_SHAPE = RoundedCornerShape(AppDimension.Radius.small)

/**
 * The track's outer height: the mockup's 32dp segment plus [TRACK_PADDING] on both sides, so the
 * padding sits outside the segment rather than eating into it.
 */
private val TRACK_HEIGHT = AppDimension.heightXs + TRACK_PADDING * 2

/** One `.mseg` icon button: the glyph plus the label TalkBack reads (the SVG `title`). */
data class SegmentedIcon(
    val icon: ImageVector,
    val contentDescription: String,
)

/**
 * The `.mseg` in icon form (settings theme control): same track and lifted thumb as
 * [AppSegmentedControl], with fixed-width icon buttons instead of flexed text segments.
 */
@Composable
fun AppSegmentedIconControl(
    items: ImmutableList<SegmentedIcon>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemModifier: (Int) -> Modifier = { Modifier },
) {
    Row(
        modifier = modifier
            .height(TRACK_HEIGHT)
            .clip(SEGMENT_SHAPE)
            .background(AppUi.colors.surfaceTier1)
            .padding(TRACK_PADDING)
            // One choice among three: a radio group to TalkBack.
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(TRACK_PADDING),
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selected
            Box(
                modifier = itemModifier(index)
                    .width(MSEG_BUTTON_WIDTH)
                    .fillMaxHeight()
                    .liftedSurface(shape = SEGMENT_SHAPE, lifted = isSelected)
                    .clip(SEGMENT_SHAPE)
                    .selectable(selected = isSelected, role = Role.RadioButton) {
                        onSelectedChange(index)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(AppDimension.Icon.small),
                    imageVector = item.icon,
                    contentDescription = item.contentDescription,
                    tint = if (isSelected) {
                        AppUi.colors.accentTintedForeground
                    } else {
                        AppUi.colors.textTertiary
                    },
                )
            }
        }
    }
}

/** `.mseg button{width:38px}` — kept literal; the icon form's buttons do not flex. */
private val MSEG_BUTTON_WIDTH = 38.dp

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = PREVIEW_UI_MODE_NIGHT_YES)
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
