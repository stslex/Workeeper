// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.di.ExerciseGraph
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
 * Identity claims for the exercise `@GraphExtension`: two qualified dispatchers, a bare `Context`
 * and the route arg. See documentation/graph-extension-arc/HANDOFF.md.
 */
internal class ExerciseExtensionIdentityTest {

    // GUARD: Store construction reads the parent graph's Main.immediate binding.
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val appContextMock = mockk<Context>(relaxed = true)

    private fun buildAppGraph(): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = appContextMock,
            appDatabase = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
            appScopeLifetime = AppScopeLifetime(),
            databaseReplacement = mockk(relaxed = true),
        )

    private fun AppGraph.exercise(uuid: String?): ExerciseGraph =
        asContribution<ExerciseGraph.Factory>()
            .createExerciseGraph(Screen.Exercise(uuid = uuid))

    @Test
    fun `extension resolves the store through the parent graph`() {
        val store = buildAppGraph().exercise("ex-1").exerciseStore

        assertNotNull(store, "The contributed extension must resolve ExerciseStoreImpl from the parent")
    }

    @Test
    fun `store's app-scoped deps are the SAME instances the parent holds`() {
        val appGraph = buildAppGraph()

        val store = appGraph.exercise("ex-1").exerciseStore

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
    fun `both qualified dispatchers are inherited and stay two distinct keys`() {
        val appGraph = buildAppGraph()

        val extension = appGraph.exercise("ex-1")

        assertSame(
            appGraph.defaultDispatcher,
            extension.defaultDispatcher,
            "@DefaultDispatcher in the extension must be the parent graph's instance",
        )
        assertSame(
            appGraph.mainImmediateDispatcher,
            extension.mainImmediateDispatcher,
            "@MainImmediateDispatcher in the extension must be the parent graph's instance",
        )
        // Two same-typed bindings separated only by qualifier: assertSame alone allows a collapse.
        assertNotSame(
            extension.defaultDispatcher,
            extension.mainImmediateDispatcher,
            "@DefaultDispatcher and @MainImmediateDispatcher must remain two distinct binding keys",
        )
    }

    @Test
    fun `the bare app Context is inherited from the parent's bound instance`() {
        val extension = buildAppGraph().exercise("ex-1")

        assertSame(
            appContextMock,
            extension.appContext,
            "The extension's Context must be the parent's create(applicationContext) bound instance",
        )
    }

    @Test
    fun `each extension carries its own route arg into the store state`() {
        val appGraph = buildAppGraph()

        val first = appGraph.exercise("ex-1").exerciseStore
        val second = appGraph.exercise("ex-2").exerciseStore

        assertNotSame(first, second, "each createExerciseGraph(screen) must build a distinct Store")
        assertEquals(
            "ex-1",
            first.state.value.uuid,
            "The first extension's Store must seed initialState from ITS OWN bound route arg",
        )
        assertEquals(
            "ex-2",
            second.state.value.uuid,
            "The second extension's arg must not be shared with or overwritten by the first",
        )
    }

    /** A null uuid is "create a new exercise", not an edge case that may be defaulted. */
    @Test
    fun `a null route arg survives the binding and reaches the store as null`() {
        val store = buildAppGraph().exercise(null).exerciseStore

        assertNull(
            store.state.value.uuid,
            "A null uuid must reach State.uuid as null, not be replaced by a default",
        )
    }
}
