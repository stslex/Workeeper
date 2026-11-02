// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.ui.platform

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.stslex.workeeper.feature.settings.mvi.mapper.SettingsAuthMapper
import io.github.stslex.workeeper.feature.settings.mvi.model.SettingsAuthResult

@Composable
internal actual fun rememberSettingsAuthLauncher(onResult: (SettingsAuthResult) -> Unit): SettingsAuthLauncher {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        onResult(SettingsAuthMapper.toResult(result.data))
    }
    return remember(launcher) {
        SettingsAuthLauncher { request ->
            launcher.launch(IntentSenderRequest.Builder(SettingsAuthMapper.toIntentSender(request)).build())
        }
    }
}
