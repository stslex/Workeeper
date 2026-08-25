// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api.observer

import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialogUserChoice
import kotlinx.coroutines.flow.Flow

/**
 * Cross-feature consumer surface for app-dialog choices, on a `replay = 0` transport — subscribers
 * must be constructed eagerly at startup. See documentation/feature-specs/app-dialogs.md.
 */
interface AppDialogObserver {

    /** Stream of user-action choices dispatched by the Host. Hot, no replay. */
    fun observeUserActions(): Flow<AppDialogUserChoice>

    /**
     * Clears the dialog's `pending_*` flag. GUARD: call this AFTER the side-effect for the user's
     * choice has run, never before.
     */
    suspend fun acknowledgeReaction(dialog: AppDialog)
}
