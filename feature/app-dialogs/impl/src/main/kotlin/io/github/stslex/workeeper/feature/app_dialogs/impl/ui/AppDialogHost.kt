// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogFeature
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action

/**
 * Renders the currently-pending `AppDialog` above every navigation destination.
 *
 * Mounting site: `App.kt`, inside `AppTheme`, in the same root `Box` as
 * `AppNavigationHost`. The host holds no state of its own; everything is
 * derived from `AppDialogStore.State.current`, which the Store's
 * `ObserveHandler` projects from `AppDialogRepository.currentDialog`.
 *
 * The Store is obtained via the screen-less [AppDialogFeature] composition
 * entry. Because `App.kt` mounts this host as a sibling of `NavHost`,
 * `LocalViewModelStoreOwner` at this depth resolves to the host
 * `ComponentActivity`, scoping the Store to the Activity (not a
 * `NavBackStackEntry`, not a `@Singleton`). Composing the host inside a
 * `NavHost` destination would silently rescope the Store; the mount-site
 * invariant on [io.github.stslex.workeeper.core.ui.mvi.AppFeature] covers
 * the rationale.
 *
 * Choice dispatch: button taps fire `Action.Choose(dialog, action)`. The
 * Store's `ChooseHandler` emits the choice into `AppDialogObserver`; a
 * `@Singleton` consumer (currently `feature/recovery/.../RestoreDialogChoiceObserver`)
 * runs the side-effect and acknowledges the reaction, which dismisses the
 * dialog. The host never clears flags itself.
 */
@Composable
fun AppDialogHost() {
    val processor = AppDialogFeature.processor()
    val state by processor.state
    AppDialogHostContent(
        current = state.current,
        onChoice = { choice -> processor.consume(Action.Choose(choice.dialog, choice.action)) },
    )
}
