// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies the singleton command-bus contract:
 *  - `navTo(screen)` emits exactly one `NavigationCommand.NavTo(screen)`.
 *  - `replaceTo(screen)` emits exactly one `NavigationCommand.ReplaceTo(screen)`.
 *  - `popBack(...)` emits exactly one `NavigationCommand.PopBack(attrsList)` carrying
 *    every key/value pair in vararg order.
 *  - Multiple emissions arrive on the receiver in the order they were dispatched.
 *
 * Each test attaches a collector before invoking the producer because the bus uses
 * `MutableSharedFlow(extraBufferCapacity = 64)` with `replay = 0` — emissions made
 * while no subscriber is attached are not replayed to subscribers that attach later.
 * The lifecycle regression test covers what the production bridge does in that case
 * (the bridge attaches via `LaunchedEffect(navController)` before any decision-side
 * emit can happen for that composition).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NavigatorEventBusTest {

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
    fun `navTo emits NavTo command with the supplied screen`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()
        val screen = Screen.Exercise(uuid = "ex-1")

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.navTo(screen)

        assertEquals(NavigationCommand.NavTo(screen), collector.await())
    }

    @Test
    fun `navTo emits NavTo command for bottom-bar singleTop screens`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()
        val screen = Screen.BottomBar.AllTrainings

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.navTo(screen)

        assertEquals(NavigationCommand.NavTo(screen), collector.await())
    }

    @Test
    fun `replaceTo emits ReplaceTo command with the supplied screen`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()
        val screen = Screen.PastSession(sessionUuid = "session-1")

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.replaceTo(screen)

        assertEquals(NavigationCommand.ReplaceTo(screen), collector.await())
    }

    @Test
    fun `popBack with no attributes emits an empty PopBack command`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.popBack()

        assertEquals(NavigationCommand.PopBack(emptyList()), collector.await())
    }

    @Test
    fun `popBack preserves attribute key value pairs in vararg order`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()
        val firstAttr: Pair<String, Any?> = "plan-editor-saved" to true
        val secondAttr: Pair<String, Any?> = "another-attr" to "value"

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.popBack(firstAttr, secondAttr)

        assertEquals(
            NavigationCommand.PopBack(listOf(firstAttr, secondAttr)),
            collector.await(),
        )
    }

    @Test
    fun `popBack tolerates null values in attribute pairs`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()
        val attr: Pair<String, Any?> = "result" to null

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.popBack(attr)

        assertEquals(NavigationCommand.PopBack(listOf(attr)), collector.await())
    }

    @Test
    fun `multiple emissions are observed in dispatch order`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus()
        val firstScreen = Screen.Exercise(uuid = "ex-1")
        val secondScreen = Screen.PastSession(sessionUuid = "session-1")

        val collector = async(dispatcher) { bus.commands.take(3).toList() }
        testScheduler.advanceUntilIdle()

        bus.navTo(firstScreen)
        bus.replaceTo(secondScreen)
        bus.popBack()

        assertEquals(
            listOf(
                NavigationCommand.NavTo(firstScreen),
                NavigationCommand.ReplaceTo(secondScreen),
                NavigationCommand.PopBack(emptyList()),
            ),
            collector.await(),
        )
    }

    @Test
    fun `bus exposes the same instance across Navigator and NavigatorReceiver surfaces`() {
        val bus = NavigatorEventBus()

        // The singleton design relies on the producer interface (`Navigator`) and the
        // consumer interface (`NavigatorReceiver`) pointing at the same SharedFlow. If
        // they diverge, commands emitted by Stores are never seen by the App/UI bridge.
        val producer: io.github.stslex.workeeper.core.ui.navigation.Navigator = bus
        val receiver: NavigatorReceiver = bus

        // Both surfaces must observe the same underlying flow.
        assertEquals(receiver.commands, (producer as NavigatorEventBus).commands)
    }
}
