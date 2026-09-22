// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.wear.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/** GUARD: one test and composition per sandbox; G4 verifies semantic state and its specific visible reason. */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "w240dp-h240dp-round")
@OptIn(ExperimentalTestApi::class)
internal class WearDisabledLabelGateTest {

    @Test
    @DisplayName("every blocked completion exposes its specific reason and disabled action semantics")
    fun disabledCompletionAlwaysCarriesItsSpecificReason() = runComposeUiTest {
        val disabledStates = listOf(
            SyntheticSurfaceFixtures.REFRESH_REQUIRED,
            SyntheticSurfaceFixtures.DISCONNECTED,
            SyntheticSurfaceFixtures.COMMAND_IN_FLIGHT,
            SyntheticSurfaceFixtures.FIELD_ERROR,
            SyntheticSurfaceFixtures.WEIGHT_ERROR,
        ).map { id -> id to fixture(id) }
        assertEquals(
            CompletionUnavailableReason.entries.toSet(),
            disabledStates.map { it.second.completionUnavailableReason }.toSet(),
            "G4 must cover every typed completion reason",
        )
        var screen by mutableStateOf(WearScreen.SMALL_ROUND)
        var model by mutableStateOf(disabledStates.first().second)
        setContent {
            WearGateHost(screen) {
                WearControllerScreen(state = model, onAction = {})
            }
        }

        WearScreen.entries.forEach { current ->
            screen = current
            disabledStates.forEach { (label, state) ->
                model = state
                waitForIdle()
                val where = "screen=$current fixture=$label"
                val action = onNodeWithTag("complete_set").assertIsNotEnabled().fetchSemanticsNode()
                assertEquals(
                    listOf(RuntimeEnvironment.getApplication().getString(R.string.complete_set_disabled_description)),
                    action.config[SemanticsProperties.ContentDescription],
                    "$where must announce that completion is disabled",
                )
                val reason = onNodeWithTag("completion_reason", useUnmergedTree = true).fetchSemanticsNode()
                assertEquals(
                    expectedCompletionReasonText(requireNotNull(state.completionUnavailableReason)),
                    reason.config[SemanticsProperties.Text].joinToString { it.text },
                    "$where must explain the actual blocking condition",
                )
                onNodeWithTag("exercise_context").assertDoesNotExist()
            }

            model = fixture(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
            waitForIdle()
            val action = onNodeWithTag("complete_set").assertIsEnabled().fetchSemanticsNode()
            assertEquals(
                listOf(RuntimeEnvironment.getApplication().getString(R.string.complete_set_enabled_description)),
                action.config[SemanticsProperties.ContentDescription],
                "screen=$current enabled completion must announce its action",
            )
            onNodeWithTag("completion_reason").assertDoesNotExist()
            onNodeWithTag("exercise_context").assertExists()
        }
    }

    private fun fixture(id: String): WearSurfaceModel =
        requireNotNull(SyntheticSurfaceFixtures.find(id))
}
