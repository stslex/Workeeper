// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerCreator
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * The generation-join contract for [BaseStore] (KMP Phase 5 R3, spec §8.4 — round-3 blocker 4).
 *
 * The claim under test is the one a replacement transaction rests on: **a Store's jobs are
 * DESCENDANTS of the runtime generation's lifetime**, so the runtime's Quiescing stage can
 * `cancelAndJoin()` them and every `finally` — including one that touches the generation's
 * database — has finished by the time that join returns and the database is closed.
 *
 * Nothing here stands in for the production wiring: it builds a REAL [BaseStore] over the REAL
 * [io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScopeImpl] (the only scope
 * [BaseStore.init] ever constructs) and a REAL [AppScopeLifetime]. The single fake is the
 * [LifecycleOwner], because a composition owner cannot exist in a JVM test — and it is precisely
 * the object the pre-R3 plus-order bug parented every Store job to.
 *
 * The known-negative this file pins is a one-token edit in `AppCoroutineScopeImpl`: write the
 * `SupervisorJob(generationJob)` as the LEFT operand of the `plus` (as it was before R3) and
 * `CoroutineContext.plus` discards it in favour of `lifecycleScope`'s Job. Store jobs stop being
 * reachable from the lifetime, and the first test below fails on its ORDERING assertion —
 * `cancelAndJoin` returns while the database-touching `finally` is still pending, which is the
 * production defect verbatim (a job writing into a database the runtime has already closed).
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class StoreGenerationJoinTest {

    private lateinit var dispatcher: TestDispatcher
    private lateinit var owner: FakeLifecycleOwner
    private var wasLogging: Boolean = true

    /**
     * `Log.isLogging` goes off for the duration: [BaseStore]'s logger is the production [Log],
     * whose Kermit writer is the logcat writer on this (Android library) unit-test classpath, and
     * `android.util.Log` is not mocked in a plain JVM test — the first `consume` would throw. The
     * Firebase sinks need no such handling: both holders resolve their client through a
     * `runCatching` that yields `null` off-device, so every call there is already a no-op.
     */
    @BeforeEach
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
        dispatcher = StandardTestDispatcher()
        // `lifecycleScope` builds its scope on `Dispatchers.Main.immediate`, so a Main dispatcher
        // must exist before any Store is initialised. Installing a TestDispatcher also makes the
        // `runTest`s below adopt ITS scheduler, which is what puts the store's jobs, the
        // lifecycle registration and the test body on one deterministic timeline.
        Dispatchers.setMain(dispatcher)
        owner = FakeLifecycleOwner()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        Log.isLogging = wasLogging
    }

    @Test
    fun `a Store job started via launchDefault is JOINED by the generation lifetime`() = runTest {
        val lifetime = AppScopeLifetime()
        val store = probeStore()
        store.init(owner, lifetime.job)

        val order = mutableListOf<String>()
        // The mandated DB touch, simulated: a recorded call made from inside the `finally`.
        val databaseTouch = mockk<() -> Unit>(relaxed = true)
        val started = CompletableDeferred<Unit>()

        val job = store.launchDefault {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                // NonCancellable + a delay is what a real teardown write looks like: work that
                // must still run AFTER cancellation and that takes time. A lifetime that only
                // cancelled (never joined) would return while this is still pending.
                withContext(NonCancellable) {
                    delay(TEARDOWN_WORK_MILLIS)
                    databaseTouch()
                    order += FINALLY
                }
            }
        }
        started.await()

        lifetime.cancelAndJoin()
        order += JOIN_RETURNED

        assertEquals(
            listOf(FINALLY, JOIN_RETURNED),
            order,
            "the Store's database-touching finally must COMPLETE before cancelAndJoin returns",
        )
        verify(exactly = 1) { databaseTouch() }
        assertTrue(job.isCompleted, "the joined job must be complete, not merely cancelled")
        assertFalse(lifetime.isActive, "the generation lifetime is ended by cancelAndJoin")
    }

    @Test
    fun `ordinary UI disposal cancels Store jobs early without waiting for the generation`() = runTest {
        val lifetime = AppScopeLifetime()
        val store = probeStore()
        store.init(owner, lifetime.job)

        val started = CompletableDeferred<Unit>()
        val job = store.launchDefault {
            started.complete(Unit)
            awaitCancellation()
        }
        started.await()

        store.dispose()
        job.join()

        assertTrue(job.isCancelled, "leaving the composition must end the Store's jobs immediately")
        assertTrue(
            lifetime.isActive,
            "a screen going away is not a generation ending — the lifetime must survive it",
        )

        lifetime.cancelAndJoin()
    }

    @Test
    fun `onCleared disposes the Store`() = runTest {
        val lifetime = AppScopeLifetime()
        val store = probeStore()
        store.init(owner, lifetime.job)

        val started = CompletableDeferred<Unit>()
        val job = store.launchDefault {
            started.complete(Unit)
            awaitCancellation()
        }
        started.await()

        // A generation teardown clears its runtime-owned ViewModelStore, which is this call.
        store.clearAsViewModelStoreWould()
        job.join()

        assertTrue(job.isCancelled, "a cleared Store must not keep running against the old generation")
        assertEquals(1, store.handledActions.size, "the dispose action runs once on the clear")

        // Idempotence: the composition's own onDispose lands AFTER the clear. Before R3 this
        // second call read the nulled scope through `requireNotNull` and threw.
        assertDoesNotThrow { store.dispose() }
        assertEquals(1, store.handledActions.size, "a second dispose must repeat no dispose work")
        assertTrue(lifetime.isActive, "clearing one Store does not end the generation")

        lifetime.cancelAndJoin()
    }

    @Test
    fun `a Store with no generation job still works`() = runTest {
        val store = probeStore()
        store.init(owner, null)

        val ran = CompletableDeferred<Unit>()
        val job = store.launchDefault { ran.complete(Unit) }
        ran.await()
        job.join()

        assertTrue(job.isCompleted, "the null default keeps previews and tests working")
        assertFalse(job.isCancelled)

        store.dispose()
    }

    private fun probeStore(): ProbeStore = ProbeStore(
        storeDispatchers = StoreDispatchers(
            defaultDispatcher = dispatcher,
            mainImmediateDispatcher = dispatcher,
        ),
    )

    private companion object {

        const val FINALLY = "finally"
        const val JOIN_RETURNED = "cancelAndJoin returned"
        const val TEARDOWN_WORK_MILLIS = 250L
    }
}

