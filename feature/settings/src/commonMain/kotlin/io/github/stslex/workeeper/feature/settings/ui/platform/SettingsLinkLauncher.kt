// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.platform

import androidx.compose.runtime.Composable

internal fun interface SettingsLinkLauncher {
    fun launch(url: String)
}

@Composable
internal expect fun rememberSettingsLinkLauncher(): SettingsLinkLauncher
