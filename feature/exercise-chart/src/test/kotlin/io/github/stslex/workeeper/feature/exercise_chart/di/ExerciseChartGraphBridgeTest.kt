// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise_chart.di

import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * KMP C.1 wave 3 — in-situ proof on exercise-chart's REAL [ExerciseChartGraph]. ASSISTED shape: the
 * graph exposes the assisted Factory (never the Store); resolving it proves all 8 bridged
 * singletons wired.
 */
internal class ExerciseChartGraphBridgeTest {

    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val navigator = mockk<Navigator>(relaxed = true)
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Unconfined,
        mainImmediateDispatcher = Dispatchers.Unconfined,
    )
    private val analyticsHolder = AnalyticsHolder()
    private val loggerHolder = LoggerHolder()
    private val defaultDispatcher = Dispatchers.Default

    @Test
    fun `assisted factory is exposed and resolvable from the real graph`() {
        val graph = createGraphFactory<ExerciseChartGraph.Factory>()
            .create(
                exerciseRepository = exerciseRepository,
                sessionRepository = sessionRepository,
                resourceWrapper = resourceWrapper,
                navigator = navigator,
                storeDispatchers = storeDispatchers,
                analyticsHolder = analyticsHolder,
                loggerHolder = loggerHolder,
                defaultDispatcher = defaultDispatcher,
            )
        assertNotNull(graph.storeFactory, "The assisted ExerciseChartStoreImpl.Factory must be resolvable")
    }
}
