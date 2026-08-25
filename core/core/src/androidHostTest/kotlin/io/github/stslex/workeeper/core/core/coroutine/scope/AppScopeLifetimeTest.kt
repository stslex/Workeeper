// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.coroutine.scope

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the [AppScopeLifetime] contract: per-consumer supervisor isolation, plus the property the
 * anonymous scopes it replaced lacked — a deterministic end.
 */
internal class AppScopeLifetimeTest {

    @Test
    fun `cancelAndJoin ends a derived scope's infinite work and awaits its finally block`() = runTest {
        val lifetime = AppScopeLifetime()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val started = CompletableDeferred<Unit>()
        var finallyRan = false

        lifetime.childScope(dispatcher).launch {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                finallyRan = true
            }
        }
        started.await()

        lifetime.cancelAndJoin()

        // Join semantics, not just cancel: "ended" includes the collector's finally path.
        assertTrue(finallyRan, "cancelAndJoin must await the cancelled child's finally block")
        assertFalse(lifetime.isActive, "the lifetime must be inactive after cancelAndJoin")
    }

    @Test
    fun `one consumer's failure never ends the generation or a sibling consumer`() = runTest {
        val lifetime = AppScopeLifetime()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val swallow = CoroutineExceptionHandler { _, _ -> /* the consumer's own error policy */ }
        var siblingRan = false

        lifetime.childScope(dispatcher + swallow).launch { error("consumer failure") }
        lifetime.childScope(dispatcher).launch { siblingRan = true }
        runCurrent()

        // Isolation: a failed consumer ends neither a sibling nor the generation.
        assertTrue(siblingRan, "a sibling consumer's work must run despite another's failure")
        assertTrue(lifetime.isActive, "a consumer failure must not cancel the generation lifetime")
    }

    @Test
    fun `failure of one launch does not cancel a sibling launch in the same derived scope`() = runTest {
        val lifetime = AppScopeLifetime()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val swallow = CoroutineExceptionHandler { _, _ -> }
        val scope = lifetime.childScope(dispatcher + swallow)
        var siblingRan = false

        scope.launch { error("first launch fails") }
        scope.launch { siblingRan = true }
        runCurrent()

        assertTrue(siblingRan, "the derived scope must be a supervisor for its own launches")
    }

    @Test
    fun `a derived scope minted after cancel runs nothing`() = runTest {
        val lifetime = AppScopeLifetime()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        var ran = false

        lifetime.cancel()
        lifetime.childScope(dispatcher).launch { ran = true }
        runCurrent()

        // A terminal generation is terminal for late arrivals too.
        assertFalse(ran, "work launched on a post-cancel child scope must not run")
    }

    @Test
    fun `cancelAndJoin is idempotent`() = runTest {
        val lifetime = AppScopeLifetime()

        lifetime.cancelAndJoin()
        lifetime.cancelAndJoin()

        assertFalse(lifetime.isActive)
    }
}
