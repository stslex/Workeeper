package io.github.stslex.workeeper.feature.single_training

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.single_training.ui.SingleTrainingsScreen
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Smoke
@RunWith(AndroidJUnit4::class)
class SingleTrainingScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun singleTrainingScreen_displaysCorrectly() {
        val state = createMockState()

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            SingleTrainingsScreen(
                state = state,
                consume = {}
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("SingleTrainingScreen")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("SingleTrainingToolbar")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("SingleTrainingContent")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("SingleTrainingCreateExerciseButton")
            .assertIsDisplayed()
    }

    @Test
    fun singleTrainingScreen_createExerciseButton_isClickable() {
        val state = createMockState()
        var createExerciseClicked = false

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            SingleTrainingsScreen(
                state = state,
                consume = { action ->
                    if (action is TrainingStore.Action.Click.CreateExercise) {
                        createExerciseClicked = true
                    }
                }
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("SingleTrainingCreateExerciseButton")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.mainClock.advanceTimeBy(100)

        assert(createExerciseClicked) { "Create exercise button click was not captured" }
    }

    @Test
    fun singleTrainingScreen_toolbar_displaysCorrectly() {
        val state = createMockState()

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            SingleTrainingsScreen(
                state = state,
                consume = {}
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("SingleTrainingToolbar")
            .assertIsDisplayed()
    }

    @Test
    fun singleTrainingScreen_withExistingTraining_displaysAllFields() {
        val state = createMockState(
            uuid = "existing-uuid",
            name = "Existing Training"
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            SingleTrainingsScreen(
                state = state,
                consume = {}
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("SingleTrainingScreen")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("SingleTrainingCreateExerciseButton")
            .assertIsDisplayed()
    }

    @Test
    fun singleTrainingScreen_newTraining_displaysAllFields() {
        val state = createMockState(
            uuid = "",
            name = ""
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            SingleTrainingsScreen(
                state = state,
                consume = {}
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("SingleTrainingScreen")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("SingleTrainingCreateExerciseButton")
            .assertIsDisplayed()
    }

    private fun createMockState(
        uuid: String = "test-uuid",
        name: String = "Test Training"
    ): TrainingStore.State {
        return TrainingStore.State(
            training = TrainingUiModel(
                uuid = uuid,
                name = PropertyHolder.StringProperty.new(name),
                labels = persistentListOf(),
                exercises = persistentListOf(),
                date = PropertyHolder.DateProperty.now(),
                isMenuOpen = false,
                menuItems = persistentSetOf()
            ),
            dialogState = DialogState.Closed,
            pendingForCreateUuid = "",
            initialTrainingUiModel = TrainingUiModel.INITIAL
        )
    }
}
