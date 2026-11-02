// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Stable

@Stable
enum class BackupScheduleUi {
    DAILY,
    WEEKLY,
    MANUAL_ONLY,
}
