// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.navigation

import io.github.stslex.workeeper.core.ui.navigation.Screen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class ResultChannelRegistryTest {
    @Test
    fun sameKeyHasOneChannelUnderContention() = runBlocking {
        repeat(20) {
            val bus = newTestBus()
            val channels = contend { index ->
                if (index % 2 == 0) bus.setResult("shared", index)
                bus.result("shared")
            }
            channels.forEach { assertSame(channels.first(), it) }
            bus.setResult("shared", "winner")
            channels.forEach { assertEquals("winner", it.value) }
        }
    }

    @Test
    fun concurrentKeysRetainTheirPublishedFlows() = runBlocking {
        val bus = newTestBus()
        val channels = contend { index ->
            val key = "key-$index"
            val channel = bus.result(key)
            bus.setResult(key, index)
            channel
        }
        channels.forEachIndexed { index, channel ->
            assertSame(channel, bus.result("key-$index"))
            assertEquals(index, channel.value)
            assertNotSame(channel, channels[(index + 1) % channels.size])
        }
    }

    @Test
    fun navigationClearsValuesWithoutReplacingChannels() {
        val bus = newTestBus()
        val first = bus.result("first")
        val second = bus.result("second")
        val navigation = listOf<() -> Unit>(
            { bus.navTo(Screen.BottomBar.Home) },
            { bus.popBack() },
            { bus.replaceTo(Screen.BottomBar.AllExercises) },
        )
        navigation.forEach { navigate ->
            bus.setResult("first", 1)
            bus.setResult("second", 2)
            navigate()
            assertNull(first.value)
            assertNull(second.value)
            assertSame(first, bus.result("first"))
            assertSame(second, bus.result("second"))
        }
        bus.setResult("first", "keep")
        bus.openRecovery()
        bus.popBackWithResult(Screen.ExerciseImage::class, "image")
        assertEquals("keep", first.value)
        bus.clearResult("first")
        assertNull(first.value)
        assertSame(first, bus.result("first"))
    }

    private suspend fun contend(block: (Int) -> StateFlow<Any?>): List<StateFlow<Any?>> = coroutineScope {
        val ready = Channel<Unit>(WORKERS)
        val start = CompletableDeferred<Unit>()
        val workers = List(WORKERS) { index ->
            async(Dispatchers.Default) {
                ready.send(Unit)
                start.await()
                block(index)
            }
        }
        repeat(WORKERS) { ready.receive() }
        start.complete(Unit)
        workers.awaitAll().also { ready.close() }
    }

    private companion object {
        const val WORKERS = 16
    }
}

internal expect fun newTestBus(): NavigatorEventBus
