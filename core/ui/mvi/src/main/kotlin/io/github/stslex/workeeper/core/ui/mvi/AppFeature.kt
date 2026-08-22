// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor

/**
 * `AppFeature` is the screen-less composition-time entry point for a feature whose Store
 * lives at the App root rather than inside a navigation destination. It is the screen-less
 * twin of [Feature] / [FeatureAssisted] — same role, same `@Inject`-constructed Store +
 * `BaseStore` shape, no `Screen` parameter — resolved through `rememberMetroStoreProcessor`.
 *
 * **Mount-site invariant.** Composables that resolve their Store through `AppFeature`
 * MUST be composed **outside / as a sibling of** `AppNavigationHost`'s `NavDisplay`. At that
 * depth `LocalViewModelStoreOwner` is the RUNTIME GENERATION's ViewModelStore (Phase 5, spec
 * §8.7 — provided at the top of `App()`'s generation region): the Store survives Activity
 * recreation (the runtime outlives the Activity) and is cleared deterministically when the
 * generation is replaced — NOT a per-entry store, NOT `@SingleIn(AppScope)`. Composing the
 * entry inside a `NavDisplay` destination silently rescopes the Store to that entry's
 * decorator-provided store: no compile error, behaviour breaks at runtime (the Store is
 * re-instantiated on each navigation). Use [Feature] inside destinations and `AppFeature`
 * only at the App root (e.g. siblings of `AppNavigationHost` inside the root `App()`
 * composable — see `app/common/.../App.kt`).
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
}
