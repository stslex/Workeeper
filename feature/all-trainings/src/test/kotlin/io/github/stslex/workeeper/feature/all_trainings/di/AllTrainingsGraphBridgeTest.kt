// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_trainings.di

import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * KMP C.1 wave 3 — in-situ proof on all-trainings' REAL [AllTrainingsGraph]. PLAIN shape (archive
 * template): the graph exposes the Store directly; construction wires all 8 bridged singletons.
 */
internal class AllTrainingsGraphBridgeTest {

    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val navigator = mockk<Navigator>(relaxed = true)
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Unconfined,
        mainImmediateDispatcher = Dispatchers.Unconfined,
    )
    private val analyticsHolder = AnalyticsHolder()
    private val loggerHolder = LoggerHolder()
    private val defaultDispatcher = Dispatchers.Default

    private fun buildGraph(): AllTrainingsGraph = createGraphFactory<AllTrainingsGraph.Factory>()
        .create(
            trainingRepository = trainingRepository,
            tagRepository = tagRepository,
            resourceWrapper = resourceWrapper,
            navigator = navigator,
            storeDispatchers = storeDispatchers,
            analyticsHolder = analyticsHolder,
            loggerHolder = loggerHolder,
            defaultDispatcher = defaultDispatcher,
        )

    @Test
    fun `plain graph constructs the store from the bridged singletons`() {
        assertNotNull(buildGraph().allTrainingsStore, "Metro must construct the plain Store from 8 bridged deps")
    }

    @Test
    fun `bridged app-scoped singletons reach the store by identity not copy`() {
        val store = buildGraph().allTrainingsStore
        assertSame(analyticsHolder, store.analyticsHolder)
        assertSame(loggerHolder, store.loggerHolder)
    }
}
