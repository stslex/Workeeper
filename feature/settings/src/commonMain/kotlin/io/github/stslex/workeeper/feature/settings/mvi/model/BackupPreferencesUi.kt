// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Immutable

/**
 * Authenticated-only snapshot of the auto-backup preferences + status. Null on
 * `SettingsStore.State` when the user is not signed in; populated after the
 * `ObservePreferences` flow lands its first emission.
 */
@Immutable
data class BackupPreferencesUi(
    val schedule: BackupScheduleUi,
    val allowOnMobileData: Boolean,
    val nextBackupText: String?,
    val isAuthPaused: Boolean,
    val aiExportEnabled: Boolean,
)
