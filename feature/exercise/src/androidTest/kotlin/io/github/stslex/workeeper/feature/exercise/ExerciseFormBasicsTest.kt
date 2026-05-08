// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseDao
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.test.TestActivity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.feature.exercise.ui.exerciseGraph
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

/**
 * Pilot integration test that validates the new feature-test infrastructure end-to-end.
 *
 * Boots a real Hilt graph against an in-memory `AppDatabase` (via
 * `core/data/database-test`'s `TestDatabaseModule`) and mounts the Exercise feature graph
 * inside [TestActivity]. The single scenario, F-02, types a name into the create form
 * and taps Save; the assertion is that the exercise lands in `exercise_table` with the
 * expected default fields (catalogued in `documentation/test-scenarios/exercise.md`).
 *
 * If this passes and remains stable, the rest of the Exercise scenarios follow this same
 * skeleton.
 */
@Regression
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
internal class ExerciseFormBasicsTest {

    @get:Rule(order = 0)
    val hiltRule: HiltAndroidRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<TestActivity>()

    @Inject
    lateinit var database: AppDatabase

    @Inject
    lateinit var exerciseDao: ExerciseDao

    @Before
    fun setup() {
        hiltRule.inject()
        // The Hilt singleton-scoped AppDatabase survives across tests in the same JVM
        // process. Reset table state per @Test so scenarios stay isolated.
        runBlocking { database.clearAllTables() }
    }

    @After
    fun tearDown() {
        // Don't close the database here — Hilt owns the singleton. clearAllTables() in
        // @Before resets state for the next test.
    }

    /**
     * F-02 — minimal happy path: type a name, tap Save, exercise lands in DB.
     *
     * Pilot scenario validating the full test infrastructure stack: TestActivity host,
     * Hilt graph, in-memory Room, real coroutine dispatchers.
     */
    @Test
    fun f02_create_with_name_only_persists() {
        composeRule.setContent {
            // ExerciseEditScreen reads from `LocalAppColors` (AppUi.colors), so the
            // mount has to live inside `AppTheme` exactly like the production
            // hierarchy. Same wrap that the smoke `ExerciseScreenTest` uses.
            AppTheme(themeMode = ThemeMode.LIGHT) {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = Screen.Exercise(uuid = null),
                ) {
                    exerciseGraph()
                }
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
