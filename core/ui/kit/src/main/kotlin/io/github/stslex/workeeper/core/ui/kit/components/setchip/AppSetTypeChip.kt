package io.github.stslex.workeeper.core.ui.kit.components.setchip

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The set row's trailing `.tchip` (extraction §1.6): min-width 34, height 32, an 8dp radius
 * (mockup 9px), mono label. Shares its geometry with `PersonalRecordTag` — the two occupy the
 * same trailing slot and are never shown together.
 *
 * The mockup draws exactly one type — the work set's `·` on a transparent chip with a
 * `hair-s` ring in `dim` — so **WORK is the mockup's treatment verbatim** (ring in
 * `borderDefault`, the palette's measured stand-in for `hair-s` on an operable control).
 * WARMUP / FAIL / DROP are this app's own mechanic with no drawn counterpart; they keep their
 * `SetTypeColors` pairs ("neutral by decision" — see the palette KDoc) inside the new
 * geometry, so the quiet chip is the common case and a non-work type still reads at a glance.
 */
@Composable
fun AppSetTypeChip(
    type: SetType,
    modifier: Modifier = Modifier,
) {
    val palette = AppUi.colors.setType
    val (background, foreground) = when (type) {
        SetType.WARMUP -> palette.warmupBackground to palette.warmupForeground
        SetType.WORK -> Color.Transparent to AppUi.colors.textDim
        SetType.FAIL -> palette.failureBackground to palette.failureForeground
        SetType.DROP -> palette.dropBackground to palette.dropForeground
    }
    val border = if (type == SetType.WORK) AppUi.colors.borderDefault else foreground
    val label = when (type) {
        SetType.WARMUP -> "W"
        SetType.WORK -> "·"
        SetType.FAIL -> "F"
        SetType.DROP -> "D"
    }
    val shape = RoundedCornerShape(AppDimension.Radius.small)
    Row(
        modifier = modifier
            .border(AppDimension.Border.small, border, shape)
            .height(CHIP_HEIGHT)
            .widthIn(min = CHIP_MIN_WIDTH)
            .clip(shape)
            .background(background)
            .padding(horizontal = AppDimension.Space.xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = foreground,
            style = AppUi.typography.mono.meta,
        )
    }
}

/** Trailing-chip geometry, shared with `PersonalRecordTag` (extraction §1.6). */
internal val CHIP_HEIGHT = 32.dp

/** See [CHIP_HEIGHT]. */
internal val CHIP_MIN_WIDTH = 34.dp

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppSetTypeChipPreview() {
    AppTheme {
        Row(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            SetType.entries.forEach { AppSetTypeChip(it) }
        }
    }
}
