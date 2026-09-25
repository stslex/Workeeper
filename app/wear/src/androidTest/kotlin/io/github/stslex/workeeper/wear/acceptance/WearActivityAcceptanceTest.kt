// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.acceptance

import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.wear.ui.CompletionUnavailableReason
import io.github.stslex.workeeper.wear.ui.SyntheticSurfaceFixtures
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Regression
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class WearActivityAcceptanceTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @Test
    fun fixtureRendersThroughRealActivity() {
        WearAcceptanceSession(compose).run("fixtureRendersThroughRealActivity") {
            val model = expectedModel()
            capture("first-view")
            assertFirstView(model)
            assertDetails(model)
        }
    }

    @Test
    fun rotaryEditorsAndPressUseRealActivity() {
        WearAcceptanceSession(compose).run("rotaryEditorsAndPressUseRealActivity") {
            assertEquals(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY, fixture)
            expectedModel()
            capture("first-view")
            assertControllerRotary("active", requireOverflow = true)
            exerciseUpperBoundsAndDismissal()
            exerciseLowerBoundsAndExpiry()
            listOf(SyntheticSurfaceFixtures.RETRYABLE, SyntheticSurfaceFixtures.COMPLETE).forEach { id ->
                dispatchScenario("fixture:$id")
                assertControllerRotary(id)
                capture("rotary-$id")
            }
            assertPressBehavior()
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun WearAcceptanceSession.exerciseUpperBoundsAndDismissal() {
    openEditor("reps_card")
    rule.onNodeWithTag("editor_increase").assertIsNotEnabled()
    rotate(-EDITOR_STEP_PX)
    assertEquals(998, runtimeModel().reps)
    rotate(EDITOR_STEP_PX)
    assertEquals(999, runtimeModel().reps)
    rule.onNodeWithTag("editor_increase").performTouchInput { click() }
    assertEquals("Upper reps clamp", 999, runtimeModel().reps)
    capture("reps-upper")
    pressBack()
    assertControllerRotary("after-reps-back", requireOverflow = true)

    openEditor("weight_card")
    rule.onNodeWithTag("editor_increase").assertIsNotEnabled()
    rotate(-EDITOR_STEP_PX)
    assertEquals(99_749, runtimeModel().weightHundredthsKg)
    rotate(EDITOR_STEP_PX)
    assertEquals(99_999, runtimeModel().weightHundredthsKg)
    rule.onNodeWithTag("editor_increase").performTouchInput { click() }
    assertEquals("Upper weight clamp", 99_999, runtimeModel().weightHundredthsKg)
    capture("weight-upper")
    rule.onNodeWithTag("editor").performTouchInput { swipeRight() }
    rule.waitForIdle()
    assertControllerRotary("after-weight-swipe", requireOverflow = true)
}

@OptIn(ExperimentalTestApi::class)
private fun WearAcceptanceSession.exerciseLowerBoundsAndExpiry() {
    dispatchScenario("fixture:${SyntheticSurfaceFixtures.UNSET_WEIGHT}")
    openEditor("weight_card")
    rule.onNodeWithTag("editor_decrease").assertIsNotEnabled()
    rotate(-EDITOR_STEP_PX)
    assertEquals("Absent weight cannot decrement", null, runtimeModel().weightHundredthsKg)
    rule.onNodeWithTag("editor_increase").performTouchInput { click() }
    rule.waitForIdle()
    assertEquals(0, runtimeModel().weightHundredthsKg)
    rule.onNodeWithTag("editor_decrease").performTouchInput { click() }
    rule.waitForIdle()
    assertEquals("Zero explicitly clears weight", null, runtimeModel().weightHundredthsKg)
    capture("weight-unset")
    pressBack()
    assertControllerRotary("after-weight-back", requireOverflow = true)

    dispatchScenario("fixture:${SyntheticSurfaceFixtures.FIELD_ERROR}")
    openEditor("reps_card")
    assertEquals(0, runtimeModel().reps)
    rule.onNodeWithTag("editor_decrease").assertIsNotEnabled()
    rotate(-EDITOR_STEP_PX)
    assertEquals("Lower reps clamp", 0, runtimeModel().reps)
    rotate(EDITOR_STEP_PX)
    assertEquals(1, runtimeModel().reps)
    rule.onNodeWithTag("editor").performTouchInput { swipeRight() }
    rule.waitForIdle()
    assertControllerRotary("after-reps-swipe", requireOverflow = true)
    openEditor("reps_card")
    dispatchScenario("expire")
    assertFalse("Synthetic expiry removes editing authority", runtimeModel().controlsEnabled)
    assertControllerRotary("after-explicit-authority-expiry", requireOverflow = true)
    capture("expired")
}

internal fun WearAcceptanceSession.openEditor(card: String) {
    rule.onNodeWithTag(card).performScrollTo().performTouchInput { click() }
    rule.waitForIdle()
    rule.onNodeWithTag("editor_rotary").assertIsFocused()
    assertTarget("editor_increase")
    assertTarget("editor_decrease")
}

internal fun WearAcceptanceSession.pressBack() {
    InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    rule.waitForIdle()
    rule.onNodeWithTag("editor").assertDoesNotExist()
}

@OptIn(ExperimentalTestApi::class)
internal fun WearAcceptanceSession.rotate(pixels: Float) {
    // The Android root receives input; the currently focused node must route it.
    rule.onRoot().performRotaryScrollInput { rotateToScrollVertically(pixels) }
    rule.waitForIdle()
}

@OptIn(ExperimentalTestApi::class)
private fun WearAcceptanceSession.assertControllerRotary(label: String, requireOverflow: Boolean = false) {
    rule.onNodeWithTag("editor").assertDoesNotExist()
    rule.onNodeWithTag("controller_scroll").assertIsFocused()
    val beforeModel = runtimeModel()
    val range = node("controller_scroll").config[SemanticsProperties.VerticalScrollAxisRange]
    val maximum = range.maxValue()
    if (requireOverflow) assertTrue("$label needs actual overflow", maximum > 0f)
    rotate(-CONTROLLER_SCROLL_PX)
    val before = scrollPosition()
    rotate(CONTROLLER_SCROLL_PX)
    val forward = scrollPosition()
    rotate(-CONTROLLER_SCROLL_PX)
    val backward = scrollPosition()
    if (maximum > 0f) {
        assertTrue("$label clockwise scroll", forward > before)
        assertTrue("$label counterclockwise scroll", backward < forward)
    } else {
        assertEquals("$label has no scroll range", before, forward, 0.01f)
    }
    assertEquals("Controller rotary must not edit numeric values", beforeModel, runtimeModel())
    val observations = receipt.optJSONArray("rotary") ?: JSONArray().also { receipt.put("rotary", it) }
    observations.put(JSONObject().put("scenario", label).put("maximumPx", maximum)
        .put("beforePx", before).put("forwardPx", forward).put("backwardPx", backward)
        .put("motionObserved", maximum > 0f)
        .put("fixturePreview", label == SyntheticSurfaceFixtures.RETRYABLE))
}

private fun WearAcceptanceSession.assertPressBehavior() {
    dispatchScenario("fixture:${SyntheticSurfaceFixtures.REFRESH_REQUIRED}")
    rule.onNodeWithTag("complete_set").assertIsNotEnabled()
    val disabledSize = node("complete_set", merged = true).size
    val beforeModel = runtimeModel()
    val button = rule.onNodeWithTag("complete_set")
    rule.mainClock.autoAdvance = false
    val disabled: Bitmap
    try {
        rule.mainClock.advanceTimeBy(PRESS_SETTLE_MS)
        rule.waitForIdle()
        disabled = button.captureToImage().asAndroidBitmap()
        button.performTouchInput { down(center) }
        rule.mainClock.advanceTimeBy(PRESS_HOLD_MS)
        rule.waitForIdle()
        val held = button.captureToImage().asAndroidBitmap()
        saveBitmap(disabled, "disabled-before.png")
        saveBitmap(held, "disabled-held.png")
        assertEquals("Disabled press must not change pixels", 0, changedPixels(disabled, held))
    } finally {
        button.performTouchInput { cancel() }
        rule.mainClock.autoAdvance = true
    }
    button.performTouchInput { click() }
    rule.waitForIdle()
    assertEquals("Disabled actual touch must not issue a command", beforeModel, runtimeModel())
    dispatchScenario("fixture:${SyntheticSurfaceFixtures.ACTIVE_BOUNDARY}")
    button.assertIsEnabled()
    assertEquals("Enabled/disabled action geometry", disabledSize, node("complete_set", merged = true).size)
    val enabled = button.captureToImage().asAndroidBitmap()
    saveBitmap(enabled, "enabled-before.png")
    assertTrue("Pixel oracle must detect enabled versus disabled state", changedPixels(disabled, enabled) > 0)
    button.performTouchInput { click() }
    rule.waitForIdle()
    assertEquals(CompletionUnavailableReason.COMMAND_IN_FLIGHT, runtimeModel().completionUnavailableReason)
    assertFalse(runtimeModel().completeEnabled)
    capture("submitted")
}

private fun changedPixels(first: Bitmap, second: Bitmap): Int {
    assertEquals("Capture width", first.width, second.width)
    assertEquals("Capture height", first.height, second.height)
    var changed = 0
    repeat(first.height) { y ->
        repeat(first.width) { x ->
            if (first.getPixel(x, y) != second.getPixel(x, y)) changed++
        }
    }
    return changed
}

private const val EDITOR_STEP_PX = 49f
private const val CONTROLLER_SCROLL_PX = 240f
private const val PRESS_SETTLE_MS = 5_000L
private const val PRESS_HOLD_MS = 500L
