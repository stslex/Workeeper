package io.github.stslex.workeeper.bottom_app_bar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
internal fun WorkeeperBottomAppBar(
    selectedItem: State<BottomBarItem?>,
    modifier: Modifier = Modifier,
    onItemClick: (BottomBarItem) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    BottomAppBar(
        modifier = modifier
            .systemBarsPadding()
            .height(AppDimension.BottomNavBar.height),
        contentPadding = PaddingValues(AppDimension.Padding.medium),
    ) {
        BottomBarItem.entries.forEachIndexed { index, bottomBarItem ->
            BottomAppBarItem(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),
                titleRes = bottomBarItem.titleRes,
                iconRes = bottomBarItem.iconRes,
                selected = selectedItem.value == bottomBarItem,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BottomAppBarItem(
    titleRes: Int,
    iconRes: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    @Composable
    fun <T : Any> animationSpec(): AnimationSpec<T> = tween(
        durationMillis = AppUi.uiFeatures.defaultAnimationDuration,
        easing = FastOutSlowInEasing,
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onBackground
        } else {
            MaterialTheme.colorScheme.surface
        },
        animationSpec = animationSpec(),
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.background
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = animationSpec(),
    )

    val rotation by animateFloatAsState(
        targetValue = if (selected) 360f else 0f,
        animationSpec = spring(
            stiffness = 50f,
            dampingRatio = 0.5f,
        ),
    )

    val size by animateDpAsState(
        targetValue = if (selected) {
            AppDimension.Icon.big
        } else {
            AppDimension.Icon.medium
        },
        animationSpec = spring(
            stiffness = 50f,
            dampingRatio = 0.5f,
        ),
    )

    FilledIconButton(
        modifier = modifier
            .fillMaxHeight(),
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        enabled = true,
        shapes = IconButtonDefaults.shapes(
            shape = IconButtonDefaults.extraLargeSelectedRoundShape,
            pressedShape = IconButtonDefaults.extraSmallPressedShape,
        ),
    ) {
        Icon(
            modifier = Modifier
                .size(size)
                .rotate(rotation),
            imageVector = ImageVector.vectorResource(iconRes),
            contentDescription = stringResource(titleRes),
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
            modifier = Modifier.fillMaxWidth(),
        ) {
            WorkeeperBottomAppBar(
                onItemClick = {
                    selectedItem.value = it
                },
                selectedItem = selectedItem,
            )
        }
    }
}
