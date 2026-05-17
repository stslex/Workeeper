// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.domain

import androidx.datastore.preferences.core.Preferences
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogKeys

/**
 * Pure priority walk over the persisted dialog flag set. Returns the single
 * highest-priority pending `AppDialog?`, or `null` when no flag is set.
 *
 * The order is intentionally a literal `when` chain rather than a sortable
 * list / enum-ordinal sort so that any priority change shows up in code review
 * with surrounding context. Priority is a property of the variant, not of
 * runtime data.
 *
 * Priority (highest first):
 *
 * 1. [AppDialog.RestoreFailure] — critical; the user must acknowledge that
 *    their restore failed and their data is intact.
 * 2. [AppDialog.RestoreSuccess] — informational; positive confirmation of
 *    restore.
 * 3. [AppDialog.UndoRestoreSuccess] — informational; positive confirmation
 *    of undo.
 * 4. [AppDialog.UndoRestoreConfirmation] — user-initiated; least urgent
 *    because the user chose to enter this flow themselves.
 *
 * One read per [Preferences] snapshot — there is no flag-by-flag iteration
 * pass; the `when` short-circuits at the first true branch.
 *
 * Pure object: no `@Inject`, no DI, no state. Consumers (the repository
 * inside its `dataStore.data.map { ... }`, plus unit tests with synthetic
 * `Preferences`) treat this as a function literal.
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
