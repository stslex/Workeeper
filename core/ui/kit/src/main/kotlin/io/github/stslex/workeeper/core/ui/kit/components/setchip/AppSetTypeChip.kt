package io.github.stslex.workeeper.core.ui.kit.components.setchip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppSetTypeChip(
    type: SetType,
    modifier: Modifier = Modifier,
) {
    val palette = AppUi.colors.setType
    val (background, foreground) = when (type) {
        SetType.WARMUP -> palette.warmupBackground to palette.warmupForeground
        SetType.WORK -> palette.workBackground to palette.workForeground
        SetType.FAIL -> palette.failureBackground to palette.failureForeground
        SetType.DROP -> palette.dropBackground to palette.dropForeground
    }
    val label = when (type) {
        SetType.WARMUP -> "W"
        SetType.WORK -> "·"
        SetType.FAIL -> "F"
        SetType.DROP -> "D"
    }
    Row(
        modifier = modifier
            .height(18.dp)
            .clip(AppUi.shapes.small)
            .background(background)
            .padding(horizontal = AppDimension.Space.sm, vertical = AppDimension.Space.xxs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = foreground,
            style = AppUi.typography.labelSmall.copy(letterSpacing = 0.4.sp),
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppSetTypeChipPreview() {
    AppTheme {
        Row(
            modifier = Modifier
                .background(Color.Transparent)
                .padding(AppDimension.Space.lg),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            SetType.entries.forEach { AppSetTypeChip(it) }
        }
    }
}
