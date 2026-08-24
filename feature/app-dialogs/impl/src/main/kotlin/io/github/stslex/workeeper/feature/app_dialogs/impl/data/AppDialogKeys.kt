// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore Preferences keys for every pending dialog flag, named `pending_<dialog_id>[_<field>]`.
 *
 * GUARD: key names are wire format — never rename one. The deprecation path and the
 * add-a-variant checklist are in the app-dialogs spec.
 */
internal object AppDialogKeys {

    val PENDING_RESTORE_SUCCESS =
        booleanPreferencesKey("pending_restore_success")
    val PENDING_RESTORE_SUCCESS_AT_EPOCH_MS =
        longPreferencesKey("pending_restore_success_at_epoch_ms")
    val PENDING_RESTORE_SUCCESS_HAS_PREVIOUS =
        booleanPreferencesKey("pending_restore_success_has_previous")

    val PENDING_RESTORE_FAILURE =
        booleanPreferencesKey("pending_restore_failure")
    val PENDING_RESTORE_FAILURE_REASON =
        stringPreferencesKey("pending_restore_failure_reason")

    val PENDING_UNDO_RESTORE_CONFIRMATION =
        booleanPreferencesKey("pending_undo_restore_confirmation")
    val PENDING_UNDO_RESTORE_CONFIRMATION_ORIGINAL_AT_EPOCH_MS =
        longPreferencesKey("pending_undo_restore_confirmation_original_date_epoch_ms")

    val PENDING_UNDO_RESTORE_SUCCESS =
        booleanPreferencesKey("pending_undo_restore_success")
}
