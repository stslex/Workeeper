// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.image_viewer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import coil3.PlatformContext
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfadeMillis
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import io.github.stslex.workeeper.feature.image_viewer.resources.Res
import io.github.stslex.workeeper.feature.image_viewer.resources.feature_image_viewer_action_remove
import io.github.stslex.workeeper.feature.image_viewer.resources.feature_image_viewer_action_replace
import io.github.stslex.workeeper.feature.image_viewer.resources.feature_image_viewer_menu
import io.github.stslex.workeeper.feature.image_viewer.ui.ImageViewerScreen
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImageViewerSceneIosTest {

    @Test
    fun resourcesBranchesAndActionsRenderAndDispatch() = runComposeUiTest {
        val state = mutableStateOf(State.create("native-image-model", editable = false))
        val actions = mutableListOf<Action>()

        setContent {
            AppTheme {
                ImageViewerScreen(
                    state = state.value,
                    consume = actions::add,
                )
            }
        }

        settleScene()
        onNodeWithTag("ImageViewerCanvas", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ImageViewerBackButton", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ImageViewerMenuButton", useUnmergedTree = true).assertDoesNotExist()

        onNodeWithTag("ImageViewerBackButton", useUnmergedTree = true).performClick()
        assertEquals(listOf<Action>(Action.Click.OnBackClick), actions)

        state.value = State.create("native-image-model", editable = true)
        settleScene()
        onNodeWithTag("ImageViewerMenuButton", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("ImageViewerMenuButton", useUnmergedTree = true).performClick()
        assertEquals(
            listOf<Action>(Action.Click.OnBackClick, Action.Click.OnMenuClick),
            actions,
        )

        state.value = state.value.copy(sheetState = State.SheetState.Menu)
        settleScene()

        val expectedCopy = runBlocking {
            listOf(
                getString(Res.string.feature_image_viewer_menu),
                getString(Res.string.feature_image_viewer_action_replace),
                getString(Res.string.feature_image_viewer_action_remove),
            )
        }
        assertEquals(
            listOf("Image actions", "Replace photo", "Remove photo"),
            expectedCopy,
        )
        expectedCopy.forEach { copy ->
            onNodeWithText(copy, useUnmergedTree = true).assertIsDisplayed()
        }

        onNodeWithTag("ImageViewerReplaceItem", useUnmergedTree = true).performClick()
        assertEquals(
            listOf<Action>(
                Action.Click.OnBackClick,
                Action.Click.OnMenuClick,
                Action.Click.OnReplaceClick,
            ),
            actions,
        )
    }

    @Test
    @OptIn(ExperimentalCoilApi::class)
    fun actualImageRequestRetainsModelContextAndCrossfade() = runComposeUiTest {
        val state = mutableStateOf(State.create("request-first", editable = false))
        val requests = mutableListOf<ImageRequest>()
        lateinit var platformContext: PlatformContext
        val observer = AsyncImagePreviewHandler { _, request ->
            requests += request
            AsyncImagePainter.State.Empty
        }
        setContent {
            platformContext = LocalPlatformContext.current
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides observer,
            ) {
                AppTheme { ImageViewerScreen(state = state.value, consume = {}) }
            }
        }
        settleScene()
        assertTrue(requests.isNotEmpty(), "The production AsyncImage must construct a request")
        assertEquals("request-first", requests.last().data)
        assertSame(platformContext, requests.last().context)
        assertEquals(200, requests.last().crossfadeMillis)

        state.value = State.create("request-second", editable = false)
        settleScene()
        assertEquals("request-second", requests.last().data)
        assertSame(platformContext, requests.last().context)
        assertEquals(200, requests.last().crossfadeMillis)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.settleScene() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
    }
}
