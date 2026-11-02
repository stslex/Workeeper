// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.navigation.NavCommand
import io.github.stslex.workeeper.core.ui.navigation.NavResultKey
import io.github.stslex.workeeper.core.ui.navigation.NavigatorReceiver
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The singleton command-bus contract. Every test attaches a collector before emitting: the bus is
 * `replay = 0`, so emissions made with no subscriber are never replayed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NavigatorEventBusTest {

    // The real `Log.tag` logger reaches Firebase, which is not initialized in a JVM test.
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

    /** The bus keys the command off the destination and carries the value through unchanged. */
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

        // Producer and consumer interfaces must point at the same SharedFlow, or commands emitted
        // by Stores are never seen by the UI bridge.
        val producer: io.github.stslex.workeeper.core.ui.navigation.Navigator = bus
        val receiver: NavigatorReceiver = bus

        assertEquals(receiver.commands, (producer as NavigatorEventBus).commands)
    }

    /**
     * The pending-result lifecycle: a result survives only the pop that delivers it, and every
     * other navigation clears every channel — the stand-in for Nav2-style per-entry scoping.
     */
    @Test
    fun `navTo clears a pending result`() {
        val bus = NavigatorEventBus(mockk(relaxed = true))
        val key = NavResultKey.of(Screen.PlanEditor::class)
        bus.setResult(key, true)

        bus.navTo(Screen.Settings)

        assertNull(bus.result(key).value)
    }

    @Test
    fun `replaceTo clears a pending result`() {
        val bus = NavigatorEventBus(mockk(relaxed = true))
        val key = NavResultKey.of(Screen.PlanEditor::class)
        bus.setResult(key, true)

        bus.replaceTo(Screen.BottomBar.Home)

        assertNull(bus.result(key).value)
    }

    @Test
    fun `plain popBack clears a pending result`() {
        val bus = NavigatorEventBus(mockk(relaxed = true))
        val key = NavResultKey.of(Screen.PlanEditor::class)
        bus.setResult(key, true)

        bus.popBack()

        assertNull(bus.result(key).value)
    }

    @Test
    fun `popBackWithResult does not clear other pending channels`() {
        val bus = NavigatorEventBus(mockk(relaxed = true))
        val otherKey = NavResultKey.of(Screen.ExerciseImage::class)
        bus.setResult(otherKey, "REPLACE")

        bus.popBackWithResult(Screen.PlanEditor::class, result = true)

        assertEquals("REPLACE", bus.result(otherKey).value)
    }
}
