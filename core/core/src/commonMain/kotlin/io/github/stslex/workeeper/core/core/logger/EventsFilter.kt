package io.github.stslex.workeeper.core.core.logger

import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Debounces duplicate log/analytics events by key within a short monotonic window.
 * GUARD: holds no lock — call it only from the `@Synchronized` platform Firebase holders.
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
