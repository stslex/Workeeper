// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State.Companion.MAX_SCALE
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State.Companion.MIN_SCALE
import io.github.stslex.workeeper.feature.image_viewer.resources.Res
import io.github.stslex.workeeper.feature.image_viewer.resources.feature_image_viewer_content_description
import io.github.stslex.workeeper.feature.image_viewer.resources.feature_image_viewer_unavailable
import org.jetbrains.compose.resources.stringResource

private val UNAVAILABLE_ICON_SIZE = 48.dp

@Composable
internal fun ZoomableImage(
    model: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onTransform: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val animatedScale by animateFloatAsState(scale, label = "scale")
    val animatedOffsetX by animateFloatAsState(offsetX, label = "offsetX")
    val animatedOffsetY by animateFloatAsState(offsetY, label = "offsetY")

    // GUARD: pointerInput's coroutine captures launch-time values, so every value the gesture
    // lambda reads must go through rememberUpdatedState or pinch accumulation breaks.
    val currentScale by rememberUpdatedState(scale)
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)
    val currentOnTransform by rememberUpdatedState(onTransform)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var loadFailed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag("ImageViewerCanvas")
            .onSizeChanged { viewportSize = it }
            .pointerInput(viewportSize) {
                // GUARD: one pointerInput runs only its first detector; taps need their own block.
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (currentScale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val newOffsetX: Float
                    val newOffsetY: Float
                    if (newScale <= MIN_SCALE) {
                        newOffsetX = 0f
                        newOffsetY = 0f
                    } else {
                        // Bound keeps the image edges from pulling inside the viewport.
                        val maxOffsetX = (viewportSize.width * (newScale - 1f)) / 2f
                        val maxOffsetY = (viewportSize.height * (newScale - 1f)) / 2f
                        newOffsetX = (currentOffsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        newOffsetY = (currentOffsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    }
                    currentOnTransform(newScale, newOffsetX, newOffsetY)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { currentOnDoubleTap() })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loadFailed) {
            UnavailableState()
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(
                    Res.string.feature_image_viewer_content_description,
                ),
                contentScale = ContentScale.Fit,
                onState = { state ->
                    loadFailed = state is AsyncImagePainter.State.Error
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("ImageViewerImage")
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                        translationX = animatedOffsetX
                        translationY = animatedOffsetY
                    },
            )
        }
    }
}

@Composable
private fun UnavailableState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ImageViewerUnavailable")
            .padding(AppDimension.Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            modifier = Modifier.size(UNAVAILABLE_ICON_SIZE),
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.7f),
        )
        Text(
            modifier = Modifier.padding(top = AppDimension.Space.sm),
            text = stringResource(Res.string.feature_image_viewer_unavailable),
            style = AppUi.typography.bodyMedium,
            color = Color.White,
        )
    }
}
