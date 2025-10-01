package io.github.stslex.workeeper.core.ui.kit.components.buttons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi.uiFeatures

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun AppActionButton(
    onClick: () -> Unit,
    contentIcon: ImageVector,
    modifier: Modifier = Modifier,
    selectedMode: Boolean = false,
    selectedContentIcon: ImageVector? = null,
    selectedContentDescription: String? = null,
    contentDescription: String? = null,
    hazeState: HazeState = rememberHazeState(blurEnabled = uiFeatures.enableBlur),
) {
    val shapeA = remember {
        RoundedPolygon.rectangle(
            rounding = CornerRounding(
                radius = 0.35f,
                smoothing = 1f,
            ),
        )
    }

    val shapeB = remember {
        RoundedPolygon.star(
            6,
            rounding = CornerRounding(
                radius = 0.35f,
                smoothing = 1f,
            ),
        )
    }

    val morph = remember {
        Morph(shapeA, shapeB)
    }

    val animatedProgress = animateFloatAsState(
        targetValue = if (selectedMode) 1f else 0f,
        label = "progress",
        animationSpec = tween(uiFeatures.defaultAnimationDuration),
    )

    val containerColor by animateColorAsState(
        targetValue = if (selectedMode) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "Container color animation",
        animationSpec = tween(uiFeatures.defaultAnimationDuration),
    )

    val contentColor by animateColorAsState(
        targetValue = if (selectedMode) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        label = "Container color animation",
        animationSpec = tween(uiFeatures.defaultAnimationDuration),
    )

    val borderColor by animateColorAsState(
        targetValue = if (selectedMode) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            Color.Transparent
        },
        label = "Border color animation",
        animationSpec = tween(uiFeatures.defaultAnimationDuration),
    )

    val borderWidth by animateDpAsState(
        targetValue = if (selectedMode) {
            AppDimension.Border.medium
        } else {
            0.dp
        },
        label = "Border width animation",
        animationSpec = tween(uiFeatures.defaultAnimationDuration),
    )

    Box(
        modifier = modifier
            .clip(AppMorphTransformationShape(morph, animatedProgress.value))
            .clickable(
                onClick = onClick,
            )
            .size(AppDimension.Button.big)
            .hazeEffect(state = hazeState, style = HazeMaterials.thin())
            .background(
                color = containerColor,
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = AppMorphTransformationShape(morph, animatedProgress.value),
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = selectedMode,
        ) { isSelecting ->
            Icon(
                modifier = Modifier.size(AppDimension.Icon.medium),
                imageVector = if (isSelecting && selectedContentIcon != null) {
                    selectedContentIcon
                } else {
                    contentIcon
                },
                tint = contentColor,
                contentDescription = if (isSelecting) {
                    selectedContentDescription
                } else {
                    contentDescription
                },
            )
        }
    }
}

@Preview(device = "spec:width=1080px,height=2340px,dpi=440")
@Composable
private fun AppActionButtonPreviewUnSelected() {
    AppTheme {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(AppDimension.Padding.large),
        ) {
            AppActionButton(
                contentIcon = Icons.Default.Add,
                selectedContentIcon = Icons.Default.Delete,
                selectedMode = false,
                onClick = {},
            )
        }
    }
}

@Preview
@Composable
private fun AppActionButtonPreviewSelected() {
    AppTheme {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .padding(AppDimension.Padding.large),
        ) {
            AppActionButton(
                contentIcon = Icons.Default.Add,
                selectedContentIcon = Icons.Default.Delete,
                selectedMode = true,
                onClick = {},
            )
        }
    }
}
