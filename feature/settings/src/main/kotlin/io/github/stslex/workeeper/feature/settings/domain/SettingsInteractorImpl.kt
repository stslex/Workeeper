// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.domain

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.feature.settings.domain.model.ThemeModeDomain
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@ViewModelScoped
internal class SettingsInteractorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val commonDataStore: CommonDataStore,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : SettingsInteractor {

    private val packageInfo: PackageInfo by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    }

    override fun appVersionName(): String = packageInfo.versionName.orEmpty()

    override fun appVersionCode(): Long = packageInfo.longVersionCode

    override fun observeThemeMode(): Flow<ThemeModeDomain> = commonDataStore.themePreference
        .map { value -> ThemeModeDomain.fromValue(value) }
        .flowOn(defaultDispatcher)

    override suspend fun setThemeMode(mode: ThemeModeDomain) {
        commonDataStore.setThemePreference(mode.value)
    }
}
