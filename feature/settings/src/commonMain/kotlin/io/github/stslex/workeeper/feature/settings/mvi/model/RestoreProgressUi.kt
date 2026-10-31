// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import androidx.compose.runtime.Stable

@Stable
sealed interface RestoreProgressUi {

    val isActive: Boolean get() = this !is Idle

    data object Idle : RestoreProgressUi

    data object Restoring : RestoreProgressUi

    data object Completed : RestoreProgressUi
}
