package io.github.stslex.workeeper.core.core.logger

import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Debounces duplicate log/analytics events by key within a short window. Uses a monotonic
 * [TimeSource] rather than wall-clock so the window is immune to clock adjustments.
 *
 * Called only from the platform Firebase-holder actuals, whose public methods are already
 * `@Synchronized` on Android; iOS holders are no-ops. `@Volatile` (the multiplatform
 * `kotlin.concurrent.Volatile`) keeps the last-event read visible across those callers.
 */
internal class EventsFilter {

    @Volatile
    private var lastTrackedEvent: Pair<String, TimeSource.Monotonic.ValueTimeMark>? = null

    operator fun invoke(
        filterKey: String,
        block: () -> Unit,
    ) {
        val now = TimeSource.Monotonic.markNow()
        val lastEvent = lastTrackedEvent
        if (
            lastEvent == null ||
            lastEvent.first != filterKey ||
            (now - lastEvent.second) > LAST_EVENT_TIME_DIFF
        ) {
            block()
        }
        lastTrackedEvent = filterKey to now
    }

    fun clear() {
        lastTrackedEvent = null
    }

    companion object {

        private val LAST_EVENT_TIME_DIFF = 2_000L.milliseconds
    }
}
