// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.di

import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder

/**
 * Store-infrastructure spine: the MVI-store app-scope dependencies every nav feature needs
 * (analytics + logger + dispatchers). One shared interface — NOT duplicated per feature.
 *
 * Part of the [AppGraphContract][io.github.stslex.workeeper.core.di.AppGraphContract] split
 * (variant A, spine variant γ). `AppGraph` implements this; the accessor signatures are copied
 * verbatim from `AppGraphContract` so its existing overrides satisfy them with no new provision.
 * Deliberately excludes `navigator` (that lives in
 * [NavigatorDeps][io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps]), so consumers that
 * need store-infra without navigation (e.g. app-dialogs) do not pull a `core:ui:navigation` edge.
 *
 * All three types are owned by `core:ui:mvi` — same-module, no dependency change.
 */
interface StoreCoreDeps {
    val analyticsHolder: AnalyticsHolder
    val loggerHolder: LoggerHolder
    val storeDispatchers: StoreDispatchers
}
