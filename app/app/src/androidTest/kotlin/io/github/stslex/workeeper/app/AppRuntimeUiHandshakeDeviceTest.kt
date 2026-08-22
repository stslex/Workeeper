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
import io.github.stslex.workeeper.runtime.StartupProcessor
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
 * THE COMPOSED REAL-HANDSHAKE PROOF (Phase 5 R2 finding 3 / spec §8.7): a REAL [AppRuntime]
 * drives the whole app shell through [MetroTestGraphHolder.runtimeDelegate] — `TestApplication`
 * serves the runtime's OWN `uiPhases` stream, and the composition's attach/dispose callbacks land
 * in the runtime's OWN generation-id-bound gate. Nothing in the handshake is a harness stand-in.
 *
 * What one graph-only `reinitialize()` against live composition proves end-to-end:
 *  1. the transition's quiesce AWAITS the UI: it publishes `Transitioning`, composition tears the
 *     old generation region down, the region's disposal callback releases the runtime's gate, and
 *     only then does the new generation publish — the test observing `Published` (not the
 *     `Aborted` disposal-timeout) IS the closed loop, because the gate can only open when the
 *     production `onUiGenerationDisposed` path fired with the OUTGOING id (known-negative: with
 *     the dispose callback severed, this exact test goes red with the disposal-timeout abort —
 *     executed and reverted per §11.4, not committed);
 *  2. the published successor re-keys `App()`: Nav3 restarts at the root and the old generation's
 *     stack does not survive;
 *  3. the graph-only handover carries the SAME database object into the new generation (a
 *     pre-swap write is served by the new generation's DAO);
 *  4. an ordinary Activity recreation AFTER the swap restores the NEW generation's saveable slot,
 *     never the old generation's entries.
 *
 * Boundary of the claim: the swap here is GRAPH-ONLY (no file replacement, no DB close) — the
 * file-swap transaction's device proof is [RuntimeGenerationSwapDeviceTest], which runs the
 * runtime directly WITHOUT composition. Together they cover both halves; neither claims the
 * other's.
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
                    StartupProcessor(isLowRamDevice = { false }).preflightAndArm(
                        graph = generation.graph,
                        appDatabase = generation.database,
                        lifetime = generation.lifetime,
                    )
                },
            )
            MetroTestGraphHolder.runtimeDelegate = runtime
        }

        override fun after() {
            // The activity is already torn down (this rule is outermost); its disposal signals
            // have drained into the runtime. Release the LAST generation's resources.
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

        // Leave the root: generation 1's stack now shows Trainings.
        composeRule.onNodeWithTag(BottomBarItem.TRAININGS.testTag).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertIsDisplayed()

        // Pre-swap write through generation 1 — the graph-only handover must serve it after.
        runBlocking { genOne.database.tagDao.insert(TagEntity(name = HANDSHAKE_SENTINEL)) }

        // Proof 1 — the closed handshake loop. The submission runs on a background thread while
        // the test thread PUMPS composition via waitUntil: the compose rule owns the frame clock,
        // so a blocked test thread would starve recomposition and fake a disposal timeout. The
        // quiesce completes only when the LIVE region's disposal releases the id-bound gate
        // (a dropped signal turns this into the bounded Aborted disposal-timeout, never a hang).
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

        // Proof 2 — the successor re-keys App(): fresh root, old stack gone.
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("HomeGraph").assertIsDisplayed()
        composeRule.onNodeWithTag("AllTrainingsGraph").assertDoesNotExist()
        assertFalse("outgoing lifetime must have ended", genOne.lifetime.isActive)

        // Proof 3 — same-database handover, live.
        assertSame(genOne.database, genTwo.database)
        val names = runBlocking {
            genTwo.database.tagDao.searchByPrefix(HANDSHAKE_SENTINEL).map { it.name }
        }
        assertTrue(
            "the new generation must serve the pre-swap write; got $names",
            names.contains(HANDSHAKE_SENTINEL),
        )

        // Proof 4 — recreation restores the NEW generation only.
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
