// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.domain.AppDialogResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
 * DI (App-Scope Collapse Step 3, app-dialogs slice): Metro-owned, self-bound
 * app singleton (`@SingleIn(AppScope)` + `@Inject` on the DataStore-building
 * secondary ctor). Public because app/app's `AppGraph` names the concrete type
 * in its accessor (Metro contributions/accessors can't reach an `internal`
 * cross-module type). It implements [AppDialogPublisher] but is NOT bound to it
 * (the producer binding is [AppDialogPublisherImpl]); so it is a self accessor,
 * never `@ContributesBinding`. The `Context` drops from Hilt's
 * `@ApplicationContext` to a plain param resolved from the graph's
 * `create(applicationContext)` bound instance.
 */
@SingleIn(AppScope::class)
class AppDialogRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : AppDialogPublisher {

    @Inject
    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile(PREFS_NAME)
        },
    )

    /**
     * Reactive view of the currently-pending top-priority dialog. Re-emits on
     * every DataStore write; emits `null` when no flag is set. Priority is
     * resolved by [AppDialogResolver]; this repository only owns persistence.
     */
    val currentDialog: Flow<AppDialog?> = dataStore.data
        .map { prefs -> AppDialogResolver(prefs) }
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
