package io.github.stslex.workeeper.bottom_app_bar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@Composable
internal fun WorkeeperBottomAppBar(
    selectedItem: State<BottomBarItem?>,
    modifier: Modifier = Modifier,
    onItemClick: (BottomBarItem) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    BottomAppBar(
        modifier = modifier,
        contentPadding = PaddingValues(AppDimension.Padding.medium)
    ) {
        BottomBarItem.entries.forEachIndexed { index, bottomBarItem ->
            BottomAppBarItem(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                titleRes = bottomBarItem.titleRes,
                selected = selectedItem.value == bottomBarItem
            ) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onItemClick(bottomBarItem)
            }
            if (index != BottomBarItem.entries.lastIndex) {
                Spacer(Modifier.width(AppDimension.Padding.medium))
            }
        }
    }
}

private const val ANIMATION_DURATION_MS = 300

@Composable
private fun BottomAppBarItem(
    titleRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    @Composable
    fun <T : Any> animationSpec(): AnimationSpec<T> = tween(
        durationMillis = ANIMATION_DURATION_MS,
        easing = FastOutSlowInEasing
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = animationSpec()
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onTertiary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = animationSpec()
    )
    val paddingSize by animateDpAsState(
        targetValue = if (selected) 0.dp else AppDimension.Padding.medium,
        animationSpec = animationSpec()
    )
    val textSizePercent by animateFloatAsState(
        targetValue = if (selected) 1.0f else 0.9f,
        animationSpec = animationSpec()
    )

    val elevation by animateDpAsState(
        targetValue = if (selected) 0.dp else AppDimension.Elevation.medium,
        animationSpec = animationSpec()
    )

    val borderStrokeWidth by animateDpAsState(
        targetValue = if (selected) AppDimension.Border.small else 0.dp,
        animationSpec = animationSpec()
    )

    val borderStrokeColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.outline
        } else {
            Color.Transparent
        },
        animationSpec = animationSpec()
    )

    val borderStroke = if (selected) BorderStroke(
        width = borderStrokeWidth,
        color = borderStrokeColor
    ) else {
        null
    }

    FilledTonalButton(
        modifier = modifier.padding(vertical = paddingSize),
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        border = borderStroke,
        elevation = ButtonDefaults.filledTonalButtonElevation(
            defaultElevation = elevation
        )
    ) {
        Text(
            text = stringResource(titleRes),
            fontSize = MaterialTheme.typography.labelLarge.fontSize * textSizePercent,
            maxLines = 1
        )
    }
}

@Composable
@Preview
private fun WorkeeperBottomAppBarPreview() {
    AppTheme {
        val selectedItem = remember {
            mutableStateOf(BottomBarItem.EXERCISES)
        }

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            WorkeeperBottomAppBar(
                onItemClick = {
                    selectedItem.value = it
                },
                selectedItem = selectedItem
            )
        }
    }
}