/**
 * A [LifecycleOwner] usable off-device. [LifecycleRegistry.createUnsafe] drops the main-thread
 * assertion `ArchTaskExecutor` makes (it reads `Looper.getMainLooper()`, which is not mocked in a
 * JVM unit test); the registry is otherwise the production one, so `lifecycleScope` and
 * [BaseStore]'s lifecycle observer behave as they do in the app.
 */
private class FakeLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle get() = registry
}

private data object ProbeState : Store.State

private data object ProbeAction : Store.Action

private data object ProbeEvent : Store.Event

/**
 * The smallest REAL [BaseStore]. It carries a dispose action and records every action its handler
 * receives, so idempotence can be asserted as behaviour ("the dispose work ran exactly once")
 * rather than as "the second call did not throw".
 */
private class ProbeStore(
    storeDispatchers: StoreDispatchers,
    private val handled: MutableList<ProbeAction> = mutableListOf(),
) : BaseStore<ProbeState, ProbeAction, ProbeEvent>(
    name = "ProbeStore",
    initialState = ProbeState,
    storeEmitter = BaseHandlerStore(),
    handlerCreator = HandlerCreator<ProbeAction> {
        Handler<ProbeAction> { action -> handled += action }
    },
    storeDispatchers = storeDispatchers,
    disposeActions = listOf(ProbeAction),
    analyticsHolder = AnalyticsHolder(),
    loggerHolder = LoggerHolder(),
) {

    val handledActions: List<ProbeAction> get() = handled

    /** `onCleared` is `protected`; a subclass is the only place that can reach it. */
    fun clearAsViewModelStoreWould() {
        onCleared()
    }
}
