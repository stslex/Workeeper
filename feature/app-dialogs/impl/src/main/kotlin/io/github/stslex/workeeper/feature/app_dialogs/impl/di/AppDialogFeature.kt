// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.di

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.mvi.AppFeature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStoreImpl

internal typealias AppDialogStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * Screen-less composition entry for the app-root `AppDialogStore`. First
 * production user of `AppFeature` — see its KDoc for the mount-site
 * invariant (must be composed as a sibling of `NavHost`, never inside a
 * destination, otherwise `LocalViewModelStoreOwner` silently rescopes the
 * Store to a `NavBackStackEntry`).
 *
 * Not yet referenced from `AppDialogHost` — that migration is part of the
 * next commit. Hilt KSP generates the Store binding at this point so the
 * graph is ready when the host is cut over.
 */
internal object AppDialogFeature : AppFeature<AppDialogStoreProcessor>() {

    @Composable
    override fun processor(): AppDialogStoreProcessor = createProcessor<AppDialogStoreImpl>()
}
