// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Lifecycle coverage for the navigation command-bus.
 *
 * Renamed from `NavigationLifecycleRegressionTest` at the Nav3 swap: the stage-1.3 plan marked
 * both variants of that class for deletion, but only the INSTRUMENTED one guarded the
 * Nav2-specific bug class (a singleton-scoped, controller-backed navigator going stale across
 * `scenario.recreate()`). The five tests here reference no controller and no navigation library
 * at all — they pin the `NavigatorEventBus` invariants the bridge KEEPS relying on under Nav3,
 * where `NavigatorExt.NavigationEventBusSetup` collects the same hot flow inside a
 * `LaunchedEffect(navigatorHolder)` and re-binds across recompositions exactly as it did across
 * fresh controllers.
 *
 * The architecture the invariants describe: decisions and execution split — the singleton
 * `NavigatorEventBus` stores only a `SharedFlow<NavCommand>` (no back-stack reference), and the
 * bridge collector is the only thing that touches the app-owned stack.
 *
 * The tests below cover the JVM-observable invariants of that design:
 *  - The bus instance stays usable across "bridge detach" / "bridge re-attach" — the
 *    singleton is unchanged and a fresh subscriber on the same instance receives
 *    new emissions.
 *  - Commands emitted **after** a new collector attaches are observed by the new
 *    collector. Pre-subscription emissions are NOT guaranteed to be replayed:
 *    `NavigatorEventBus` uses `MutableSharedFlow(replay = 0, extraBufferCapacity = 64)`
 *    where the buffer absorbs `tryEmit` so it does not block, but does not redeliver
 *    to subscribers that attach later. The production bridge attaches synchronously
 *    inside `App.kt` before any Store action could emit for that composition, so
 *    pre-subscription emits are not part of the lifecycle contract.
 *  - Multiple concurrent collectors each see the same hot stream slice — the stream
 *    is multicast, not single-shot.
 *  - Command order is preserved within each subscriber's slice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NavigatorEventBusLifecycleTest {

    // `NavigatorEventBus` constructs a `Log.tag(...)` logger that funnels through
    // `FirebaseCrashlyticsHolder` → `Firebase.crashlytics` on every emit. In a JVM
    // unit test Firebase is not initialized and `Process.myPid()` is not mocked,
    // so the real logger throws. Stub `Log.tag(...)` to return a relaxed Logger.
    @BeforeEach
    fun setUpLogger() {
        mockkObject(Log)
        every { Log.tag(any()) } returns mockk<Logger>(relaxed = true)
    }

    @AfterEach
    fun tearDownLogger() {
        unmockkObject(Log)
    }

    @Test
    fun `bus survives across simulated bridge detach and re-attach`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))

        // Bridge 1 attaches, observes one command, then detaches when its launched
        // job completes (simulates LaunchedEffect leaving the composition).
        val bridge1 = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.navTo(Screen.Exercise(uuid = "ex-1"))
        val firstCommand = bridge1.await()
        assertEquals(NavCommand.NavTo(Screen.Exercise(uuid = "ex-1")), firstCommand)

        // Activity recreation: a new bridge subscribes on the SAME bus instance.
        val bridge2 = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.navTo(Screen.PastSession(sessionUuid = "session-1"))
        val secondCommand = bridge2.await()

        assertEquals(
            NavCommand.NavTo(Screen.PastSession(sessionUuid = "session-1")),
            secondCommand,
            "Bridge re-collection on the same singleton bus must observe newly-emitted commands.",
        )
    }

    @Test
    fun `bus continues operating after commands emitted with no subscriber`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))

        // Decision-side dispatch happens while no executor is collecting (the bridge
        // has not attached yet). The bus uses replay = 0 with extraBufferCapacity = 64,
        // so `tryEmit` returns true without blocking; the emissions are NOT cached for
        // subscribers that attach later. The load-bearing assertion is simply that
        // the bus does not crash or get into a stuck state.
        bus.navTo(Screen.Exercise(uuid = "ex-1"))
        bus.replaceTo(Screen.PastSession(sessionUuid = "session-1"))
        bus.popBack()

        // A fresh subscriber attaches (mimics the bridge of a new composition) and
        // observes the next emission. We do NOT assert on the pre-subscription
        // emissions — they are not guaranteed to be replayed and the production
        // bridge attaches before any Store action could fire for that composition.
        val deferred = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.navTo(Screen.BottomBar.Home)

        assertEquals(NavCommand.NavTo(Screen.BottomBar.Home), deferred.await())
    }

    @Test
    fun `concurrent collectors each observe the same slice of the stream`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))

        val firstFlow = async(dispatcher) { bus.commands.take(2).toList() }
        val secondFlow = async(dispatcher) { bus.commands.take(2).toList() }

        // Give both subscribers a tick to attach before emitting.
        testScheduler.advanceUntilIdle()

        bus.navTo(Screen.Exercise(uuid = "ex-1"))
        bus.replaceTo(Screen.PastSession(sessionUuid = "session-1"))

        val expected = listOf(
            NavCommand.NavTo(Screen.Exercise(uuid = "ex-1")),
            NavCommand.ReplaceTo(Screen.PastSession(sessionUuid = "session-1")),
        )
        assertEquals(expected, firstFlow.await())
        assertEquals(expected, secondFlow.await())
    }

    @Test
    fun `bridge re-collection preserves command order across handovers`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))

        // First bridge collects two commands.
        val first = async(dispatcher) { bus.commands.take(2).toList() }
        testScheduler.advanceUntilIdle()
        bus.navTo(Screen.BottomBar.Home)
        bus.navTo(Screen.BottomBar.AllTrainings)
        val firstObserved = first.await()

        // Second bridge attaches (post-recreation). It should see the next two
        // emissions in dispatch order.
        val second = async(dispatcher) { bus.commands.take(2).toList() }
        testScheduler.advanceUntilIdle()
        bus.navTo(Screen.Exercise(uuid = "ex-2"))
        bus.popBack()
        val secondObserved = second.await()

        assertEquals(
            listOf(
                NavCommand.NavTo(Screen.BottomBar.Home),
                NavCommand.NavTo(Screen.BottomBar.AllTrainings),
            ),
            firstObserved,
        )
        assertEquals(
            listOf(
                NavCommand.NavTo(Screen.Exercise(uuid = "ex-2")),
                NavCommand.PopBack,
            ),
            secondObserved,
        )
    }

    @Test
    fun `repeated subscription returns the same SharedFlow instance from the bus`() {
        val bus = NavigatorEventBus(mockk(relaxed = true))

        // Repeated reads must return the same flow instance — otherwise re-attaching
        // after recreation would attach to a different stream and miss subsequent
        // commands.
        val first = bus.commands
        val second = bus.commands

        assertSame(first, second)
    }
}
