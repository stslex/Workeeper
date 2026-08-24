// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.domain.AppDialogResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Single writer of every `pending_*` flag in the `app_dialogs_prefs` DataStore; state is derived
 * from DataStore on every read so a pending dialog survives process death.
 *
 * GUARD: mint the store through [DataStoreProviderFactory] only — a second one throws at runtime.
 */
@SingleIn(AppScope::class)
class AppDialogRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : AppDialogPublisher {

    @Inject
    constructor(storeFactory: DataStoreProviderFactory) : this(
        storeFactory.create(PREFS_NAME).dataStore,
    )

    /** Highest-priority pending dialog, or `null`; re-emits on every DataStore write. */
    val currentDialog: Flow<AppDialog?> = dataStore.data
        .map { prefs -> AppDialogResolver(prefs) }
        .distinctUntilChanged()

    override suspend fun publish(dialog: AppDialog) {
        dataStore.edit { prefs ->
            if (prefs.isAlreadyPending(dialog)) return@edit
            prefs.writeFlags(dialog)
        }
    }

    /** Clears every flag of [dialog]; reached through `AppDialogObserver.acknowledgeReaction`. */
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
