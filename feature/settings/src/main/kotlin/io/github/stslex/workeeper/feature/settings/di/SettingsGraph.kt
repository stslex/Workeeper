// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import android.content.Context
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.platform.PlatformInfoProvider
import io.github.stslex.workeeper.core.core.platform.TempFileProvider
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.app_dialogs.api.publisher.AppDialogPublisher
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractor
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractorImpl
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractorImpl
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStoreImpl
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The single Metro dependency graph for feature/settings. Scoped to [SettingsScope].
 *
 * The 18 app-scoped deps are app-graph-owned bindings handed in as `@Provides` bound instances via
 * [Factory]. The three `@Binds` (SettingsInteractor, BackupInteractor, SettingsHandlerStore) live
 * here. [settingsStore] is the root.
 *
 * Binding specifics:
 * - `@DefaultDispatcher` + `@IODispatcher` factory params stay QUALIFIED → two distinct
 *   `(CoroutineDispatcher + qualifier)` binding keys, no collision.
 * - `context` is a PLAIN `Context` param: one `Context` per graph.
 */
@DependencyGraph(scope = SettingsScope::class)
internal interface SettingsGraph {

    /** Root accessor: the retained Store. Metro constructs [SettingsStoreImpl], wiring its deps. */
    val settingsStore: SettingsStoreImpl

    // Bridge-observability accessors (inert roots): expose the two qualified dispatchers and the
    // app Context as the graph resolves them, so the real graph is self-verifying — proving the
    // (type + qualifier) keys resolve distinctly and the bare Context is bound. Consumed by
    // SettingsGraphBridgeTest; no runtime cost unless read.
    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    val appContext: Context

    @Binds
    val SettingsInteractorImpl.bindSettingsInteractor: SettingsInteractor

    @Binds
    val BackupInteractorImpl.bindBackupInteractor: BackupInteractor

    @Binds
    val SettingsHandlerStoreImpl.bindHandlerStore: SettingsHandlerStore

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides navigator: Navigator,
            @Provides platformInfoProvider: PlatformInfoProvider,
            @Provides commonDataStore: CommonDataStore,
            @Provides backupAuth: BackupAuth,
            @Provides backupStorage: BackupStorage,
            @Provides snapshotExportRunner: SnapshotExportRunner,
            @Provides databaseSnapshotProvider: DatabaseSnapshotProvider,
            @Provides restoreStateRepository: RestoreStateRepository,
            @Provides backupPreferencesRepository: BackupPreferencesRepository,
            @Provides autoBackupController: AutoBackupController,
            @Provides appDialogPublisher: AppDialogPublisher,
            @Provides tempFileProvider: TempFileProvider,
            @Provides storeDispatchers: StoreDispatchers,
            @Provides analyticsHolder: AnalyticsHolder,
            @Provides loggerHolder: LoggerHolder,
            @Provides @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
            @Provides @IODispatcher ioDispatcher: CoroutineDispatcher,
            // PLAIN Context — unqualified; one Context per graph.
            @Provides context: Context,
        ): SettingsGraph
    }
}
