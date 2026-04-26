package io.github.stslex.workeeper.core.ui.kit.components.empty

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButtonSize
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppEmptyState(
    headline: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    icon: ImageVector? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = AppDimension.Space.xxxl, horizontal = AppDimension.Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md, Alignment.CenterVertically),
    ) {
        icon?.let {
            Icon(
                modifier = Modifier.size(AppDimension.iconLg),
                imageVector = it,
                contentDescription = null,
                tint = AppUi.colors.textTertiary,
            )
        }
        Text(
            text = headline,
            style = AppUi.typography.titleMedium,
            color = AppUi.colors.textPrimary,
            textAlign = TextAlign.Center,
        )
        supportingText?.let {
            Text(
                text = it,
                style = AppUi.typography.bodyMedium,
                color = AppUi.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            AppButton.Tertiary(
                text = actionLabel,
                onClick = onAction,
                size = AppButtonSize.MEDIUM,
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppEmptyStatePreview() {
    AppTheme {
        Column(
            modifier = Modifier.background(AppUi.colors.surfaceTier0),
        ) {
            AppEmptyState(
                headline = "No exercises yet",
                supportingText = "Tap + to log your first session.",
                icon = Icons.Default.SearchOff,
                actionLabel = "Add exercise",
                onAction = {},
            )
        }
    }
}
