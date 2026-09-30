// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.logger.FirebaseAnalyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseEvent
import io.github.stslex.workeeper.core.core.logger.Log
import io.github.stslex.workeeper.core.core.logger.TelemetryDedupeKey
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.handler.BaseHandlerStore
import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.core.ui.mvi.handler.HandlerCreator
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.mockk.MockKAnswerScope
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * wear-paired-transport.md §9.2 item 5: a Store action and event that carry what a user entered
 * (a name, a weight, reps) reach Analytics and the Crashlytics log as their type names only.
 *
 * Every telemetry sink is captured, not just the two call sites under test: the Analytics
 * parameters, every Crashlytics log line, the tag and message of every recorded exception, and
 * every custom key.
 * Each breadcrumb path is proven to have run by its type-name line, so an absent sentinel cannot
 * come from a path that never executed.
 */
internal class StoreTelemetryRedactionTest {

    private val analytics = mutableListOf<FirebaseEvent>()
    private val breadcrumbs = mutableListOf<String>()
    private val breadcrumbKeys = mutableListOf<Pair<String, TelemetryDedupeKey>>()
    private lateinit var dispatcher: TestDispatcher
    private var wasLogging = true

    @BeforeEach
    fun setUp() {
        wasLogging = Log.isLogging
        Log.isLogging = false
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        mockkObject(FirebaseAnalyticsHolder, FirebaseCrashlyticsHolder)
        every { FirebaseAnalyticsHolder.log(any()) } answers { analytics += firstArg<FirebaseEvent>() }
        every { FirebaseCrashlyticsHolder.log(any<String>()) } answers { breadcrumbs += firstArg<String>() }
        every { FirebaseCrashlyticsHolder.log(any<String>(), any<TelemetryDedupeKey>()) } answers {
            breadcrumbs += firstArg<String>()
            breadcrumbKeys += firstArg<String>() to secondArg<TelemetryDedupeKey>()
        }
        every { FirebaseCrashlyticsHolder.recordException(any(), any()) } answers {
            breadcrumbs += "${secondArg<String>()} ${firstArg<Throwable>().message}"
        }
        every { FirebaseCrashlyticsHolder.setCustomKey(any(), any<String>()) } answers { customKey() }
        every { FirebaseCrashlyticsHolder.setCustomKey(any(), any<Int>()) } answers { customKey() }
        every { FirebaseCrashlyticsHolder.setCustomKey(any(), any<Long>()) } answers { customKey() }
        every { FirebaseCrashlyticsHolder.setCustomKey(any(), any<Boolean>()) } answers { customKey() }
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(FirebaseAnalyticsHolder, FirebaseCrashlyticsHolder)
        Dispatchers.resetMain()
        Log.isLogging = wasLogging
    }

    @Test
    fun `entered values never reach analytics parameters or breadcrumbs`() = runTest {
        val lifetime = AppScopeLifetime()
        val store = RedactionStore(StoreDispatchers(dispatcher, dispatcher), lifetime)
        val action = RedactionAction.Recorded(SENTINEL_NAME, SENTINEL_WEIGHT, SENTINEL_REPS)

        // Before init: the "consume skipped" breadcrumb.
        store.consume(action)
        store.init(RedactionLifecycleOwner())
        // After init: the "consume" breadcrumb and the action parameter.
        store.consume(action)

        // Park one collector so the 33rd event overflows the 32-slot buffer: the event-buffer
        // warning is a breadcrumb too.
        val gate = CompletableDeferred<Unit>()
        val collector = launch { store.event.collect { gate.await() } }
        advanceUntilIdle()
        repeat(EVENTS_TO_OVERFLOW) { index ->
            store.sendEvent(RedactionEvent.SetShown(index, SENTINEL_NAME, SENTINEL_WEIGHT, SENTINEL_REPS))
        }
        gate.complete(Unit)
        advanceUntilIdle()
        collector.cancel()

        val parameters = analytics.flatMap { event -> event.params.values }
        assertEquals(
            listOf("Recorded"),
            analytics.filterIsInstance<FirebaseEvent.Store.Action>().map { it.params.getValue("action") },
        )
        assertTrue(
            analytics.filterIsInstance<FirebaseEvent.Store.Event>().map { it.params.getValue("event") }
                .let { names -> names.isNotEmpty() && names.all { it == "SetShown" } },
            "every event parameter is the type name: $parameters",
        )
        assertRan("consume skipped for Recorded")
        assertRan("consume: Recorded")
        assertRan("sendEvent: SetShown")
        assertRan("Event SetShown", "was try emitted: false")

        val sinks = parameters + breadcrumbs
        SENTINELS.forEach { sentinel ->
            val leaks = sinks.filter { sentinel in it }
            assertTrue(leaks.isEmpty(), "'$sentinel' reached telemetry ${leaks.size} times: $leaks")
        }
        println(
            "telemetry checked: ${analytics.size} analytics events (${parameters.size} parameters), " +
                "${breadcrumbs.size} breadcrumbs, ${SENTINELS.size} sentinels, 0 leaks",
        )

        store.dispose()
        lifetime.cancelAndJoin()
    }

