package io.github.stslex.workeeper.core.ui.kit.components.buttons

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle
import androidx.graphics.shapes.star
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppActionButton(
    onClick: () -> Unit,
    contentIcon: ImageVector,
    modifier: Modifier = Modifier,
    selectedMode: Boolean = false,
    selectedContentIcon: ImageVector? = null,
    selectedContentDescription: String? = null,
    contentDescription: String? = null,
) {
    val shapeA = remember {
        RoundedPolygon.rectangle(
            rounding = CornerRounding(
                radius = 0.35f,
                smoothing = 1f, // Maximum smoothing for smooth edges
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
        animationSpec = tween(durationMillis = 600),
    )

    val containerColor by animateColorAsState(
        targetValue = if (selectedMode) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "Container color animation",
    )

    val contentColor by animateColorAsState(
        targetValue = if (selectedMode) {
            MaterialTheme.colorScheme.onTertiary
        } else {
            MaterialTheme.colorScheme.onPrimary
        },
        label = "Container color animation",
    )

    Box(
        modifier = modifier
            .clip(AppMorphTransformationShape(morph, animatedProgress.value))
            .background(
                color = containerColor,
            )
            .clickable(
                onClick = onClick,
            )
            .size(ButtonDefaults.LargeContainerHeight),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = selectedMode,
        ) { isSelecting ->
            Icon(
                modifier = Modifier.size(ButtonDefaults.MediumIconSize),
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
