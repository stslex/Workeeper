// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.wear.MainActivity
import io.github.stslex.workeeper.wear.R
import org.junit.Rule
import org.junit.Test

@Regression
class WearControllerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun weightedBoundaryControlsAreReachableAndAtLeast48Dp() {
        show(requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)))

        val resources = composeRule.activity.resources
        val weight = resources.getString(R.string.weight_value, "999.99")

        composeRule.onNodeWithTag("weight_decrease").performScrollTo().assertIsDisplayed()
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
            .assertContentAndState(
                resources.getString(R.string.decrease_weight, weight),
                resources.getString(R.string.control_enabled),
            )
        composeRule.onNodeWithTag("weight_increase").assertIsNotEnabled()
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
            .assertContentAndState(
                resources.getString(R.string.increase_weight, weight),
                resources.getString(R.string.control_disabled),
            )
        composeRule.onNodeWithTag("reps_increase").performScrollTo().assertIsNotEnabled()
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
            .assertContentAndState(
                resources.getString(R.string.increase_reps, 999),
                resources.getString(R.string.control_disabled),
            )
        composeRule.onNodeWithTag("reps_decrease").assertIsEnabled()
            .assertContentAndState(
                resources.getString(R.string.decrease_reps, 999),
                resources.getString(R.string.control_enabled),
            )
        composeRule.onNodeWithTag("complete_set").performScrollTo().assertIsEnabled().assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(resources.getString(R.string.complete_set_enabled_description)),
                ),
            )
    }

    @Test
    fun readOnlyAndPhoneActionStatesExposeNoEnabledCompletion() {
        show(requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.REFRESH_REQUIRED)))
        val resources = composeRule.activity.resources
        composeRule.onNodeWithTag("complete_set").performScrollTo().assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(resources.getString(R.string.complete_set_disabled_description)),
                ),
            )
        composeRule.onNodeWithTag("reps_increase").assertContentAndState(
            resources.getString(R.string.increase_reps, 8),
            resources.getString(R.string.control_disabled),
        )

        show(requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.NO_SETS)))
        composeRule.onNodeWithTag("exercise_name").assertIsDisplayed()
        composeRule.onNodeWithTag("complete_set").assertDoesNotExist()
    }

    @Test
    fun weightlessPayloadAndCompletionNeverExposeWrongControls() {
        show(requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.WEIGHTLESS)))
        composeRule.onNodeWithTag("weight_value").assertDoesNotExist()
        composeRule.onNodeWithTag("reps_value").performScrollTo().assertIsDisplayed()

        show(requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.COMPLETE)))
        composeRule.onNodeWithTag("finish_on_phone").assertIsDisplayed()
        composeRule.onNodeWithTag("complete_set").assertDoesNotExist()

        show(WearSurfaceModel(kind = WearSurfaceKind.PAYLOAD_TOO_LARGE))
        composeRule.onNodeWithTag("workout_generic").assertIsDisplayed()
        composeRule.onNodeWithTag("complete_set").assertDoesNotExist()
    }

    @Test
    fun validationErrorPublishesTalkBackErrorSemantics() {
        val base = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.WEIGHTLESS))
        show(base.copy(fieldError = io.github.stslex.workeeper.core.wear.protocol.NumericField.REPS))

        val message = composeRule.activity.getString(R.string.reps_invalid)
        composeRule.onNodeWithTag("field_error").performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, message))
    }

    private fun show(model: WearSurfaceModel) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent { WearControllerScreen(state = model, onAction = {}) }
        }
        composeRule.waitForIdle()
    }

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertContentAndState(
        content: String,
        state: String,
    ): androidx.compose.ui.test.SemanticsNodeInteraction = assert(
        SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(content)) and
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state),
    )
}
