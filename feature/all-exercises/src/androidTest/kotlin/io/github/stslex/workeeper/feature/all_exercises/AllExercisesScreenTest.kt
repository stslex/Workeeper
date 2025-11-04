package io.github.stslex.workeeper.feature.all_exercises

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore
import io.github.stslex.workeeper.feature.all_exercises.ui.ExerciseWidget
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.uuid.Uuid

@RunWith(AndroidJUnit4::class)
class AllExercisesScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun allExercisesScreen_displaysCorrectly() {
        val mockExercises = createMockExercises(5)
        val state = ExercisesStore.State(
            items = { flowOf(PagingData.from(mockExercises)) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            androidx.compose.animation.AnimatedContent("") {
                androidx.compose.animation.SharedTransitionScope {
                    ExerciseWidget(
                        state = state,
                        modifier = it,
                        consume = {},
                        sharedTransitionScope = this,
                        animatedContentScope = this@AnimatedContent,
                        lazyState = LazyListState()
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllExercisesScreen")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("AllExercisesWidget")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("AllExercisesSearchWidget")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("AllExercisesList")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("AllExercisesActionButton")
            .assertIsDisplayed()
    }

    @Test
    fun allExercisesScreen_actionButton_isClickable() {
        val mockExercises = createMockExercises(2)
        var actionButtonClicked = false
        val state = ExercisesStore.State(
            items = { flowOf(PagingData.from(mockExercises)) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            androidx.compose.animation.AnimatedContent("") {
                androidx.compose.animation.SharedTransitionScope {
                    ExerciseWidget(
                        state = state,
                        modifier = it,
                        consume = { action ->
                            if (action is ExercisesStore.Action.Click.FloatButtonClick) {
                                actionButtonClicked = true
                            }
                        },
                        sharedTransitionScope = this,
                        animatedContentScope = this@AnimatedContent,
                        lazyState = LazyListState()
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllExercisesActionButton")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.mainClock.advanceTimeBy(100)

        assert(actionButtonClicked) { "Action button click was not captured" }
    }

    @Test
    fun allExercisesScreen_exerciseItems_areDisplayed() {
        val mockExercises = createMockExercises(3)
        val state = ExercisesStore.State(
            items = { flowOf(PagingData.from(mockExercises)) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            androidx.compose.animation.AnimatedContent("") {
                androidx.compose.animation.SharedTransitionScope {
                    ExerciseWidget(
                        state = state,
                        modifier = it,
                        consume = {},
                        sharedTransitionScope = this,
                        animatedContentScope = this@AnimatedContent,
                        lazyState = LazyListState()
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllExercisesList")
            .assertIsDisplayed()

        // Verify individual exercise items are tagged correctly
        mockExercises.forEach { exercise ->
            composeTestRule
                .onNodeWithTag("ExerciseItem_${exercise.uuid}")
                .assertExists()
        }
    }

    @Test
    fun allExercisesScreen_emptyState_displaysWhenNoItems() {
        val state = ExercisesStore.State(
            items = { flowOf(PagingData.from(emptyList())) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            androidx.compose.animation.AnimatedContent("") {
                androidx.compose.animation.SharedTransitionScope {
                    ExerciseWidget(
                        state = state,
                        modifier = it,
                        consume = {},
                        sharedTransitionScope = this,
                        animatedContentScope = this@AnimatedContent,
                        lazyState = LazyListState()
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllExercisesScreen")
            .assertIsDisplayed()
    }

    @Test
    fun allExercisesScreen_searchWidget_isDisplayed() {
        val mockExercises = createMockExercises(3)
        val state = ExercisesStore.State(
            items = { flowOf(PagingData.from(mockExercises)) },
            query = "test query",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            androidx.compose.animation.AnimatedContent("") {
                androidx.compose.animation.SharedTransitionScope {
                    ExerciseWidget(
                        state = state,
                        modifier = it,
                        consume = {},
                        sharedTransitionScope = this,
                        animatedContentScope = this@AnimatedContent,
                        lazyState = LazyListState()
                    )
                }
            }
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllExercisesSearchWidget")
            .assertIsDisplayed()
    }

    private fun createMockExercises(count: Int): List<ExerciseUiModel> {
        return List(count) { index ->
            ExerciseUiModel(
                uuid = Uuid.random().toString(),
                name = "Exercise $index",
                dateProperty = PropertyHolder.DateProperty.now()
            )
        }
    }
}
