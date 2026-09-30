// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.IODispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.backup.api.BackupAuth
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.RecoveryDiagnosticsExporter
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.api.restore.RestoreStateRepository
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.backup.worker.BackupWorkerDeps
import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.database.AppDatabase
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.ui.kit.utils.activityHolder.ActivityHolderProducer
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.feature.app_dialogs.api.observer.AppDialogObserver
import io.github.stslex.workeeper.feature.app_dialogs.impl.data.AppDialogRepository
import io.github.stslex.workeeper.feature.app_dialogs.impl.observer.AppDialogObserverImpl
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerGraph
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorGraph
import io.github.stslex.workeeper.feature.recovery.boot.RecoveryBootstrap
import io.github.stslex.workeeper.feature.recovery.di.RecoveryDeps
import io.github.stslex.workeeper.feature.recovery.domain.RestoreRecoveryCoordinator
import io.github.stslex.workeeper.feature.recovery.domain.StartupMigrationCoordinator
import io.github.stslex.workeeper.feature.wear_bridge.WearBridgeDeps
import io.github.stslex.workeeper.navigation.NavigatorEventBus
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The Metro app-scope dependency graph for one runtime generation. `BaseApplication` publishes
 * the current generation, and replacement creates a new graph through `create(...)`.
 */
@DependencyGraph(scope = AppScope::class)
internal interface AppGraph :
    RecoveryDeps,
    BackupWorkerDeps,
    WearBridgeDeps,
    AppRootDeps {

    val analyticsHolder: AnalyticsHolder

    val loggerHolder: LoggerHolder

    /**
     * Metro-owned [StoreDispatchers]. No reader: kept as the compile-time assertion that the
     * qualified dispatcher pair still resolves from this graph.
     */
    val storeDispatchers: StoreDispatchers

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @MainImmediateDispatcher
    val mainImmediateDispatcher: CoroutineDispatcher

    @IODispatcher
    val ioDispatcher: CoroutineDispatcher

    /**
     * Navigator subsystem: the one `NavigatorEventBus` contributes [Navigator] and is also exposed
     * as its concrete type. [navigator] has no reader; it asserts the binding still resolves.
     */
    val navigator: Navigator
    override val navigatorEventBus: NavigatorEventBus

    override val imageViewerGraphFactory: ImageViewerGraph.Factory

    override val planEditorGraphFactory: PlanEditorGraph.Factory

    val activityHolderProducer: ActivityHolderProducer

    override val autoBackupController: AutoBackupController
    override val backupNotificationHelper: BackupNotificationHelper

    override val backupPreferencesRepository: BackupPreferencesRepository

    override val commonDataStore: CommonDataStore

    /**
     * App-scoped singletons of feature/app-dialogs:impl. [appDialogObserver] has no reader; it is
     * the compile-time assertion that the contributed binding resolves to the same singleton.
     */
    val appDialogRepository: AppDialogRepository
    val appDialogObserverImpl: AppDialogObserverImpl
    val appDialogObserver: AppDialogObserver

    /**
     * The google-drive auth-chain bindings. [backupAuth] has no reader — it is the compile-time
     * assertion that the gd auth chain still resolves.
     */
    val backupAuth: BackupAuth
    override val backupStorage: BackupStorage

    override val snapshotExportRunner: SnapshotExportRunner

    override val recoveryDiagnosticsExporter: RecoveryDiagnosticsExporter

    /**
     * Exercise repositories are Metro-owned and taken as constructor deps by features; only
     * [sessionRepository] keeps an accessor, for the live-workout extension identity test.
     */
    val sessionRepository: SessionRepository

    val sessionConflictResolver: SessionConflictResolver

    val imageStorage: ImageStorage

    /** GUARD: Metro must hand this exact factory-bound generation lifetime to every Store. */
    val appScopeLifetime: AppScopeLifetime

    /** Recovery graph nodes. Resolving [recoveryBootstrap] eagerly arms its subscriber. */
    override val restoreRecoveryCoordinator: RestoreRecoveryCoordinator
    override val startupMigrationCoordinator: StartupMigrationCoordinator
    val recoveryBootstrap: RecoveryBootstrap

    /**
     * Metro-owned [RestoreStateRepository]. No production reader: it exists so that
     * `AppScopeDataStoreSingletonTest` reads `restore_state_prefs` through the real binding.
     */
    override val restoreStateRepository: RestoreStateRepository

    override val databaseSnapshotProvider: DatabaseSnapshotProvider

    @Provides
    @SingleIn(AppScope::class)
    fun provideAnalyticsHolder(): AnalyticsHolder = AnalyticsHolder()

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides applicationContext: Context,
            @Provides appDatabase: AppDatabase,
            @Provides imageStorage: ImageStorage,
            @Provides appScopeLifetime: AppScopeLifetime,
            @Provides databaseReplacement: DatabaseReplacement,
        ): AppGraph
    }
}
