// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.Settings
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStoreImpl

internal typealias SettingsStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/settings resolves its Store through the **Metro** path (KMP C.1), not `hiltViewModel()`.
 * The Metro graph is built INSIDE the `rememberMetroStoreProcessor` factory lambda so it is created
 * at most once per retained [SettingsStoreImpl] (per `NavBackStackEntry`), binding the graph +
 * `@SingleIn(SettingsScope)` nodes to the Store's lifetime — the way Hilt `@ViewModelScoped` did.
 *
 * The 18 app-scoped Hilt singletons are pulled from the `SingletonComponent` via
 * [SettingsHiltEntryPoint]. The two dispatchers cross the bridge QUALIFIED (`includeJavax`); the
 * app `Context` is resolved on the Hilt side (`applicationContext()` keeps `@ApplicationContext`)
 * and handed to the graph as a plain `Context`.
 */
internal object SettingsFeature : Feature<SettingsStoreProcessor, Settings>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): SettingsStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<SettingsStoreImpl> {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                SettingsHiltEntryPoint::class.java,
            )
            createGraphFactory<SettingsGraph.Factory>()
                .create(
                    navigator = entryPoint.navigator(),
                    platformInfoProvider = entryPoint.platformInfoProvider(),
                    commonDataStore = entryPoint.commonDataStore(),
                    backupAuth = entryPoint.backupAuth(),
                    backupStorage = entryPoint.backupStorage(),
                    snapshotExportRunner = entryPoint.snapshotExportRunner(),
                    databaseSnapshotProvider = entryPoint.databaseSnapshotProvider(),
                    restoreStateRepository = entryPoint.restoreStateRepository(),
                    backupPreferencesRepository = entryPoint.backupPreferencesRepository(),
                    autoBackupController = entryPoint.autoBackupController(),
                    appDialogPublisher = entryPoint.appDialogPublisher(),
                    tempFileProvider = entryPoint.tempFileProvider(),
                    storeDispatchers = entryPoint.storeDispatchers(),
                    analyticsHolder = entryPoint.analyticsHolder(),
                    loggerHolder = entryPoint.loggerHolder(),
                    defaultDispatcher = entryPoint.defaultDispatcher(),
                    ioDispatcher = entryPoint.ioDispatcher(),
                    context = entryPoint.applicationContext(),
                )
                .settingsStore
        } as SettingsStoreProcessor
    }
}
