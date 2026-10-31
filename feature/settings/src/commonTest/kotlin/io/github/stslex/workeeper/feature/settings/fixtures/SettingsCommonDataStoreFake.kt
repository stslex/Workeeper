// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.fixtures

import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

internal class SettingsCommonDataStoreFake : CommonDataStore {
    override val homeSelectedStartDate: Flow<Long?> = emptyFlow()
    override val homeSelectedEndDate: Flow<Long?> = emptyFlow()
    override var themePreference: Flow<String> = flowOf("SYSTEM")
    override var homeStartCardMode: Flow<String> = flowOf("WEEK")
    val themes = mutableListOf<String>()
    val modes = mutableListOf<String>()
    override suspend fun setThemePreference(value: String) { themes += value }
    override suspend fun setHomeStartCardMode(value: String) { modes += value }
    override suspend fun setHomeSelectedStartDate(value: Long) = error("Unexpected start date")
    override suspend fun setHomeSelectedEndDate(value: Long) = error("Unexpected end date")
}
