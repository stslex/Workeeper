// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.test.BaseComposeTest
import io.github.stslex.workeeper.core.ui.test.annotations.Smoke
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartFooterStatsUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPointUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartReadoutUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ExercisePickerItemUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.github.stslex.workeeper.feature.exercise_chart.ui.ExerciseChartScreen
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@Suppress("MagicNumber")
@Smoke
@RunWith(AndroidJUnit4::class)
class ExerciseChartScreenTest : BaseComposeTest() {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val basePoints = persistentListOf(
        ChartPointUiModel(LocalDate.of(2026, 4, 5), 0L, "session-1", 80.0, 1),
        ChartPointUiModel(LocalDate.of(2026, 4, 12), 0L, "session-2", 90.0, 1),
        ChartPointUiModel(LocalDate.of(2026, 4, 19), 0L, "session-3", 95.0, 1),
        ChartPointUiModel(LocalDate.of(2026, 4, 26), 0L, "session-4", 105.0, 2),
    )

    private val baseFooter = ChartFooterStatsUiModel(
        minTitle = "Minimum",
        minValue = "80",
        maxTitle = "Maximum",
        maxValue = "105",
        lastTitle = "Last",
        lastValue = "105",
        unit = "kg",
    )

    private val baseReadout = ChartReadoutUiModel(
        metricName = "Heaviest weight",
        isRecord = false,
        caption = "Apr 26 2026 · 2 sets",
        value = "105",
        unit = "kg",
    )

    private fun baseState(): State =
        State.create(initialUuid = "uuid-1").copy(
            isLoading = false,
            selectedExercise = ExercisePickerItemUiModel(
                "uuid-1",
                "Bench press",
                ExerciseTypeUiModel.WEIGHTED,
            ),
            recentExercises = persistentListOf(
                ExercisePickerItemUiModel(
                    "uuid-1",
                    "Bench press",
                    ExerciseTypeUiModel.WEIGHTED,
                ),
            ),
            points = basePoints,
            footerStats = baseFooter,
            activeIndex = 3,
            readout = baseReadout,
        )

    @Test
    fun chart_rendersReadout_whenReadoutIsPresent() {
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(state = baseState(), consume = {})
            }
        }

        composeTestRule.onNodeWithTag("ChartReadout").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ChartReadoutRecordDot").assertDoesNotExist()
    }

    @Test
    fun chart_rendersRecordDot_whenReadoutIsRecord() {
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(
                    state = baseState().copy(
                        readout = baseReadout.copy(isRecord = true),
                    ),
                    consume = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ChartReadoutRecordDot").assertIsDisplayed()
    }

    @Test
    fun chart_pressNearLeftEdge_dispatchesOnScrubToFirstIndex() {
        val capture = createActionCapture<Action>()
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(state = baseState(), consume = capture)
            }
        }

        composeTestRule.onNodeWithTag("ChartCanvas").performTouchInput {
            down(Offset(1f, height / 2f))
            up()
        }

        assertEquals(0, capture.capturedFirst<Action.Click.OnScrub>().index)
    }

    @Test
    fun chart_pressNearRightEdge_dispatchesOnScrubToLastIndex() {
        val capture = createActionCapture<Action>()
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(state = baseState(), consume = capture)
            }
        }

        composeTestRule.onNodeWithTag("ChartCanvas").performTouchInput {
            down(Offset(width - 1f, height / 2f))
            up()
        }

        assertEquals(3, capture.capturedFirst<Action.Click.OnScrub>().index)
    }

    @Test
    fun chart_sameDaySessions_dispatchesBothScrubIndices() {
        val day = LocalDate.of(2026, 4, 28)
        val capture = createActionCapture<Action>()
        val state = baseState().copy(
            points = persistentListOf(
                ChartPointUiModel(day, 0L, "morning", 80.0, 2),
                ChartPointUiModel(day, 0L, "evening", 100.0, 1),
            ),
            activeIndex = 1,
        )
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(state = state, consume = capture)
            }
        }

        composeTestRule.onNodeWithTag("ChartCanvas").performTouchInput {
            down(Offset(1f, height / 2f))
            up()
            down(Offset(width - 1f, height / 2f))
            up()
        }

        assertEquals(
            listOf(0, 1),
            capture.captured<Action.Click.OnScrub>().map { it.index },
        )
    }

    @Test
    fun chart_tappingExerciseHeader_dispatchesOnPickerOpen() {
        val capture = createActionCapture<Action>()
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(state = baseState(), consume = capture)
            }
        }

        composeTestRule.onNodeWithTag("ExerciseChartPickerOpen").performClick()

        capture.assertCaptured<Action.Click.OnPickerOpen>()
    }

    @Test
    fun chart_singlePoint_neverReachesTheCanvas() {
        // Pins reachability, not the canvas's maths: the screen routes a sub-threshold dataset
        // to Content.Loading, so it never composes the canvas. Production never builds this.
        val singlePointState = baseState().copy(
            points = persistentListOf(
                ChartPointUiModel(LocalDate.of(2026, 4, 20), 0L, "session-1", 100.0, 1),
            ),
            activeIndex = 0,
        )

        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(state = singlePointState, consume = {})
            }
        }

        composeTestRule.onNodeWithTag("ExerciseChartScreen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ChartCanvas").assertDoesNotExist()
        composeTestRule.onNodeWithTag("ChartFooterStats").assertDoesNotExist()
    }
}
