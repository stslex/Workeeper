// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.app.ios

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.di.MainDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.platform.AppReinitializationHost
import io.github.stslex.workeeper.core.core.platform.AppReinitializer
import io.github.stslex.workeeper.core.core.platform.CameraPermission
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.backup.api.restore.InstallEpochSource
import io.github.stslex.workeeper.core.data.dataStore.core.DataStorePathResolver
import io.github.stslex.workeeper.core.data.dataStore.core.IosDataStorePathResolver
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Borrows one generation's database and lifetime. The caller disposes UI and owners, joins the
 * lifetime, then closes its database.
 * Ownership: `documentation/feature-specs/kmp-phase-7-20b-ios-metro-graph.md#production-graph-and-ownership`.
 */
fun buildIosAppGraph(
    database: AppDatabase,
    lifetime: AppScopeLifetime,
    imageStorage: ImageStorage,
    cameraPermission: CameraPermission,
    resources: ResourceWrapper,
    installEpochSource: InstallEpochSource,
    reinitializationHost: AppReinitializationHost,
): AppRootDeps = createGraphFactory<IosAppGraph.Factory>().create(
    database,
    lifetime,
    imageStorage,
    cameraPermission,
    resources,
    installEpochSource,
    reinitializationHost,
)

@DependencyGraph(AppScope::class)
internal interface IosAppGraph : AppRootDeps {
    val appDatabase: AppDatabase
    val appScopeLifetime: AppScopeLifetime
    val storeDispatchers: StoreDispatchers
    val navigator: Navigator
    val appDialogRepository: AppDialogRepository

    @MainDispatcher
    val mainDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    @Provides
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @MainImmediateDispatcher
    fun provideMainImmediateDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @IODispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @SingleIn(AppScope::class)
    fun provideReinitializer(host: AppReinitializationHost): AppReinitializer = AppReinitializer(host)

    @Provides
    @SingleIn(AppScope::class)
    fun providePlatformInfo(): PlatformInfoProvider = PlatformInfoProvider()

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnalytics(): AnalyticsHolder = AnalyticsHolder()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDataStorePaths(): DataStorePathResolver = IosDataStorePathResolver()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides database: AppDatabase,
            @Provides lifetime: AppScopeLifetime,
            @Provides imageStorage: ImageStorage,
            @Provides cameraPermission: CameraPermission,
            @Provides resources: ResourceWrapper,
            @Provides installEpochSource: InstallEpochSource,
            @Provides reinitializationHost: AppReinitializationHost,
        ): IosAppGraph
    }
}
