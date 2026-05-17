// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store

import io.github.stslex.workeeper.core.ui.mvi.Store
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserAction
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Action
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.Event
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStore.State

/**
 * Activity-scoped `@HiltViewModel BaseStore` projecting the
 * `AppDialogRepository` flow into UI state and accepting user choices from
 * the Host. Obtained at the App root through the screen-less
 * `AppDialogFeature` composition entry so its `ViewModelStoreOwner` is the
 * host `ComponentActivity` — same lifetime as the Activity, not a
 * `NavBackStackEntry`, not a `@Singleton`.
 *
 * Why this isn't `@Singleton`: see
 * `documentation/feature-specs/app-dialogs.md` → "Layering — data / domain /
 * presentation". The persistence-only role lives in `AppDialogRepository`
 * (also `@Singleton`); this Store is the presentation projection of that
 * repository — Activity-scoped because UI lives on the Activity.
 *
 * Events: intentionally empty in v1. Every user-visible outcome flows
 * through `State.current`; the empty `Event` sealed interface satisfies the
 * MVI lint rule's "outer sealed" check without committing the catalog to
 * a particular event surface.
 */
internal interface AppDialogStore : Store<State, Action, Event> {

    /**
     * The current pending dialog as projected by `ObserveHandler` from
     * `AppDialogRepository.currentDialog`. `null` means "no flag set" and the
     * Host composes nothing.
     */
    data class State(
        val current: AppDialog?,
    ) : Store.State {
        companion object {
            val EMPTY: State = State(current = null)
        }
    }

    sealed interface Action : Store.Action {

        /** Subscribe to the repository flow. Dispatched as an `initialAction`. */
        data object Observe : Action

        /**
         * Producer-driven publish path. Used by the `AppDialogPublisher`
         * facade (a `@Singleton` over the repository) and, internally, by
         * the cross-feature observer-side reactors.
         */
        data class Publish(val dialog: AppDialog) : Action

        /**
         * The Host emits this when the user taps any button on the
         * currently-rendered dialog. Reaction is delegated outside the Store
         * — see `ChooseHandler`'s KDoc for the contract that determines who
         * clears the dialog flag and when.
         *
         * Named `Choose` (not `UserAction`) so the leaf class name doesn't
         * end in "Action" — the `MviActionNamingRule` lint rule treats any
         * "*Action" name as needing to be sealed, which conflates the outer
         * sealed parent with a leaf data class.
         */
        data class Choose(
            val dialog: AppDialog,
            val action: AppDialogUserAction,
        ) : Action

        /**
         * Implicit dismiss (e.g. back-press on a dialog whose dismiss policy
         * allows it). Clears the variant's flag set in the repository.
         */
        data class Dismiss(val dialog: AppDialog) : Action
    }

    /** No events in v1 — every user-visible outcome flows through State. */
    sealed interface Event : Store.Event
}
