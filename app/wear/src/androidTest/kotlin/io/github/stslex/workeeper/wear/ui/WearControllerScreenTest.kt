// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.wear.MainActivity
import io.github.stslex.workeeper.wear.R
import io.github.stslex.workeeper.wear.mvi.store.WearPresentation
import io.github.stslex.workeeper.wear.mvi.store.WearStore
import org.junit.Rule
import org.junit.Test

@Regression
class WearControllerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun weightedBoundaryCardsOpenTheEditorAndBoundsAreHonoured() {
        show(SyntheticSurfaceFixtures.ACTIVE_BOUNDARY)
        val resources = composeRule.activity.resources

        composeRule.onNodeWithTag("weight_card").performScrollTo().assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .assert(stateDescriptionIs(resources.getString(R.string.control_enabled)))
        composeRule.onNodeWithTag("reps_card").assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("complete_set").assertIsEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(resources.getString(R.string.complete_set_enabled_description)),
                ),
            )

        composeRule.onNodeWithTag("reps_card").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("editor_value").assertIsDisplayed()
        composeRule.onNodeWithTag("editor_increase").assertIsNotEnabled()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .assert(stateDescriptionIs(resources.getString(R.string.control_disabled)))
        composeRule.onNodeWithTag("editor_decrease").assertIsEnabled()
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            .assert(stateDescriptionIs(resources.getString(R.string.control_enabled)))
    }

    @Test
    fun readOnlyAndPhoneActionStatesExposeNoEnabledCompletion() {
        show(SyntheticSurfaceFixtures.REFRESH_REQUIRED)
        val resources = composeRule.activity.resources
        composeRule.onNodeWithTag("complete_set").assertIsNotEnabled()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(resources.getString(R.string.complete_set_disabled_description)),
                ),
            )
        composeRule.onNodeWithTag("completion_reason").assertIsDisplayed()
            .assertTextEquals(resources.getString(R.string.complete_reason_refresh))
        composeRule.onNodeWithTag("weight_card").performScrollTo()
            .assert(stateDescriptionIs(resources.getString(R.string.control_disabled)))

        show(SyntheticSurfaceFixtures.NO_SETS)
        composeRule.onNodeWithTag("exercise_name").assertIsDisplayed()
        composeRule.onNodeWithTag("complete_set").assertDoesNotExist()
    }

    @Test
    fun weightlessPayloadAndCompletionNeverExposeWrongControls() {
        show(SyntheticSurfaceFixtures.WEIGHTLESS)
        composeRule.onNodeWithTag("weight_card").assertDoesNotExist()
        composeRule.onNodeWithTag("reps_card").performScrollTo().assertIsDisplayed()

        show(SyntheticSurfaceFixtures.COMPLETE)
        composeRule.onNodeWithTag("finish_on_phone").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("complete_set").assertDoesNotExist()

        show(SyntheticSurfaceFixtures.PAYLOAD_TOO_LARGE)
        composeRule.onNodeWithTag("workout_generic").assertIsDisplayed()
        composeRule.onNodeWithTag("complete_set").assertDoesNotExist()
    }

    @Test
    fun validationErrorPublishesTalkBackErrorSemantics() {
        val base = requireNotNull(SyntheticSurfaceFixtures.find(SyntheticSurfaceFixtures.WEIGHTLESS))
        showPreview(base.copy(fieldError = io.github.stslex.workeeper.core.wear.protocol.NumericField.REPS))

        val message = composeRule.activity.getString(R.string.reps_invalid)
        composeRule.onNodeWithTag("field_error").performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, message))
    }

    private fun show(fixture: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.startActivity(
                // ActivityScenario matches lifecycle callbacks against the launch action/categories.
                Intent(activity.intent)
                    .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(SyntheticSurfaceFixtures.EXTRA_ID, "fixture:$fixture"),
            )
        }
        composeRule.waitForIdle()
    }

    private fun showPreview(model: WearSurfaceModel) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                WearControllerScreen(state = WearStore.State(presentation = WearPresentation(model)), consume = {})
            }
        }
        composeRule.waitForIdle()
    }

    private fun stateDescriptionIs(expected: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)
}
