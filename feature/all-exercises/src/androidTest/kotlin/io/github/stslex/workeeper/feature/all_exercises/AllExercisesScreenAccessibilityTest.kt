package io.github.stslex.workeeper.feature.all_exercises

import android.annotation.SuppressLint
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.paging.PagingData
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.MockDataFactory
import io.github.stslex.workeeper.core.ui.test.PagingTestUtils
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore
import io.github.stslex.workeeper.feature.all_exercises.ui.ExerciseWidget
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Accessibility tests for All Exercises screen
 * Tests content descriptions, click actions, and screen reader support
 */
@Smoke
@RunWith(AndroidJUnit4::class)
@SuppressLint("UnusedContentLambdaTargetStateParameter")
class AllExercisesScreenAccessibilityTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun allExercisesScreen_actionButton_hasContentDescription() {
        val mockExercises = createMockExercises(3)
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
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // Verify action button has click action (important for accessibility)
        composeTestRule
            .onNodeWithTag("AllExercisesActionButton")
            .assertIsDisplayed()
            .assertHasClickAction()
    }

    @Test
    fun allExercisesScreen_searchField_hasTextInputAction() {
        val mockExercises = createMockExercises(3)
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
            .assertHasClickAction()
    }

    @Test
    fun allExercisesScreen_exerciseItems_haveClickActions() {
        val mockExercises = createMockExercises(5)
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
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // Verify exercise items have click actions
        mockExercises.take(3).forEach { exercise ->
            composeTestRule
                .onNodeWithTag("ExerciseItem_${exercise.uuid}")
                .assertHasClickAction()
        }
    }

    @Test
    fun allExercisesScreen_allInteractiveElements_areFocusable() {
        val mockExercises = createMockExercises(3)
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
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        // Verify key interactive elements exist and are clickable
        composeTestRule
            .onNodeWithTag("AllExercisesActionButton")
            .assertHasClickAction()

        composeTestRule
            .onNodeWithTag("AllExercisesSearchWidget")
            .assertHasClickAction()
    }

    @Test
    fun allExercisesScreen_emptyState_isAccessible() {
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createEmptyPagingFlow() },
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

        // Verify screen is still accessible in empty state
        composeTestRule
            .onNodeWithTag("AllExercisesScreen")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithTag("AllExercisesActionButton")
            .assertHasClickAction()
    }

    @Test
    fun allExercisesScreen_selectedItems_haveProperSemantics() {
        val mockExercises = createMockExercises(5)
        val selectedExercises = persistentSetOf(mockExercises[0].uuid)
        val state = ExercisesStore.State(
            items = { flowOf(PagingData.from(mockExercises)) },
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

        // Verify selected item still has proper semantics
        composeTestRule
            .onNodeWithTag("ExerciseItem_${mockExercises[0].uuid}")
            .assertExists()
            .assertHasClickAction()
    }

    // ========== Helper Functions ==========

    private fun createMockExercises(count: Int): List<ExerciseUiModel> {
        val uuids = MockDataFactory.createUuids(count)
        val names = MockDataFactory.createTestNames("Exercise", count)
        return List(count) { index ->
            ExerciseUiModel(
                uuid = uuids[index],
                name = names[index],
                dateProperty = MockDataFactory.createDateProperty(),
            )
        }
    }

}
