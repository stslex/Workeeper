// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api.model

/**
 * Pairs an [AppDialog] variant with the [AppDialogUserAction] the user chose.
 * This is the unit dispatched from the Host into the Store's
 * `Action.UserAction`, and the unit observed by consumer-side `@Singleton`
 * handlers that react to user choices without taking a dependency on the
 * Store.
 */
data class AppDialogUserChoice(
    val dialog: AppDialog,
    val action: AppDialogUserAction,
)
