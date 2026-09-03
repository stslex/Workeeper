// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performRotaryScrollInput
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The §5 editor contract: it emits only the existing [ControllerAction.SetReps] /
 * [ControllerAction.SetWeight] with values produced by the unchanged [WearDraftPolicy],
 * controls at a bound are disabled, the `null` weight transition is preserved in both
 * directions, rotary input drives the value, and losing mutation authority closes the editor.
 *
 * GUARD: keep one `@Test` and one composition — a second `runComposeUiTest` in the same
 * Robolectric sandbox hangs. See the v3 redesign spec §27.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@OptIn(ExperimentalTestApi::class)
internal class WearNumericEditorTest {

    @Test
    @DisplayName("the editor emits existing draft actions, honours bounds, and follows authority")
    fun editorEmitsExistingDraftActionsWithinPolicyBounds() = runComposeUiTest {
        val actions = mutableListOf<ControllerAction>()
        val weighted = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
            .copy(reps = 8, weightHundredthsKg = 10_000)
        var model by mutableStateOf(weighted)
        setContent { WearControllerScreen(state = model, onAction = actions::add) }
        waitForIdle()

        onNodeWithTag("reps_card").performClick()
        waitForIdle()
        onNodeWithTag("editor_value").assertExists()
        onNodeWithTag("editor_increase").performClick()
        onNodeWithTag("editor_decrease").performClick()
        assertEquals(
            listOf<ControllerAction>(ControllerAction.SetReps(9), ControllerAction.SetReps(7)),
            actions,
            "reps steps must be WearDraftPolicy steps from the canonical value",
        )

        model = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.REFRESH_REQUIRED))
        waitForIdle()
        onNodeWithTag("editor_value").assertDoesNotExist()
        onNodeWithTag("status").assertExists()

        model = weighted
        waitForIdle()
        actions.clear()
        onNodeWithTag("weight_card").performClick()
        waitForIdle()
        onNodeWithTag("editor_increase").performClick()
        onNodeWithTag("editor_decrease").performClick()
        assertEquals(
            listOf<ControllerAction>(
                ControllerAction.SetWeight(10_250),
                ControllerAction.SetWeight(9_750),
            ),
            actions,
            "weight steps must be WEIGHT_STEP_HUNDREDTHS_KG from the canonical value",
        )

        actions.clear()
        onNodeWithTag("editor").performRotaryScrollInput {
            rotateToScrollVertically(ONE_ROTARY_STEP_PX)
        }
        waitForIdle()
        assertEquals(
            listOf<ControllerAction>(ControllerAction.SetWeight(10_250)),
            actions,
            "one rotary step must emit exactly one policy increment",
        )

        model = weighted.copy(weightHundredthsKg = 0)
        waitForIdle()
        actions.clear()
        onNodeWithTag("editor_decrease").performClick()
        assertEquals(
            listOf<ControllerAction>(ControllerAction.SetWeight(null)),
            actions,
            "decrement at zero must produce the null transition, not clamp",
        )

        model = weighted.copy(weightHundredthsKg = null)
        waitForIdle()
        actions.clear()
        onNodeWithTag("editor_decrease").assertIsNotEnabled()
        onNodeWithTag("editor_increase").performClick()
        assertEquals(
            listOf<ControllerAction>(ControllerAction.SetWeight(0)),
            actions,
            "increment from null must produce zero, per the unchanged policy",
        )

        model = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY))
        waitForIdle()
        onNodeWithTag("editor_increase").assertIsNotEnabled()
    }

    private companion object {
        /** Just past the editor's rotary accumulator threshold, so exactly one step fires. */
        const val ONE_ROTARY_STEP_PX = 49f
    }
}