    @Test
    fun `repeats are debounced exactly when the entered values repeat`() = runTest {
        val lifetime = AppScopeLifetime()
        val store = RedactionStore(StoreDispatchers(dispatcher, dispatcher), lifetime)
        store.init(RedactionLifecycleOwner())
        // "FB" and "Ea" share a String.hashCode(), so the two payloads differ under one hash.
        val first = RedactionAction.Recorded("${SENTINEL_NAME}FB", SENTINEL_WEIGHT, SENTINEL_REPS)
        val second = RedactionAction.Recorded("${SENTINEL_NAME}Ea", SENTINEL_WEIGHT, SENTINEL_REPS)
        assertEquals(first.toString().hashCode(), second.toString().hashCode())

        store.consume(first)
        store.consume(second)
        store.consume(first)

        // Analytics: the params are the same type name; the local key keeps the events apart
        // exactly as far as the payloads were (§9.2 item 6: no telemetry removed by the debounce).
        val events = analytics.filterIsInstance<FirebaseEvent.Store.Action>()
        assertEquals(3, events.size)
        assertEquals(1, events.map { it.params }.distinct().size, "the params never carry the payload")
        assertNotEquals(events[0], events[1], "different payloads")
        assertEquals(events[0], events[2], "the same payload debounces as before")
        // Breadcrumbs: the same line, the key distinct exactly when the payload is.
        val consumed = breadcrumbKeys.filter { (line, _) -> "consume: Recorded" in line }
        assertEquals(3, consumed.size)
        assertEquals(1, consumed.map { it.first }.distinct().size)
        assertNotEquals(consumed[0].second, consumed[1].second)
        assertEquals(consumed[0].second, consumed[2].second)
        val printed = consumed.map { it.second.toString() } + events.map { it.toString() }
        assertTrue(printed.none { SENTINEL_NAME in it }, "a key or an event prints the payload: $printed")

        store.dispose()
        lifetime.cancelAndJoin()
    }

    /** A custom key is a sink too: key and value are checked like a log line. */
    private fun MockKAnswerScope<Unit, Unit>.customKey() {
        breadcrumbs += "${firstArg<String>()}=${secondArg<Any>()}"
    }

    /** A breadcrumb holding every fragment proves its path ran; what else it holds is the leak check's. */
    private fun assertRan(vararg fragments: String) {
        assertTrue(
            breadcrumbs.any { crumb -> fragments.all { it in crumb } },
            "no breadcrumb contains ${fragments.toList()}: $breadcrumbs",
        )
    }

    private companion object {
        const val SENTINEL_NAME = "Sentinel-Bench-7f3a"
        const val SENTINEL_WEIGHT = 987.25
        const val SENTINEL_REPS = 4321
        val SENTINELS = listOf(SENTINEL_NAME, SENTINEL_WEIGHT.toString(), SENTINEL_REPS.toString())

        /** One past `BaseStore.EVENTS_BUFFER_CAPACITY` (32). */
        const val EVENTS_TO_OVERFLOW = 33
    }
}

private class RedactionLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle get() = registry
}

private data object RedactionState : Store.State

private sealed interface RedactionAction : Store.Action {

    data class Recorded(val name: String, val weight: Double, val reps: Int) : RedactionAction
}

private sealed interface RedactionEvent : Store.Event {

    data class SetShown(val index: Int, val name: String, val weight: Double, val reps: Int) : RedactionEvent
}

private class RedactionStore(
    storeDispatchers: StoreDispatchers,
    appScopeLifetime: AppScopeLifetime,
) : BaseStore<RedactionState, RedactionAction, RedactionEvent>(
    name = "RedactionStore",
    initialState = RedactionState,
    storeEmitter = BaseHandlerStore(),
    handlerCreator = HandlerCreator<RedactionAction> { Handler<RedactionAction> { } },
    storeDispatchers = storeDispatchers,
    analyticsHolder = AnalyticsHolder(),
    loggerHolder = LoggerHolder(),
    appScopeLifetime = appScopeLifetime,
)
