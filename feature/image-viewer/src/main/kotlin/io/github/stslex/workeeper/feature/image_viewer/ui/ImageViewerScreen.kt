// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.feature.image_viewer.R
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import io.github.stslex.workeeper.feature.image_viewer.ui.components.ZoomableImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImageViewerScreen(
    state: State,
    consume: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("ImageViewerScreen"),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(
                        modifier = Modifier.testTag("ImageViewerBackButton"),
                        onClick = { consume(Action.Click.OnBackClick) },
                    ) {
                        Icon(
                            modifier = Modifier.size(AppDimension.iconSm),
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(
                                R.string.feature_image_viewer_back,
                            ),
                            tint = Color.White,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
                modifier = Modifier.systemBarsPadding(),
            )
        },
        containerColor = Color.Black,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ZoomableImage(
                modifier = Modifier.fillMaxSize(),
                model = state.model,
                scale = state.scale,
                offsetX = state.offsetX,
                offsetY = state.offsetY,
                onTransform = { newScale, newOffsetX, newOffsetY ->
                    consume(
                        Action.Common.TransformChange(
                            scale = newScale,
                            offsetX = newOffsetX,
                            offsetY = newOffsetY,
                        ),
                    )
                },
                onDoubleTap = { consume(Action.Click.OnDoubleTap) },
            )
        }
    }
}
