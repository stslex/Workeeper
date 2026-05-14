// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Stable

@Stable
internal sealed interface BackupOperationUi {

    val isInProgress: Boolean get() = this !is Idle

    data object Idle : BackupOperationUi

    data object SigningIn : BackupOperationUi

    data object CreatingBackup : BackupOperationUi

    data object FetchingBackups : BackupOperationUi

    data object Restoring : BackupOperationUi

    data object SigningOut : BackupOperationUi
}
