// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.test.TestActivity
import io.github.stslex.workeeper.core.ui.test.TestSingleScreenHost
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
 * F-02 create-form persistence seam over the real app graph and an in-memory Room DB. Hosted by the
 * empty [TestActivity], since `MainActivity` sets its own content in `onCreate`.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class ExerciseCreatePersistenceTest {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<TestActivity>()

    private val exerciseDao get() = metroRule.appDatabase.exerciseDao

    @Test
    fun f02_create_with_name_only_persists() {
        composeRule.setContent {
            // ExerciseEditScreen reads `LocalAppColors`, so the mount must live inside `AppTheme`.
            AppTheme(themeMode = ThemeMode.LIGHT) {
                TestSingleScreenHost(start = Screen.Exercise(uuid = null)) {
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
