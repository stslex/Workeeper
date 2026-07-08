// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.holders

import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.mvi.Store

/**
 * KMP C.1 app-collapse Phase 1 (leaf E-proof): `@Inject` + `@Singleton` were STRIPPED so Hilt no
 * longer auto-binds this type in `SingletonComponent`. Ownership moved to the Metro `AppGraph`
 * (`@Provides @SingleIn(AppScope)`); Hilt readers resolve it through a delegating `@Provides` that
 * returns the SAME app-graph instance (single-owner adopt-back). The primary constructor stays
 * public so feature bridge tests can still instantiate a fake directly.
 *
 * The strip is the general adopt-back mechanic for every implicit `@Inject`-constructed app-scoped
 * binding — validated here leaf-first before the bulk migration applies it across the board.
 */
class AnalyticsHolder {

    fun <A : Store.Action, E : Store.Event> create(
        name: String,
    ) = StoreAnalytics<A, E>("${BaseStore.STORE_LOGGER_PREFIX}_$name")
}
