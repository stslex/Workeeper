package io.github.stslex.workeeper.core.ui.kit.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CardWithAnimatedBorder(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    borderColors: List<Color> = emptyList(),
    isAnimated: Boolean = true,
    isFullBackground: Boolean = false,
    isEnableBrush: Boolean = false,
    disableBorderColor: Color = MaterialTheme.colorScheme.onSurface,
    cornerRadius: Dp = AppDimension.Radius.medium,
    borderSize: Dp = 1.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    backgroundEnableColor: Color = backgroundColor,
    content: @Composable () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition()
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
    )

    val brush = if (borderColors.isNotEmpty()) {
        Brush.sweepGradient(borderColors)
    } else {
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF595A),
                Color(0xFFFFC766),
                Color(0xFF35A07F),
                Color(0xFF35A07F),
                Color(0xFFFFC766),
                Color(0xFFFF595A),
            ),
        )
    }

    val brushBackground = Brush.linearGradient(
        0f to Color(0xFF35A07F).copy(alpha = 0.5f),
        0.5f to Color(0xFFFFC766).copy(alpha = 0.5f),
        1f to Color(0xFFFF595A).copy(alpha = 0.5f),
    )

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(cornerRadius - borderSize),
        color = backgroundColor,
    ) {
        Surface(
            modifier = Modifier
                .clipToBounds()
                .fillMaxWidth()
                .padding(borderSize)
                .drawWithContent {
                    if (isAnimated) {
                        rotate(angle) {
                            drawCircle(
                                brush = brush,
                                radius = size.width,
                                blendMode = BlendMode.SrcIn,
                            )
                        }
                    } else if (isEnableBrush) {
                        drawCircle(
                            brush = brush,
                            radius = size.width,
                            blendMode = BlendMode.SrcIn,
                        )
                    } else {
                        drawCircle(
                            color = disableBorderColor,
                            radius = size.width,
                            center = center,
                        )
                    }
                    drawContent()
                },
            shape = RoundedCornerShape(cornerRadius - borderSize),
            color = backgroundColor,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        if (isFullBackground) {
                            drawCircle(
                                brush = brushBackground,
                                radius = size.width,
                                center = center,
                            )
                        } else if (isAnimated) {
                            drawCircle(
                                color = backgroundEnableColor,
                                radius = size.width,
                                center = center,
                            )
                        } else {
                            drawCircle(
                                color = backgroundColor,
                                radius = size.width,
                                center = center,
                            )
                        }
                        drawContent()
                    },
            ) {
                content()
            }
        }
    }
}
