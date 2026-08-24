// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingGraph
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
 * Identity claims for the single-training `@GraphExtension`: the inherited session subsystem, both
 * qualified dispatchers and the route arg. See documentation/graph-extension-arc/HANDOFF.md.
 */
internal class SingleTrainingExtensionIdentityTest {

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

    private fun AppGraph.singleTraining(uuid: String?): SingleTrainingGraph =
        asContribution<SingleTrainingGraph.Factory>()
            .createSingleTrainingGraph(Screen.Training(uuid = uuid))

    @Test
    fun `extension resolves the store through the parent graph`() {
        val store = buildAppGraph().singleTraining("tr-1").singleTrainingStore

        assertNotNull(
            store,
            "The contributed extension must resolve SingleTrainingStoreImpl from the parent",
        )
    }

    @Test
    fun `store's app-scoped deps are the SAME instances the parent holds`() {
        val appGraph = buildAppGraph()

        val store = appGraph.singleTraining("tr-1").singleTrainingStore

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
    fun `the session subsystem is inherited from the parent, not rebuilt`() {
        val appGraph = buildAppGraph()

        val extension = appGraph.singleTraining("tr-1")

        assertNotNull(extension.singleTrainingStore, "the store must construct off the real stack")
        assertSame(
            appGraph.sessionConflictResolver,
            extension.sessionConflictResolver,
            "SessionConflictResolver in the extension must be the PARENT's instance, not a double",
        )
    }

    @Test
    fun `both qualified dispatchers are inherited and stay two distinct keys`() {
        val appGraph = buildAppGraph()

        val extension = appGraph.singleTraining("tr-1")

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
        // assertSame alone would still pass if the parent held one instance for both keys.
        assertNotSame(
            extension.defaultDispatcher,
            extension.mainImmediateDispatcher,
            "@DefaultDispatcher and @MainImmediateDispatcher must remain two distinct binding keys",
        )
    }

    @Test
    fun `each extension carries its own route arg into the store state`() {
        val appGraph = buildAppGraph()

        val first = appGraph.singleTraining("tr-1").singleTrainingStore
        val second = appGraph.singleTraining("tr-2").singleTrainingStore

        assertNotSame(first, second, "each createSingleTrainingGraph(screen) must build a distinct Store")
        assertEquals(
            "tr-1",
            first.state.value.uuid,
            "The first extension's Store must seed initialState from ITS OWN bound route arg",
        )
        assertEquals(
            "tr-2",
            second.state.value.uuid,
            "The second extension's arg must not be shared with or overwritten by the first",
        )
    }

    /** A null uuid is "create a new training" — a real destination, not an edge case. */
    @Test
    fun `a null route arg survives the binding and reaches the store as null`() {
        val store = buildAppGraph().singleTraining(null).singleTrainingStore

        assertNull(
            store.state.value.uuid,
            "A null uuid must reach State.uuid as null, not be replaced by a default",
        )
    }
}
