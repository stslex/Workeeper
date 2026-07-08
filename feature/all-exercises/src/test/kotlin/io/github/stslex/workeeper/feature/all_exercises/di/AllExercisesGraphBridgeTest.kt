// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.all_exercises.di

import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
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
 * KMP C.1 wave 2 checkpoint — in-situ proof on all-exercises' REAL [AllExercisesGraph]. This is the
 * PLAIN (non-assisted) shape (the archive template) on a live BULK feature — it proves the
 * plain-bulk flip is mechanical, not just the hand-built M0 archive. The graph exposes the Store
 * directly; construction wires all 8 bridged singletons.
 *
 * Pure-JVM: `createGraphFactory` is Metro-compiler codegen, no Android runtime.
 */
internal class AllExercisesGraphBridgeTest {

    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
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

    private fun buildGraph(): AllExercisesGraph = createGraphFactory<AllExercisesGraph.Factory>()
        .create(
            exerciseRepository = exerciseRepository,
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
        val store = buildGraph().allExercisesStore

        assertNotNull(store, "Metro must construct the plain AllExercisesStoreImpl from the 8 bridged deps")
    }

    @Test
    fun `bridged app-scoped singletons reach the store by identity not copy`() {
        val store = buildGraph().allExercisesStore

        assertSame(analyticsHolder, store.analyticsHolder, "AnalyticsHolder must be === the provided instance")
        assertSame(loggerHolder, store.loggerHolder, "LoggerHolder must be === the provided instance")
    }
}
