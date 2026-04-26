package io.github.stslex.workeeper.core.ui.kit.components.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val baseModifier = modifier
        .fillMaxWidth()
        .clip(AppUi.shapes.medium)
        .background(AppUi.colors.surfaceTier1)
    val clickable = if (onClick != null) baseModifier.clickable(onClick = onClick) else baseModifier
    Row(
        modifier = clickable
            .heightIn(min = AppDimension.heightSm)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        leadingContent?.let { Box { it() } }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
        ) {
            Text(
                text = headline,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textPrimary,
            )
            supportingText?.let {
                Text(
                    text = it,
                    style = AppUi.typography.bodySmall,
                    color = AppUi.colors.textTertiary,
                )
            }
        }
        trailingContent?.let { Box { it() } }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppListItemPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            AppListItem(headline = "Bench press", supportingText = "5 sets · 80 kg")
            AppListItem(
                headline = "Squat",
                supportingText = "Logged today",
                onClick = {},
            )
        }
    }
}
