// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpoch
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreOwnerId
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreRecoveryFiles
import io.github.stslex.workeeper.core.data.dataStore.core.DataStoreProviderFactory
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.app_dialogs.impl.domain.AppDialogResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
    private val installEpoch: suspend () -> InstallEpoch,
) : AppDialogPublisher {

    @Inject
    constructor(
        storeFactory: DataStoreProviderFactory,
        recoveryFiles: RestoreRecoveryFiles,
    ) : this(
        storeFactory.create(PREFS_NAME).dataStore,
        recoveryFiles::installEpoch,
    )

    /** Highest-priority pending dialog, or `null`; re-emits on every DataStore write. */
    val currentDialog: Flow<AppDialog?> = flow {
        val epoch = installEpoch()
        dataStore.edit { prefs -> prefs.reconcileRestoreEpoch(epoch) }
        emitAll(
            dataStore.data.map { prefs ->
                if (prefs[AppDialogKeys.RESTORE_DIALOG_INSTALL_EPOCH] == epoch.toString()) {
                    AppDialogResolver(prefs)
                } else {
                    null
                }
            },
        )
    }.distinctUntilChanged()

    override suspend fun publish(dialog: AppDialog) {
        val epoch = installEpoch()
        dataStore.edit { prefs ->
            prefs.reconcileRestoreEpoch(epoch)
            if (prefs.isAlreadyPending(dialog)) return@edit
            prefs.writeFlags(dialog)
        }
    }

    /** Clears every flag of [dialog]; reached through `AppDialogObserver.acknowledgeReaction`. */
    suspend fun dismiss(dialog: AppDialog) {
        val epoch = installEpoch()
        dataStore.edit { prefs ->
            prefs.reconcileRestoreEpoch(epoch)
            prefs.clearFlags(dialog)
        }
    }

    private fun Preferences.isAlreadyPending(dialog: AppDialog): Boolean = when (dialog) {
        is AppDialog.RestoreFailure ->
            this[AppDialogKeys.PENDING_RESTORE_FAILURE] == true &&
                this[AppDialogKeys.PENDING_RESTORE_FAILURE_REASON] == dialog.reason.name &&
                this[AppDialogKeys.PENDING_RESTORE_FAILURE_OWNER] ==
                dialog.terminalOwner?.value

        is AppDialog.RestoreSuccess ->
            this[AppDialogKeys.PENDING_RESTORE_SUCCESS] == true &&
                this[AppDialogKeys.PENDING_RESTORE_SUCCESS_AT_EPOCH_MS] ==
                dialog.restoredAtEpochMs &&
                this[AppDialogKeys.PENDING_RESTORE_SUCCESS_HAS_PREVIOUS] ==
                dialog.previousVersionAvailable &&
                this[AppDialogKeys.PENDING_RESTORE_SUCCESS_OWNER] ==
                dialog.terminalOwner?.value

        is AppDialog.UndoRestoreSuccess ->
            this[AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS] == true &&
                this[AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS_OWNER] ==
                dialog.terminalOwner?.value

        is AppDialog.UndoRestoreConfirmation -> {
            val pendingOwner = this[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_OWNER]
            this[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION] == true &&
                pendingOwner == dialog.undoRef.owner.value
        }
    }

    private fun MutablePreferences.writeFlags(dialog: AppDialog) {
        when (dialog) {
            is AppDialog.RestoreFailure -> {
                this[AppDialogKeys.PENDING_RESTORE_FAILURE] = true
                this[AppDialogKeys.PENDING_RESTORE_FAILURE_REASON] = dialog.reason.name
                writeOptionalOwner(
                    AppDialogKeys.PENDING_RESTORE_FAILURE_OWNER,
                    dialog.terminalOwner,
                )
            }

            is AppDialog.RestoreSuccess -> {
                this[AppDialogKeys.PENDING_RESTORE_SUCCESS] = true
                this[AppDialogKeys.PENDING_RESTORE_SUCCESS_AT_EPOCH_MS] = dialog.restoredAtEpochMs
                this[AppDialogKeys.PENDING_RESTORE_SUCCESS_HAS_PREVIOUS] =
                    dialog.previousVersionAvailable
                writeOptionalOwner(
                    AppDialogKeys.PENDING_RESTORE_SUCCESS_OWNER,
                    dialog.terminalOwner,
                )
            }

            is AppDialog.UndoRestoreSuccess -> {
                // The terminal is published only after rollback finalization is durable. Clearing
                // the initiating confirmation in this same dialog-store edit prevents it resurfacing.
                remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION)
                remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS)
                remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_OWNER)
                this[AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS] = true
                writeOptionalOwner(
                    AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS_OWNER,
                    dialog.terminalOwner,
                )
            }

            is AppDialog.UndoRestoreConfirmation -> {
                this[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION] = true
                this[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS] =
                    dialog.originalDataDateEpochMs
                this[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_OWNER] =
                    dialog.undoRef.owner.value
            }
        }
    }

    private fun MutablePreferences.clearFlags(dialog: AppDialog) {
        if (!isAlreadyPending(dialog)) return
        when (dialog) {
            is AppDialog.RestoreFailure -> {
                remove(AppDialogKeys.PENDING_RESTORE_FAILURE)
                remove(AppDialogKeys.PENDING_RESTORE_FAILURE_REASON)
                remove(AppDialogKeys.PENDING_RESTORE_FAILURE_OWNER)
            }

            is AppDialog.RestoreSuccess -> {
                remove(AppDialogKeys.PENDING_RESTORE_SUCCESS)
                remove(AppDialogKeys.PENDING_RESTORE_SUCCESS_AT_EPOCH_MS)
                remove(AppDialogKeys.PENDING_RESTORE_SUCCESS_HAS_PREVIOUS)
                remove(AppDialogKeys.PENDING_RESTORE_SUCCESS_OWNER)
            }

            is AppDialog.UndoRestoreSuccess -> {
                remove(AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS)
                remove(AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS_OWNER)
            }

            is AppDialog.UndoRestoreConfirmation -> {
                val pendingOwner = this[AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_OWNER]
                if (pendingOwner == dialog.undoRef.owner.value) {
                    remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION)
                    remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS)
                    remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_OWNER)
                }
            }
        }
    }

    private fun MutablePreferences.reconcileRestoreEpoch(epoch: InstallEpoch) {
        if (this[AppDialogKeys.RESTORE_DIALOG_INSTALL_EPOCH] == epoch.toString()) return
        clearAllRestoreDialogs()
        this[AppDialogKeys.RESTORE_DIALOG_INSTALL_EPOCH] = epoch.toString()
    }

    private fun MutablePreferences.clearAllRestoreDialogs() {
        remove(AppDialogKeys.PENDING_RESTORE_SUCCESS)
        remove(AppDialogKeys.PENDING_RESTORE_SUCCESS_AT_EPOCH_MS)
        remove(AppDialogKeys.PENDING_RESTORE_SUCCESS_HAS_PREVIOUS)
        remove(AppDialogKeys.PENDING_RESTORE_SUCCESS_OWNER)
        remove(AppDialogKeys.PENDING_RESTORE_FAILURE)
        remove(AppDialogKeys.PENDING_RESTORE_FAILURE_REASON)
        remove(AppDialogKeys.PENDING_RESTORE_FAILURE_OWNER)
        remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION)
        remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS)
        remove(AppDialogKeys.PENDING_UNDO_RESTORE_CONFIRMATION_OWNER)
        remove(AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS)
        remove(AppDialogKeys.PENDING_UNDO_RESTORE_SUCCESS_OWNER)
    }

    private fun MutablePreferences.writeOptionalOwner(
        key: Preferences.Key<String>,
        owner: RestoreOwnerId?,
    ) {
        if (owner == null) remove(key) else this[key] = owner.value
    }

    internal companion object {
        const val PREFS_NAME = "app_dialogs_prefs"
    }
}
