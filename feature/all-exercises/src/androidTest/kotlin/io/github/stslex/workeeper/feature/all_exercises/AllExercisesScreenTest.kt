package io.github.stslex.workeeper.feature.all_exercises

import android.annotation.SuppressLint
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.MockDataFactory
import io.github.stslex.workeeper.core.ui.test.PagingTestUtils
import io.github.stslex.workeeper.feature.all_exercises.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.ExercisesStore
import io.github.stslex.workeeper.feature.all_exercises.ui.ExerciseWidget
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SuppressLint("UnusedContentLambdaTargetStateParameter")
internal class AllExercisesScreenTest : BaseComposeTest() {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allExercisesScreen_displaysCorrectly() {
        val mockExercises = createMockExercises(5)
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeRule.mainClock.autoAdvance = false

        composeRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AllExercisesScreen")
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag("AllExercisesWidget")
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag("AllExercisesSearchWidget")
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag("AllExercisesList")
            .assertIsDisplayed()

        composeRule
            .onNodeWithTag("AllExercisesActionButton")
            .assertIsDisplayed()
    }

    @Test
    fun allExercisesScreen_actionButton_isClickable() {
        val mockExercises = createMockExercises(2)
        val actionCapture = ActionCapture<ExercisesStore.Action>()
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = actionCapture.consume,
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AllExercisesActionButton")
            .assertIsDisplayed()
            .performClick()

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        actionCapture.assertCaptured { it is ExercisesStore.Action.Click.FloatButtonClick }
            ?: error("FloatButtonClick action was not captured")
    }

    @Test
    fun allExercisesScreen_exerciseItems_areDisplayed() {
        val mockExercises = createMockExercises(3)
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AllExercisesList")
            .assertIsDisplayed()

        // Verify individual exercise items are tagged correctly
        mockExercises.forEach { exercise ->
            composeRule
                .onNodeWithTag("ExerciseItem_${exercise.uuid}")
                .assertExists()
        }
    }

    @Test
    fun allExercisesScreen_emptyState_displaysWhenNoItems() {
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createEmptyPagingFlow() },
            query = "",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AllExercisesScreen")
            .assertIsDisplayed()
    }

    @Test
    fun allExercisesScreen_searchWidget_isDisplayed() {
        val mockExercises = createMockExercises(3)
        val state = ExercisesStore.State(
            items = { PagingTestUtils.createPagingFlow(mockExercises) },
            query = "test query",
            selectedItems = persistentSetOf(),
            isKeyboardVisible = false,
        )

        composeRule.mainClock.autoAdvance = false
        composeRule.setTransitionContent { animatedContentScope, modifier ->
            ExerciseWidget(
                state = state,
                modifier = modifier,
                consume = {},
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                lazyState = rememberLazyListState(),
            )
        }

        composeRule.mainClock.advanceTimeBy(100)
        composeRule.waitForIdle()

        composeRule
            .onNodeWithTag("AllExercisesSearchWidget")
            .assertIsDisplayed()
    }

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
