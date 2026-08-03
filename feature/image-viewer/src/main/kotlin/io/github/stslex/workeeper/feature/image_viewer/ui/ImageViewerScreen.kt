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
import androidx.compose.ui.res.stringResource
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppBottomSheet
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetItem
import io.github.stslex.workeeper.core.ui.kit.components.sheet.AppSheetLayout
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
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
                            // B34's fourth production site. Swapped HERE rather than left to
                            // B33(a)'s PR because this commit puts a kit stroke mark in the
                            // trailing slot, and a bar carrying one stroke mark beside one filled
                            // Material import is the mismatch B33 names — visible in one frame.
                            imageVector = AppIcons.ChevronLeft,
                            contentDescription = stringResource(
                                R.string.feature_image_viewer_back,
                            ),
                            tint = Color.White,
                        )
                    }
                },
                actions = {
                    // The picture's two verbs live where the picture is (§26, "The image moves
                    // into the pushed top bar"). One `⋮` rather than two marks, because the kit
                    // ships no camera and no bin and inventing either would settle two of
                    // B33(b)'s open glyph questions by writing them.
                    IconButton(
                        modifier = Modifier.testTag("ImageViewerMenuButton"),
                        onClick = { consume(Action.Click.OnMenuClick) },
                    ) {
                        Icon(
                            modifier = Modifier.size(AppDimension.iconSm),
                            imageVector = AppIcons.MoreVertical,
                            contentDescription = stringResource(R.string.feature_image_viewer_menu),
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

    // A SHEET, not a dialog — §26 "Every modal on the three editors is a SHEET" rules the editors
    // and this is the surface their image row moved to, so drawing a dialog here would put the
    // one modal in the flow that is not a sheet at the end of it. Text items, no glyphs: the kit
    // ships no camera and no bin, and inventing either would settle two of B33(b)'s open
    // questions by writing them.
    when (state.sheetState) {
        State.SheetState.Hidden -> Unit

        State.SheetState.Menu -> AppBottomSheet(
            onDismiss = { consume(Action.Click.OnSheetDismiss) },
        ) {
            AppSheetLayout(title = stringResource(R.string.feature_image_viewer_menu)) {
                AppSheetItem(
                    modifier = Modifier.testTag("ImageViewerReplaceItem"),
                    title = stringResource(R.string.feature_image_viewer_action_replace),
                    onClick = { consume(Action.Click.OnReplaceClick) },
                )
                AppSheetItem(
                    modifier = Modifier.testTag("ImageViewerRemoveItem"),
                    title = stringResource(R.string.feature_image_viewer_action_remove),
                    onClick = { consume(Action.Click.OnRemoveClick) },
                    destructive = true,
                )
            }
        }
    }
}
