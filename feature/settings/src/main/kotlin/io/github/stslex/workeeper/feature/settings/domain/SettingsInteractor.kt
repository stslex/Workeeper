// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import io.github.stslex.workeeper.feature.settings.domain.model.ThemeModeDomain
import kotlinx.coroutines.flow.Flow

internal interface SettingsInteractor {

    fun appVersionName(): String

    fun appVersionCode(): Long

    fun observeThemeMode(): Flow<ThemeModeDomain>

    suspend fun setThemeMode(mode: ThemeModeDomain)
}
