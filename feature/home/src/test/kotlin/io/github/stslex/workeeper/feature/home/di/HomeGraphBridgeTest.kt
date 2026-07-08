// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.di

import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
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
 * KMP C.1 wave 3 — in-situ proof on home's REAL [HomeGraph]. PLAIN shape: the graph exposes the
 * Store directly; construction wires all 9 bridged singletons.
 */
internal class HomeGraphBridgeTest {

    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val sessionConflictResolver = mockk<SessionConflictResolver>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val navigator = mockk<Navigator>(relaxed = true)
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Unconfined,
        mainImmediateDispatcher = Dispatchers.Unconfined,
    )
    private val analyticsHolder = AnalyticsHolder()
    private val loggerHolder = LoggerHolder()
    private val defaultDispatcher = Dispatchers.Default

    private fun buildGraph(): HomeGraph = createGraphFactory<HomeGraph.Factory>()
        .create(
            trainingRepository = trainingRepository,
            sessionRepository = sessionRepository,
            sessionConflictResolver = sessionConflictResolver,
            resourceWrapper = resourceWrapper,
            navigator = navigator,
            storeDispatchers = storeDispatchers,
            analyticsHolder = analyticsHolder,
            loggerHolder = loggerHolder,
            defaultDispatcher = defaultDispatcher,
        )

    @Test
    fun `plain graph constructs the store from the bridged singletons`() {
        assertNotNull(buildGraph().homeStore, "Metro must construct the plain HomeStoreImpl from 9 bridged deps")
    }

    @Test
    fun `bridged app-scoped singletons reach the store by identity not copy`() {
        val store = buildGraph().homeStore
        assertSame(analyticsHolder, store.analyticsHolder)
        assertSame(loggerHolder, store.loggerHolder)
    }
}
