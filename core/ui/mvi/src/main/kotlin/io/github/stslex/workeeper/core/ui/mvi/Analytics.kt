package io.github.stslex.workeeper.core.ui.mvi

import io.github.stslex.workeeper.core.core.logger.FirebaseAnalyticsHolder
import io.github.stslex.workeeper.core.core.logger.FirebaseEvent
import io.github.stslex.workeeper.core.ui.mvi.Store.Action
import io.github.stslex.workeeper.core.ui.mvi.Store.Event

internal class Analytics<A : Action, E : Event>(
    val name: String,
) {

    fun logAction(action: A) {
        FirebaseAnalyticsHolder.log(
            FirebaseEvent.Store.Action(
                storeName = name,
                action = action.toString()
            )
        )
    }

    fun logEvent(event: E) {
        FirebaseAnalyticsHolder.log(
            FirebaseEvent.Store.Event(
                storeName = name,
                event = event.toString()
            )
        )
    }
}