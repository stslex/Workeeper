package io.github.stslex.workeeper.core.ui.kit.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shaped = modifier
        .clip(AppUi.shapes.medium)
        .background(AppUi.colors.surfaceTier1)
    val clickable = if (onClick != null) shaped.clickable(onClick = onClick) else shaped
    Column(
        modifier = clickable.padding(AppDimension.cardPadding),
        content = content,
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppCardPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
        ) {
            AppCard(onClick = {}) {
                Text(
                    text = "AppCard with click",
                    style = AppUi.typography.titleMedium,
                    color = AppUi.colors.textPrimary,
                )
                Text(
                    text = "Surface tier 1 background, medium shape",
                    style = AppUi.typography.bodyMedium,
                    color = AppUi.colors.textSecondary,
                )
            }
        }
    }
}
