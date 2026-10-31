// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.mvi.model

import android.content.Intent
import android.content.IntentSender

internal class AndroidSettingsAuthRequest(val intentSender: IntentSender) : SettingsAuthRequest

internal class AndroidSettingsAuthResult(val intent: Intent?) : SettingsAuthResult
