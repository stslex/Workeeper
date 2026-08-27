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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The generation-join contract for [BaseStore]: a Store's jobs are descendants of the runtime
 * generation's lifetime, so `cancelAndJoin` waits for every `finally` before the database closes.
 */
internal class StoreGenerationJoinTest {

    private lateinit var dispatcher: TestDispatcher
    private lateinit var owner: FakeLifecycleOwner
    private var wasLogging: Boolean = true

    /** Logging off for the duration: the logcat writer throws on unmocked `android.util.Log`. */
    @BeforeTest
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
        dispatcher = StandardTestDispatcher()
        // GUARD: setMain before any Store init — lifecycleScope builds on Main.immediate; the
        // shared TestDispatcher scheduler puts Store jobs and the test body on one timeline.
        Dispatchers.setMain(dispatcher)
        owner = FakeLifecycleOwner()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        Log.isLogging = wasLogging
    }

    @Test
    fun aStoreJobStartedViaLaunchDefaultIsJoinedByTheGenerationLifetime() = runTest {
        val lifetime = AppScopeLifetime()
        val store = probeStore(lifetime)
        store.init(owner)

        val order = mutableListOf<String>()
        // The mandated DB touch, simulated: a recorded call made from inside the `finally`.
        val databaseTouch = CallCounter()
        val started = CompletableDeferred<Unit>()

        val job = store.launchDefault {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                // A teardown write: work that must still run after cancellation, and takes time.
                withContext(NonCancellable) {
                    delay(TEARDOWN_WORK_MILLIS)
                    databaseTouch.record()
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
        assertEquals(1, databaseTouch.calls, "the teardown write must run exactly once")
        assertTrue(job.isCompleted, "the joined job must be complete, not merely cancelled")
        assertFalse(lifetime.isActive, "the generation lifetime is ended by cancelAndJoin")
    }

    @Test
    fun ordinaryDisposalCancelsStoreJobsWithoutEndingTheGeneration() = runTest {
        val lifetime = AppScopeLifetime()
        val store = probeStore(lifetime)
        store.init(owner)

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
    fun onClearedDisposesTheStoreExactlyOnce() = runTest {
        val lifetime = AppScopeLifetime()
        val store = probeStore(lifetime)
        store.init(owner)

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

        // Idempotence: the composition's own onDispose lands after the clear.
        store.dispose()
        assertEquals(1, store.handledActions.size, "a second dispose must repeat no dispose work")
        assertTrue(lifetime.isActive, "clearing one Store does not end the generation")

        lifetime.cancelAndJoin()
    }

    @Test
    fun actionsAfterDisposalAreRejected() = runTest {
        val lifetime = AppScopeLifetime()
        val store = probeStore(lifetime)
        store.init(owner)

        store.consume(ProbeAction)
        assertEquals(1, store.handledActions.size, "an active Store consumes the action")

        store.dispose()
        // dispose() consumes disposeActions before disabling the guard, so that is the 2nd.
        assertEquals(2, store.handledActions.size, "the dispose action is consumed by dispose()")

        store.consume(ProbeAction)
        assertEquals(
            2,
            store.handledActions.size,
            "the consume guard must reject every action submitted after disposal",
        )

        lifetime.cancelAndJoin()
    }

    /**
     * The ancestry itself: `SupervisorJob(generationJob)` is the RIGHT operand of the composed
     * context, so the generation — not the lifecycle scope's Job — is the Store's job parent.
     */
    @Test
    fun theStoreJobIsALiveDescendantOfTheInjectedGeneration() = runTest {
        val lifetime = AppScopeLifetime()
        val store = probeStore(lifetime)
        store.init(owner)

        val started = CompletableDeferred<Unit>()
        val job = store.launchDefault {
            started.complete(Unit)
            awaitCancellation()
        }
        started.await()

        assertTrue(job.isActive, "the Store job is running")
        assertTrue(lifetime.isActive, "and so is the generation it must descend from")

        // Cancelling ONLY the generation must end the Store's job; that is what descent means.
        lifetime.cancelAndJoin()

        assertTrue(
            job.isCompleted,
            "cancelling the generation must end the Store job — if it survives, the Store " +
                "parented to something else and the runtime would tear down under it",
        )
    }

    private fun probeStore(lifetime: AppScopeLifetime): ProbeStore = ProbeStore(
        storeDispatchers = StoreDispatchers(
            defaultDispatcher = dispatcher,
            mainImmediateDispatcher = dispatcher,
        ),
        appScopeLifetime = lifetime,
    )

    private companion object {

        const val FINALLY = "finally"
        const val JOIN_RETURNED = "cancelAndJoin returned"
        const val TEARDOWN_WORK_MILLIS = 250L
    }
}

/** Replaces the MockK relaxed lambda: an explicit counter needs no mocking framework. */
private class CallCounter {

    var calls: Int = 0
        private set

    fun record() {
        calls++
    }
}

/**
 * A [LifecycleOwner] usable off-device: [LifecycleRegistry.createUnsafe] drops the main-thread
 * assertion, and the registry is otherwise the production one.
 */
private class FakeLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle get() = registry
}

private data object ProbeState : Store.State

private data object ProbeAction : Store.Action

private data object ProbeEvent : Store.Event

/** The smallest REAL [BaseStore]; it records handled actions so dispose idempotence is testable. */
private class ProbeStore(
    storeDispatchers: StoreDispatchers,
    appScopeLifetime: AppScopeLifetime,
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
    appScopeLifetime = appScopeLifetime,
) {

    val handledActions: List<ProbeAction> get() = handled

    /** `onCleared` is `protected`; a subclass is the only place that can reach it. */
    fun clearAsViewModelStoreWould() {
        onCleared()
    }
}
