// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.mapper

import android.content.Intent
import android.content.IntentSender
import io.github.stslex.workeeper.feature.settings.mvi.model.AndroidSettingsAuthRequest
import io.github.stslex.workeeper.feature.settings.mvi.model.AndroidSettingsAuthResult
import io.github.stslex.workeeper.feature.settings.mvi.model.SettingsAuthRequest
import io.github.stslex.workeeper.feature.settings.mvi.model.SettingsAuthResult

internal object SettingsAuthMapper {

    fun toRequest(intentSender: IntentSender): SettingsAuthRequest = AndroidSettingsAuthRequest(intentSender)

    fun toIntentSender(request: SettingsAuthRequest): IntentSender =
        (request as AndroidSettingsAuthRequest).intentSender

    fun toResult(intent: Intent?): SettingsAuthResult = AndroidSettingsAuthResult(intent)

    fun toIntent(result: SettingsAuthResult): Intent? = (result as AndroidSettingsAuthResult).intent
}
