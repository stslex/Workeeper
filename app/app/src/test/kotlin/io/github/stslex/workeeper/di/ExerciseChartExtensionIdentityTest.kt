// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartGraph
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Identity claims for the exercise-chart `@GraphExtension`, whose route arg is nullable.
 * See documentation/graph-extension-arc/HANDOFF.md.
 */
internal class ExerciseChartExtensionIdentityTest {

    // GUARD: Store construction reads the parent graph's Main.immediate binding.
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildAppGraph(): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = mockk<Context>(relaxed = true),
            appDatabase = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
            appScopeLifetime = AppScopeLifetime(),
            databaseReplacement = mockk(relaxed = true),
        )

    private fun AppGraph.exerciseChart(exerciseUuid: String?): ExerciseChartGraph =
        asContribution<ExerciseChartGraph.Factory>()
            .createExerciseChartGraph(Screen.ExerciseChart(exerciseUuid = exerciseUuid))

    @Test
    fun `extension resolves the store through the parent graph`() {
        val store = buildAppGraph().exerciseChart("ex-1").exerciseChartStore

        assertNotNull(store, "The contributed extension must resolve ExerciseChartStoreImpl from the parent")
    }

    @Test
    fun `store's app-scoped deps are the SAME instances the parent holds`() {
        val appGraph = buildAppGraph()

        val store = appGraph.exerciseChart("ex-1").exerciseChartStore

        assertSame(
            appGraph.analyticsHolder,
            store.analyticsHolder,
            "AnalyticsHolder in the extension-built store must be the parent graph's instance",
        )
        assertSame(
            appGraph.loggerHolder,
            store.loggerHolder,
            "LoggerHolder in the extension-built store must be the parent graph's instance",
        )
    }

    @Test
    fun `the extension inherits the Default dispatcher key and not the IO one`() {
        val appGraph = buildAppGraph()

        val extension = appGraph.exerciseChart("ex-1")

        assertSame(
            appGraph.defaultDispatcher,
            extension.defaultDispatcher,
            "@DefaultDispatcher in the extension must be the parent graph's instance",
        )
        // Only @DefaultDispatcher is consumed, so a parent-side collapse escapes assertSame.
        assertNotSame(
            appGraph.ioDispatcher,
            extension.defaultDispatcher,
            "@DefaultDispatcher and @IODispatcher must remain two distinct binding keys",
        )
    }

    @Test
    fun `each extension carries its own route arg into the store state`() {
        val appGraph = buildAppGraph()

        val first = appGraph.exerciseChart("ex-1").exerciseChartStore
        val second = appGraph.exerciseChart("ex-2").exerciseChartStore

        assertNotSame(first, second, "each createExerciseChartGraph(screen) must build a distinct Store")
        assertEquals(
            "ex-1",
            first.state.value.initialUuid,
            "The first extension's Store must seed initialState from ITS OWN bound route arg",
        )
        assertEquals(
            "ex-2",
            second.state.value.initialUuid,
            "The second extension's arg must not be shared with or overwritten by the first",
        )
    }

    /** A null arg is a real destination, and a nullable bound instance may be defaulted. */
    @Test
    fun `a null route arg survives the binding and reaches the store as null`() {
        val appGraph = buildAppGraph()

        val store = appGraph.exerciseChart(null).exerciseChartStore

        assertNull(
            store.state.value.initialUuid,
            "A null exerciseUuid must reach State.initialUuid as null, not be replaced by a default",
        )
    }
}
