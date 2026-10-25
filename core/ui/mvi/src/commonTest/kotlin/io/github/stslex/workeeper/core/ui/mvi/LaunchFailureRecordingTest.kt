// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScopeImpl
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.logger.TelemetryDedupeKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The launch-failure recording contract of [AppCoroutineScopeImpl] (Phase 7.16 D2–D4): a failure
 * that is not the coroutine's own cancellation is recorded once, through the logger the scope was
 * built with, before `onError` receives it.
 *
 * Every identity asserts the exact ordered list of logger and callback events. Exceptions are
 * compared by type and message, never by identity: stack-trace recovery on the JVM host can copy
 * an exception across a suspension.
 */
internal class LaunchFailureRecordingTest {

    private lateinit var main: TestDispatcher
    private lateinit var work: TestDispatcher
    private lateinit var each: TestDispatcher
    private val events = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
        main = StandardTestDispatcher()
        // GUARD: setMain before any scope exists — lifecycleScope builds on Main.immediate; the
        // shared scheduler puts the scope's jobs and the test body on one timeline.
        Dispatchers.setMain(main)
        work = StandardTestDispatcher(main.scheduler, name = "work")
        each = StandardTestDispatcher(main.scheduler, name = "each")
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun anActionFailureIsRecordedOnceBeforeOnErrorReceivesIt() = runTest {
        val scope = newScope()
        var onErrorDispatcher: ContinuationInterceptor? = null

        scope.launch<Unit>(
            onError = { failure ->
                onErrorDispatcher = currentCoroutineContext()[ContinuationInterceptor]
                events += "onError:${failure.describe()}"
            },
            action = { throw IllegalStateException("action") },
        )
        advanceUntilIdle()

        assertEquals(
            listOf("record:e:IllegalStateException:action", "onError:IllegalStateException:action"),
            events,
        )
        assertSame(each, onErrorDispatcher, "onError runs on the each dispatcher")
        scope.cancel()
    }

    @Test
    fun aCancelledActionRecordsNothingAndSkipsOnError() = runTest {
        val scope = newScope()

        val job = scope.launch<Unit>(
            onError = { failure -> events += "onError:${failure.describe()}" },
            action = { awaitCancellation() },
        )
        advanceUntilIdle()
        job.cancel()
        advanceUntilIdle()

        assertEquals(emptyList(), events)
        assertTrue(job.isCancelled, "the launch is cancelled")
        assertTrue(scope.isActive, "cancelling one launch leaves the scope active")
        scope.cancel()
    }

    @Test
    fun aTimeoutInsideALiveActionIsRecordedAndReachesOnError() = runTest {
        val scope = newScope()

        scope.launch<Unit>(
            onError = { failure -> events += "onError:${failure.describe()}" },
            action = { withTimeout(TIMEOUT_MILLIS) { awaitCancellation() } },
        )
        advanceUntilIdle()

        val message = events.firstOrNull()?.removePrefix(TIMEOUT_RECORD).orEmpty()
        assertEquals(listOf("$TIMEOUT_RECORD$message", "$TIMEOUT_ON_ERROR$message"), events)
        scope.cancel()
    }

    @Test
    fun anAsyncChildFailureIsRecordedOnceThroughTheBackstop() = runTest {
        val scope = newScope()
        var onErrorDispatcher: ContinuationInterceptor? = null

        scope.launch<Unit>(
            onError = { failure ->
                onErrorDispatcher = currentCoroutineContext()[ContinuationInterceptor]
                events += "onError:${failure.describe()}"
            },
            action = { async<Unit> { throw IllegalStateException("child") }.await() },
        )
        advanceUntilIdle()

        assertEquals(
            listOf("record:e:IllegalStateException:child", "onError:IllegalStateException:child"),
            events,
        )
        assertSame(work, onErrorDispatcher, "the backstop runs onError on the work dispatcher")
        scope.cancel()
    }

    @Test
    fun anOnSuccessFailureIsRecordedOnceThroughTheBackstop() = runTest {
        val scope = newScope()
        var onErrorDispatcher: ContinuationInterceptor? = null

        scope.launch<Int>(
            onError = { failure ->
                onErrorDispatcher = currentCoroutineContext()[ContinuationInterceptor]
                events += "onError:${failure.describe()}"
            },
            onSuccess = { throw IllegalStateException("success") },
            action = { 1 },
        )
        advanceUntilIdle()

        assertEquals(
            listOf("record:e:IllegalStateException:success", "onError:IllegalStateException:success"),
            events,
        )
        assertSame(work, onErrorDispatcher, "the backstop runs onError on the work dispatcher")
        scope.cancel()
    }

