package io.github.stslex.workeeper.feature.charts

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.feature.charts.mvi.model.CalendarState
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartsState
import io.github.stslex.workeeper.feature.charts.mvi.model.ChartsType
import io.github.stslex.workeeper.feature.charts.mvi.store.ChartsStore
import io.github.stslex.workeeper.feature.charts.ui.ChartsScreenWidget
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartsScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chartsScreen_datePickersAreDisplayed() {
        val state = ChartsStore.State(
            name = "Test Exercise",
            chartState = ChartsState.Loading,
            startDate = PropertyHolder.DateProperty.now(),
            endDate = PropertyHolder.DateProperty.now(),
            type = ChartsType.EXERCISE,
            calendarState = CalendarState.Closed,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ChartsScreenWidget(
                state = state,
                modifier = modifier,
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                consume = {},
                pagerState = rememberPagerState { 1 },
                chartsListState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("DatePickersWidget")
            .assertIsDisplayed()
    }

    @Test
    fun chartsScreen_typePickerIsDisplayed() {
        val state = ChartsStore.State(
            name = "Test Exercise",
            chartState = ChartsState.Loading,
            startDate = PropertyHolder.DateProperty.now(),
            endDate = PropertyHolder.DateProperty.now(),
            type = ChartsType.EXERCISE,
            calendarState = CalendarState.Closed,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ChartsScreenWidget(
                state = state,
                modifier = modifier,
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                consume = {},
                pagerState = rememberPagerState { 1 },
                chartsListState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ChartsTypePicker")
            .assertIsDisplayed()
    }

    @Test
    fun chartsScreen_loadingState_displaysCorrectly() {
        val state = ChartsStore.State(
            name = "Test Exercise",
            chartState = ChartsState.Loading,
            startDate = PropertyHolder.DateProperty.now(),
            endDate = PropertyHolder.DateProperty.now(),
            type = ChartsType.EXERCISE,
            calendarState = CalendarState.Closed,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ChartsScreenWidget(
                state = state,
                modifier = modifier,
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                consume = {},
                pagerState = rememberPagerState { 1 },
                chartsListState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("ChartsScreenBody")
            .assertIsDisplayed()
    }

    @Test
    fun chartsScreen_emptyState_displaysCorrectly() {
        val state = ChartsStore.State(
            name = "Test Exercise",
            chartState = ChartsState.Empty,
            startDate = PropertyHolder.DateProperty.now(),
            endDate = PropertyHolder.DateProperty.now(),
            type = ChartsType.EXERCISE,
            calendarState = CalendarState.Closed,
        )

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setTransitionContent { animatedContentScope, modifier ->
            ChartsScreenWidget(
                state = state,
                modifier = modifier,
                sharedTransitionScope = this,
                animatedContentScope = animatedContentScope,
                consume = {},
                pagerState = rememberPagerState { 1 },
                chartsListState = rememberLazyListState(),
            )
        }

        composeTestRule.mainClock.advanceTimeBy(100)
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag("EmptyWidget")
            .assertIsDisplayed()
    }
}
