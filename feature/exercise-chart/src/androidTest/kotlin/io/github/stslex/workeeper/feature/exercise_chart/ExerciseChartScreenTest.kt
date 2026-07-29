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
        ChartPointUiModel(LocalDate.of(2026, 4, 5), 0L, 80.0, "s1", 80.0, 5, 1),
        ChartPointUiModel(LocalDate.of(2026, 4, 12), 0L, 90.0, "s2", 90.0, 5, 1),
        ChartPointUiModel(LocalDate.of(2026, 4, 19), 0L, 95.0, "s3", 95.0, 5, 1),
        ChartPointUiModel(LocalDate.of(2026, 4, 26), 0L, 105.0, "s4", 105.0, 3, 2),
    )

    private val baseFooter = ChartFooterStatsUiModel(
        minTitle = "Min",
        minValue = "80 kg",
        maxTitle = "Max",
        maxValue = "105 kg",
        lastTitle = "Last",
        lastValue = "105 kg",
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
    fun chart_singlePoint_rendersCanvasWithoutCrash() {
        // Below two points the canvas draws only its gridlines (index-spacing needs n ≥ 2);
        // the sub-threshold empty state is the handler's job, not the canvas's. This pins
        // that a directly-constructed 1-point state renders rather than dividing by zero.
        val singlePointState = baseState().copy(
            points = persistentListOf(
                ChartPointUiModel(LocalDate.of(2026, 4, 20), 0L, 100.0, "s1", 100.0, 5, 1),
            ),
            activeIndex = 0,
        )

        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(state = singlePointState, consume = {})
            }
        }

        composeTestRule.onNodeWithTag("ChartCanvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ChartFooterStats").assertIsDisplayed()
    }
}
