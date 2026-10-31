// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.handler

import io.github.stslex.workeeper.core.ui.mvi.handler.Handler
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action

internal interface SettingsBackupHandler : Handler<Action.Backup> {
    val isAvailable: Boolean
}
