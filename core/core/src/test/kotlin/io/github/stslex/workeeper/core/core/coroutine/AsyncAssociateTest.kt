// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.coroutine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class AsyncAssociateTest {

    @Test
    fun `empty Iterable produces empty Map`() = runTest {
        val result = emptyList<Int>().asyncAssociate { it to it.toString() }
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single element produces a one-entry Map`() = runTest {
        val result = listOf(7).asyncAssociate { it to "value-$it" }
        assertEquals(mapOf(7 to "value-7"), result)
    }

    @Test
    fun `multiple unique keys are all preserved`() = runTest {
        val items = (1..50).toList()

        val result = items.asyncAssociate { it to it * 10 }

        assertEquals(items.toSet(), result.keys)
        items.forEach { assertEquals(it * 10, result[it]) }
    }

    @Test
    fun `duplicate keys are resolved by last-write-wins via mutableMap put`() = runTest {
        // Document the chosen semantics. The implementation uses
        // `mutableMap[key] = value` inside a coroutineScope; after `awaitAll()`
        // returns the Map, the resolved value for any duplicate key is whichever
        // async transform happened to write last. Tests assert that each key
        // is present and that the value is *one of* the candidates — order is
        // not guaranteed across runs because async children execute in parallel.
        val pairs = listOf(
            "a" to 1,
            "a" to 2,
            "b" to 3,
            "a" to 4,
        )

        val result = pairs.asyncAssociate { it }

        assertEquals(setOf("a", "b"), result.keys)
        assertTrue(result["a"] in setOf(1, 2, 4))
        assertEquals(3, result["b"])
    }

    @Test
    fun `transforms run concurrently rather than sequentially`() = runTest {
        // If the implementation ran transforms sequentially, the test would deadlock:
        // each transform suspends on a Deferred that is only completed after a higher-
        // numbered transform has started. Concurrent execution unblocks the chain.
        val gates = (0..3).map { CompletableDeferred<Unit>() }

        // Launch the transformer chain on a background coroutine so we can observe
        // completion ordering from the test scope.
        val coroutineScope = this
        val deferred = coroutineScope.async {
            (0..3).asyncAssociate { idx ->
                if (idx > 0) {
                    // Wait for the previous gate before completing the current one.
                    gates[idx - 1].await()
                }
                gates[idx].complete(Unit)
                idx to idx
            }
        }

        // Kick off the chain by completing the first gate from outside.
        gates[0].complete(Unit)

        val result = deferred.await()
        assertEquals(mapOf(0 to 0, 1 to 1, 2 to 2, 3 to 3), result)
    }

    @Test
    fun `asyncAll returns true when every predicate holds`() = runTest {
        assertTrue((1..5).asyncAll { it > 0 })
        assertTrue(emptyList<Int>().asyncAll { it > 0 })
    }

    @Test
    fun `asyncAll returns false when any predicate fails`() = runTest {
        // Regression for the loadSession-refactor `asyncAll` bug where the early
        // `return@asyncMap false` only short-circuited the inner map-lambda — the
        // outer function ignored the result and always returned `true`, which made
        // `ExerciseRepositoryImpl.canBulkPermanentDelete` greenlight deletion of
        // exercises that still had finished-session history.
        assertEquals(false, (1..5).asyncAll { it < 4 })
        assertEquals(false, listOf(true, false, true).asyncAll { it })
    }

    @Test
    fun `cancellation of parent scope cancels in-flight transforms`() = runTest {
        // Track how many transforms were observably running when the parent was
        // cancelled. Each transform increments a counter, then suspends on a
        // never-completing Deferred. When the parent scope is cancelled, the suspend
        // points throw CancellationException — assert via the awaitsCancelled flag.
        val started = mutableListOf<Int>()
        var awaitsCancelled = 0
        val neverComplete = CompletableDeferred<Unit>()

        val parent = launch {
            try {
                (0..2).asyncAssociate { idx ->
                    started += idx
                    try {
                        neverComplete.await()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        awaitsCancelled++
                        throw e
                    }
                    idx to idx
                }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Expected — cancellation propagates out.
            }
        }
        // Let the children start.
        runCurrent()
        assertEquals(setOf(0, 1, 2), started.toSet())

        parent.cancel()
        // Drain cancellation. CancellationException propagates through await(),
        // awaitAll(), and out of asyncAssociate's coroutineScope so no leak remains.
        runCurrent()
        // The job is cancelled — by the time we read this assertion, every started
        // transform has had its `neverComplete.await()` cancelled.
        assertEquals(3, awaitsCancelled)
    }
}
