package io.github.stslex.workeeper.dev

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.stslex.workeeper.MainActivity
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
internal class ApplicationBottomBarTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val graphsForSelected = listOf(
        "ChartsGraph" to BottomBarItem.CHARTS,
        "AllTrainingsGraph" to BottomBarItem.TRAININGS,
        "AllExercisesGraph" to BottomBarItem.EXERCISES,
    )

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun appStartInitial() {
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AppRoot")
            .assertIsDisplayed()

        checkScreenOpen(BottomBarItem.CHARTS)
    }

    @Test
    fun navigateToTrainingsAndBack() {
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AppRoot")
            .assertIsDisplayed()

        checkScreenOpen(BottomBarItem.CHARTS)

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

        checkScreenOpen(BottomBarItem.CHARTS)

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

        checkScreenOpen(BottomBarItem.CHARTS)

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
