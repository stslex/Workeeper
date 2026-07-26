// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.feature.settings.di.SettingsScope
import io.github.stslex.workeeper.feature.settings.domain.model.ThemeModeDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

@Inject
@SingleIn(SettingsScope::class)
class SettingsInteractorImpl(
    private val platformInfo: PlatformInfoProvider,
    private val commonDataStore: CommonDataStore,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : SettingsInteractor {

    override fun appVersionName(): String = platformInfo.appVersionName()

    override fun appVersionCode(): Long = platformInfo.appVersionCode()

    override fun observeThemeMode(): Flow<ThemeModeDomain> = commonDataStore.themePreference
        .map { value -> ThemeModeDomain.fromValue(value) }
        .flowOn(defaultDispatcher)

    override suspend fun setThemeMode(mode: ThemeModeDomain) {
        commonDataStore.setThemePreference(mode.value)
    }
}
