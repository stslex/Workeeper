package io.github.stslex.workeeper.core.ui.kit.components.loading

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES

@Composable
fun AppLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = AppDimension.iconMd,
    color: Color = AppUi.colors.accent,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = PREVIEW_UI_MODE_NIGHT_YES)
@Composable
private fun AppLoadingIndicatorPreview() {
    AppTheme {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.xxl),
            contentAlignment = Alignment.Center,
        ) {
            AppLoadingIndicator()
        }
    }
}
