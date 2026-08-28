// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.holders

import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.Store

/**
 * Owned by the Metro `AppGraph` as a `@Provides @SingleIn(AppScope)` binding (single
 * process-lifetime instance). The primary constructor stays public so feature bridge tests can
 * still instantiate a fake directly.
 */
class AnalyticsHolder {

    fun <A : Store.Action, E : Store.Event> create(
        name: String,
    ) = StoreAnalytics<A, E>("${BaseStore.STORE_LOGGER_PREFIX}_$name")
}
