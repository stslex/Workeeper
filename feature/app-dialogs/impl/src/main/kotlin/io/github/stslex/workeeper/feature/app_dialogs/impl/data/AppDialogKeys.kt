// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.app_dialogs.impl.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore Preferences keys for every pending dialog flag.
 *
 * Naming convention: `pending_<dialog_id>` for the primary boolean flag,
 * `pending_<dialog_id>_<field>` for each metadata field. Keys are grouped per
 * variant; the same group is written together inside the `dataStore.edit { }`
 * block in `AppDialogRepository.publish`, and cleared together in `dismiss`.
 *
 * Adding a new variant: append its primary boolean key + metadata keys here,
 * then update `AppDialogRepository`'s `resolveCurrentDialog` / `writeFlags` /
 * `clearFlags` / `isAlreadyPending` to handle the new sealed-type branch.
 * See `.claude/skills/app-dialogs-pattern.md`.
 *
 * **Key names are WIRE FORMAT.** Never rename an existing key — users with
 * pending dialogs persisted on an old key would lose the dialog after the
 * app update. If a key MUST be renamed, follow the deprecation path:
 *
 * 1. Add the new key under the new name (don't touch the old key).
 * 2. Write to BOTH keys; read prefers the new key, falls back to the old.
 * 3. Ship one release.
 * 4. Remove the old key in the next release once telemetry confirms zero
 *    reads of the old name.
 *
 * Pure additions (a new dialog variant with new keys) need no migration —
 * existing users see no pending dialog for the new variant on first launch,
 * which is the correct default.
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
