package io.github.stslex.workeeper.core.ui.kit.components.paging_item

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.ResizeMode.Companion.ScaleToBounds
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.NoiseColumn
import io.github.stslex.workeeper.core.ui.kit.model.ItemPosition
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun BasePagingColumnItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    itemKey: String,
    isSelected: Boolean,
    itemPosition: ItemPosition,
    modifier: Modifier = Modifier,
    innerPadding: Dp = AppDimension.Padding.big,
    itemPadding: Dp = AppDimension.Padding.small,
    itemCornerRadius: Dp = AppDimension.Radius.medium,
    content: @Composable ColumnScope.(contentColor: State<Color>) -> Unit,
) {
    val containerColor = animateColorAsState(
        if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = 600),
    )
    val contentColor = animateColorAsState(
        if (isSelected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 600),
    )
    val topRadius by animateDpAsState(
        targetValue = when {
            itemPosition == ItemPosition.FIRST ||
                    itemPosition == ItemPosition.SINGLE ||
                    isSelected -> itemCornerRadius

            else -> AppDimension.Radius.smallest
        },
        animationSpec = tween(durationMillis = 600),
    )
    val bottomRadius by animateDpAsState(
        targetValue = when {
            itemPosition == ItemPosition.LAST ||
                    itemPosition == ItemPosition.SINGLE ||
                    isSelected -> itemCornerRadius

            else -> AppDimension.Radius.smallest
        },
        animationSpec = tween(durationMillis = 600),
    )
    with(sharedTransitionScope) {
        NoiseColumn(
            modifier = modifier
                .sharedBounds(
                    sharedContentState = sharedTransitionScope.rememberSharedContentState(itemKey),
                    animatedVisibilityScope = animatedContentScope,
                    resizeMode = ScaleToBounds(
                        ContentScale.Inside,
                        Alignment.Center,
                    ),
                )
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(
                    bottom = if (itemPosition != ItemPosition.LAST) itemPadding else 0.dp,
                )
                .clip(
                    RoundedCornerShape(
                        topStart = topRadius,
                        topEnd = topRadius,
                        bottomEnd = bottomRadius,
                        bottomStart = bottomRadius,
                    ),
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
            paddingValues = PaddingValues(innerPadding),
            baseColor = containerColor.value,
            noiseIntensity = 0.15f,
            content = {
                content(contentColor)
            },
        )
    }
}

@SuppressLint("UnusedContentLambdaTargetStateParameter")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
@Preview
private fun ExercisePagingItemPreview() {
    AppTheme {
        AnimatedContent("") {
            SharedTransitionScope { modifier ->
                BasePagingColumnItem(
                    modifier = modifier,
                    itemKey = "itemKey",
                    isSelected = true,
                    onClick = {},
                    sharedTransitionScope = this,
                    animatedContentScope = this@AnimatedContent,
                    itemPosition = ItemPosition.SINGLE,
                    onLongClick = {},
                ) { contentColor ->
                    Text(
                        text = "Item name",
                        style = MaterialTheme.typography.titleLarge,
                        color = contentColor.value,
                    )
                    Text(
                        text = "01 Jan 2024",
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.value,
                    )
                }
            }
        }
    }
}
