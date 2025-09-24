package io.github.stslex.workeeper.core.ui.mvi

object AnalyticsHolder {

    fun <A : Store.Action, E : Store.Event> createStore(
        name: String,
    ) = StoreAnalytics<A, E>("${BaseStore.STORE_LOGGER_PREFIX}_$name")
}
