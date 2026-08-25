// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.runtime

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreSourceRef
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.di.AppGraph
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Graph-only transition pins: same-database handover, aborts leaving the outgoing generation
 * intact, the committed safe boundary. The file-swap transaction has its own suite.
 */
internal class AppRuntimeTest {

    private class NoOpEffects(
        override val attemptId: RestoreOwnerId,
    ) : DatabaseReplacementEffects {
        override suspend fun onBeforeMutation(
            undoRef: UndoRef,
            restoreSourceRef: RestoreSourceRef?,
        ) = Unit

        override suspend fun onMutationCommitted() = Unit

        override suspend fun onBeforeCompensation(
            rollbackOwner: RestoreOwnerId,
            appliedRef: UndoRef,
        ) = Unit

        override suspend fun onCompensationCommitted(rollbackOwner: RestoreOwnerId) = Unit
    }

    private class ProbeViewModel(val onClear: () -> Unit = {}) : ViewModel() {
        override fun onCleared() = onClear()
    }

    private val context = mockk<Context>(relaxed = true)
    private val database = mockk<AppDatabase>(relaxed = true)
    private var databaseBuilds = 0
    private val builtLifetimes =
        mutableListOf<io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime>()
    private val closedDatabases = mutableListOf<AppDatabase>()
    private val graphFactoryFailures = mutableSetOf<Int>()
    private var builtGraphs = 0
    private var epochAdvances = 0
    private val phaseAtEpochAdvance = mutableListOf<AppUiPhase>()

    /** Runs inside the graph factory BEFORE its injected failure — the partial-build seam. */
    private var graphFactoryAction:
        ((Int, io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime) -> Unit)? =
        null

    private var preflightOutcome: StartupOutcome = StartupOutcome.Proceed
    private var preflightGate: CompletableDeferred<Unit>? = null
    private var preflightAction: (suspend (RuntimeGeneration) -> Unit)? = null
    private var preflightError: Throwable? = null

    private val provider = mockk<DatabaseSnapshotProvider>(relaxed = true)
    private var runtimeRef: AppRuntime? = null

    private fun runtimeTest(
        body: suspend kotlinx.coroutines.test.TestScope.(AppRuntime) -> Unit,
    ) = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        lateinit var runtimeHandle: AppRuntime
        val runtime = AppRuntime(
            applicationContext = context,
            dbFactory = {
                databaseBuilds++
                database
            },
            imageStorageFactory = { mockk<ImageStorage>(relaxed = true) },
            graphFactory = { _, _, _, lifetime, _ ->
                val index = builtGraphs++
                graphFactoryAction?.invoke(index, lifetime)
                check(index !in graphFactoryFailures) { "injected graph construction failure" }
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
                advanceSnackbarGeneration = {
                    epochAdvances++
                    phaseAtEpochAdvance += runtimeRef?.uiPhases?.value ?: AppUiPhase.Transitioning
                },
                mainDispatcher = dispatcher,
                hostDispatcher = dispatcher,
                uiDisposalTimeoutMillis = 1_000,
                drainTimeoutMillis = 1_000,
            ),
        )
        runtimeHandle = runtime
        runtimeRef = runtimeHandle
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
            // Both teardown observers saw the transition window, never generation N+1.
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
    fun `pending terminal publication terminalizes graph-only transition`() = runtimeTest { runtime ->
        val genOne = runtime.currentGeneration
        preflightOutcome = StartupOutcome.FinalizationPending

        val outcome = runtime.reinitialize()

        assertEquals(ReinitializeOutcome.Fatal, outcome)
        assertEquals(RuntimePhase.Fatal, runtime.phases.value)
        assertEquals(0, epochAdvances)
        assertFalse(builtLifetimes.last().isActive)
        assertNull(runtime.admitUiGeneration(genOne.id))
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

            // Every submitted deferred completes exactly once, internal cancellation included.
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
                nestedResult = runtime.rollbackFromUndo(
                    sourceRef = NESTED_UNDO_REF,
                    effects = NoOpEffects(NESTED_ROLLBACK_OWNER),
                )
            }
            preflightOutcome = StartupOutcome.RestartRequired

            val outcome = runtime.reinitialize()

