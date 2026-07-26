// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Immutable

@Immutable
data class BackupInfoUi(
    val lastBackupText: String,
    val backupCountText: String,
)
