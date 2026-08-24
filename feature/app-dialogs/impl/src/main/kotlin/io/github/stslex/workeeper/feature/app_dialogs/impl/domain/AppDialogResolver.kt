// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.domain

import androidx.datastore.preferences.core.Preferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogKeys

/**
 * Pure priority walk over the persisted flag set, returning the highest-priority pending dialog.
 * A literal `when` chain rather than a sorted list so priority changes surface in review.
 */
internal object AppDialogResolver {

    operator fun invoke(prefs: Preferences): AppDialog? = when {
        prefs[AppDialogKeys.PENDING_RESTORE_FAILURE] == true -> readRestoreFailure(prefs)
        prefs[AppDialogKeys.PENDING_RESTORE_SUCCESS] == true -> readRestoreSuccess(prefs)
        prefs[AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS] == true -> AppDialog.UndoRestoreSuccess
        prefs[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION] == true ->
            readUndoRestoreConfirmation(prefs)

        else -> null
    }

    private fun readRestoreFailure(prefs: Preferences): AppDialog.RestoreFailure {
        val raw = prefs[AppDialogKeys.PENDING_RESTORE_FAILURE_REASON]
        val reason = raw
            ?.let { name -> runCatching { BackupErrorCode.valueOf(name) }.getOrNull() }
            ?: BackupErrorCode.Unknown
        return AppDialog.RestoreFailure(reason = reason)
    }

    private fun readRestoreSuccess(prefs: Preferences): AppDialog.RestoreSuccess =
        AppDialog.RestoreSuccess(
            restoredAtEpochMs = prefs[AppDialogKeys.PENDING_RESTORE_SUCCESS_AT_EPOCH_MS] ?: 0L,
            previousVersionAvailable =
            prefs[AppDialogKeys.PENDING_RESTORE_SUCCESS_HAS_PREVIOUS] ?: false,
        )

    private fun readUndoRestoreConfirmation(prefs: Preferences): AppDialog.UndoRestoreConfirmation =
        AppDialog.UndoRestoreConfirmation(
            originalDataDateEpochMs =
            prefs[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS] ?: 0L,
        )
}
