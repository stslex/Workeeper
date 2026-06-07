// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberStoreProcessor

/**
 * `AppFeature` is the screen-less composition-time entry point for a feature whose Store
 * lives at the App root rather than inside a navigation destination. It is the screen-less
 * twin of [Feature] / [FeatureAssisted] — same role, same `@HiltViewModel` + `BaseStore`
 * shape, no `Screen` parameter — and delegates to the existing no-`Screen` overload of
 * [rememberStoreProcessor].
 *
 * **Mount-site invariant.** Composables that resolve their Store through `AppFeature`
 * MUST be composed **outside / as a sibling of** `NavHost`. At that depth
 * `LocalViewModelStoreOwner` is the host `ComponentActivity`, which scopes the Store to
 * the Activity's `ViewModelStore` — same lifetime as the Activity, NOT a
 * `NavBackStackEntry`, NOT a `@Singleton`. Composing the entry inside a `NavHost`
 * destination silently rescopes the Store to that destination: no compile error, behaviour
 * breaks at runtime (the Store is re-instantiated on each navigation). Use [Feature]
 * inside `NavHost` destinations and `AppFeature` only at the App root (e.g. siblings of
 * `AppNavigationHost` inside the root `App()` composable — see
 * `app/app/.../App.kt`).
 *
 * Navigation is never executed here: the Store/Handler layer dispatches navigation
 * decisions through `Navigator` (the command-bus contract), and the App/UI bridge
 * (`NavigatorExt.NavigationEventBusSetup`) executes them on the current `NavController`.
 *
 * @see [Feature]
 * @see [FeatureAssisted]
 * @see [StoreProcessor]
 * */
@Immutable
abstract class AppFeature<TProcessor : StoreProcessor<*, *, *>> {

    @Composable
    abstract fun processor(): TProcessor

    @Suppress("UNCHECKED_CAST")
    @Composable
    inline fun <reified TSImpl : BaseStore<*, *, *>> AppFeature<TProcessor>.createProcessor(): TProcessor =
        rememberStoreProcessor<TSImpl>() as TProcessor
}