            assertInstanceOf(
                DatabaseReplacementResult.RejectedBeforeMutation::class.java,
                nestedResult,
            )
            assertInstanceOf(ReinitializeOutcome.Aborted::class.java, outcome)
            assertSame(genOne, runtime.currentGeneration)
            coVerify(exactly = 0) { provider.replaceLiveDatabaseFromUndo(any()) }
            coVerify(exactly = 0) { provider.deleteUndo(any()) }
            assertTrue(closedDatabases.isEmpty())
        }

    @Test
    fun `unreleased worker lease aborts the graph-only transition too`() = runtimeTest { runtime ->
        val genOne = runtime.currentGeneration
        val lease = checkNotNull(runtime.awaitBackupWorkLease())

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

            runtime.releaseUiGeneration(foreign)
            runCurrent()
            assertTrue(transition.isActive)

            runtime.releaseUiGeneration(token)
            runCurrent()
            assertInstanceOf(ReinitializeOutcome.Published::class.java, transition.await())
        }

    @Test
    fun `the snackbar epoch advances BEFORE the successor is published`() =
        runtimeTest { runtime ->
            runtime.currentGeneration

            assertInstanceOf(ReinitializeOutcome.Published::class.java, runtime.reinitialize())

            // Publishing first would let generation N's queued models run against N+1.
            assertEquals(1, phaseAtEpochAdvance.size)
            assertInstanceOf(
                AppUiPhase.Transitioning::class.java,
                phaseAtEpochAdvance.single(),
                "the epoch must advance while the transition window is still published",
            )
        }

    // A failed graph-only teardown is terminal, never publish-anyway.

    @Test
    fun `outgoing ViewModelStore clear failure after PONR is FATAL - no successor is published`() =
        runtimeTest { runtime ->
            // A throwing clear leaves generation N's ViewModel-owned work in an unknown state.
            val genOne = runtime.currentGeneration
            putProbeViewModel(genOne, onClear = { error("injected onCleared failure") })

            val outcome = runtime.reinitialize()

            assertEquals(ReinitializeOutcome.Fatal, outcome)
            assertEquals(RuntimePhase.Fatal, runtime.phases.value, "no N+1 was ever published")
            assertEquals(0, epochAdvances, "a failed handover must not discard N's queued models")
            assertNull(
                runtime.admitUiGeneration(genOne.id),
                "the retired outgoing id must stay closed — nothing reopened the UI gate",
            )
            assertThrows<IllegalStateException> { runtime.currentGeneration }
        }

    @Test
    fun `unjoinable outgoing lifetime after PONR is FATAL - epoch unchanged, admission refused`() =
        runtimeTest { runtime ->
            // An unjoinable job of generation N means N's work survives any publication.
            val genOne = runtime.currentGeneration
            val never = CompletableDeferred<Unit>()
            genOne.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                withContext(NonCancellable) { never.await() }
            }

            val transition = async { runtime.reinitialize() }
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(ReinitializeOutcome.Fatal, transition.await())
            assertEquals(RuntimePhase.Fatal, runtime.phases.value)
            assertEquals(0, epochAdvances, "N's producers are still live — their models survive")
            assertNull(
                runtime.admitUiGeneration(genOne.id),
                "UI admission stays closed for the retired id",
            )
            val lease = async { runCatching { runtime.awaitBackupWorkLease() } }
            runCurrent()
            assertTrue(lease.isCompleted, "the acquirer fails loud instead of parking")
            assertInstanceOf(IllegalStateException::class.java, lease.await().exceptionOrNull())
            never.complete(Unit)
            runCurrent()
        }

    @Test
    fun `candidate preflight failure with an unjoinable candidate is FATAL - N is never republished`() =
        runtimeTest { runtime ->
            // The candidate's jobs share the live database, so republishing N beside an
            // unjoined candidate job would strand an orphan over it.
            runtime.currentGeneration
            val never = CompletableDeferred<Unit>()
            preflightAction = { generation ->
                generation.lifetime.childScope(UnconfinedTestDispatcher(testScheduler)).launch {
                    withContext(NonCancellable) {
                        never.await()
                    }
                }
                // Yield so the nested unconfined job genuinely starts before preflight returns.
                kotlinx.coroutines.yield()
            }
            preflightOutcome = StartupOutcome.RouteToRecovery

            val transition = async { runtime.reinitialize() }
            advanceTimeBy(5_000)
            runCurrent()

            assertEquals(ReinitializeOutcome.Fatal, transition.await())
            assertEquals(
                RuntimePhase.Fatal,
                runtime.phases.value,
                "Serving N must NOT come back while a candidate job may still hold the database",
            )
            assertEquals(0, epochAdvances)
            never.complete(Unit)
            runCurrent()
        }

    @Test
    fun `partial construction with an unjoinable child is TERMINAL - not an ordinary abort`() =
        runtimeTest { runtime ->
            // The graph constructor starts an unjoinable job on the fresh lifetime, then throws.
            runtime.currentGeneration
            val never = CompletableDeferred<Unit>()
            graphFactoryFailures.add(1)
            graphFactoryAction = { index, lifetime ->
                if (index == 1) {
                    // GUARD: a real dispatcher — the unwind join runs inside buildGeneration's
                    // own runBlocking, which the test scheduler never advances.
                    lifetime.childScope(Dispatchers.Unconfined).launch {
                        withContext(NonCancellable) {
                            never.await()
                        }
                    }
                }
            }

            val outcome = runtime.reinitialize()

            assertEquals(ReinitializeOutcome.Fatal, outcome)
            assertEquals(RuntimePhase.Fatal, runtime.phases.value, "N was not republished")
            assertEquals(0, epochAdvances)
            never.complete(Unit)
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

    private companion object {
        val NESTED_UNDO_REF = UndoRef(
            RestoreOwnerId("00000000-0000-4000-8000-000000000021"),
        )
        val NESTED_ROLLBACK_OWNER = RestoreOwnerId(
            "00000000-0000-4000-8000-000000000022",
        )
    }
}
