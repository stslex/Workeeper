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
 * Lifecycle coverage for the command-bus singleton: it survives bridge detach and re-attach,
 * multicasts to concurrent collectors and preserves order. `replay = 0`, so pre-subscription
 * emissions are not part of the contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class NavigatorEventBusLifecycleTest {

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
    fun `bus survives across simulated bridge detach and re-attach`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val bus = NavigatorEventBus(mockk(relaxed = true))

        // Bridge 1 attaches, observes one command, then detaches when its job completes.
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

        // Dispatch while no executor is collecting: `tryEmit` buffers without blocking, and the
        // emissions are not cached for subscribers that attach later.
        bus.navTo(Screen.Exercise(uuid = "ex-1"))
        bus.replaceTo(Screen.PastSession(sessionUuid = "session-1"))
        bus.popBack()

        // A fresh subscriber attaches and observes the next emission, not the earlier ones.
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

        // Second bridge attaches post-recreation and sees the next two emissions in order.
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

        // Repeated reads must return the same flow, or a re-attach would miss later commands.
        val first = bus.commands
        val second = bus.commands

        assertSame(first, second)
    }
}
