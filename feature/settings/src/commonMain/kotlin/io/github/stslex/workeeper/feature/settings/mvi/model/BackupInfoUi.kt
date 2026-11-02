// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Immutable

/**
 * What is known about the account's stored backups. GUARD: never collapse [Unknown] (not yet
 * loaded) into [Empty] (known to have none) — that reports a network condition as user data.
 */
@Immutable
sealed interface BackupInfoUi {

    data object Unknown : BackupInfoUi

    /** The account has zero stored backups; no "last backup" line to compose with. */
    data class Empty(val backupCountText: String) : BackupInfoUi

    data class Present(
        val lastBackupText: String,
        val backupCountText: String,
    ) : BackupInfoUi
}
