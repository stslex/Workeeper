// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.ui.mvi.Feature
import io.github.stslex.workeeper.core.ui.mvi.di.StoreCoreDeps
import io.github.stslex.workeeper.core.ui.mvi.di.appDeps
import io.github.stslex.workeeper.core.ui.mvi.processor.StoreProcessor
import io.github.stslex.workeeper.core.ui.mvi.processor.rememberMetroStoreProcessor
import io.github.stslex.workeeper.core.ui.navigation.NavigatorDeps
import io.github.stslex.workeeper.core.ui.navigation.Screen.Settings
import io.github.stslex.workeeper.feature.app_dialogs.api.appDialogPublisher
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Action
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.Event
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStore.State
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStoreImpl

internal typealias SettingsStoreProcessor = StoreProcessor<State, Action, Event>

/**
 * feature/settings resolves its Store through the **Metro** path via `rememberMetroStoreProcessor`.
 * The Metro graph is built INSIDE the `rememberMetroStoreProcessor` factory lambda so it is created
 * at most once per retained [SettingsStoreImpl] (per `NavBackStackEntry`), binding the graph +
 * `@SingleIn(SettingsScope)` nodes to the Store's lifetime.
 *
 * The app-scoped dependencies are acquired as the composition of three narrow interfaces
 * ([StoreCoreDeps] + [NavigatorDeps] + [SettingsDeps] — the wide backup/platform/dataStore/db tail plus
 * both qualified dispatchers) via `context.appDeps<T>()` (the god-object split, mechanism A);
 * `appDialogPublisher` is read through the feature-api holder seam; the app `Context` is passed
 * directly. Both `appDialogPublisher` and `Context` are composition-sourced — NOT in [SettingsDeps].
 */
internal object SettingsFeature : Feature<SettingsStoreProcessor, Settings>() {

    @Suppress("UNCHECKED_CAST")
    @Composable
    override fun processor(): SettingsStoreProcessor {
        val context = LocalContext.current
        return rememberMetroStoreProcessor<SettingsStoreImpl> {
            // Mechanism A (the god-object split): spine four from StoreCoreDeps + NavigatorDeps; the wide
            // domain tail (backup slice + platform/dataStore/db + BOTH qualified dispatchers) from
            // SettingsDeps. appDialogPublisher (feature-api holder seam) and the app Context (LocalContext)
            // are composition-sourced — passed direct, NOT via appDeps.
            val coreDeps = context.appDeps<StoreCoreDeps>()
            val navDeps = context.appDeps<NavigatorDeps>()
            val deps = context.appDeps<SettingsDeps>()
            createGraphFactory<SettingsGraph.Factory>()
                .create(
                    navigator = navDeps.navigator,
                    platformInfoProvider = deps.platformInfoProvider,
                    commonDataStore = deps.commonDataStore,
                    backupAuth = deps.backupAuth,
                    backupStorage = deps.backupStorage,
                    snapshotExportRunner = deps.snapshotExportRunner,
                    databaseSnapshotProvider = deps.databaseSnapshotProvider,
                    restoreStateRepository = deps.restoreStateRepository,
                    backupPreferencesRepository = deps.backupPreferencesRepository,
                    autoBackupController = deps.autoBackupController,
                    appDialogPublisher = context.appDialogPublisher(),
                    tempFileProvider = deps.tempFileProvider,
                    storeDispatchers = coreDeps.storeDispatchers,
                    analyticsHolder = coreDeps.analyticsHolder,
                    loggerHolder = coreDeps.loggerHolder,
                    defaultDispatcher = deps.defaultDispatcher,
                    ioDispatcher = deps.ioDispatcher,
                    context = context.applicationContext,
                )
                .settingsStore
        } as SettingsStoreProcessor
    }
}
