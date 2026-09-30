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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.ambient.WearAmbientController
import io.github.stslex.workeeper.wear.runtime.ControllerAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Locale

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class)
internal class WearAmbientEditorEligibilityTest {

    @Test
    fun ambientRestoresBothEditorsOnlyWhenEligibleAtWake() = runComposeUiTest {
        val fixture = AmbientEditorEligibilityFixture()
        lateinit var back: OnBackPressedDispatcher
        setContent {
            val ambient by fixture.provider.state.collectAsState()
            val owner = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
            SideEffect { back = owner.onBackPressedDispatcher }
            WearGateHost(WearScreen.SMALL_ROUND, LARGEST_WEAR_FONT_SCALE) {
                WearControllerScreen(state = fixture.model, ambient = ambient, onAction = fixture::onAction)
            }
        }
        waitForIdle()
        listOf("reps_card", "weight_card").forEach { card ->
            assertTransientAmbientEligibility(card, fixture)
            runOnIdle { back.onBackPressed() }
            waitForIdle()
            assertIneligibleWakeDiscardsEditor(card, fixture)
            onNodeWithTag(card).performScrollTo().performClick()
            waitForIdle()
            onNodeWithTag("editor_rotary").assertIsFocused()
            runOnIdle { back.onBackPressed() }
            waitForIdle()
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.openDraftEditor(card: String, fixture: AmbientEditorEligibilityFixture): String {
    runOnIdle { fixture.model = fixture.active }
    waitForIdle()
    onNodeWithTag(card).performScrollTo().performClick()
    waitForIdle()
    onNodeWithTag("editor_increase").performClick()
    waitForIdle()
    fixture.actions.clear()
    return onNodeWithTag("editor_value").fetchSemanticsNode().config[SemanticsProperties.Text].single().text
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertTransientAmbientEligibility(card: String, fixture: AmbientEditorEligibilityFixture) {
    val displayed = openDraftEditor(card, fixture)
    val draft = fixture.model
    runOnIdle { fixture.provider.onEnterAmbient(deviceHasLowBitAmbient = true, burnInProtectionRequired = true) }
    waitForIdle()
    runOnIdle { fixture.loseEligibility() }
    waitForIdle()
    assertAmbientWithoutEditorInput(fixture)
    runOnIdle { fixture.model = draft }
    waitForIdle()
    assertAmbientWithoutEditorInput(fixture)
    runOnIdle { fixture.provider.onExitAmbient() }
    waitForIdle()
    onNodeWithTag("ambient_summary").assertDoesNotExist()
    onNodeWithTag("editor_value").assertTextEquals(displayed)
    onNodeWithTag("editor_rotary").assertIsFocused()
    assertEquals(draft, fixture.model, "$card must retain its draft through temporary ambient ineligibility")
    assertTrue(fixture.actions.isEmpty(), "$card ambient transitions must not submit an action")
    onRoot().performRotaryScrollInput { rotateToScrollVertically(49f) }
    waitForIdle()
    val expected = if (card == "reps_card") {
        ControllerAction.AdjustDraft(NumericField.REPS, 1)
    } else {
        ControllerAction.AdjustDraft(NumericField.WEIGHT, 1)
    }
    assertEquals(listOf(expected), fixture.actions, "$card must regain rotary focus after restored eligibility")
    fixture.actions.clear()
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertIneligibleWakeDiscardsEditor(card: String, fixture: AmbientEditorEligibilityFixture) {
    openDraftEditor(card, fixture)
    val draft = fixture.model
    runOnIdle { fixture.provider.onEnterAmbient(deviceHasLowBitAmbient = true, burnInProtectionRequired = true) }
    waitForIdle()
    runOnIdle { fixture.loseEligibility() }
    waitForIdle()
    assertAmbientWithoutEditorInput(fixture)
    runOnIdle { fixture.provider.onExitAmbient() }
    waitForIdle()
    onNodeWithTag("ambient_summary").assertDoesNotExist()
    onNodeWithTag("editor").assertDoesNotExist()
    onNodeWithTag("controller_scroll").assertIsFocused()
    onNodeWithTag("complete_set").assertIsNotEnabled()
    assertEquals(draft.reps, fixture.model.reps, "$card wake must not rewrite unsent reps")
    assertEquals(
        draft.weightHundredthsKg,
        fixture.model.weightHundredthsKg,
        "$card wake must not rewrite unsent weight",
    )
    onRoot().performRotaryScrollInput { rotateToScrollVertically(49f) }
    waitForIdle()
    assertTrue(fixture.actions.isEmpty(), "$card ineligible wake must not route rotary into an editor")
    runOnIdle { fixture.model = draft }
    waitForIdle()
    onNodeWithTag("editor").assertDoesNotExist()
    onNodeWithTag("controller_scroll").assertIsFocused()
    assertTrue(fixture.actions.isEmpty(), "$card later eligibility must not reopen or submit the discarded editor")
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.assertAmbientWithoutEditorInput(fixture: AmbientEditorEligibilityFixture) {
    onNodeWithTag("ambient_summary").assertExists()
    onNodeWithTag("editor").assertDoesNotExist()
    onNodeWithTag("controller_scroll").assertDoesNotExist()
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), true).assertCountEquals(0)
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.RequestFocus), true).assertCountEquals(0)
    onRoot().performRotaryScrollInput { rotateToScrollVertically(49f) }
    waitForIdle()
    assertTrue(fixture.actions.isEmpty(), "Ambient must not route rotary into the retained selection")
}

private class AmbientEditorEligibilityFixture {
    val active = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)).copy(
        reps = 8,
        weightHundredthsKg = 10_000,
        selectedLocale = Locale.ENGLISH,
    )
    var model by mutableStateOf(active)
    val actions = mutableListOf<ControllerAction>()
    val provider = WearAmbientController(wallClockMillis = { 0L }, expireAuthority = {})

    fun loseEligibility() {
        model = model.copy(
            kind = WearSurfaceKind.REFRESH_REQUIRED,
            controlsEnabled = false,
            completeEnabled = false,
            completionUnavailableReason = CompletionUnavailableReason.REFRESH_REQUIRED,
        )
    }

    fun onAction(action: ControllerAction) {
        actions += action
        model = when (action) {
            is ControllerAction.AdjustDraft -> model.withRelativeDraft(action)
            is ControllerAction.SetReps -> model.copy(reps = action.value, hasUnsubmittedDraft = true)
            is ControllerAction.SetWeight -> model.copy(weightHundredthsKg = action.value, hasUnsubmittedDraft = true)
            ControllerAction.CompleteSet,
            ControllerAction.Retry,
            -> model
        }
    }
}
