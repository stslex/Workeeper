// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.mvi.store

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartHandlerStore
import io.github.stslex.workeeper.feature.exercise_chart.domain.ExerciseChartInteractor
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFoldDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartFooterStatsDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ChartPointDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise_chart.domain.model.RecentExerciseDomain
import io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.exercise_chart.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartMetricUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.model.ChartPresetUiModel
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Action
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.Content
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStore.State
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The empty-state flash, pinned in the state model: drive the real handlers over unplottable
 * data and assert that no emitted state resolves to [Content.Plot].
 */
internal class ChartContentResolutionTest {

    private val interactor = mockk<ExerciseChartInteractor>()
    private val resources = mockk<ResourceWrapper>(relaxed = true)

    @Test
    fun `one-point exercise never resolves to Plot across metric and preset switches`() {
        val emissions = driveSwitches(fold = fold(pointCount = 1))

        assertTrue(emissions.isNotEmpty(), "the drive produced no emissions at all")
        val plotted = emissions.filter { it.content == Content.Plot }
        assertTrue(
            plotted.isEmpty(),
            "no emitted state may compose the canvas for a sub-threshold dataset, but " +
                "${plotted.size} of ${emissions.size} did: " +
                plotted.joinToString { "points=${it.points.size}/reason=${it.emptyReason}" },
        )
    }

    @Test
    fun `zero-point exercise never resolves to Plot across metric and preset switches`() {
        val emissions = driveSwitches(fold = fold(pointCount = 0))

        assertTrue(emissions.isNotEmpty(), "the drive produced no emissions at all")
        assertTrue(
            emissions.none { it.content == Content.Plot },
            "an empty dataset reached Content.Plot",
        )
    }

    /**
     * The discriminator: without it the assertions above would pass on a `content` that never
     * plots anything.
     */
    @Test
    fun `two-point exercise does resolve to Plot`() {
        val emissions = driveSwitches(fold = fold(pointCount = 2))

        assertTrue(
            emissions.any { it.content == Content.Plot },
            "plottable data never reached Content.Plot — the invariant is vacuous",
        )
        assertEquals(Content.Plot, emissions.last().content)
    }

    /** A reload that resolves to nothing lands on Empty without a Loading frame between. */
    @Test
    fun `switching a resolved empty state never passes through Loading`() {
        val emissions = driveSwitches(fold = fold(pointCount = 1))
        val afterFirstResolve = emissions
            .dropWhile { it.content !is Content.Empty }

        assertTrue(afterFirstResolve.isNotEmpty(), "never resolved to Empty")
        assertTrue(
            afterFirstResolve.none { it.content == Content.Loading },
            "a resolved empty state fell back to Loading mid-reload, taking the recovery " +
                "chips off screen: " + afterFirstResolve.map { it.content },
        )
    }

    /**
     * Init, then a metric switch, then a preset switch. Returns every state the handlers
     * emitted, in order.
     */
    private fun driveSwitches(fold: ChartFoldDomain): List<State> {
        val flow = MutableStateFlow(State.create(initialUuid = EXERCISE_UUID))
        val emissions = mutableListOf<State>()
        val store = recordingStore(flow, emissions)
        val commonHandler = CommonHandler(
            interactor = interactor,
            resourceWrapper = resources,
            store = store,
        )
        val clickHandler = ClickHandler(
            commonHandler = commonHandler,
            resourceWrapper = resources,
            store = store,
        )
        coEvery { interactor.getRecentlyTrainedExercises() } returns listOf(
            RecentExerciseDomain(EXERCISE_UUID, "Bench", ExerciseTypeDomain.WEIGHTED, 1_000L),
        )
        coEvery { interactor.getLastTrainedExerciseUuid() } returns EXERCISE_UUID
        coEvery { interactor.loadChartData(any(), any(), any(), any(), any()) } returns fold

        commonHandler.invoke(Action.Common.Init)
        clickHandler.invoke(Action.Click.OnMetricSelect(ChartMetricUiModel.VOLUME_PER_SET))
        clickHandler.invoke(Action.Click.OnPresetSelect(ChartPresetUiModel.MONTH_1))

        return emissions.toList()
    }

    private fun fold(pointCount: Int): ChartFoldDomain {
        val points = (0 until pointCount).map { index ->
            ChartPointDomain(
                day = LocalDate.of(2026, 4, index + 1),
                dayMillis = index * DAY_MILLIS,
                value = 100.0 + index,
                sessionUuid = "s$index",
                weight = 100.0 + index,
                reps = 5,
                setCount = 2,
            )
        }
        // The one-point case carries a non-null footer in production, as `toFooter` does too.
        val footer = points.firstOrNull()?.let { first ->
            ChartFooterStatsDomain(min = first, max = points.last(), last = points.last())
        }
        return ChartFoldDomain(points = points, footer = footer)
    }

    private fun recordingStore(
        flow: MutableStateFlow<State>,
        emissions: MutableList<State>,
    ): ExerciseChartHandlerStore = mockk<ExerciseChartHandlerStore>(relaxed = true).also { store ->
        every { store.state } returns flow
        every { store.updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            flow.value = update(flow.value)
            emissions.add(flow.value)
        }
        coEvery { store.updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
            val update = firstArg<suspend (State) -> State>()
            flow.value = update(flow.value)
            emissions.add(flow.value)
        }
        every {
            store.launchDefault<Any?>(onError = any(), onSuccess = any(), action = any())
        } answers {
            val onError = arg<suspend (Throwable) -> Unit>(0)
            val onSuccess = arg<suspend CoroutineScope.(Any?) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any?>(2)
            runBlocking {
                try {
                    onSuccess(action())
                } catch (t: Throwable) {
                    onError(t)
                }
            }
            mockk(relaxed = true)
        }
    }

    private companion object {

        const val EXERCISE_UUID = "uuid-1"
        const val DAY_MILLIS = 86_400_000L
    }
}
