// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Metro-owned, `@SingleIn(AppScope)` for the process-lifetime single-owner. Concrete self-bound data
 * class (no interface) → the app-scope `AppGraph` exposes a `val storeDispatchers` accessor rather than
 * `@ContributesBinding`.
 *
 * COLLIDER ctor deps: the two `CoroutineDispatcher`s are distinguished only by their qualifiers
 * (`@DefaultDispatcher` / `@MainImmediateDispatcher`). The dispatchers are Metro-owned
 * (`DispatchersBindingContainer`, a `@BindingContainer @ContributesTo(AppScope)` in core:core-android),
 * so the graph resolves these qualified deps from its own aggregated bindings.
 */
@SingleIn(AppScope::class)
@Inject
data class StoreDispatchers(
    @DefaultDispatcher val defaultDispatcher: CoroutineDispatcher,
    @MainImmediateDispatcher val mainImmediateDispatcher: CoroutineDispatcher,
)
