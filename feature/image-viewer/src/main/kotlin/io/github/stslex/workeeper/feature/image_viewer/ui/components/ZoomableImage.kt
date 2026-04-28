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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.image_viewer.R
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State.Companion.MAX_SCALE
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State.Companion.MIN_SCALE

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
    // animate so double-tap toggle and scale changes ease in instead of snapping.
    val animatedScale by animateFloatAsState(scale, label = "scale")
    val animatedOffsetX by animateFloatAsState(offsetX, label = "offsetX")
    val animatedOffsetY by animateFloatAsState(offsetY, label = "offsetY")

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var loadFailed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .testTag("ImageViewerCanvas")
            .onSizeChanged { viewportSize = it }
            .pointerInput(viewportSize) {
                // Compose runs only the first matching detector inside one pointerInput,
                // so transform-gestures and tap-gestures must live in separate blocks.
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    val newOffsetX: Float
                    val newOffsetY: Float
                    if (newScale <= MIN_SCALE) {
                        newOffsetX = 0f
                        newOffsetY = 0f
                    } else {
                        // Bound = ((scale - 1) * viewportSize) / 2 keeps image edges from
                        // pulling beyond the viewport on either side.
                        val maxOffsetX = (viewportSize.width * (newScale - 1f)) / 2f
                        val maxOffsetY = (viewportSize.height * (newScale - 1f)) / 2f
                        newOffsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                        newOffsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                    }
                    onTransform(newScale, newOffsetX, newOffsetY)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onDoubleTap() })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (loadFailed) {
            UnavailableState()
        } else {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(model)
                    .crossfade(true)
                    .build(),
                contentDescription = stringResource(
                    R.string.feature_image_viewer_content_description,
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
            text = stringResource(R.string.feature_image_viewer_unavailable),
            style = AppUi.typography.bodyMedium,
            color = Color.White,
        )
    }
}
