// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.past_session.di.PastSessionGraph
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Identity claims for the past-session `@GraphExtension`, which consumes only `@IODispatcher`.
 * See documentation/graph-extension-arc/HANDOFF.md.
 */
internal class PastSessionExtensionIdentityTest {

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

    private fun AppGraph.pastSession(sessionUuid: String): PastSessionGraph =
        asContribution<PastSessionGraph.Factory>()
            .createPastSessionGraph(Screen.PastSession(sessionUuid = sessionUuid))

    @Test
    fun `extension resolves the store through the parent graph`() {
        val store = buildAppGraph().pastSession("session-1").pastSessionStore

        assertNotNull(store, "The contributed extension must resolve PastSessionStoreImpl from the parent")
    }

    @Test
    fun `store's app-scoped deps are the SAME instances the parent holds`() {
        val appGraph = buildAppGraph()

        val store = appGraph.pastSession("session-1").pastSessionStore

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
    fun `the extension inherits the IO dispatcher key and not the Default one`() {
        val appGraph = buildAppGraph()

        val extension = appGraph.pastSession("session-1")

        assertSame(
            appGraph.ioDispatcher,
            extension.ioDispatcher,
            "@IODispatcher in the extension must be the parent graph's instance",
        )
        // Only @IODispatcher is consumed, so a parent-side collapse is invisible to assertSame.
        assertNotSame(
            appGraph.defaultDispatcher,
            extension.ioDispatcher,
            "@IODispatcher and @DefaultDispatcher must remain two distinct binding keys",
        )
    }

    @Test
    fun `each extension carries its own route arg into the store state`() {
        val appGraph = buildAppGraph()

        val first = appGraph.pastSession("session-1").pastSessionStore
        val second = appGraph.pastSession("session-2").pastSessionStore

        assertNotSame(first, second, "each createPastSessionGraph(screen) must build a distinct Store")
        assertEquals(
            "session-1",
            first.state.value.sessionUuid,
            "The first extension's Store must seed initialState from ITS OWN bound route arg",
        )
        assertEquals(
            "session-2",
            second.state.value.sessionUuid,
            "The second extension's arg must not be shared with or overwritten by the first",
        )
    }
}
