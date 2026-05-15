// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.api.actions

import android.net.Uri

/**
 * Side-effects the cross-feature `AppDialogHost` triggers when the user taps
 * an action button. The interface lives in the api module so the host can
 * call into it without depending on the `app/app` orchestrator that knows
 * how to actually perform the work.
 *
 * Concrete impl is wired at the app graph (`app/app`) and reaches across
 * modules — coordinator, restartApp, diagnostics exporter, browser intents —
 * none of which belong inside the app-dialogs module.
 */
interface AppDialogActions {

    /**
     * Triggers the user-initiated undo of the last restore (Scenario 3).
     * Implementations file-swap the live database with `pre_restore_backup.db`,
     * publish `AppDialog.UndoRestoreSuccess` so it surfaces after restart,
     * and call [restartApp].
     */
    suspend fun performUndoRestore()

    /**
     * Publishes [io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog.UndoRestoreConfirmation]
     * using the preserved-snapshot's original-data date as the body argument.
     * Called when the user taps "Undo restore" from the post-restore success
     * dialog (alternative entry point to the Settings "Revert last restore"
     * row).
     */
    suspend fun publishUndoConfirmation()

    /**
     * Writes a plain-text diagnostic file and returns a `content://` URI
     * grantable to the share-chooser intent. Returns `null` when the write
     * fails (caller does not surface the share action in that case).
     */
    suspend fun exportRestoreDiagnostics(): Uri?

    /** Performs the destructive app-restart used by the undo flow. */
    fun restartApp()
}
