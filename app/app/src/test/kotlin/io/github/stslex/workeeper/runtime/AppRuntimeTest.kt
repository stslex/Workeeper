// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.di.AppGraph
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
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
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Graph-only transition pins (round-2 protocol): same-database handover, submission ownership,
 * candidate-unpublished-before-preflight, aborts leaving the outgoing generation INTACT
 * (ViewModelStore included), the committed safe boundary — N's teardown completes BEFORE N+1
 * is exposed — the deterministic nested-rollback rejection, CancellationException-safe
 * deferreds, and the epoch/admission behavior of commits vs aborts. The file-swap replacement
 * transaction has its own suite.
 */
internal class AppRuntimeTest {

    private class ProbeViewModel(val onClear: () -> Unit = {}) : ViewModel() {
        override fun onCleared() = onClear()
    }

    @TempDir
    lateinit var tempDir: File

    private val context = mockk<Context>(relaxed = true)
    private val database = mockk<AppDatabase>(relaxed = true)
    private var databaseBuilds = 0
    private val builtLifetimes =
        mutableListOf<io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime>()
    private val closedDatabases = mutableListOf<AppDatabase>()
    private val graphFactoryFailures = mutableSetOf<Int>()
    private var builtGraphs = 0
    private var epochAdvances = 0

    private var preflightOutcome: StartupOutcome = StartupOutcome.Proceed
    private var preflightGate: CompletableDeferred<Unit>? = null
    private var preflightAction: (suspend (RuntimeGeneration) -> Unit)? = null
    private var preflightError: Throwable? = null

    private val provider = mockk<DatabaseSnapshotProvider>(relaxed = true)

