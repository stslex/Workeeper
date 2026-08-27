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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Event delivery under buffer pressure. `sendEvent` calls `tryEmit` first and falls back to a
 * launched suspending `emit` when the 32-slot extra buffer is full; without that fallback the
 * overflowing events are dropped silently.
 *
 * Deterministic by construction, not a stress loop: one collector is parked on an explicit gate,
 * so the buffer is provably full before the gate opens. Order is deliberately NOT asserted — the
 * launched fallback emissions are unordered and the production code never claimed otherwise.
 */
internal class StoreEventPressureTest {

    private lateinit var dispatcher: TestDispatcher
    private lateinit var owner: PressureLifecycleOwner
    private var wasLogging: Boolean = true

    @BeforeTest
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        owner = PressureLifecycleOwner()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        Log.isLogging = wasLogging
    }

    @Test
    fun everyEventSubmittedUnderBufferPressureIsObservedExactlyOnce() = runTest {
        val lifetime = AppScopeLifetime()
        val store = PressureStore(
            storeDispatchers = StoreDispatchers(
                defaultDispatcher = dispatcher,
                mainImmediateDispatcher = dispatcher,
            ),
            appScopeLifetime = lifetime,
        )
        store.init(owner)

        val gate = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        val collector = launch {
            store.event.collect { event ->
                // Park the collector on the first event so nothing drains while we overfill.
                if (event.index == 0) gate.await()
                seen += event.index
            }
        }
        // The collector must be subscribed before anything is sent: at replay = 0 a value
        // emitted with no subscriber is discarded, which would fake the result.
        advanceUntilIdle()

        repeat(TOTAL_EVENTS) { index -> store.sendEvent(PressureEvent.DeliveryRequested(index)) }
        advanceUntilIdle()

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            TOTAL_EVENTS,
            seen.size,
            "every submitted event must reach the collector; a short count is the suspending " +
                "`emit` fallback missing, which drops whatever overflowed the 32-slot buffer",
        )
        assertEquals(
            (0 until TOTAL_EVENTS).toSet(),
            seen.toSet(),
            "the set of delivered events must be exactly the set submitted",
        )
        assertEquals(
            TOTAL_EVENTS,
            seen.distinct().size,
            "no event may be delivered twice",
        )

        collector.cancel()
        store.dispose()
        lifetime.cancelAndJoin()
    }

    private companion object {

        /**
         * Twice `BaseStore.EVENTS_BUFFER_CAPACITY` (32), so the overflow path is entered by
         * construction. That the path IS entered is proved by the mandated negative control:
         * deleting the fallback `emit` must turn this test red.
         */
        const val TOTAL_EVENTS = 64
    }
}

private class PressureLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle get() = registry
}

private data object PressureState : Store.State

private data object PressureAction : Store.Action

private sealed interface PressureEvent : Store.Event {

    val index: Int

    data class DeliveryRequested(override val index: Int) : PressureEvent
}

private class PressureStore(
    storeDispatchers: StoreDispatchers,
    appScopeLifetime: AppScopeLifetime,
) : BaseStore<PressureState, PressureAction, PressureEvent>(
    name = "PressureStore",
    initialState = PressureState,
    storeEmitter = BaseHandlerStore(),
    handlerCreator = HandlerCreator<PressureAction> { Handler<PressureAction> { } },
    storeDispatchers = storeDispatchers,
    analyticsHolder = AnalyticsHolder(),
    loggerHolder = LoggerHolder(),
    appScopeLifetime = appScopeLifetime,
)
