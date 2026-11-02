// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.platform

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.feature.settings.mvi.model.SettingsAuthRequest
import io.github.stslex.workeeper.feature.settings.mvi.model.SettingsAuthResult

internal fun interface SettingsAuthLauncher {
    fun launch(request: SettingsAuthRequest)
}

@Composable
internal expect fun rememberSettingsAuthLauncher(onResult: (SettingsAuthResult) -> Unit): SettingsAuthLauncher
