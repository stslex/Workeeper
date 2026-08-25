// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.AppFeature
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStoreImpl

internal typealias AppDialogStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Screen-less composition entry for the app-root `AppDialogStore`.
 *
 * GUARD: compose as a sibling of the nav host — inside a destination `LocalViewModelStoreOwner`
 * silently rescopes the Store to a `NavBackStackEntry`.
 */
internal object AppDialogFeature : AppFeature<AppDialogStoreProcessor>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): AppDialogStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<AppDialogStoreImpl> {
            context.appDeps<AppDialogGraph.Factory>()
                .createAppDialogGraph()
                .appDialogStore
        } as AppDialogStoreProcessor
    }
}
