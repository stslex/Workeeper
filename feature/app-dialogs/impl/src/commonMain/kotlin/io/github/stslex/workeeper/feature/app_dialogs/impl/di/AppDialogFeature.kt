// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.AppFeature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStoreImpl

internal typealias AppDialogStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Screen-less composition entry for the app-root `AppDialogStore`. The extension is created inside
 * the `rememberMetroStoreProcessor` lambda, so it lives exactly as long as the Store.
 *
 * GUARD: compose as a sibling of the nav host — inside a destination `LocalViewModelStoreOwner`
 * silently rescopes the Store to a `NavBackStackEntry`.
 */
internal class AppDialogFeature(
    private val factory: AppDialogGraph.Factory,
) : AppFeature<AppDialogStoreProcessor>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AppDialogStoreProcessor {
        return rememberMetroStoreProcessor<AppDialogStoreImpl> {
            factory
                .createAppDialogGraph()
                .appDialogStore
        } as AppDialogStoreProcessor
    }
}
