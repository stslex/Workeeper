// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.di.AppGraph
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The R2 generation-lifecycle pins for [AppRuntime]'s graph-only transitions
 * (`kmp-phase-5-startup-processor.md` §8.1/§12): same-database handover, atomic publication,
 * candidate-not-published-before-preflight, abort-leaves-N-serving, deterministic disposal,
 * single-flight + coalescing. The file-swap replacement transaction has its own suite.
 */
internal class AppRuntimeTest {

    private val context = mockk<Context>(relaxed = true)
    private val database = mockk<AppDatabase>(relaxed = true)
    private var databaseBuilds = 0
    private val builtLifetimes = mutableListOf<AppScopeLifetime>()

    private var preflightOutcome: StartupOutcome = StartupOutcome.Proceed
    private var preflightGate: CompletableDeferred<Unit>? = null
    private var drainWorkersBlocks = false

    private fun runtimeTest(
        body: suspend kotlinx.coroutines.test.TestScope.(AppRuntime) -> Unit,
    ) = runTest {
        val runtime = AppRuntime(
            applicationContext = context,
            dbFactory = {
                databaseBuilds++
                database
            },
            imageStorageFactory = { mockk<ImageStorage>(relaxed = true) },
            graphFactory = { _, _, _, lifetime, _ ->
                builtLifetimes += lifetime
                mockk<AppGraph>(relaxed = true)
            },
            preflight = {
                preflightGate?.await()
                preflightOutcome
            },
            policy = RuntimeTransitionPolicy(
                drainWorkers = { if (drainWorkersBlocks) awaitCancellation() },
                mainDispatcher = UnconfinedTestDispatcher(testScheduler),
                uiDisposalTimeoutMillis = 1_000,
                drainTimeoutMillis = 1_000,
            ),
        )
        body(runtime)
    }

    @Test
    fun `generation 1 builds lazily exactly once and publishes atomically`() = runtimeTest { runtime ->
        assertEquals(0, databaseBuilds)

        val first = runtime.currentGeneration
        val second = runtime.currentGeneration

        assertSame(first, second)
        assertEquals(1, first.id)
        assertEquals(1, first.dbGeneration)
        assertEquals(1, databaseBuilds)
        val phase = runtime.phases.value
        assertInstanceOf(RuntimePhase.Serving::class.java, phase)
        assertSame(first, (phase as RuntimePhase.Serving).generation)
    }

    @Test
    fun `graph-only handover - same database object, fresh graph lifetime store, next id`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration

            val outcome = runtime.reinitialize()

