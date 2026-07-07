// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.processor

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.stslex.workeeper.core.ui.mvi.BaseStore

/**
 * Metro-backed Store resolution path — the transition-era twin of the Hilt-backed
 * [rememberStoreProcessor] overloads (which call `hiltViewModel`). Added **alongside**
 * the Hilt path, not replacing it: the 8 non-migrated features keep resolving their Store
 * via `hiltViewModel` through [rememberStoreProcessor], untouched.
 *
 * A migrated feature (first: `feature/archive`) supplies [factory] — a lambda that resolves
 * its Metro graph / assisted factory and constructs the [BaseStore] subclass. Because
 * [BaseStore] already IS an `androidx.lifecycle.ViewModel`, the Metro-created Store is
 * retained **directly** in the Compose `ViewModelStore` via [viewModel] — scoped to the
 * current `LocalViewModelStoreOwner` (the `NavBackStackEntry` inside a `NavHost`), the
 * exact same lifetime `hiltViewModel` gives today: survives configuration change and
 * recomposition, cleared on back-stack pop. No separate ViewModel shim is needed.
 *
 * The retained Store is then handed to the existing backend-agnostic
 * [rememberStoreProcessor] `(StoreCreator)` overload, which owns ALL lifecycle wiring
 * (`store.init` / `store.dispose` via `DisposableEffect`, analytics, render trace) — so the
 * Metro and Hilt paths share identical post-resolution behaviour.
 *
 * NOTE (Android-only): this file resolves retention on Android. iOS retention (no
 * `ViewModelStore`) is resolved by the Compose Multiplatform nav host and lives in the
 * feature's `iosMain` — see the C.1.2 archive conversion.
 *
 * @param factory resolves the Metro graph and creates the [BaseStore] subclass. Invoked at
 * most once per retained Store instance (inside the [viewModel] initializer).
 * @see rememberStoreProcessor
 */
@Composable
inline fun <reified TStoreImpl : BaseStore<*, *, *>> rememberMetroStoreProcessor(
    noinline factory: () -> TStoreImpl,
): StoreProcessor<*, *, *> = rememberStoreProcessor {
    viewModel<TStoreImpl>(
        factory = viewModelFactory {
            initializer { factory() }
        },
    )
}
