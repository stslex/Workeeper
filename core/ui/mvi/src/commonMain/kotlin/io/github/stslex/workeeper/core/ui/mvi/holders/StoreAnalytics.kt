package io.github.stslex.workeeper.core.ui.mvi.holders

import androidx.lifecycle.Lifecycle
import io.github.stslex.workeeper.core.core.logger.FirebaseAnalyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseEvent
import io.github.stslex.workeeper.core.core.logger.telemetryDedupeKey
import io.github.stslex.workeeper.core.core.logger.telemetryTypeName
import io.github.stslex.workeeper.core.ui.mvi.Store

class StoreAnalytics<A : Store.Action, E : Store.Event>(
    val name: String,
) {

    fun logAction(action: A) {
        FirebaseAnalyticsHolder.log(
            FirebaseEvent.Store.Action(
                storeName = name,
                action = telemetryTypeName(action),
                dedupeKey = telemetryDedupeKey(action),
            ),
        )
    }

    fun logEvent(event: E) {
        FirebaseAnalyticsHolder.log(
            FirebaseEvent.Store.Event(
                storeName = name,
                event = telemetryTypeName(event),
                dedupeKey = telemetryDedupeKey(event),
            ),
        )
    }

    fun logLifecycleEvent(event: Lifecycle.Event) {
        FirebaseAnalyticsHolder.log(
            FirebaseEvent.Store.Lifecycle(
                lifecycleEvent = event.name,
                targetState = event.targetState.name,
                storeName = name,
            ),
        )
    }
}
