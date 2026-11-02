// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.stslex.workeeper.feature.settings.mvi.model.SettingsAuthResult

@Composable
internal actual fun rememberSettingsAuthLauncher(onResult: (SettingsAuthResult) -> Unit): SettingsAuthLauncher =
    remember { SettingsAuthLauncher { } }
