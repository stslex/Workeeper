// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store

import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State

/**
 * Activity-scoped MVI store projecting `AppDialogRepository` into UI state, obtained at the App
 * root through the screen-less `AppDialogFeature`. See the app-dialogs spec.
 */
interface AppDialogStore : Store<State, Action, Event> {

    /** Top-priority pending dialog as projected from the repository; `null` renders nothing. */
    data class State(
        val current: AppDialog?,
    ) : Store.State {
        companion object {
            val EMPTY: State = State(current = null)
        }
    }

    sealed interface Action : Store.Action {

        /** Repository-side mutations and observation, routed to `AppDialogRepoHandler`. */
        sealed interface RepoAction : Action {

            /** Subscribe to the repository flow. Dispatched as an `initialAction`. */
            data object Observe : RepoAction

            /** Store-mediated publish; unused — `AppDialogPublisher` bypasses the Store. */
            data class Publish(val dialog: AppDialog) : RepoAction

            /** Store-mediated dismiss; unused — `acknowledgeReaction` bypasses the Store. */
            data class Dismiss(val dialog: AppDialog) : RepoAction
        }

        /** User tapped a button on the rendered dialog; handled by `ChooseHandler`. */
        data class Choose(
            val dialog: AppDialog,
            val action: AppDialogUserAction,
        ) : Action
    }

    /** No events — every user-visible outcome flows through State. */
    sealed interface Event : Store.Event
}
