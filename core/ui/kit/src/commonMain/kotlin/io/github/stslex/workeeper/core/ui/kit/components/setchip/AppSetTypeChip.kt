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
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_type_mark_drop
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_type_mark_failure
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_type_mark_warmup
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_type_mark_work
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES
import org.jetbrains.compose.resources.stringResource

/**
 * The set row's trailing `.tchip`: min-width 34, height 32, 8dp radius, mono label. Shares its
 * slot geometry with `PersonalRecordTag`, and the two are never shown together.
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
    // GUARD: all four marks are resources and none may become a literal - a literal draws `W`
    // over разминка. See documentation/feature-specs/v3-redesign-spec.md §26.
    val label = stringResource(
        when (type) {
            SetType.WARMUP -> Res.string.core_ui_kit_set_type_mark_warmup
            SetType.WORK -> Res.string.core_ui_kit_set_type_mark_work
            SetType.FAIL -> Res.string.core_ui_kit_set_type_mark_failure
            SetType.DROP -> Res.string.core_ui_kit_set_type_mark_drop
        },
    )
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
    uiMode = PREVIEW_UI_MODE_NIGHT_YES,
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
