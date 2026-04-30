package io.github.stslex.workeeper.core.core.logger

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

object FirebaseAnalyticsHolder {

    private val analytics by lazy { Firebase.analytics }
    private val filter = EventsFilter()

    @Synchronized
    fun log(event: FirebaseEvent) {
        filter(event.hashCode().toString()) {
            analytics.logEvent(event.name) {
                event.params.forEach { (key, value) -> param(key, value) }
            }
        }
    }
}