            val genTwo = assertInstanceOf(ReinitializeOutcome.Published::class.java, outcome).generation
            // R2: graph-only lifecycle work reuses the current database instance — the db factory
            // must not run again, and the same OBJECT is handed into the next generation.
            assertSame(genOne.database, genTwo.database)
            assertEquals(1, databaseBuilds)
            assertEquals(genOne.dbGeneration, genTwo.dbGeneration)
            assertNotSame(genOne.graph, genTwo.graph)
            assertNotSame(genOne.lifetime, genTwo.lifetime)
            assertNotSame(genOne.viewModelStore, genTwo.viewModelStore)
            assertEquals(genOne.id + 1, genTwo.id)
            assertSame(genTwo, runtime.currentGeneration)
        }

    @Test
    fun `old lifetime is cancelled and joined after publish - new lifetime active`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            var finallyRan = false
            genOne.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                try {
                    awaitCancellation()
                } finally {
                    finallyRan = true
                }
            }

            val outcome = runtime.reinitialize()

            val genTwo = assertInstanceOf(ReinitializeOutcome.Published::class.java, outcome).generation
            assertTrue(finallyRan, "the outgoing generation's collector must have ENDED")
            assertFalse(genOne.lifetime.isActive)
            assertTrue(genTwo.lifetime.isActive)
        }

    @Test
    fun `candidate is not published before preflight completes`() = runtimeTest { runtime ->
        val genOne = runtime.currentGeneration
        val gate = CompletableDeferred<Unit>()
        preflightGate = gate

        val transition = async { runtime.reinitialize() }
        runCurrent()

        // Mid-preflight: the candidate exists but is UNPUBLISHED — seam readers still get gen 1,
        // and the UI phase is Transitioning (no generation offered to new UI work).
        assertSame(genOne, runtime.currentGeneration)
        assertEquals(RuntimePhase.Transitioning, runtime.phases.value)

        gate.complete(Unit)
        val outcome = transition.await()
        assertInstanceOf(ReinitializeOutcome.Published::class.java, outcome)
    }

    @Test
    fun `preflight failure aborts - generation 1 keeps serving with its reactors alive`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            preflightOutcome = StartupOutcome.RouteToRecovery

            val outcome = runtime.reinitialize()

            assertInstanceOf(ReinitializeOutcome.Aborted::class.java, outcome)
            assertSame(genOne, runtime.currentGeneration)
            // The relaxed graph-only quiesce order is exactly what makes this possible: the
            // outgoing lifetime is untouched until AFTER a successful publish.
            assertTrue(genOne.lifetime.isActive, "an abort must leave generation 1 fully serving")
            val phase = runtime.phases.value
            assertSame(genOne, (phase as RuntimePhase.Serving).generation)
            // The failed candidate's lifetime was disposed — nothing of it leaks.
            val candidateLifetime = builtLifetimes.last()
            assertNotSame(genOne.lifetime, candidateLifetime)
            assertFalse(candidateLifetime.isActive)
        }

    @Test
    fun `worker drain timeout aborts before anything irreversible`() = runtimeTest { runtime ->
        val genOne = runtime.currentGeneration
        drainWorkersBlocks = true

        val outcome = runtime.reinitialize()

        val aborted = assertInstanceOf(ReinitializeOutcome.Aborted::class.java, outcome)
        assertTrue(aborted.reason.contains("worker drain"), "reason was: ${aborted.reason}")
        assertSame(genOne, runtime.currentGeneration)
        assertTrue(genOne.lifetime.isActive)
        // No candidate was ever built: the abort happened inside Quiescing.
        assertEquals(1, builtLifetimes.size)
    }

    @Test
    fun `stale expected generation coalesces instead of running a second transition`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration

            val first = runtime.reinitialize(expected = genOne)
            val second = runtime.reinitialize(expected = genOne)

            val published = assertInstanceOf(ReinitializeOutcome.Published::class.java, first)
            val coalesced = assertInstanceOf(ReinitializeOutcome.AlreadyReplaced::class.java, second)
            assertSame(published.generation, coalesced.serving)
            assertEquals(2, builtLifetimes.size, "exactly one transition must have run")
        }

    @Test
    fun `concurrent transitions serialize - readers never observe a mixture`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate

            val a = async { runtime.reinitialize(expected = genOne) }
            val b = async { runtime.reinitialize(expected = genOne) }
            runCurrent()
            gate.complete(Unit)

            val outcomes = listOf(a.await(), b.await())
            assertEquals(
                1,
                outcomes.count { it is ReinitializeOutcome.Published },
                "exactly one of two concurrent same-expected requests may publish; got $outcomes",
            )
            assertEquals(1, outcomes.count { it is ReinitializeOutcome.AlreadyReplaced })
        }

    @Test
    fun `attached ui region gates the transition until it signals disposal`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            runtime.onUiGenerationAttached(genOne.id)
            val transition = async { runtime.reinitialize() }
            runCurrent()

            // Blocked on the UI region's departure; nothing published, gen 1 still current.
            assertEquals(RuntimePhase.Transitioning, runtime.phases.value)
            assertTrue(transition.isActive)

            runtime.onUiGenerationDisposed(genOne.id)
            val outcome = transition.await()
            assertInstanceOf(ReinitializeOutcome.Published::class.java, outcome)
        }
}
