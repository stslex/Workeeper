// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.test.TestActivity
import io.github.stslex.workeeper.core.ui.test.TestSingleScreenHost
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import io.github.stslex.workeeper.feature.all_trainings.ui.allTrainingsGraph
import io.github.stslex.workeeper.harness.MetroTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Proves the in-memory `AppDatabase` swapped at the parent graph is visible inside the contributed
 * all-trainings `@GraphExtension`, by seeding a row and reading it off the screen.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalUuidApi::class)
@Regression
@RunWith(AndroidJUnit4::class)
internal class AllTrainingsExtensionDbVisibilityTest {

    @get:Rule(order = 0)
    val metroRule = MetroTestRule()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<TestActivity>()

    private val trainingDao get() = metroRule.appDatabase.trainingDao

    @Test
    fun extension_reads_the_parent_in_memory_database() {
        val name = "ExtensionDbProbe Training"
        runBlocking {
            trainingDao.insert(
                TrainingEntity(
                    uuid = Uuid.random(),
                    name = name,
                    description = null,
                    isAdhoc = false,
                    archived = false,
                    createdAt = 1_000L,
                    archivedAt = null,
                ),
            )
        }

        composeRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                SharedTransitionLayout {
                    TestSingleScreenHost(start = Screen.BottomBar.AllTrainings) {
                        allTrainingsGraph()
                    }
                }
            }
        }

        // If the extension saw a different (empty) DB, this row never appears.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(name).assertIsDisplayed()
    }
}