    private fun runtimeTest(
        body: suspend kotlinx.coroutines.test.TestScope.(AppRuntime) -> Unit,
    ) = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val runtime = AppRuntime(
            applicationContext = context,
            dbFactory = {
                databaseBuilds++
                database
            },
            imageStorageFactory = { mockk<ImageStorage>(relaxed = true) },
            graphFactory = { _, _, _, lifetime, _ ->
                check(builtGraphs++ !in graphFactoryFailures) { "injected graph construction failure" }
                builtLifetimes += lifetime
                mockk<AppGraph>(relaxed = true) {
                    every { databaseSnapshotProvider } returns provider
                }
            },
            preflight = { generation ->
                preflightAction?.invoke(generation)
                preflightGate?.await()
                preflightError?.let { throw it }
                preflightOutcome
            },
            closeDatabase = { closedDatabases += it },
            policy = RuntimeTransitionPolicy(
                advanceSnackbarGeneration = { epochAdvances++ },
                mainDispatcher = dispatcher,
                hostDispatcher = dispatcher,
                stagingDirectory = { tempDir },
                uiDisposalTimeoutMillis = 1_000,
                drainTimeoutMillis = 1_000,
            ),
        )
        body(runtime)
    }

    private fun putProbeViewModel(generation: RuntimeGeneration, onClear: () -> Unit = {}) {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ProbeViewModel(onClear) as T
        }
        ViewModelProvider(generation.viewModelStore, factory)[ProbeViewModel::class.java]
    }

    @Test
    fun `generation 1 builds lazily exactly once - both phase views derive from ONE value`() =
        runtimeTest { runtime ->
            assertEquals(0, databaseBuilds)

            val first = runtime.currentGeneration
            val second = runtime.currentGeneration

            assertSame(first, second)
            assertEquals(1, first.id)
            assertEquals(1, first.dbGeneration)
            assertEquals(1, databaseBuilds)
            val phase = assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            val uiPhase = assertInstanceOf(
                AppUiPhase.Generation::class.java,
                runtime.uiPhases.value,
            )
            assertSame(first, phase.generation)
            assertEquals(first.id, uiPhase.id)
            assertSame(first, uiPhase.viewModelStoreOwner)
        }

    @Test
    fun `graph-only handover - same database object, fresh graph lifetime store, next id`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration

            val outcome = runtime.reinitialize()

            val genTwo =
                assertInstanceOf(ReinitializeOutcome.Published::class.java, outcome).generation
            assertSame(genOne.database, genTwo.database)
            assertEquals(1, databaseBuilds, "the db factory must not run again")
            assertEquals(genOne.dbGeneration, genTwo.dbGeneration)
            assertNotSame(genOne.graph, genTwo.graph)
            assertNotSame(genOne.lifetime, genTwo.lifetime)
            assertNotSame(genOne.viewModelStore, genTwo.viewModelStore)
            assertEquals(genOne.id + 1, genTwo.id)
            assertTrue(closedDatabases.isEmpty(), "graph-only transitions never close the database")
            assertSame(genTwo, runtime.currentGeneration)
        }

    @Test
    fun `committed safe boundary - N teardown COMPLETES before N+1 is exposed`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            var phaseAtVmClear: AppUiPhase? = null
            var phaseAtJobEnd: AppUiPhase? = null
            putProbeViewModel(genOne, onClear = { phaseAtVmClear = runtime.uiPhases.value })
            genOne.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                try {
                    awaitCancellation()
                } finally {
                    phaseAtJobEnd = runtime.uiPhases.value
                }
            }

            val outcome = runtime.reinitialize()

            val genTwo =
                assertInstanceOf(ReinitializeOutcome.Published::class.java, outcome).generation
            // Both teardown observers saw the TRANSITION window — never generation N+1: the
            // publication happened only after N's teardown reached its committed boundary.
            assertInstanceOf(AppUiPhase.Transitioning::class.java, phaseAtVmClear)
            assertInstanceOf(AppUiPhase.Transitioning::class.java, phaseAtJobEnd)
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

        assertSame(genOne, runtime.currentGeneration)
        assertEquals(RuntimePhase.Transitioning, runtime.phases.value)

        gate.complete(Unit)
        runCurrent()
        assertInstanceOf(ReinitializeOutcome.Published::class.java, transition.await())
    }

    @Test
    fun `preflight failure aborts - generation 1 serving, reactors alive, ViewModelStore INTACT`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            putProbeViewModel(genOne)
            preflightOutcome = StartupOutcome.RouteToRecovery

            val outcome = runtime.reinitialize()

            assertInstanceOf(ReinitializeOutcome.Aborted::class.java, outcome)
            assertSame(genOne, runtime.currentGeneration)
            assertTrue(genOne.lifetime.isActive, "an abort must leave generation 1 fully serving")
            assertTrue(
                genOne.viewModelStore.keys().isNotEmpty(),
                "the outgoing ViewModelStore must be intact after a pre-PONR abort",
            )
            val candidateLifetime = builtLifetimes.last()
            assertNotSame(genOne.lifetime, candidateLifetime)
            assertFalse(candidateLifetime.isActive)
            assertTrue(closedDatabases.isEmpty(), "the SHARED database must never be closed")
        }

    @Test
    fun `candidate construction failure unwinds - Serving restored, shared db open, no strand`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            putProbeViewModel(genOne)
            graphFactoryFailures.add(1) // the candidate's graph build

            val outcome = runtime.reinitialize()

            val aborted = assertInstanceOf(ReinitializeOutcome.Aborted::class.java, outcome)
            assertTrue(aborted.reason.contains("construction"), "reason: ${aborted.reason}")
            assertSame(genOne, (runtime.phases.value as RuntimePhase.Serving).generation)
            assertTrue(closedDatabases.isEmpty(), "a graph-only candidate shares the database")
            assertTrue(genOne.viewModelStore.keys().isNotEmpty())
        }

    @Test
    fun `preflight throw unwinds deterministically to Serving`() = runtimeTest { runtime ->
        val genOne = runtime.currentGeneration
        preflightError = IllegalStateException("injected preflight failure")

        val outcome = runtime.reinitialize()

        assertInstanceOf(ReinitializeOutcome.Aborted::class.java, outcome)
        assertSame(genOne, (runtime.phases.value as RuntimePhase.Serving).generation)
        assertTrue(genOne.lifetime.isActive)
    }

    @Test
    fun `preflight CancellationException - the deferred STILL resolves, with an abort`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            preflightError = CancellationException("injected cancellation inside preflight")

            val outcome = runtime.reinitialize()

            // Mandate 8: every submitted deferred completes exactly once — an internal
            // CancellationException resolves like any escape instead of stranding the awaiter.
            assertInstanceOf(ReinitializeOutcome.Aborted::class.java, outcome)
            assertSame(genOne, (runtime.phases.value as RuntimePhase.Serving).generation)
            assertTrue(genOne.lifetime.isActive)
            assertEquals(0, epochAdvances, "no commit — no epoch advance")
        }

    @Test
    fun `nested rollback inside a graph-only preflight is REJECTED - no deadlock, no file ops`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            var nestedResult: DatabaseReplacementResult? = null
            preflightAction = { _ ->
                nestedResult = runtime.rollbackToPreRestoreBackup()
            }
            preflightOutcome = StartupOutcome.RestartRequired

            val outcome = runtime.reinitialize()

            assertInstanceOf(
                DatabaseReplacementResult.RejectedBeforeMutation::class.java,
                nestedResult,
            )
            assertInstanceOf(ReinitializeOutcome.Aborted::class.java, outcome)
            assertSame(genOne, runtime.currentGeneration)
            coVerify(exactly = 0) { provider.replaceLiveDatabaseFile(any()) }
            coVerify(exactly = 0) { provider.deletePreRestoreBackup() }
            assertTrue(closedDatabases.isEmpty())
        }

    @Test
    fun `unreleased worker lease aborts the graph-only transition too`() = runtimeTest { runtime ->
        val genOne = runtime.currentGeneration
        val lease = runtime.awaitBackupWorkLease()

        val outcome = runtime.reinitialize()

        val aborted = assertInstanceOf(ReinitializeOutcome.Aborted::class.java, outcome)
        assertTrue(aborted.reason.contains("lease"), "reason: ${aborted.reason}")
        assertSame(genOne, runtime.currentGeneration)
        lease.release()
        assertInstanceOf(ReinitializeOutcome.Published::class.java, runtime.reinitialize())
    }

    @Test
    fun `stale expected generation coalesces instead of running a second transition`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration

            val first = runtime.reinitialize(expected = genOne)
            val second = runtime.reinitialize(expected = genOne)

            val published = assertInstanceOf(ReinitializeOutcome.Published::class.java, first)
            val coalesced =
                assertInstanceOf(ReinitializeOutcome.AlreadyReplaced::class.java, second)
            assertSame(published.generation, coalesced.serving)
        }

    @Test
    fun `caller cancellation abandons only the await - the transition itself completes`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            val gate = CompletableDeferred<Unit>()
            preflightGate = gate
            var callerCancelled = false

            val caller = launch {
                try {
                    runtime.reinitialize()
                } catch (e: CancellationException) {
                    callerCancelled = true
                    throw e
                }
            }
            runCurrent()
            caller.cancel()
            runCurrent()
            assertTrue(callerCancelled)

            gate.complete(Unit)
            runCurrent()

            val serving = assertInstanceOf(RuntimePhase.Serving::class.java, runtime.phases.value)
            assertEquals(2, serving.generation.id, "the transition must complete despite the caller")
        }

    @Test
    fun `an admitted ui region gates the transition - only ITS OWN token releases it`() =
        runtimeTest { runtime ->
            val genOne = runtime.currentGeneration
            val token = requireNotNull(runtime.admitUiGeneration(genOne.id))
            // A second region of ANOTHER generation, whose release must not open this gate.
            val foreign = requireNotNull(runtime.admitUiGeneration(genOne.id + 7))
            val transition = async { runtime.reinitialize() }
            runCurrent()

            assertEquals(RuntimePhase.Transitioning, runtime.phases.value)
            assertTrue(transition.isActive)

            runtime.releaseUiGeneration(foreign) // another generation's token — must not release
            runCurrent()
            assertTrue(transition.isActive)

            runtime.releaseUiGeneration(token)
            runCurrent()
            assertInstanceOf(ReinitializeOutcome.Published::class.java, transition.await())
        }

    @Test
    fun `committed graph-only handover advances the snackbar epoch - aborts never do`() =
        runtimeTest { runtime ->
            runtime.currentGeneration
            preflightOutcome = StartupOutcome.RouteToRecovery
            runtime.reinitialize()
            assertEquals(0, epochAdvances, "aborts preserve the queued snackbar models")

            preflightOutcome = StartupOutcome.Proceed
            assertInstanceOf(ReinitializeOutcome.Published::class.java, runtime.reinitialize())
            assertEquals(1, epochAdvances, "the commit discards the outgoing generation's queue")
        }
}
