// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.ambient.WearAmbientController
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Locale

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearAmbientControllerIntegrationTest {

    @Test
    fun ambientRetainsBothEditorsAndScrollButExpiryPreventsEditingOnWake() = runComposeUiTest {
        val actions = mutableListOf<ControllerAction>()
        val active = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)).copy(
            reps = 8,
            weightHundredthsKg = 10_000,
            selectedLocale = Locale.ENGLISH,
        )
        var model by mutableStateOf(active)
        var elapsed = 0L
        var expiryDeadline = Long.MAX_VALUE
        val ambientProvider = WearAmbientController(
            wallClockMillis = { elapsed },
            expireAuthority = {
                if (elapsed >= expiryDeadline) {
                    model = model.copy(
                        kind = WearSurfaceKind.REFRESH_REQUIRED,
                        controlsEnabled = false,
                        completeEnabled = false,
                        completionUnavailableReason = CompletionUnavailableReason.REFRESH_REQUIRED,
                    )
                }
            },
        )
        lateinit var back: OnBackPressedDispatcher
        setContent {
            val ambient by ambientProvider.state.collectAsState()
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { back = owner.onBackPressedDispatcher }
            WearGateHost(WearScreen.SMALL_ROUND, LARGEST_WEAR_FONT_SCALE) {
                WearControllerScreen(state = model, ambient = ambient) { action ->
                    actions += action
                    model = when (action) {
                        is ControllerAction.SetReps -> model.copy(reps = action.value, hasUnsubmittedDraft = true)
                        is ControllerAction.SetWeight -> model.copy(
                            weightHundredthsKg = action.value,
                            hasUnsubmittedDraft = true,
                        )
                        ControllerAction.CompleteSet,
                        ControllerAction.Retry,
                        -> model
                    }
                }
            }
        }
        waitForIdle()

        listOf("reps_card", "weight_card").forEach { card ->
            model = active
            onNodeWithTag(card).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("editor_increase").performClick()
            waitForIdle()
            val draft = model
            val displayed = editorText()
            actions.clear()
            runOnIdle { ambientProvider.onEnterAmbient(deviceHasLowBitAmbient = true, burnInProtectionRequired = true) }
            waitForIdle()
            assertAmbientHasNoInput(actions)
            val description = onNodeWithTag("ambient_summary").fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription].single()
            assertTrue(description.contains(RuntimeEnvironment.getApplication().getString(R.string.ambient_not_sent)))
            runOnIdle {
                elapsed += 60_000L
                ambientProvider.onSystemUpdate()
                ambientProvider.onExitAmbient()
            }
            waitForIdle()
            onNodeWithTag("ambient_summary").assertDoesNotExist()
            onNodeWithTag("editor_value").assertTextEquals(displayed)
            onNodeWithTag("editor_rotary").assertIsFocused()
            assertEquals(draft, model, "$card draft changed across ambient")
            assertTrue(actions.isEmpty(), "$card emitted an action while entering or leaving ambient")

            onRoot().performRotaryScrollInput { rotateToScrollVertically(49f) }
            waitForIdle()
            val expected = if (card == "reps_card") {
                ControllerAction.SetReps(requireNotNull(draft.reps) + 1)
            } else {
                ControllerAction.SetWeight(requireNotNull(draft.weightHundredthsKg) + 250)
            }
            assertEquals(listOf(expected), actions, "$card must regain rotary focus on its retained draft")
            actions.clear()
            runOnIdle { back.onBackPressed() }
            waitForIdle()
        }

        onNodeWithTag("controller_scroll").assertIsFocused()
        onRoot().performRotaryScrollInput { rotateToScrollVertically(240f) }
        waitForIdle()
        val beforeAmbientScroll = controllerScroll()
        assertTrue(beforeAmbientScroll > 0f, "Scroll restoration requires an overflowing controller")
        runOnIdle { ambientProvider.onEnterAmbient(deviceHasLowBitAmbient = false, burnInProtectionRequired = false) }
        waitForIdle()
        assertAmbientHasNoInput(actions)
        runOnIdle { ambientProvider.onExitAmbient() }
        waitForIdle()
        onNodeWithTag("controller_scroll").assertIsFocused()
        assertEquals(beforeAmbientScroll, controllerScroll(), "Ambient discarded the controller scroll position")

        onNodeWithTag("weight_card").performScrollTo().performClick()
        waitForIdle()
        val valueBeforeExpiry = model.weightHundredthsKg
        runOnIdle { ambientProvider.onEnterAmbient(deviceHasLowBitAmbient = false, burnInProtectionRequired = false) }
        waitForIdle()
        runOnIdle {
            expiryDeadline = elapsed + 1L
            elapsed = expiryDeadline
            ambientProvider.onExitAmbient()
        }
        waitForIdle()
        onNodeWithTag("editor").assertDoesNotExist()
        onNodeWithTag("ambient_summary").assertDoesNotExist()
        onNodeWithTag("controller_scroll").assertIsFocused()
        onNodeWithTag("complete_set").assertIsNotEnabled()
        assertEquals(valueBeforeExpiry, model.weightHundredthsKg, "Expiry must not rewrite the unsent value")
        assertTrue(actions.isEmpty(), "Expiry and wake must not submit a draft")
        onRoot().performRotaryScrollInput { rotateToScrollVertically(49f) }
        waitForIdle()
        assertTrue(actions.isEmpty(), "An expired editor must not receive rotary after wake")
    }

    private fun ComposeUiTest.assertAmbientHasNoInput(actions: List<ControllerAction>) {
        onNodeWithTag("editor").assertDoesNotExist()
        onNodeWithTag("controller_scroll").assertDoesNotExist()
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), true).assertCountEquals(0)
        onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus), true).assertCountEquals(0)
        onNodeWithTag("ambient_summary").performTouchInput { click() }
        onRoot().performRotaryScrollInput { rotateToScrollVertically(49f) }
        waitForIdle()
        assertTrue(actions.isEmpty(), "Ambient must not route touch or rotary into the retained editor")
    }

    private fun ComposeUiTest.editorText(): String = onNodeWithTag("editor_value").fetchSemanticsNode()
        .config[SemanticsProperties.Text].single().text

    private fun ComposeUiTest.controllerScroll(): Float = onNodeWithTag("controller_scroll").fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange].value()
}
