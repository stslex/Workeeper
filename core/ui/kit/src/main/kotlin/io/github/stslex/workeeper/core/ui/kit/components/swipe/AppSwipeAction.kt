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
import androidx.compose.runtime.rememberUpdatedState
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
    // Veto the EndToStart swipe before it commits so the row never animates to the
    // action position. The action callback (which usually surfaces a confirmation
    // dialog) fires while the row springs back to its default state — successful
    // archive removes the item from the underlying list, so no manual reset is
    // needed when the user confirms, and Cancel naturally leaves the row unmoved.
    val onActionState = rememberUpdatedState(onAction)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { newValue ->
            if (newValue == SwipeToDismissBoxValue.EndToStart) {
                onActionState.value()
                false
            } else {
                true
            }
        },
    )
    // Apply the rounded clip on an outer Box that wraps SwipeToDismissBox. M3's
    // SwipeToDismissBox places its incoming modifier on a container that also has
    // .anchoredDraggable applied, and internally renders backgroundContent (via
    // matchParentSize) and content (via draggableAnchors) as two stacked layers.
    // When the same modifier carries clip + anchoredDraggable, the clip layer can
    // be ignored for content rendered through the draggableAnchors transform —
    // surfaceTier1 then bleeds past the rounded corners. Clipping on a clean outer
    // Box guarantees the rounded shape applies to both layers.
    Box(modifier = modifier.clip(AppUi.shapes.medium)) {
        SwipeToDismissBox(
            state = state,
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(AppUi.shapes.medium)
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
}

@Preview(name = "AtRest Light", showBackground = true)
@Preview(
    name = "AtRest Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
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

/**
 * Renders the action layer alone with the same outer clip + content backdrop the
 * runtime composable produces, so reviewers can confirm the rounded corners hold
 * when the row is mid-swipe (action panel exposed) without needing a real swipe.
 */
@Preview(name = "ActionRevealed Light", showBackground = true)
@Preview(
    name = "ActionRevealed Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppSwipeActionRevealedPreview() {
    AppTheme {
        Box(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .fillMaxWidth()
                .padding(AppDimension.Space.lg),
        ) {
            Box(modifier = Modifier.clip(AppUi.shapes.medium)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppUi.colors.status.warning)
                        .padding(
                            horizontal = AppDimension.Space.lg,
                            vertical = AppDimension.Space.lg,
                        ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
                    ) {
                        Icon(
                            modifier = Modifier.size(AppDimension.iconSm),
                            imageVector = Icons.Default.Archive,
                            contentDescription = "Archive",
                            tint = AppUi.colors.surfaceTier0,
                        )
                        Text(
                            text = "Archive",
                            style = AppUi.typography.labelSmall,
                            color = AppUi.colors.surfaceTier0,
                        )
                    }
                }
            }
        }
    }
}
