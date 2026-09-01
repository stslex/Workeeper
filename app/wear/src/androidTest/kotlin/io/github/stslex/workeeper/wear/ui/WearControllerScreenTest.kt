// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.activity.compose.setContent
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
import org.junit.Rule
import org.junit.Test

@Regression
class WearControllerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun weightedBoundaryControlsAreReachableAndAtLeast48Dp() {
        show(requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)))

        composeRule.onNodeWithTag("weight_decrease").performScrollTo().assertIsDisplayed()
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        composeRule.onNodeWithTag("weight_increase").assertIsNotEnabled()
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        composeRule.onNodeWithTag("reps_increase").performScrollTo().assertIsNotEnabled()
            .assertTouchWidthIsEqualTo(48.dp).assertTouchHeightIsEqualTo(48.dp)
        composeRule.onNodeWithTag("complete_set").performScrollTo().assertIsEnabled().assertIsDisplayed()
    }

    @Test
    fun readOnlyAndPhoneActionStatesExposeNoEnabledCompletion() {
        show(requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.REFRESH_REQUIRED)))
        composeRule.onNodeWithTag("complete_set").performScrollTo().assertIsNotEnabled()

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

    private fun show(model: WearSurfaceModel) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent { WearControllerScreen(state = model, onAction = {}) }
        }
        composeRule.waitForIdle()
    }
}
