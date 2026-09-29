package io.github.stslex.workeeper.core.ui.mvi.holders

import androidx.lifecycle.Lifecycle
import io.github.stslex.workeeper.core.core.logger.FirebaseAnalyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseEvent
import io.github.stslex.workeeper.core.ui.mvi.Store

class StoreAnalytics<A : Store.Action, E : Store.Event>(
    val name: String,
) {

    fun logAction(action: A) {
        FirebaseAnalyticsHolder.log(
            FirebaseEvent.Store.Action(
                storeName = name,
                action = storeTypeName(action),
            ),
        )
    }

    fun logEvent(event: E) {
        FirebaseAnalyticsHolder.log(
            FirebaseEvent.Store.Event(
                storeName = name,
                event = storeTypeName(event),
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

/**
 * The telemetry label of a Store action or event: its type name, never its content.
 *
 * GUARD: never `toString()`. Actions and events carry what the user entered (names, weights,
 * reps), and a data class prints every field into Analytics and the Crashlytics log
 * (wear-paired-transport.md §9.2). The names survive R8 through the `-keepnames` rules in
 * proguard/firebase-crashlytics.pro.
 */
internal fun storeTypeName(value: Any): String = value::class.simpleName ?: UNNAMED_STORE_TYPE

private const val UNNAMED_STORE_TYPE = "Unnamed"
