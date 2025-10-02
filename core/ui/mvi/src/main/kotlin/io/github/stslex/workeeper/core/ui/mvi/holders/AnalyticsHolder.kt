package io.github.stslex.workeeper.core.ui.mvi.holders

import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.Store
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsHolder @Inject constructor() {

    fun <A : Store.Action, E : Store.Event> create(
        name: String,
    ) = StoreAnalytics<A, E>("${BaseStore.Companion.STORE_LOGGER_PREFIX}_$name")
}
