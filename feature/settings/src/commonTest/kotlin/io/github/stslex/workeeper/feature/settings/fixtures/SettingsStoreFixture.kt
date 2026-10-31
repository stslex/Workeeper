// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.fixtures

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.ScreenWithResult
import io.github.stslex.workeeper.feature.settings.di.SettingsHandlerStoreImpl
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.domain.model.ArchivedCountsDomain
import io.github.stslex.workeeper.feature.settings.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.settings.domain.model.ThemeModeDomain
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsBackupHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsClickHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsInputHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsNavigationHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsPagingHandler
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStoreImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestDispatcher
import kotlin.reflect.KClass

internal class SettingsStoreFixture(dispatcher: TestDispatcher, backupHandler: SettingsBackupHandler) {
    val lifetime = AppScopeLifetime()
    val interactor = RecordingSettingsInteractor()
    val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this)
    }
    private val emitter = SettingsHandlerStoreImpl()
    val store = SettingsStoreImpl(
        navigationHandler = SettingsNavigationHandler(RecordingNavigator()),
        pagingHandler = SettingsPagingHandler(interactor, emitter),
        clickHandler = SettingsClickHandler(emitter),
        inputHandler = SettingsInputHandler(interactor, emitter),
        backupHandler = backupHandler,
        interactor = interactor,
        storeDispatchers = StoreDispatchers(dispatcher, dispatcher),
        storeEmitter = emitter,
        analyticsHolder = AnalyticsHolder(),
        loggerHolder = LoggerHolder(),
        appScopeLifetime = lifetime,
    )

    suspend fun close() {
        store.dispose()
        lifetime.cancelAndJoin()
    }
}

internal class RecordingSettingsInteractor : SettingsInteractor {
    val observations = mutableListOf<String>()
    val themes = mutableListOf<ThemeModeDomain>()
    val modes = mutableListOf<StartCardModeDomain>()
    override fun appVersionName() = "1.0.0"
    override fun appVersionCode() = 1L
    override fun observeThemeMode(): Flow<ThemeModeDomain> {
        observations += "theme"
        return flowOf(ThemeModeDomain.DARK)
    }
    override fun observeStartCardMode(): Flow<StartCardModeDomain> {
        observations += "startMode"
        return flowOf(StartCardModeDomain.LAGGING_GROUPS)
    }
    override fun observeArchivedCounts(): Flow<ArchivedCountsDomain> {
        observations += "archive"
        return flowOf(ArchivedCountsDomain(4, 1))
    }
    override suspend fun setThemeMode(mode: ThemeModeDomain) { themes += mode }
    override suspend fun setStartCardMode(mode: StartCardModeDomain) { modes += mode }
}

internal class RecordingNavigator : Navigator {
    val screens = mutableListOf<Screen>()
    var pops = 0
    override fun navTo(screen: Screen) { screens += screen }
    override fun replaceTo(screen: Screen) { screens += screen }
    override fun popBack() { pops++ }
    override fun restartApp() = Unit
    override fun openRecovery() = Unit
    override fun <S, R : Any> popBackWithResult(destination: KClass<S>, result: R) where S : ScreenWithResult<R> = Unit
}
