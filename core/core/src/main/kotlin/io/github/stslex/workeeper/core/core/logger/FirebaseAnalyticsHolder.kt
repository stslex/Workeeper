package io.github.stslex.workeeper.core.core.logger

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

object FirebaseAnalyticsHolder {

    // See `FirebaseCrashlyticsHolder.crashlytics` — same rationale: instrumentation tests
    // run under HiltTestApplication and never initialise Firebase, so the bare
    // `Firebase.analytics` access throws before any test code runs.
    private val analytics: FirebaseAnalytics? by lazy {
        runCatching { Firebase.analytics }.getOrNull()
    }
    private val filter = EventsFilter()

    @Synchronized
    fun log(event: FirebaseEvent) {
        val sink = analytics ?: return
        filter(event.hashCode().toString()) {
            sink.logEvent(event.name) {
                event.params.forEach { (key, value) -> param(key, value) }
            }
        }
    }
}
