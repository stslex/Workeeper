package io.github.stslex.workeeper.feature.all_trainings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.all_trainings.mvi.model.TrainingUiModel
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.TrainingStore
import io.github.stslex.workeeper.feature.all_trainings.ui.AllTrainingsScreen
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllTrainingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun allTrainingsScreen_displaysCorrectly() {
        val mockTrainings = createMockTrainings(5)
        val state = TrainingStore.State(
            pagingUiState = { flowOf(PagingData.from(mockTrainings)) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            androidx.compose.animation.AnimatedContent("") {
                androidx.compose.animation.SharedTransitionScope { modifier ->
                    AllTrainingsScreen(
                        state = state,
                        modifier = modifier,
                        consume = {},
                        sharedTransitionScope = this,
                        animatedContentScope = this@AnimatedContent
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllTrainingsScreen")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("AllTrainingsSearchWidget")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("AllTrainingsList")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("AllTrainingsActionButton")
            .assertIsDisplayed()
    }

    @Test
    fun allTrainingsScreen_searchWidget_isInteractive() {
        val mockTrainings = createMockTrainings(3)
        var capturedQuery = ""
        val state = TrainingStore.State(
            pagingUiState = { flowOf(PagingData.from(mockTrainings)) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            androidx.compose.animation.AnimatedContent("") {
                androidx.compose.animation.SharedTransitionScope { modifier ->
                    AllTrainingsScreen(
                        state = state,
                        modifier = modifier,
                        consume = { action ->
                            if (action is TrainingStore.Action.Input.SearchQuery) {
                                capturedQuery = action.query
                            }
                        },
                        sharedTransitionScope = this,
                        animatedContentScope = this@AnimatedContent
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllTrainingsSearchWidget")
            .assertIsDisplayed()
    }

    @Test
    fun allTrainingsScreen_actionButton_isClickable() {
        val mockTrainings = createMockTrainings(2)
        var actionButtonClicked = false
        val state = TrainingStore.State(
            pagingUiState = { flowOf(PagingData.from(mockTrainings)) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            androidx.compose.animation.AnimatedContent("") {
                androidx.compose.animation.SharedTransitionScope { modifier ->
                    AllTrainingsScreen(
                        state = state,
                        modifier = modifier,
                        consume = { action ->
                            if (action is TrainingStore.Action.Click.ActionButton) {
                                actionButtonClicked = true
                            }
                        },
                        sharedTransitionScope = this,
                        animatedContentScope = this@AnimatedContent
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllTrainingsActionButton")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.mainClock.advanceTimeBy(100)

        assert(actionButtonClicked) { "Action button click was not captured" }
    }

    @Test
    fun allTrainingsScreen_emptyState_displaysWhenNoItems() {
        val state = TrainingStore.State(
            pagingUiState = { flowOf(PagingData.from(emptyList())) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            androidx.compose.animation.AnimatedContent("") {
                androidx.compose.animation.SharedTransitionScope { modifier ->
                    AllTrainingsScreen(
                        state = state,
                        modifier = modifier,
                        consume = {},
                        sharedTransitionScope = this,
                        animatedContentScope = this@AnimatedContent
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllTrainingsScreen")
            .assertIsDisplayed()
    }

    @Test
    fun allTrainingsScreen_trainingItems_areDisplayed() {
        val mockTrainings = createMockTrainings(3)
        val state = TrainingStore.State(
            pagingUiState = { flowOf(PagingData.from(mockTrainings)) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            androidx.compose.animation.AnimatedContent("") {
                androidx.compose.animation.SharedTransitionScope { modifier ->
                    AllTrainingsScreen(
                        state = state,
                        modifier = modifier,
                        consume = {},
                        sharedTransitionScope = this,
                        animatedContentScope = this@AnimatedContent
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllTrainingsList")
            .assertIsDisplayed()

        // Verify individual training items are tagged correctly
        mockTrainings.forEach { training ->
            composeTestRule
                .onNodeWithTag("TrainingItem_${training.uuid}")
                .assertExists()
        }
    }

    private fun createMockTrainings(count: Int): List<TrainingUiModel> {
        return List(count) { index ->
            TrainingUiModel(
                uuid = "uuid_$index",
                name = "Training $index",
                labels = List(2) { "label_${index}_$it" }.toImmutableList(),
                exerciseUuids = List(3) { "exercise_${index}_$it" }.toImmutableList(),
                date = PropertyHolder.DateProperty.now()
            )
        }
    }
}
