// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
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
 * Replaces the former feature-module `PastSessionGraphBridgeTest` (a `@GraphExtension` cannot be created
 * standalone, so the assertion must run where the parent [AppGraph] is compiled — here, `:app`).
 *
 * past-session is port 1 of the assisted BATCH and the third shape-B port. Its route arg
 * (`Screen.PastSession`) is a flat 2-level data class — the shape already proven for
 * `ScreenInjectionRule` on image-viewer — so this test's job is the binding claims, not the arg shape.
 *
 * The dispatcher test is the one that is specific to this feature. past-session consumes exactly ONE
 * dispatcher (`@IODispatcher`), so an `assertSame` against the parent's cannot on its own distinguish
 * "the extension inherited the IO key" from "the parent collapsed IO and Default into a single
 * instance" — both pass. The claim is therefore made in two halves: same as the parent's `@IODispatcher`
 * AND *not* the parent's `@DefaultDispatcher`.
 */
internal class PastSessionExtensionIdentityTest {

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

    private fun buildAppGraph(): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = mockk<Context>(relaxed = true),
            appDatabase = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
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

        // Identity, not just non-null: the extension inherits the parent's app-scoped singletons.
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
        // Both halves are needed. The assertSame above would ALSO pass if the parent held one instance
        // for both dispatcher keys, which is precisely the cross-wire this guards: past-session reads
        // only @IODispatcher, so a collapse would be invisible from the store alone.
        assertNotSame(
            appGraph.defaultDispatcher,
            extension.ioDispatcher,
            "@IODispatcher and @DefaultDispatcher must remain two distinct binding keys",
        )
    }

    /** Shape B's defining property: the route arg is per-extension, never shared or stale. */
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
