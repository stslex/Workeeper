// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Immutable

@Immutable
data class BackupInfoUi(
    val lastBackupText: String,
    val backupCountText: String,
    /**
     * True when the account has zero stored backups. The restore row's sub-line collapses
     * to [backupCountText] alone in that state — composing both texts with `·` reads as a
     * redundant double statement («Ещё нет резервных копий · Резервных копий ещё нет»),
     * and the mockup draws the separator only for the populated case.
     */
    val isEmpty: Boolean = false,
)
