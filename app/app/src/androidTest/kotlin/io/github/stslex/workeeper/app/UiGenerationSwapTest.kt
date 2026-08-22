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
 * The UI half of the R2 generation boundary (Phase 5, `kmp-phase-5-startup-processor.md` §8.7),
 * proven against the composed app shell: a generation swap resets Nav3 to the root, the old
 * stack is unreachable, and — the resurrection pin — an ordinary Activity recreation AFTER the
 * swap restores the NEW generation's state, never the old generation's saved entries (they live
 * only under the old `SaveableStateProvider` slot, which the swap removed).
 *
 * The swap is driven through [MetroTestGraphHolder.install] — the harness's runtime-equivalent:
 * clear the old generation's ViewModelStore, publish a fresh generation id with a fresh store.
 * The runtime side of the same transition (quiesce ordering, DB handover) is proven by
 * `AppRuntimeTest`/`AppRuntimeReplacementTest` (JVM) and `RuntimeGenerationSwapDeviceTest`
 * (device); THIS test pins what composition does with the published id change.
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

        // Leave the root: generation 1's stack now shows Trainings.
        composeRule.onNodeWithTag(BottomBarItem.TRAININGS.testTag).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertIsDisplayed()

        swapToSecondGeneration()
        composeRule.waitForIdle()

        // The new generation starts at the intended root; the old top is gone with its stack.
        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()

        // Ordinary Activity recreation AFTER the swap: the new generation's saveable slot
        // restores (Home), and generation 1's saved entries must NOT resurrect — their slot
        // was removed when the new id composed.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AppRoot").assertIsDisplayed()
        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()
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
