// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.MainActivity
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import io.github.stslex.workeeper.di.buildAppGraph
import io.github.stslex.workeeper.harness.MetroTestGraphHolder
import io.github.stslex.workeeper.harness.MetroTestRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI half of the generation boundary: a swap resets Nav3 to the root, and an Activity recreation
 * after it restores the NEW generation, never the old saveable slot.
 * See documentation/feature-specs/kmp-phase-5-startup-processor.md §8.7.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class UiGenerationSwapTest {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private var secondDatabase: AppDatabase? = null
    private var secondLifetime: AppScopeLifetime? = null

    @After
    fun tearDownSecondGeneration() {
        secondLifetime?.let { runBlocking { it.cancelAndJoin() } }
        secondLifetime = null
        secondDatabase?.close()
        secondDatabase = null
    }

    @Test
    fun generationSwap_resetsNavToRoot_andRecreationDoesNotResurrectTheOldStack() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AppRoot").assertIsDisplayed()
        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()

        composeRule.onNodeWithTag(BottomBarItem.TRAININGS.testTag).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertIsDisplayed()

        swapToSecondGeneration()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertDoesNotExist()

        // Recreation after the swap restores the new slot; generation 1 must not resurrect.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AppRoot").assertIsDisplayed()
        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertDoesNotExist()
    }

    private fun swapToSecondGeneration() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = InMemoryDatabaseProvider.create(context).also { secondDatabase = it }
        val lifetime = AppScopeLifetime().also { secondLifetime = it }
        MetroTestGraphHolder.install(
            buildAppGraph(
                applicationContext = context,
                appDatabase = database,
                imageStorage = FakeImageStorage(),
                appScopeLifetime = lifetime,
            ),
        )
    }
}
