// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import io.github.stslex.workeeper.core.ui.navigation.NavResultKey
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies the singleton command-bus contract:
 *  - `navTo(screen)` emits exactly one `NavCommand.NavTo(screen)`.
 *  - `replaceTo(screen)` emits exactly one `NavCommand.ReplaceTo(screen)`.
 *  - `popBack()` emits exactly one `NavCommand.PopBack`.
 *  - `popBackWithResult(destination, result)` emits one `NavCommand.PopBackWithResult`
 *    keyed by the destination, carrying the value unchanged.
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
        val bus = NavigatorEventBus(mockk(relaxed = true))
        val screen = Screen.Exercise(uuid = "ex-1")

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.navTo(screen)

        assertEquals(NavCommand.NavTo(screen), collector.await())
    }

    @Test
    fun `navTo emits NavTo command for bottom-bar singleTop screens`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))
        val screen = Screen.BottomBar.AllTrainings

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.navTo(screen)

        assertEquals(NavCommand.NavTo(screen), collector.await())
    }

    @Test
    fun `replaceTo emits ReplaceTo command with the supplied screen`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))
        val screen = Screen.PastSession(sessionUuid = "session-1")

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.replaceTo(screen)

        assertEquals(NavCommand.ReplaceTo(screen), collector.await())
    }

    @Test
    fun `popBack emits the PopBack command`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.popBack()

        assertEquals(NavCommand.PopBack, collector.await())
    }

    /**
     * Replaces the three vararg-attribute cases this file used to carry. They asserted the
     * shape of a transport that no longer exists — ordered `Pair<String, Any?>`, and a null
     * value standing in for "no result". Both are now expressed by the type on the
     * destination, so what is worth pinning here is that the bus keys the command off that
     * destination and carries the value through unchanged.
     */
    @Test
    fun `popBackWithResult keys the command by destination and carries the result`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.popBackWithResult(Screen.PlanEditor::class, true)

        assertEquals(
            NavCommand.PopBackWithResult(
                key = NavResultKey.of(Screen.PlanEditor::class),
                result = true,
            ),
            collector.await(),
        )
    }

    @Test
    fun `popBackWithResult distinguishes destinations`() = runTest {
        assertNotEquals(
            NavResultKey.of(Screen.PlanEditor::class),
            NavResultKey.of(Screen.ExerciseImage::class),
            "two destinations must not share a result key",
        )
    }

    @Test
    fun `openRecovery emits OpenRecovery command`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))

        val collector = async(dispatcher) { bus.commands.first() }
        testScheduler.advanceUntilIdle()
        bus.openRecovery()

        assertEquals(NavCommand.OpenRecovery, collector.await())
    }

    @Test
    fun `multiple emissions are observed in dispatch order`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))
        val firstScreen = Screen.Exercise(uuid = "ex-1")
        val secondScreen = Screen.PastSession(sessionUuid = "session-1")

        val collector = async(dispatcher) { bus.commands.take(3).toList() }
        testScheduler.advanceUntilIdle()

        bus.navTo(firstScreen)
        bus.replaceTo(secondScreen)
        bus.popBack()

        assertEquals(
            listOf(
                NavCommand.NavTo(firstScreen),
                NavCommand.ReplaceTo(secondScreen),
                NavCommand.PopBack,
            ),
            collector.await(),
        )
    }

    @Test
    fun `bus exposes the same instance across Navigator and NavigatorReceiver surfaces`() {
        val bus = NavigatorEventBus(mockk(relaxed = true))

        // The singleton design relies on the producer interface (`Navigator`) and the
        // consumer interface (`NavigatorReceiver`) pointing at the same SharedFlow. If
        // they diverge, commands emitted by Stores are never seen by the App/UI bridge.
        val producer: io.github.stslex.workeeper.core.ui.navigation.Navigator = bus
        val receiver: NavigatorReceiver = bus

        // Both surfaces must observe the same underlying flow.
        assertEquals(receiver.commands, (producer as NavigatorEventBus).commands)
    }
}
