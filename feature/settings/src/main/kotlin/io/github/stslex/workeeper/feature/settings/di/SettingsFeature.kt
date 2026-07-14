// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.di.appGraphContract
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.Screen.Settings
import io.github.stslex.workeeper.feature.app_dialogs.api.appDialogPublisher
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
            // App-Scope Collapse Step 6 (cut): app-scope deps via the Metro AppGraphContract; appDialogPublisher
            // via the feature-api holder seam (core:di can't name the feature type); app Context direct.
            val graph = context.appGraphContract()
            createGraphFactory<SettingsGraph.Factory>()
                .create(
                    navigator = graph.navigator,
                    platformInfoProvider = graph.platformInfoProvider,
                    commonDataStore = graph.commonDataStore,
                    backupAuth = graph.backupAuth,
                    backupStorage = graph.backupStorage,
                    snapshotExportRunner = graph.snapshotExportRunner,
                    databaseSnapshotProvider = graph.databaseSnapshotProvider,
                    restoreStateRepository = graph.restoreStateRepository,
                    backupPreferencesRepository = graph.backupPreferencesRepository,
                    autoBackupController = graph.autoBackupController,
                    appDialogPublisher = context.appDialogPublisher(),
                    tempFileProvider = graph.tempFileProvider,
                    storeDispatchers = graph.storeDispatchers,
                    analyticsHolder = graph.analyticsHolder,
                    loggerHolder = graph.loggerHolder,
                    defaultDispatcher = graph.defaultDispatcher,
                    ioDispatcher = graph.ioDispatcher,
                    context = context.applicationContext,
                )
                .settingsStore
        } as SettingsStoreProcessor
    }
}
