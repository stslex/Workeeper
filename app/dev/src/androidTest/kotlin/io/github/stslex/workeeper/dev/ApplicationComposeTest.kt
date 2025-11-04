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
internal class ApplicationComposeTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun appStartInitial() {
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AppRoot")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("ChartsGraph")
            .assertIsDisplayed()

        checkSelectedBottomAppBar(BottomBarItem.CHARTS)
    }

    @Test
    fun navigateToTrainings() {
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AppRoot")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("ChartsGraph")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("BottomAppBarItem_${BottomBarItem.TRAININGS.name}")
            .assertIsDisplayed()
            .assertIsNotSelected()
            .performClick()

        composeTestRule.waitForIdle()

        checkSelectedBottomAppBar(BottomBarItem.TRAININGS)

        composeTestRule
            .onNodeWithTag("AllTrainingsGraph")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("ChartsGraph")
            .assertIsNotDisplayed()
    }

    private fun checkSelectedBottomAppBar(
        selectedItem: BottomBarItem,
    ) {
        composeTestRule
            .onNodeWithTag("WorkeeperBottomAppBar")
            .assertIsDisplayed()

        BottomBarItem.entries.forEach { item ->
            composeTestRule
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
