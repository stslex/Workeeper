// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.App
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A generation already retired when its region composes resolves zero dependencies and leaks no
 * grant. Refusal only — `onAbandoned` is not executable from a test. See the Phase-5 spec.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class UiAdmissionRaceTest {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createComposeRule()

    private var secondDatabase: AppDatabase? = null
    private var secondLifetime: AppScopeLifetime? = null

    @After
    fun tearDownSecondGeneration() {
        secondLifetime?.let { runBlocking { it.cancelAndJoin() } }
        secondLifetime = null
        secondDatabase?.close()
        secondDatabase = null
    }

    /** Positive control: without it, "resolved nothing" below would be vacuous. */
    @Test
    fun admittedGeneration_composesTheRegion_andResolvesItsDependencies() {
        val generationId = publishedGenerationId()
        MetroTestGraphHolder.appRootDepsResolutions.set(0)

        composeRule.setContent { App() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(APP_ROOT_TAG).assertIsDisplayed()
        assertEquals(
            "an admitted region must hold exactly one grant while it is composed",
            1,
            MetroTestGraphHolder.outstandingAdmissions(generationId),
        )
        assertEquals(
            "an admitted region resolves the generation's app-scope deps",
            1,
            MetroTestGraphHolder.appRootDepsResolutions.get(),
        )
    }

    /** Retired before the region composes: the grant is refused, so nothing behind it resolves. */
    @Test
    fun retiredGeneration_composesNothing_andResolvesNothing() {
        val generationId = publishedGenerationId()
        MetroTestGraphHolder.appRootDepsResolutions.set(0)
        MetroTestGraphHolder.retireUiGeneration(generationId)

        composeRule.setContent { App() }
        composeRule.waitForIdle()

        assertEquals(
            "a retired generation must resolve NOTHING — not even the app root deps",
            0,
            MetroTestGraphHolder.appRootDepsResolutions.get(),
        )
        assertEquals(
            "a refused region must leak no admission grant",
            0,
            MetroTestGraphHolder.outstandingAdmissions(generationId),
        )
        // The published phase IS a Generation, so an absent AppRoot means the region drew nothing.
        composeRule.onNodeWithTag(APP_ROOT_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(TRANSITIONING_TAG).assertDoesNotExist()
    }

    /**
     * THE RACE: generation N+1 is retired inside the window between its publication and the frame
     * that would compose its region.
     */
    @Test
    fun retirementBetweenPublicationAndFrame_resolvesNothing() {
        composeRule.setContent { App() }
        composeRule.waitForIdle()
        val generationOne = publishedGenerationId()
        composeRule.onNodeWithTag(APP_ROOT_TAG).assertIsDisplayed()

        // From here the test owns the apply boundary — the clock closes the race, not waitForIdle.
        composeRule.mainClock.autoAdvance = false

        publishSecondGeneration()
        val generationTwo = publishedGenerationId()
        assertNotEquals("the swap must publish a NEW generation id", generationOne, generationTwo)

        // Inside the window: published, not yet composed, now retired.
        MetroTestGraphHolder.retireUiGeneration(generationTwo)
        MetroTestGraphHolder.appRootDepsResolutions.set(0)

        repeat(FRAMES_TO_SETTLE) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()

        assertEquals(
            "the stale generation's region must resolve NOTHING",
            0,
            MetroTestGraphHolder.appRootDepsResolutions.get(),
        )
        assertEquals(
            "the stale region must leak no admission grant",
            0,
            MetroTestGraphHolder.outstandingAdmissions(generationTwo),
        )
        assertEquals(
            "the outgoing region must have released its own grant on the way out",
            0,
            MetroTestGraphHolder.outstandingAdmissions(generationOne),
        )
        composeRule.onNodeWithTag(APP_ROOT_TAG).assertDoesNotExist()
    }

    private fun publishedGenerationId(): Int = requireNotNull(MetroTestGraphHolder.currentGenerationId) {
        "no generation published — MetroTestRule installs one in @Before"
    }

    /** The harness's generation swap, the same call `UiGenerationSwapTest` drives. */
    private fun publishSecondGeneration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

    private companion object {

        const val APP_ROOT_TAG = "AppRoot"
        const val TRANSITIONING_TAG = "AppTransitioning"
        const val FRAMES_TO_SETTLE = 5
    }
}
