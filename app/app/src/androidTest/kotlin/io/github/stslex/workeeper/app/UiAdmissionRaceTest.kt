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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * THE STALE-REGION PROOF (Phase 5 R3, spec §8.4 step 1 — round-3 blocker 5), composed.
 *
 * `App()`'s generation region takes its admission **during composition** —
 * `remember(id) { GenerationAdmission(holder, id) }` — and composes `AppGenerationContent()` only
 * when that grant was given. The property this file pins is the consequence: **a generation that
 * is already retired when its region composes resolves ZERO dependencies and leaks no grant.**
 *
 * The region under test is the REAL one: this test composes the production [App] composable, and
 * the admission it asks for goes through the production `AppUiGenerationsHolder` seam that
 * `TestApplication` implements. The two harness-side knobs are honest accounting rather than
 * stand-ins:
 *  - `MetroTestGraphHolder.retireUiGeneration(id)` makes `admitUiGeneration(id)` answer `null`,
 *    which is exactly what the runtime's own gate answers for a generation it has handed over;
 *  - `MetroTestGraphHolder.appRootDepsResolutions` counts `TestApplication.appRootDeps()`, and
 *    `App()` is the only caller of that method in the entire app (measured) — it resolves it once
 *    per composed region, to build the `AppRootViewModel` that ctor-captures the generation's
 *    `navigatorEventBus`. Zero is therefore a literal "this region reached the graph zero times".
 *
 * **What the third test drives, and how the race is made deterministic.** With
 * `mainClock.autoAdvance = false` no frame runs until this test asks for one, so the publication
 * of generation N+1 and the recomposition that would compose its region are separated by a window
 * the test owns. The retirement is applied INSIDE that window — after the phase is published,
 * before any frame composes it. No `waitForIdle` is used to close the race; the clock is.
 *
 * Boundary of the claim, stated rather than implied: this proves the REFUSAL path — a region that
 * composes under an already-retired id resolves nothing. It does NOT prove the other half of
 * `GenerationAdmission`'s leak-freedom, `onAbandoned` (a composition composed and then thrown away
 * before it is applied): Compose offers no supported way to force an abandoned composition from a
 * test, so that path stays covered by construction (the `RememberObserver` contract) rather than
 * by execution here.
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

    /**
     * The positive control. Without it "resolved nothing" below would be vacuous — a region that
     * resolves nothing when it IS admitted proves nothing about admission.
     */
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
        assertTrue(
            "an admitted region resolves the generation's app-scope deps",
            MetroTestGraphHolder.appRootDepsResolutions.get() >= 1,
        )
    }

    /**
     * Retired BEFORE the region ever composes: the grant is refused during composition, so the
     * content the grant gates is never composed and nothing behind it is ever resolved.
     */
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
        // The published phase IS a Generation, so an absent AppRoot means the REGION rendered
        // nothing — not that the shell was showing the Transitioning interstitial instead.
        composeRule.onNodeWithTag(APP_ROOT_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(TRANSITIONING_TAG).assertDoesNotExist()
    }

    /**
     * THE RACE. Generation N+1 is published while the composition is live, and retired inside the
     * window between that publication and the frame that would compose its region. The stale
     * region must resolve nothing.
     */
    @Test
    fun retirementBetweenPublicationAndFrame_resolvesNothing() {
        composeRule.setContent { App() }
        composeRule.waitForIdle()
        val generationOne = publishedGenerationId()
        composeRule.onNodeWithTag(APP_ROOT_TAG).assertIsDisplayed()

        // From here the test owns the apply boundary: nothing recomposes until a frame is asked
        // for, which is what makes the window below a window and not a hope.
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
