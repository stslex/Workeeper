// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.MainActivity
import io.github.stslex.workeeper.bottom_app_bar.BottomBarItem
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database_test.InMemoryDatabaseProvider
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.core.ui.test.fakes.FakeImageStorage
import io.github.stslex.workeeper.di.buildAppGraph
import io.github.stslex.workeeper.harness.MetroTestGraphHolder
import io.github.stslex.workeeper.runtime.AppRuntime
import io.github.stslex.workeeper.runtime.ReinitializeOutcome
import io.github.stslex.workeeper.runtime.launchStartupProcessor
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith

/**
 * A real [AppRuntime] behind the live app shell: one graph-only transition, whose quiesce awaits
 * the composition's own disposal. Graph-only — the file swap is [RuntimeGenerationSwapDeviceTest].
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class AppRuntimeUiHandshakeDeviceTest {

    /** Builds the real runtime and enters runtime mode BEFORE the activity launches (order 0). */
    inner class RuntimeModeRule : ExternalResource() {

        override fun before() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            runtime = AppRuntime(
                applicationContext = context,
                dbFactory = { InMemoryDatabaseProvider.create(it) },
                imageStorageFactory = { FakeImageStorage() },
                graphFactory = ::buildAppGraph,
                preflight = { generation ->
                    launchStartupProcessor(context, isLowRamDevice = { false }).preflightAndArm(
                        graph = generation.graph,
                        appDatabase = generation.database,
                        lifetime = generation.lifetime,
                    )
                },
            )
            MetroTestGraphHolder.runtimeDelegate = runtime
        }

        override fun after() {
            // The activity is already torn down, so its disposal signals have drained.
            MetroTestGraphHolder.reset()
            runCatching {
                val generation = runtime.currentGeneration
                runBlocking { generation.lifetime.cancelAndJoin() }
                generation.database.close()
            }
        }
    }

    private lateinit var runtime: AppRuntime

    @get:Rule(order = 0)
    val runtimeRule = RuntimeModeRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun realHandshake_graphOnlySwap_awaitsLiveUiDisposal_thenPublishesFreshRoot() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AppRoot").assertIsDisplayed()
        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()

        val genOne = runtime.currentGeneration

        composeRule.onNodeWithTag(BottomBarItem.TRAININGS.testTag).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertIsDisplayed()

        // Pre-swap write — the graph-only handover must serve it afterwards.
        runBlocking { genOne.database.tagDao.insert(TagEntity(name = HANDSHAKE_SENTINEL)) }

        // GUARD: submit off the test thread — the compose rule owns the frame clock, so a blocked
        // test thread would starve recomposition and fake a disposal timeout.
        val outcomeRef = AtomicReference<ReinitializeOutcome?>(null)
        thread(name = "handshake-submitter") {
            outcomeRef.set(runBlocking { runtime.reinitialize(expected = genOne) })
        }
        composeRule.waitUntil(timeoutMillis = 15_000) { outcomeRef.get() != null }
        val outcome = outcomeRef.get()
        val genTwo = (outcome as? ReinitializeOutcome.Published)?.generation
            ?: run {
                fail("graph-only transition must publish against live UI; got $outcome")
                error("unreachable")
            }

        // The successor re-keys App(): fresh root, old stack gone.
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertDoesNotExist()
        assertFalse("outgoing lifetime must have ended", genOne.lifetime.isActive)

        assertSame(genOne.database, genTwo.database)
        val names = runBlocking {
            genTwo.database.tagDao.searchByPrefix(HANDSHAKE_SENTINEL).map { it.name }
        }
        assertTrue(
            "the new generation must serve the pre-swap write; got $names",
            names.contains(HANDSHAKE_SENTINEL),
        )

        // Recreation restores the new generation only.
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AppRoot").assertIsDisplayed()
        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertDoesNotExist()
    }

    private companion object {
        const val HANDSHAKE_SENTINEL = "runtime-handshake-sentinel"
    }
}
