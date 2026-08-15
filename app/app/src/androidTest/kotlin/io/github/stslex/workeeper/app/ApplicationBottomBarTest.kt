package io.github.stslex.workeeper.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.MainActivity
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.harness.MetroTestRule
import io.github.stslex.workeeper.harness.NavPaths
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App-Scope Collapse Step 6 (Phase 3.3): moved from `app/dev` into the consolidated `:app:app`
 * androidTest suite and de-Hilt'd. [MetroTestRule] (order 0) installs the per-test Metro graph
 * (in-memory DB + fake image storage) BEFORE the compose rule (order 1) launches `MainActivity`, which
 * reads it via `application as AppGraphOwner`.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class ApplicationBottomBarTest {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val graphsForSelected = listOf(
        "HomeGraph" to BottomBarItem.HOME,
        "AllTrainingsGraph" to BottomBarItem.TRAININGS,
        "AllExercisesGraph" to BottomBarItem.EXERCISES,
    )

    @Test
    fun appStartInitial() {
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AppRoot")
            .assertIsDisplayed()

        checkScreenOpen(BottomBarItem.HOME)
    }

    @Test
    fun navigateToTrainingsAndBack() {
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AppRoot")
            .assertIsDisplayed()

        checkScreenOpen(BottomBarItem.HOME)

        /**Open Trainings*/
        BottomBarItem.TRAININGS.performClick()
        composeRule.waitForIdle()
        checkScreenOpen(BottomBarItem.TRAININGS)

        /**Back press - close app*/
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        checkAppClosed()
    }

    @Test
    fun navigateToExercisesAndBack() {
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AppRoot")
            .assertIsDisplayed()

        checkScreenOpen(BottomBarItem.HOME)

        /**Open Exercises*/
        BottomBarItem.EXERCISES.performClick()
        composeRule.waitForIdle()
        checkScreenOpen(BottomBarItem.EXERCISES)

        /**Back press - close app*/
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        checkAppClosed()
    }

    @Test
    fun navigateToExercisesTrainingsAndBack() {
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AppRoot")
            .assertIsDisplayed()

        checkScreenOpen(BottomBarItem.HOME)

        /**Open Exercises*/
        BottomBarItem.EXERCISES.performClick()
        composeRule.waitForIdle()
        checkScreenOpen(BottomBarItem.EXERCISES)

        /**Open Trainings*/
        BottomBarItem.TRAININGS.performClick()
        composeRule.waitForIdle()
        checkSelectedBottomAppBar(BottomBarItem.TRAININGS)

        /**Back press - close app*/
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }

        checkAppClosed()
    }

    private fun checkAppClosed() {
        // `onBackPressed()` returns before the activity's `finish()` lands on the main
        // looper, so the composition can still hold `AppRoot` for a few frames.
        // `assertDoesNotExist` samples once with no wait of its own — gate on the
        // absence instead of sampling it, or this races teardown (tech-debt.md,
        // "Flaky UI test"). Not `waitForIdle`: idle can be reached before the frame
        // that removes the root.
        // `atLeastOneRootRequired = false`: once the window is gone there are ZERO compose
        // roots, and the default throws "No compose hierarchies found" at exactly the
        // moment the wait should succeed.
        composeRule.waitUntil(timeoutMillis = NavPaths.ARRIVAL_TIMEOUT_MS) {
            composeRule
                .onAllNodesWithTag("AppRoot")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isEmpty()
        }
        composeRule
            .onNodeWithTag("AppRoot")
            .assertDoesNotExist()
        BottomBarItem.entries.forEach {
            composeRule
                .onNodeWithTag("BottomAppBarItem_${it.name}")
                .assertDoesNotExist()
        }
    }

    private fun checkScreenOpen(
        item: BottomBarItem,
    ) {
        checkSelectedBottomAppBar(item)

        graphsForSelected.forEach { (graphName, bottomBarTag) ->
            composeRule
                .onNodeWithTag(graphName)
                .apply {
                    if (bottomBarTag == item) {
                        assertIsDisplayed()
                    } else {
                        assertIsNotDisplayed()
                    }
                }
        }
    }

    private fun BottomBarItem.performClick() = composeRule
        .onNodeWithTag("BottomAppBarItem_${this.name}")
        .performClick()

    private fun checkSelectedBottomAppBar(
        selectedItem: BottomBarItem,
    ) {
        composeRule
            .onNodeWithTag("WorkeeperBottomAppBar")
            .assertIsDisplayed()

        BottomBarItem.entries.forEach { item ->
            composeRule
                .onNodeWithTag("BottomAppBarItem_${item.name}")
                .assertIsDisplayed()
                .apply {
                    if (item == selectedItem) {
                        assertIsSelected()
                    } else {
                        assertIsNotSelected()
                    }
                }

        }
    }
}
