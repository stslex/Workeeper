// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.exercise.ui.ExerciseEditScreen
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import kotlinx.collections.immutable.persistentListOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Smoke
@RunWith(AndroidJUnit4::class)
class ExerciseScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun createState(): State = State.create(uuid = null).copy(isLoading = false)

    private fun editState(): State = State.create(uuid = "uuid-1").copy(
        mode = Mode.Edit(isCreate = false),
        isLoading = false,
        name = "Bench",
    )

    @Test
    fun edit_inCreateMode_rendersInlinePlanEditorBodyAndAddSetButton() {
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseEditScreen(state = createState(), consume = {})
            }
        }

        // Empty plan in create-mode shows the body's empty hint, not the legacy
        // "Add plan" button (`ExerciseEditPlanEditButton`).
        composeTestRule.onNodeWithTag("PlanEditorBodyEmpty").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ExerciseEditPlanAddSetButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ExerciseEditPlanEditButton").assertDoesNotExist()
    }

    @Test
    fun edit_inCreateMode_rendersPlanRowsWhenAdhocPlanIsPresent() {
        val state = createState().copy(
            adhocPlan = persistentListOf(
                PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                PlanSetUiModel(weight = 90.0, reps = 5, type = SetTypeUiModel.WORK),
            ),
        )

        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseEditScreen(state = state, consume = {})
            }
        }

        composeTestRule.onNodeWithTag("PlanEditorBodyRow_0").assertIsDisplayed()
        composeTestRule.onNodeWithTag("PlanEditorBodyRow_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ExerciseEditPlanAddSetButton").assertIsDisplayed()
    }

    @Test
    fun edit_inEditModeForExistingExercise_rendersLegacyAddPlanButton() {
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseEditScreen(state = editState(), consume = {})
            }
        }

        // Edit-mode for an existing exercise still routes to the full-screen
        // PlanEditor route via the legacy summary + button surface.
        composeTestRule.onNodeWithTag("ExerciseEditPlanEditButton").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ExerciseEditPlanAddSetButton").assertDoesNotExist()
        composeTestRule.onNodeWithTag("PlanEditorBodyEmpty").assertDoesNotExist()
    }
}
