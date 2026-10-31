// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action

internal object UnavailableSettingsBackupHandler : SettingsBackupHandler {
    override val isAvailable: Boolean = false
    override fun invoke(action: Action.Backup) = Unit
}
