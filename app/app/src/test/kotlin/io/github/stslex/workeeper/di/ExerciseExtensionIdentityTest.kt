// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
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
 * Replaces the former feature-module `ExerciseGraphBridgeTest` (a `@GraphExtension` cannot be created
 * standalone, so the assertion must run where the parent [AppGraph] is compiled — here, `:app`).
 *
 * exercise is port 3 of the assisted batch and, alongside `settings`, the widest inheritance claim in
 * the arc: 14 formerly hand-threaded bindings, and the only OTHER graph carrying all three hard
 * categories at once — two same-typed qualified dispatchers, a bare unqualified `Context`, and six
 * repositories. Each of those gets its own assertion below, because each is a distinct way for
 * inheritance across a graph boundary to go silently wrong.
 *
 * Unlike past-session and exercise-chart, this feature consumes BOTH dispatchers, so the
 * qualifier-distinctness claim is made *within the extension* — the stronger form, and the same one
 * `SettingsExtensionIdentityTest` uses.
 */
internal class ExerciseExtensionIdentityTest {

    // The real parent graph provides Dispatchers.Main.immediate (DispatchersBindingContainer); a plain
    // JVM test must install a Main dispatcher before the store constructs.
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
        // The cross-wire this guards: two same-typed bindings separated only by qualifier, collapsing
        // into one across the graph boundary. Both assertSame calls above would still pass if the
        // parent itself held one instance for both keys, so distinctness is asserted explicitly.
        assertNotSame(
            extension.defaultDispatcher,
            extension.mainImmediateDispatcher,
            "@DefaultDispatcher and @MainImmediateDispatcher must remain two distinct binding keys",
        )
    }

    /**
     */
    @Test
    fun `the bare app Context is inherited from the parent's bound instance`() {
        val extension = buildAppGraph().exercise("ex-1")

        assertSame(
            appContextMock,
            extension.appContext,
            "The extension's Context must be the parent's create(applicationContext) bound instance",
        )
    }

    /** Shape B's defining property: the route arg is per-extension, never shared or stale. */
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

    /**
     * A null uuid is "create a new exercise" — a real destination, not an edge case. A bound instance
     * of a nullable type is where a graph could quietly substitute a non-null default.
     */
    @Test
    fun `a null route arg survives the binding and reaches the store as null`() {
        val store = buildAppGraph().exercise(null).exerciseStore

        assertNull(
            store.state.value.uuid,
            "A null uuid must reach State.uuid as null, not be replaced by a default",
        )
    }
}
