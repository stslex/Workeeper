package io.github.stslex.workeeper.core.ui.kit.components.swipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListItem
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSwipeAction(
    actionIcon: ImageVector,
    actionLabel: String,
    actionTint: Color,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onAction()
            state.reset()
        }
    }
    SwipeToDismissBox(
        state = state,
        modifier = modifier.clip(AppUi.shapes.medium),
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(actionTint)
                    .padding(horizontal = AppDimension.Space.lg),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
                ) {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconSm),
                        imageVector = actionIcon,
                        contentDescription = actionLabel,
                        tint = AppUi.colors.surfaceTier0,
                    )
                    Text(
                        text = actionLabel,
                        style = AppUi.typography.labelSmall,
                        color = AppUi.colors.surfaceTier0,
                    )
                }
            }
        },
        content = { content() },
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppSwipeActionPreview() {
    AppTheme {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .fillMaxWidth()
                .padding(AppDimension.Space.lg),
        ) {
            AppSwipeAction(
                actionIcon = Icons.Default.Archive,
                actionLabel = "Archive",
                actionTint = AppUi.colors.status.warning,
                onAction = {},
            ) {
                AppListItem(headline = "Bench press", supportingText = "Tap to expand")
            }
        }
    }
}
