// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher

/**
 * App-Scope Collapse Step 3 (SB1). Hilt's `@Inject`/`@Singleton` stripped; now Metro-owned,
 * `@SingleIn(AppScope)` for the process-lifetime single-owner. Concrete self-bound data class (no
 * interface) → the app-scope `AppGraph` exposes a `val storeDispatchers` accessor rather than
 * `@ContributesBinding`; the 13 `*HiltEntryPoint.storeDispatchers()` readers resolve it via the single
 * adopt-back `@Provides` in `AppGraphAdoptBackModule`.
 *
 * COLLIDER ctor deps: the two `CoroutineDispatcher`s are distinguished only by their javax qualifiers
 * (`@DefaultDispatcher` / `@MainImmediateDispatcher`), which survive into the Metro graph via
 * `includeJavax`. App-Scope Collapse Step 3 (PF commit 1): the dispatchers are now Metro-owned
 * (`DispatchersBindingContainer`, a `@BindingContainer @ContributesTo(AppScope)` in core:core-android),
 * so the graph resolves these qualified deps from its own aggregated bindings — no longer bridged
 * through `AppGraph.create()`.
 */
@SingleIn(AppScope::class)
@Inject
data class StoreDispatchers(
    @DefaultDispatcher val defaultDispatcher: CoroutineDispatcher,
    @MainImmediateDispatcher val mainImmediateDispatcher: CoroutineDispatcher,
)
