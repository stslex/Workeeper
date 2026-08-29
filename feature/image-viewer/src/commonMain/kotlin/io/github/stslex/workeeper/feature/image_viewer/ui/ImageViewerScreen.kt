// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
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
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetItem
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetLayout
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import io.github.stslex.workeeper.feature.image_viewer.resources.Res
import io.github.stslex.workeeper.feature.image_viewer.resources.feature_image_viewer_action_remove
import io.github.stslex.workeeper.feature.image_viewer.resources.feature_image_viewer_action_replace
import io.github.stslex.workeeper.feature.image_viewer.resources.feature_image_viewer_back
import io.github.stslex.workeeper.feature.image_viewer.resources.feature_image_viewer_menu
import io.github.stslex.workeeper.feature.image_viewer.ui.components.ZoomableImage
import org.jetbrains.compose.resources.stringResource

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
                            // A kit stroke mark: this bar's trailing slot carries one too.
                            imageVector = AppIcons.ChevronLeft,
                            contentDescription = stringResource(
                                Res.string.feature_image_viewer_back,
                            ),
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    // One `⋮` for the picture's two verbs, drawn only when the CALLER can honour
                    // the request — an affordance elsewhere would stage an edit that is lost.
                    if (state.editable) {
                        IconButton(
                            modifier = Modifier.testTag("ImageViewerMenuButton"),
                            onClick = { consume(Action.Click.OnMenuClick) },
                        ) {
                            Icon(
                                modifier = Modifier.size(AppDimension.iconSm),
                                imageVector = AppIcons.MoreVertical,
                                contentDescription = stringResource(
                                    Res.string.feature_image_viewer_menu,
                                ),
                                tint = Color.White,
                            )
                        }
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

    // A SHEET, not a dialog, per §26; text items because the kit ships no camera and no bin glyph.
    when (state.sheetState) {
        State.SheetState.Hidden -> Unit

        State.SheetState.Menu -> AppBottomSheet(
            onDismiss = { consume(Action.Click.OnSheetDismiss) },
        ) {
            AppSheetLayout(title = stringResource(Res.string.feature_image_viewer_menu)) {
                AppSheetItem(
                    modifier = Modifier.testTag("ImageViewerReplaceItem"),
                    title = stringResource(Res.string.feature_image_viewer_action_replace),
                    onClick = { consume(Action.Click.OnReplaceClick) },
                )
                AppSheetItem(
                    modifier = Modifier.testTag("ImageViewerRemoveItem"),
                    title = stringResource(Res.string.feature_image_viewer_action_remove),
                    onClick = { consume(Action.Click.OnRemoveClick) },
                    destructive = true,
                )
            }
        }
    }
}
