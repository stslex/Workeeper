// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class)

package io.github.stslex.workeeper.feature.image_viewer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
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

    private fun androidx.compose.ui.test.ComposeUiTest.settleScene() {
        mainClock.autoAdvance = false
        mainClock.advanceTimeBy(1_000)
        mainClock.autoAdvance = true
        waitForIdle()
    }
}
