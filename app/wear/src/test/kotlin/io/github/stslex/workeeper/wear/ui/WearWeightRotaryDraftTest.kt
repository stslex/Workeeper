package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.wear.runtime.RuntimeTestEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "en-w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearWeightRotaryDraftTest {
    @Test
    fun oneWeightRotaryEventAppliesEveryStepToTheCurrentDraft() = runComposeUiTest {
        val runtime = RuntimeTestEnvironment().also { it.accept() }
        setContent {
            val model by runtime.owner.surface.collectAsState(initial = runtime.owner.surface.value)
            WearGateHost(WearScreen.XL_ROUND) {
                WearControllerScreen(state = model, onAction = { runtime.owner.onAction(it) })
            }
        }
        waitForIdle()
        onNodeWithTag("weight_card").performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag("editor_rotary").assertIsFocused()
        onRoot().performRotaryScrollInput { rotateToScrollVertically(96f) }
        waitForIdle()
        assertEquals(10_500, runtime.owner.surface.value.weightHundredthsKg)
        onRoot().performRotaryScrollInput { rotateToScrollVertically(-144f) }
        waitForIdle()
        assertEquals(9_750, runtime.owner.surface.value.weightHundredthsKg)
    }
}
