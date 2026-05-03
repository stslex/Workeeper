// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
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
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartTooltipUiModel
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
        minLabel = "Min: 80 kg",
        maxLabel = "Max: 105 kg",
        lastLabel = "Last: 105 kg",
    )

    private val baseTooltip = ChartTooltipUiModel(
        sessionUuid = "s3",
        exerciseName = "Bench press",
        dateLabel = "Apr 19, 2026",
        displayLabel = "95 kg × 5",
        setCountLabel = null,
    )

    private fun baseState(activeTooltip: ChartTooltipUiModel? = null): State =
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
            activeTooltip = activeTooltip,
        )

    @Test
    fun chart_rendersTooltip_whenActiveTooltipIsPresent() {
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(
                    state = baseState(activeTooltip = baseTooltip),
                    consume = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ChartTooltip").assertIsDisplayed()
    }

    @Test
    fun chart_doesNotRenderTooltip_whenActiveTooltipIsNull() {
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(
                    state = baseState(activeTooltip = null),
                    consume = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ChartTooltip").assertDoesNotExist()
    }

    @Test
    fun chart_footerPositionUnchanged_whenTooltipAppears() {
        var state by mutableStateOf(baseState(activeTooltip = null))

        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(state = state, consume = {})
            }
        }

        composeTestRule.waitForIdle()
        val noTooltipBounds = composeTestRule
            .onNodeWithTag("ChartFooterStats")
            .fetchSemanticsNode()
            .boundsInRoot

        composeTestRule.runOnIdle {
            state = baseState(activeTooltip = baseTooltip)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ChartTooltip").assertIsDisplayed()

        val tooltipBounds = composeTestRule
            .onNodeWithTag("ChartFooterStats")
            .fetchSemanticsNode()
            .boundsInRoot

        assertEquals(
            "Footer must stay put when the tooltip becomes visible — otherwise the " +
                "tooltip is being laid out as a Column sibling instead of a canvas overlay.",
            noTooltipBounds,
            tooltipBounds,
        )
    }

    @Test
    fun chart_tappingTooltip_dispatchesOnTooltipTap() {
        val capture = createActionCapture<Action>()
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(
                    state = baseState(activeTooltip = baseTooltip),
                    consume = capture,
                )
            }
        }

        composeTestRule.onNodeWithTag("ChartTooltip").performClick()

        capture.assertCaptured<Action.Click.OnTooltipTap>()
    }

    @Test
    fun chart_tappingCanvasOutsideAnyPoint_dispatchesOnTooltipDismiss() {
        val capture = createActionCapture<Action>()
        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(
                    state = baseState(activeTooltip = baseTooltip),
                    consume = capture,
                )
            }
        }

        // Centre-X / very-top-Y on the canvas falls between the two middle points by a
        // wider margin than the tap radius, so it must read as a canvas-empty tap.
        composeTestRule.onNodeWithTag("ChartCanvas").performTouchInput {
            click(position = Offset(width / 2f, 4f))
        }

        capture.assertCaptured<Action.Click.OnTooltipDismiss>()
    }

    @Test
    fun chart_singlePointHistory_isCenteredNotPinnedToRightEdge() {
        // Regression for the "1-2 point ALL preset" case. With the ±14d tightening, the
        // sole point should sit roughly in the middle of the canvas — not glued near the
        // right edge as it would be without the tightening (window = first.day..today).
        val pointDay = LocalDate.of(2026, 4, 20)
        val singlePointState = State.create(initialUuid = "uuid-1").copy(
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
            points = persistentListOf(
                ChartPointUiModel(pointDay, 0L, 100.0, "s1", 100.0, 5, 1),
            ),
            footerStats = baseFooter,
            windowStartDay = pointDay.minusDays(14),
            windowEndDay = pointDay.plusDays(14),
        )

        composeTestRule.setContent {
            AppTheme(themeMode = ThemeMode.LIGHT) {
                ExerciseChartScreen(state = singlePointState, consume = {})
            }
        }

        // The empty branch must not render — sparse data with a tightened window is the
        // happy path, not "no data".
        composeTestRule.onNodeWithTag("ExerciseChartNoDataForExercise").assertDoesNotExist()
        // And the canvas itself must be displayed.
        composeTestRule.onNodeWithTag("ChartCanvas").assertIsDisplayed()

        // TODO(#119): Fails if the point is rendered outside the canvas bounds - for
        // example tablet landscape with a very wide canvas. We should ideally test the
        // point position here, but for now just verify that the footer is still visible
        // and not pushed off-screen by an excessively wide chart.
        composeTestRule.onNodeWithTag("ChartFooterStats").assertIsDisplayed()
    }
}

