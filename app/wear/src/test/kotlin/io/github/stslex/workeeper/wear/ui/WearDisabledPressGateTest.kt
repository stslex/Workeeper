// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.wear.runtime.ControllerAction
import io.github.stslex.workeeper.wear.runtime.RuntimeTestEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.io.File

/** GUARD: keep one test and composition; a second Compose test in the same sandbox hangs. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru-w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearDisabledPressGateTest {

    @Test
    @DisplayName("disabled button pixels stay fixed while held and enabled control accepts a touch")
    fun pressDoesNotCompressTheDisabledLabel() = runComposeUiTest {
        val active = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
        // The blocked state comes from the mapper: a disconnected session, the way production reaches it.
        val blocked = RuntimeTestEnvironment().let { env ->
            check(env.accept())
            env.owner.disconnected()
            WearSurfaceMapper.map(env.owner.snapshot.value)
        }
        check(blocked.kind == WearSurfaceKind.DISCONNECTED && !blocked.completeEnabled)
        var model by mutableStateOf(blocked)
        var screen by mutableStateOf(WearScreen.SMALL_ROUND)
        var scale by mutableStateOf(1f)
        val actions = mutableListOf<ControllerAction>()
        val noTouch = System.getenv("WEAR_PRESS_OBSERVATION") == "no-touch"
        setContent {
            WearGateHost(screen, fontScale = scale) {
                WearControllerScreen(state = model, onAction = actions::add)
            }
        }

        WearScreen.entries.forEach { current ->
            screen = current
            listOf(1f, LARGEST_WEAR_FONT_SCALE).forEach { fontScale ->
                scale = fontScale
                model = blocked
                waitForIdle()
                val disabledImage = onNodeWithTag("complete_set").captureToImage()
                val finalProfile = current == WearScreen.XL_ROUND && fontScale == LARGEST_WEAR_FONT_SCALE
                val pointer = !noTouch || !finalProfile
                val disabledDelta = heldPixelDelta("$current-$fontScale-disabled", pointer)
                if (finalProfile) println("$current/$fontScale observation: pointer=$pointer")
                val pixelMessage = "$current/$fontScale: disabled press changed button pixels"
                if (!finalProfile) assertEquals(0, disabledDelta, pixelMessage)
                onNodeWithTag("complete_set").performTouchInput { click() }
                waitForIdle()
                assertTrue(actions.isEmpty(), "a disabled press must not complete a set")

                model = active
                waitForIdle()
                val enabledImage = onNodeWithTag("complete_set").captureToImage()
                assertTrue(
                    changedPixels(disabledImage, enabledImage) > 0,
                    "$current/$fontScale: capture must distinguish visibly different button states",
                )
                onNodeWithTag("complete_set").performTouchInput { click() }
                waitForIdle()
                assertEquals(listOf<ControllerAction>(ControllerAction.CompleteSet), actions)
                actions.clear()
                if (finalProfile) assertEquals(0, disabledDelta, pixelMessage)
            }
        }
    }

    private fun ComposeUiTest.heldPixelDelta(profile: String, pointer: Boolean): Int {
        mainClock.autoAdvance = false
        // Let the scaffold's transient scroll indicator disappear before comparing the button region.
        mainClock.advanceTimeBy(SCAFFOLD_SETTLE_MILLIS)
        waitForIdle()
        val button = onNodeWithTag("complete_set")
        val before = button.captureToImage()
        if (pointer) button.performTouchInput { down(center) }
        mainClock.advanceTimeByFrame()
        waitForIdle()
        mainClock.advanceTimeBy(HOLD_MILLIS)
        waitForIdle()
        val during = button.captureToImage()
        saveCapture(before, "$profile-before")
        saveCapture(during, "$profile-held")
        if (pointer) button.performTouchInput { cancel() }
        mainClock.advanceTimeBy(HOLD_MILLIS)
        mainClock.autoAdvance = true
        waitForIdle()
        assertEquals(before.width, during.width, "press changed capture width")
        assertEquals(before.height, during.height, "press changed capture height")
        return changedPixels(before, during).also { println("$profile: changed pixels=$it") }
    }

    private fun saveCapture(image: ImageBitmap, name: String) {
        val directory = File("build/reports/wear-press").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { output ->
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, output)
        }
    }

    private fun changedPixels(before: ImageBitmap, during: ImageBitmap): Int {
        val initial = before.toPixelMap()
        val pressed = during.toPixelMap()
        var changed = 0
        repeat(before.height) { y ->
            repeat(before.width) { x ->
                if (initial[x, y].toArgb() != pressed[x, y].toArgb()) changed++
            }
        }
        return changed
    }

    private companion object {
        const val SCAFFOLD_SETTLE_MILLIS = 5_000L
        const val HOLD_MILLIS = 500L
    }
}
