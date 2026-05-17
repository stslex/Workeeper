// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupErrorCode
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single writer of every `pending_*` flag in the `app_dialogs_prefs` DataStore
 * and the persistence layer of the cross-feature `AppDialog` mechanism.
 * Implements [AppDialogPublisher] for producers; the impl-only [dismiss] entry
 * point is used by `AppDialogHost` (and, after the MVI rewrite, by the
 * `DismissHandler` / `UserActionHandler` inside `AppDialogStore`).
 *
 * State is derived from DataStore on every read — no in-memory queue, no
 * `MutableStateFlow<AppDialog?>` field. This is load-bearing: backup recovery's
 * primary case is "show this dialog after a process restart", which an
 * in-memory queue would not survive (see
 * `documentation/feature-specs/app-dialogs.md` → "Single source of truth").
 *
 * Naming: `Repository` suffix maps to `@Singleton` through the standard
 * `HiltScopeRule` predicate (`ScopeClassType.singletonClasses` contains
 * `"Repository"`) — no carve-out. The historical `AppDialogStore` carve-out
 * is removed in the same refactor that introduces this class.
 */
@Singleton
internal class AppDialogRepository @Inject constructor(
    @ApplicationContext context: Context,
) : AppDialogPublisher {

    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(PREFS_NAME)
        }
    }

    /**
     * Reactive view of the currently-pending top-priority dialog. Re-emits on
     * every DataStore write; emits `null` when no flag is set. Collected by
     * `AppDialogHost` via `collectAsStateWithLifecycle`.
     */
    val currentDialog: Flow<AppDialog?> = dataStore.data
        .map(::resolveCurrentDialog)
        .distinctUntilChanged()

    override suspend fun publish(dialog: AppDialog) {
        dataStore.edit { prefs ->
            if (prefs.isAlreadyPending(dialog)) return@edit
            prefs.writeFlags(dialog)
        }
    }

    /**
     * Clear every flag belonging to [dialog]. Internal API used by
     * `AppDialogHost` on user dismiss / confirm — never exposed via
     * [AppDialogPublisher].
     */
    suspend fun dismiss(dialog: AppDialog) {
        dataStore.edit { prefs -> prefs.clearFlags(dialog) }
    }

    /**
     * Priority resolution walks the catalog in a fixed order. The order is
     * intentionally a literal `when` chain rather than a sortable list so that
     * priority changes surface in code review.
     *
     * Priority (highest first): `RestoreFailure` (critical — must
     * acknowledge), `RestoreSuccess` (informational), `UndoRestoreSuccess`
     * (informational), `UndoRestoreConfirmation` (user-initiated, least
     * urgent).
     */
    private fun resolveCurrentDialog(prefs: Preferences): AppDialog? = when {
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

    private fun Preferences.isAlreadyPending(dialog: AppDialog): Boolean = when (dialog) {
        is AppDialog.RestoreFailure -> this[AppDialogKeys.PENDING_RESTORE_FAILURE] == true
        is AppDialog.RestoreSuccess -> this[AppDialogKeys.PENDING_RESTORE_SUCCESS] == true
        AppDialog.UndoRestoreSuccess -> this[AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS] == true
        is AppDialog.UndoRestoreConfirmation ->
            this[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION] == true
    }

    private fun MutablePreferences.writeFlags(dialog: AppDialog) {
        when (dialog) {
            is AppDialog.RestoreFailure -> {
                this[AppDialogKeys.PENDING_RESTORE_FAILURE] = true
                this[AppDialogKeys.PENDING_RESTORE_FAILURE_REASON] = dialog.reason.name
            }

            is AppDialog.RestoreSuccess -> {
                this[AppDialogKeys.PENDING_RESTORE_SUCCESS] = true
                this[AppDialogKeys.PENDING_RESTORE_SUCCESS_AT_EPOCH_MS] = dialog.restoredAtEpochMs
                this[AppDialogKeys.PENDING_RESTORE_SUCCESS_HAS_PREVIOUS] =
                    dialog.previousVersionAvailable
            }

            AppDialog.UndoRestoreSuccess -> {
                this[AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS] = true
            }

            is AppDialog.UndoRestoreConfirmation -> {
                this[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION] = true
                this[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS] =
                    dialog.originalDataDateEpochMs
            }
        }
    }

    private fun MutablePreferences.clearFlags(dialog: AppDialog) {
        when (dialog) {
            is AppDialog.RestoreFailure -> {
                remove(AppDialogKeys.PENDING_RESTORE_FAILURE)
                remove(AppDialogKeys.PENDING_RESTORE_FAILURE_REASON)
            }

            is AppDialog.RestoreSuccess -> {
                remove(AppDialogKeys.PENDING_RESTORE_SUCCESS)
                remove(AppDialogKeys.PENDING_RESTORE_SUCCESS_AT_EPOCH_MS)
                remove(AppDialogKeys.PENDING_RESTORE_SUCCESS_HAS_PREVIOUS)
            }

            AppDialog.UndoRestoreSuccess -> {
                remove(AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS)
            }

            is AppDialog.UndoRestoreConfirmation -> {
                remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION)
                remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS)
            }
        }
    }

    internal companion object {
        const val PREFS_NAME = "app_dialogs_prefs"
    }
}
