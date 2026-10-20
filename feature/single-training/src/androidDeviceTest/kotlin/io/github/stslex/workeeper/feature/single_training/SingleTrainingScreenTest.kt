// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.single_training.mvi.model.TrainingExerciseItem
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStore.State
import io.github.stslex.workeeper.feature.single_training.ui.TrainingEditScreen
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The training editor's card (ED14), and the head's tap-target guarantee: expansion is the
 * head's tap, but the `✕` and the drag handle are its CHILDREN, and children see pointer
 * events first — so the `✕`'s own clickable consumes its tap and the head never toggles
 * under it. These are the assertions the card's KDoc promises.
 */
@Smoke
@RunWith(AndroidJUnit4::class)
class SingleTrainingScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun editState(expanded: String? = null): State = State
        .create(uuid = "t-1")
        .copy(
            mode = State.Mode.Edit(isCreate = false),
            isLoading = false,
            name = "Push",
            expandedExerciseUuids = persistentSetOf(*listOfNotNull(expanded).toTypedArray()),
            exercises = persistentListOf(
                TrainingExerciseItem(
                    exerciseUuid = "ex-1",
                    exerciseName = "Bench",
                    exerciseType = ExerciseTypeUiModel.WEIGHTED,
                    tags = persistentListOf(),
                    position = 0,
                    planSets = null,
                    planSummary = "",
                ),
            ),
        )

    @Test
    fun cardHead_tapTogglesExpansion() {
        val capture = createActionCapture<Action>()
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                TrainingEditScreen(state = editState(), consume = capture)
            }
        }

        composeTestRule.onNodeWithTag("TrainingExerciseCardHead_ex-1")
            .performScrollTo()
            .performClick()

        capture.assertCaptured<Action.Click.OnExerciseCardToggle>()
    }

    @Test
    fun cardHead_removeIsNotSwallowedByTheHeadTap() {
        val capture = createActionCapture<Action>()
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                TrainingEditScreen(state = editState(), consume = capture)
            }
        }

        composeTestRule.onNodeWithTag("TrainingExerciseCardRemove_ex-1")
            .performScrollTo()
            .performClick()

        // The ✕ emits its own action, and the head's toggle does NOT fire for the same tap —
        // the child's clickable consumed it.
        capture.assertCaptured<Action.Click.OnExerciseRemove>()
        assertTrue(capture.captured<Action.Click.OnExerciseCardToggle>().isEmpty())
    }

    @Test
    fun expandedCard_showsThePlanBodyWithItsSetbar() {
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                TrainingEditScreen(state = editState(expanded = "ex-1"), consume = {})
            }
        }

        composeTestRule.onNodeWithTag("PlanEditorBodyEmpty").performScrollTo().assertIsDisplayed()
    }
}
