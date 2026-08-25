// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.processor

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.stslex.workeeper.core.ui.mvi.BaseStore

/**
 * Metro-backed Store resolution: [factory] constructs the [BaseStore], which is retained directly
 * in the current `LocalViewModelStoreOwner` via [viewModel] — a [BaseStore] already is a ViewModel.
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
