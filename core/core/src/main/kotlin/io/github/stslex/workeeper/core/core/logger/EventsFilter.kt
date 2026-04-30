package io.github.stslex.workeeper.core.core.logger

class EventsFilter {

    @Volatile
    private var lastTrackedEvent: Pair<String, Long>? = null

    @Synchronized
    operator fun invoke(
        filterKey: String,
        block: () -> Unit,
    ) {
        val currentTime = System.currentTimeMillis()
        val lastEvent = lastTrackedEvent
        if (
            lastEvent == null ||
            lastEvent.first != filterKey ||
            (currentTime - lastEvent.second) > LAST_EVENT_TIME_DIFF
        ) {
            block()
        }
        lastTrackedEvent = Pair(filterKey, currentTime)
    }

    fun clear() {
        lastTrackedEvent = null
    }

    companion object {

        private const val LAST_EVENT_TIME_DIFF = 2_000L
    }
}
