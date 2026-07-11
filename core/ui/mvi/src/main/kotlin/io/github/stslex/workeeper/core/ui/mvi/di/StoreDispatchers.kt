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
 * `includeJavax`. The dispatchers themselves are still Hilt-owned (CoreModule, core:core-android) at
 * this layer, so they are bridged into `AppGraph.create()` as qualified bound instances until
 * core:core-android is migrated.
 */
@SingleIn(AppScope::class)
@Inject
data class StoreDispatchers(
    @DefaultDispatcher val defaultDispatcher: CoroutineDispatcher,
    @MainImmediateDispatcher val mainImmediateDispatcher: CoroutineDispatcher,
)
