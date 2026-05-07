// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.ui.navigation.Screen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Lifecycle regression coverage for the navigation command-bus.
 *
 * The bug class this guards against: before this branch, the navigator
 * implementation retained a `NavHostController` reference at singleton scope.
 * After an activity recreation (config change, low-memory kill / restore) the
 * retained controller was stale, but the singleton survived — the next
 * `navigator.navTo(...)` call from a still-live ViewModel hit the destroyed
 * `NavController` and either no-oped or crashed.
 *
 * The fixed architecture splits decisions and execution: the singleton
 * `NavigatorEventBus` stores only a `SharedFlow<NavigationCommand>` (no controller),
 * and `NavigatorExt.NavigationEventBusSetup` collects the flow inside a
 * `LaunchedEffect(navController)` that re-binds when the composition gets a fresh
 * controller.
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
internal class NavigationLifecycleRegressionTest {

    @Test
    fun `bus survives across simulated bridge detach and re-attach`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()

        // Bridge 1 attaches, observes one command, then detaches when its launched
        // job completes (simulates LaunchedEffect leaving the composition).
        val bridge1 = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.navTo(Screen.Exercise(uuid = "ex-1"))
        val firstCommand = bridge1.await()
        assertEquals(NavigationCommand.NavTo(Screen.Exercise(uuid = "ex-1")), firstCommand)

        // Activity recreation: a new bridge subscribes on the SAME bus instance.
        val bridge2 = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.navTo(Screen.PastSession(sessionUuid = "session-1"))
        val secondCommand = bridge2.await()

        assertEquals(
            NavigationCommand.NavTo(Screen.PastSession(sessionUuid = "session-1")),
            secondCommand,
            "Bridge re-collection on the same singleton bus must observe newly-emitted commands.",
        )
    }

    @Test
    fun `bus continues operating after commands emitted with no subscriber`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()

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

        assertEquals(NavigationCommand.NavTo(Screen.BottomBar.Home), deferred.await())
    }

    @Test
    fun `concurrent collectors each observe the same slice of the stream`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()

        val firstFlow = async(dispatcher) { bus.commands.take(2).toList() }
        val secondFlow = async(dispatcher) { bus.commands.take(2).toList() }

        // Give both subscribers a tick to attach before emitting.
        testScheduler.advanceUntilIdle()

        bus.navTo(Screen.Exercise(uuid = "ex-1"))
        bus.replaceTo(Screen.PastSession(sessionUuid = "session-1"))

        val expected = listOf(
            NavigationCommand.NavTo(Screen.Exercise(uuid = "ex-1")),
            NavigationCommand.ReplaceTo(Screen.PastSession(sessionUuid = "session-1")),
        )
        assertEquals(expected, firstFlow.await())
        assertEquals(expected, secondFlow.await())
    }

    @Test
    fun `bridge re-collection preserves command order across handovers`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()

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
                NavigationCommand.NavTo(Screen.BottomBar.Home),
                NavigationCommand.NavTo(Screen.BottomBar.AllTrainings),
            ),
            firstObserved,
        )
        assertEquals(
            listOf(
                NavigationCommand.NavTo(Screen.Exercise(uuid = "ex-2")),
                NavigationCommand.PopBack(emptyList()),
            ),
            secondObserved,
        )
    }

    @Test
    fun `repeated subscription returns the same SharedFlow instance from the bus`() {
        val bus = NavigatorEventBus()

        // Repeated reads must return the same flow instance — otherwise re-attaching
        // after recreation would attach to a different stream and miss subsequent
        // commands.
        val first = bus.commands
        val second = bus.commands

        assertSame(first, second)
    }
}
