package io.github.stslex.workeeper.core.core.logger

sealed class FirebaseEvent(
    val name: String,
    val params: Map<String, String>,
) {

    sealed class Store(
        storeName: String,
        eventName: String,
        params: Map<String, String>,
    ) : FirebaseEvent(
        name = "store_${storeName}_$eventName",
        params = params,
    ) {

        data class Action(
            private val action: String,
            private val storeName: String,
        ) : Store(
            storeName = storeName,
            eventName = "action",
            params = mapOf("action" to action),
        )

        data class Event(
            private val event: String,
            private val storeName: String,
        ) : Store(
            storeName = storeName,
            eventName = "event",
            params = mapOf("event" to event),
        )

        data class Lifecycle(
            val lifecycleEvent: String,
            val targetState: String,
            private val storeName: String,
        ) : Store(
            storeName = storeName,
            eventName = "lifecycle",
            params = mapOf(
                "event" to lifecycleEvent,
                "target_state" to targetState,
            ),
        )
    }

    data class Screen(
        val screenName: String,
    ) : FirebaseEvent(
        name = "screen_view",
        params = mapOf("screen_name" to screenName),
    )

    data class Common(
        val eventName: String,
        val paramsMap: Map<String, String> = emptyMap(),
    ) : FirebaseEvent(
        name = eventName,
        params = paramsMap,
    )
}
