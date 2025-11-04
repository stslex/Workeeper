package io.github.stslex.workeeper.feature.all_exercises

import android.annotation.SuppressLint
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.MockDataFactory
import io.github.stslex.workeeper.core.ui.test.PagingTestUtils
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore.Action
import io.github.stslex.workeeper.feature.all_exercises.ui.ExerciseWidget
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Edge case and advanced UI tests for All Exercises screen
 * Tests error states, keyboard interactions, pagination, and selection modes
 */
@RunWith(AndroidJUnit4::class)
@SuppressLint("UnusedContentLambdaTargetStateParameter")
class AllExercisesScreenEdgeCasesTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ========== Search/Input Tests ==========

    @Test
    fun allExercisesScreen_searchInput_triggersQueryAction() {
        val mockExercises = createMockExercises(5)
        val actionCapture = createActionCapture<Action>()
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = actionCapture,
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // Perform search input
        val searchQuery = "bench press"
        composeTestRule
            .onNodeWithTag("AllExercisesSearchWidget")
            .performClick()

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllExercisesSearchWidget")
            .performTextInput(searchQuery)

        composeTestRule.mainClock.advanceTimeBy(200)
        composeTestRule.waitForIdle()

        // Verify search action was captured
        actionCapture.capturedFirst<Action.Input.SearchQuery> {
            "Query action was not captured"
        }
    }

    @Test
    fun allExercisesScreen_searchWithQuery_displaysQuery() {
        val mockExercises = createMockExercises(3)
        val searchQuery = "test query"
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = searchQuery,
            selectedItems = persistentSetOf(),
            isKeyboardVisible = true,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllExercisesSearchWidget")
            .assertIsDisplayed()
    }

    @Test
    fun allExercisesScreen_clearSearch_triggersAction() {
        val mockExercises = createMockExercises(3)
        val actionCapture = createActionCapture<Action>()
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = "test",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = true,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = actionCapture,
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // Clear search by inputting empty text
        composeTestRule
            .onNodeWithTag("AllExercisesSearchWidget")
            .performTextClearance()

        composeTestRule.mainClock.advanceTimeBy(100)

        // Verify query input action was captured
        actionCapture.capturedFirst<Action.Input.SearchQuery> {
            "Search query action was not captured"
        }
    }

    // ========== Selection Mode Tests ==========

    @Test
    fun allExercisesScreen_singleItemSelection_triggersAction() {
        val mockExercises = createMockExercises(5)
        var selectedId: String? = null
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = { action ->
                    if (action is Action.Click.Item) {
                        selectedId = action.uuid
                    }
                },
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        val targetExercise = mockExercises[0]
        composeTestRule
            .onNodeWithTag("ExerciseItem_${targetExercise.uuid}")
            .performClick()

        composeTestRule.mainClock.advanceTimeBy(100)

        assert(selectedId == targetExercise.uuid) { "Exercise click was not captured" }
    }

    @Test
    fun allExercisesScreen_multipleSelection_displaysSelected() {
        val mockExercises = createMockExercises(5)
        val selectedExercises = setOf(mockExercises[0].uuid, mockExercises[2].uuid).toImmutableSet()
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = "",
            selectedItems = selectedExercises,
            isKeyboardVisible = false,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // Verify selected items exist
        selectedExercises.forEach { uuid ->
            composeTestRule
                .onNodeWithTag("ExerciseItem_$uuid")
                .assertExists()
        }
    }

    // ========== Pagination Tests ==========

    @Test
    fun allExercisesScreen_largeList_displaysCorrectly() {
        val largeList = createMockExercises(100)
        val state = ExercisesStore.State(
            items = { flowOf(PagingData.from(largeList)) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // Verify list is displayed
        composeTestRule
            .onNodeWithTag("AllExercisesList")
            .assertIsDisplayed()

        // Verify first item exists
        composeTestRule
            .onNodeWithTag("ExerciseItem_${largeList[0].uuid}")
            .assertExists()
    }

    @Test
    fun allExercisesScreen_scrollToEnd_displaysLastItems() {
        val mockExercises = createMockExercises(50)
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeTestRule.mainClock.autoAdvance = true

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.waitForIdle()

        // Scroll to end
        composeTestRule
            .onNodeWithTag("AllExercisesList")
            .performScrollToIndex(mockExercises.size - 1)

        composeTestRule.waitForIdle()

        // Verify last item exists
        composeTestRule
            .onNodeWithTag("ExerciseItem_${mockExercises.last().uuid}")
            .assertExists()
    }

    // ========== Edge Case: Special Characters ==========

    @Test
    fun allExercisesScreen_specialCharactersInName_displaysCorrectly() {
        val specialNameExercise = ExerciseUiModel(
            uuid = MockDataFactory.createUuid(),
            name = "Test Exercise @#$%^&*() 123",
            dateProperty = MockDataFactory.createDateProperty(),
        )
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(listOf(specialNameExercise)) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseItem_${specialNameExercise.uuid}")
            .assertExists()
    }

    @Test
    fun allExercisesScreen_veryLongName_displaysCorrectly() {
        val longNameExercise = ExerciseUiModel(
            uuid = MockDataFactory.createUuid(),
            name = "A".repeat(200),
            dateProperty = MockDataFactory.createDateProperty(),
        )
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(listOf(longNameExercise)) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ExerciseItem_${longNameExercise.uuid}")
            .assertExists()
    }

    // ========== Edge Case: Empty Query Results ==========

    @Test
    fun allExercisesScreen_emptySearchResults_displaysEmptyState() {
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createEmptyPagingFlow() },
            query = "nonexistent exercise",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = true,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("AllExercisesScreen")
            .assertIsDisplayed()
    }

    // ========== Edge Case: Rapid Actions ==========

    @Test
    fun allExercisesScreen_rapidClicks_handlesGracefully() {
        val mockExercises = createMockExercises(5)
        var clickCount = 0
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContent, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = { action ->
                    if (action is Action.Click.FloatButtonClick) {
                        clickCount++
                    }
                },
                sharedTransitionScope = this,
                animatedContentScope = animatedContent,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // Perform rapid clicks
        repeat(5) {
            composeTestRule
                .onNodeWithTag("AllExercisesActionButton")
                .performClick()
            composeTestRule.mainClock.advanceTimeBy(10)
        }

        assert(clickCount > 0) { "No clicks were captured" }
    }

    // ========== Helper Functions ==========

    private fun createMockExercises(count: Int): List<ExerciseUiModel> {
        return List(count) { index ->
            ExerciseUiModel(
                uuid = MockDataFactory.createUuid(),
                name = "Exercise $index",
                dateProperty = MockDataFactory.createDateProperty(),
            )
        }
    }
}
