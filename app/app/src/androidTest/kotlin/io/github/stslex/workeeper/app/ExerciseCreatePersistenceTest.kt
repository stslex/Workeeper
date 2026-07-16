// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.test.TestActivity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.feature.exercise.ui.exerciseGraph
import io.github.stslex.workeeper.harness.MetroTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * F-02 — the create-form persistence seam, end to end: type a name into the Exercise create form, tap
 * Save, and assert the exercise lands in `exercise_table` with the expected default fields.
 *
 * App-Scope Collapse Step 6 (Phase 3.6): the real-graph half of the F-02 scenario, RELOCATED from
 * `feature/exercise` androidTest into the consolidated `:app:app` suite — the only source set that can
 * build the app graph (`buildAppGraph`/`AppGraph` are `:app:app`-internal). This mirrors the
 * [RecoveryActivityDbFreeTest] relocation: [MetroTestRule] installs a per-test app graph over an
 * in-memory `AppDatabase` root, and `exerciseGraph()` resolves `ExerciseFeature`'s Store through
 * `context.appGraphContract()` — so the Store→interactor→repository→Room write path runs against that
 * in-memory DB. The assertions are the SAME ones the pre-cut Hilt version asserted (commit `88031508`),
 * restored verbatim; [ExerciseFormBasicsTest] in `feature/exercise` keeps the lighter render+dispatch
 * coverage.
 *
 * Hosted by the empty [TestActivity] (not `MainActivity`, which sets its own content in `onCreate`) so
 * `composeRule.setContent { ... }` mounts the feature NavHost directly — the same host the pre-cut
 * version used.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class ExerciseCreatePersistenceTest {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<TestActivity>()

    private val exerciseDao get() = metroRule.appDatabase.exerciseDao

    /**
     * F-02 — minimal happy path: type a name, tap Save, exercise lands in DB with the create-mode
     * defaults. Exercises the full stack: MainActivity host, the per-test Metro app graph, in-memory Room,
     * real coroutine dispatchers.
     */
    @Test
    fun f02_create_with_name_only_persists() {
        composeRule.setContent {
            val navController = rememberNavController()
            NavHost(
                navController = navController,
                startDestination = Screen.Exercise(uuid = null),
            ) {
                exerciseGraph()
            }
        }

        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("ExerciseEditNameField")
            .performTextInput("Bench Press")

        composeRule
            .onNodeWithTag("ExerciseEditSaveButton")
            .performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runBlocking { exerciseDao.getAllActive().size } == 1
        }

        val rows = runBlocking { exerciseDao.getAllActive() }
        assertEquals(1, rows.size)
        with(rows.single()) {
            assertEquals("Bench Press", name)
            assertEquals(ExerciseTypeEntity.WEIGHTED, type)
            assertEquals("", description.orEmpty())
            assertNull(lastAdhocSets)
            assertNull(imagePath)
            assertNull(archivedAt)
        }
    }
}
