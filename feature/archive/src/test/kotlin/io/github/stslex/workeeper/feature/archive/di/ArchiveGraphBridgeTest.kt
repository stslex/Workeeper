// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.archive.di

import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * KMP C.1 M0 — proves the Hilt→Metro bridge for feature/archive at the graph level, on
 * archive's ACTUAL [ArchiveGraph] (not the throwaway `probe-hiltmetro` proof).
 *
 * The 8 app-scoped dependencies are Hilt-owned `@Singleton`s in production; here they are
 * fakes handed to `ArchiveGraph.Factory.create(...)` as `@Provides` bound instances — the
 * exact call `ArchiveFeature.processor()` makes with the real Hilt-provided instances.
 *
 * The M0 gate is that the deps archive receives are the SAME instances (`===`), not copies.
 * `AnalyticsHolder` and `LoggerHolder` flow all the way through the graph into the
 * Metro-constructed [ArchiveStoreImpl] and are re-exposed by `BaseStore` as public `val`s, so
 * asserting identity on them proves the bridge adopts the provided instances end-to-end,
 * through the real graph, into the Store. (Generic raw `===` adoption across the Hilt→Metro
 * boundary is separately proven at RUN in `probe-hiltmetro`.)
 *
 * Pure-JVM: `createGraphFactory` is Metro-compiler codegen with no Android runtime, so no
 * Robolectric / Hilt harness is needed — which also sidesteps the JUnit5-vs-Hilt-JUnit4
 * constraint entirely.
 */
internal class ArchiveGraphBridgeTest {

    private val navigator = mockk<Navigator>(relaxed = true)
    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Unconfined,
        mainImmediateDispatcher = Dispatchers.Unconfined,
    )
    private val analyticsHolder = AnalyticsHolder()
    private val loggerHolder = LoggerHolder()

    private fun buildGraph(): ArchiveGraph = createGraphFactory<ArchiveGraph.Factory>()
        .create(
            navigator = navigator,
            exerciseRepository = exerciseRepository,
            trainingRepository = trainingRepository,
            resourceWrapper = resourceWrapper,
            storeDispatchers = storeDispatchers,
            analyticsHolder = analyticsHolder,
            loggerHolder = loggerHolder,
            defaultDispatcher = Dispatchers.Unconfined,
        )

    @Test
    fun `metro graph constructs the store from the bridged deps`() {
        val store = buildGraph().archiveStore

        assertNotNull(store, "Metro must construct ArchiveStoreImpl by wiring all bridged deps")
    }

    @Test
    fun `bridged app-scoped singletons reach the store by identity not copy`() {
        val store = buildGraph().archiveStore

        // === identity through the real ArchiveGraph into the Metro-constructed Store.
        assertSame(
            analyticsHolder,
            store.analyticsHolder,
            "AnalyticsHolder must be the SAME instance handed to the graph factory (===), not a copy",
        )
        assertSame(
            loggerHolder,
            store.loggerHolder,
            "LoggerHolder must be the SAME instance handed to the graph factory (===), not a copy",
        )
    }

    @Test
    fun `store is unscoped so the graph does not retain it, but bridged deps stay identical`() {
        val graph = buildGraph()

        // ArchiveStoreImpl is deliberately UNSCOPED (no @SingleIn): the graph must NOT cache it,
        // because retention is owned by the Android ViewModelStore (via rememberMetroStoreProcessor),
        // not by Metro. Two reads therefore yield DIFFERENT Store instances — the correct shape.
        val first = graph.archiveStore
        val second = graph.archiveStore
        assertNotSame(
            first,
            second,
            "An unscoped Store must be re-provided per access — the graph must not retain it",
        )

        // …yet the bridged @Provides bound instances are stable, so BOTH Stores see the SAME
        // Hilt-provided singletons. This is the property the ViewModelStore-retained Store relies on.
        assertSame(first.analyticsHolder, second.analyticsHolder)
        assertSame(first.loggerHolder, second.loggerHolder)
    }
}
