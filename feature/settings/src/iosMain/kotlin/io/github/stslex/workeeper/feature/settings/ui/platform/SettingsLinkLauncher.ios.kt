// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalUriHandler

@Composable
internal actual fun rememberSettingsLinkLauncher(): SettingsLinkLauncher {
    val handler = LocalUriHandler.current
    return remember(handler) { SettingsLinkLauncher { url -> runCatching { handler.openUri(url) } } }
}