    @Test
    fun anUpstreamFlowFailureIsRecordedOnceBeforeOnErrorReceivesIt() = runTest {
        val scope = newScope()

        scope.launch(
            flow = flow<Int> { throw IllegalStateException("upstream") },
            onError = { cause -> events += "onError:${cause.describe()}" },
            each = { value -> events += "each:$value" },
        )
        advanceUntilIdle()

        assertEquals(
            listOf("record:e:IllegalStateException:upstream", "onError:IllegalStateException:upstream"),
            events,
        )
        scope.cancel()
    }

    @Test
    fun aCancelledCollectionRecordsNothingAndSkipsOnError() = runTest {
        val scope = newScope()

        val job = scope.launch(
            flow = flow<Int> { awaitCancellation() },
            onError = { cause -> events += "onError:${cause.describe()}" },
            each = { value -> events += "each:$value" },
        )
        advanceUntilIdle()
        job.cancel()
        advanceUntilIdle()

        assertEquals(emptyList(), events)
        scope.cancel()
    }

    @Test
    fun aTimeoutInsideALiveCollectionIsRecordedAndReachesOnError() = runTest {
        val scope = newScope()

        scope.launch(
            flow = flow<Int> { withTimeout(TIMEOUT_MILLIS) { awaitCancellation() } },
            onError = { cause -> events += "onError:${cause.describe()}" },
            each = { value -> events += "each:$value" },
        )
        advanceUntilIdle()

        val message = events.firstOrNull()?.removePrefix(TIMEOUT_RECORD).orEmpty()
        assertEquals(listOf("$TIMEOUT_RECORD$message", "$TIMEOUT_ON_ERROR$message"), events)
        scope.cancel()
    }

    @Test
    fun successfulLaunchesRecordNothing() = runTest {
        val scope = newScope()

        scope.launch<Int>(
            onSuccess = { value -> events += "success:$value" },
            action = { 1 },
        )
        advanceUntilIdle()
        scope.launch(
            flow = flowOf(1, 2),
            each = { value -> events += "each:$value" },
        )
        advanceUntilIdle()

        assertEquals(listOf("success:1", "each:1", "each:2"), events)
        scope.cancel()
    }

    private fun newScope(): AppCoroutineScopeImpl = AppCoroutineScopeImpl(
        RecordingLifecycleOwner(),
        work,
        each,
        RecordingLogger(events),
        Job(),
    )

    private companion object {

        const val TIMEOUT_MILLIS = 1_000L
        const val TIMEOUT_RECORD = "record:e:TimeoutCancellationException:"
        const val TIMEOUT_ON_ERROR = "onError:TimeoutCancellationException:"
    }
}

private fun Throwable.describe(): String = "${this::class.simpleName}:$message"

/**
 * A [LifecycleOwner] usable off-device: [LifecycleRegistry.createUnsafe] drops the main-thread
 * assertion, and the registry is otherwise the production one.
 */
private class RecordingLifecycleOwner : LifecycleOwner {

    private val registry = LifecycleRegistry.createUnsafe(this)

    override val lifecycle: Lifecycle get() = registry
}

/** Appends one entry per call: `record:<member>:<type>:<message>` with a throwable, else `log:<member>`. */
private class RecordingLogger(private val events: MutableList<String>) : Logger {

    override fun e(throwable: Throwable, message: String?) {
        events += "record:e:${throwable.describe()}"
    }

    override fun d(message: String) {
        events += "log:d"
    }

    override fun d(e: Throwable, message: String) {
        events += "record:d:${e.describe()}"
    }

    override fun d(e: Throwable, message: () -> String) {
        events += "record:d:${e.describe()}"
    }

    override fun d(message: () -> String) {
        events += "log:d"
    }

    override fun i(message: String) {
        events += "log:i"
    }

    override fun i(message: () -> String) {
        events += "log:i"
    }

    override fun v(message: String) {
        events += "log:v"
    }

    override fun v(message: () -> String) {
        events += "log:v"
    }

    override fun w(message: String) {
        events += "log:w"
    }

    override fun w(message: () -> String) {
        events += "log:w"
    }

    override fun w(message: String, throwable: Throwable) {
        events += "record:w:${throwable.describe()}"
    }

    override fun w(throwable: Throwable, message: () -> String) {
        events += "record:w:${throwable.describe()}"
    }

    override fun d(message: String, dedupeKey: TelemetryDedupeKey) {
        events += "log:d"
    }

    override fun i(message: String, dedupeKey: TelemetryDedupeKey) {
        events += "log:i"
    }

    override fun w(message: String, dedupeKey: TelemetryDedupeKey) {
        events += "log:w"
    }
}
