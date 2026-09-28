// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Rotary enters through the Android root so focus decides which surface receives it.
 * GUARD: keep one test and composition; a second Compose test in the same sandbox hangs.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru-w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearRotaryControllerTest {

    @Test
    @DisplayName("rotary follows controller and editor focus without a recovery tap")
    fun rotaryFollowsTheVisibleSurface() = runComposeUiTest {
        val actions = mutableListOf<ControllerAction>()
        val active = fixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
            .copy(reps = 8, weightHundredthsKg = 10_000)
        var model by mutableStateOf(active)
        lateinit var backDispatcher: OnBackPressedDispatcher
        setContent {
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { backDispatcher = owner.onBackPressedDispatcher }
            WearGateHost(WearScreen.SMALL_ROUND, fontScale = LARGEST_WEAR_FONT_SCALE) {
                WearControllerScreen(state = model, onAction = actions::add)
            }
        }
        waitForIdle()

        assertControllerRotary("initial active", requireOverflow = true)
        assertTrue(actions.isEmpty(), "controller rotation must not change the workout draft")

        openEditor("reps_card")
        rotateAtRoot(ONE_EDITOR_STEP_PX)
        assertEquals(listOf<ControllerAction>(ControllerAction.SetReps(9)), actions)
        actions.clear()
        runOnIdle { backDispatcher.onBackPressed() }
        waitForIdle()
        onNodeWithTag("editor").assertDoesNotExist()
        assertControllerRotary("after back", requireOverflow = true)
        assertTrue(actions.isEmpty(), "rotary after back must return to scrolling")

        openEditor("weight_card")
        rotateAtRoot(ONE_EDITOR_STEP_PX)
        assertEquals(listOf<ControllerAction>(ControllerAction.SetWeight(10_250)), actions)
        actions.clear()
        onNodeWithTag("editor").performTouchInput { swipeRight() }
        waitForIdle()
        onNodeWithTag("editor").assertDoesNotExist()
        assertControllerRotary("after swipe", requireOverflow = true)
        assertTrue(actions.isEmpty(), "rotary after swipe must return to scrolling")

        openEditor("reps_card")
        model = fixture(SyntheticSurfaceFixtures.REFRESH_REQUIRED)
        waitForIdle()
        onNodeWithTag("editor").assertDoesNotExist()
        assertControllerRotary("after authority loss", requireOverflow = true)
        assertTrue(actions.isEmpty(), "authority loss must not leave the editor handling rotary")

        model = fixture(SyntheticSurfaceFixtures.COMPLETE).copy(
            trainingName = "Full body strength workout with supersets",
        )
        waitForIdle()
        assertControllerRotary("long workout instruction", requireOverflow = true)
        assertTrue(actions.isEmpty(), "instruction rotation must not dispatch a controller action")

        SyntheticSurfaceFixtures.allKinds().forEach { next ->
            model = next
            waitForIdle()
            assertControllerRotary("kind=${next.kind}")
            assertTrue(actions.isEmpty(), "kind=${next.kind}: rotary must only scroll")
        }
    }

    private fun ComposeUiTest.openEditor(card: String) {
        onNodeWithTag(card).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("editor_rotary").assertIsFocused()
    }

    private fun ComposeUiTest.assertControllerRotary(surface: String, requireOverflow: Boolean = false) {
        onNodeWithTag("controller_scroll").assertIsFocused()
        val range = onNodeWithTag("controller_scroll")
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        val maximum = range.maxValue()
        if (requireOverflow) {
            assertTrue(maximum > 0f, "$surface must overflow for rotary scrolling to be observable")
        }
        rotateAtRoot(-SCROLL_ROTATION_PX)
        val start = scrollPosition()
        rotateAtRoot(SCROLL_ROTATION_PX)
        val forward = scrollPosition()
        if (maximum > 0f) {
            assertTrue(forward > start, "$surface: clockwise rotary did not advance the scroll")
            rotateAtRoot(-SCROLL_ROTATION_PX)
            assertTrue(scrollPosition() < forward, "$surface: counterclockwise rotary did not scroll back")
        } else {
            assertEquals(start, forward, "$surface: content without overflow must stay in place")
        }
    }

    private fun ComposeUiTest.scrollPosition(): Float = onNodeWithTag("controller_scroll")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun ComposeUiTest.rotateAtRoot(pixels: Float) {
        // Native SOURCE_ROTARY_ENCODER input follows focus; it never calls a node callback directly.
        onRoot().performRotaryScrollInput { rotateToScrollVertically(pixels) }
        waitForIdle()
    }

    private fun fixture(id: String): WearSurfaceModel = requireNotNull(SyntheticSurfaceFixtures.find(id))

    private companion object {
        const val ONE_EDITOR_STEP_PX = 49f
        const val SCROLL_ROTATION_PX = 240f
    }
}
