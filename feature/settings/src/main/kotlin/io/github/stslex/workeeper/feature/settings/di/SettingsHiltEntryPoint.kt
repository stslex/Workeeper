// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
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
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Hilt→Metro bridge for feature/settings (KMP C.1). Pulls settings' 18 app-scoped `@Singleton`
 * dependencies out of the Hilt `SingletonComponent` so they can be handed to [SettingsGraph] as
 * `@Provides` bound instances. Consumed via `EntryPointAccessors.fromApplication(...)` in
 * `SettingsFeature.processor()`. Aggregates into the app Dagger graph automatically (library
 * `@InstallIn(SingletonComponent)`) — no app-module change.
 *
 * Qualifier boundary:
 * - `@DefaultDispatcher` / `@IODispatcher` stay QUALIFIED across the bridge (Metro reads them via
 *   `includeJavax`), so the two same-typed `CoroutineDispatcher`s resolve distinctly.
 * - `@ApplicationContext` stays on the Hilt side HERE (Hilt resolves the app `Context`); the graph
 *   binds it UNqualified downstream (one `Context` per graph → no ambiguity, singularity verified
 *   in M2 Part A). Metro never sees `@ApplicationContext`.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SettingsHiltEntryPoint {

    fun navigator(): Navigator

    fun platformInfoProvider(): PlatformInfoProvider

    fun commonDataStore(): CommonDataStore

    fun backupAuth(): BackupAuth

    fun backupStorage(): BackupStorage

    fun snapshotExportRunner(): SnapshotExportRunner

    fun databaseSnapshotProvider(): DatabaseSnapshotProvider

    fun restoreStateRepository(): RestoreStateRepository

    fun backupPreferencesRepository(): BackupPreferencesRepository

    fun autoBackupController(): AutoBackupController

    fun appDialogPublisher(): AppDialogPublisher

    fun tempFileProvider(): TempFileProvider

    fun storeDispatchers(): StoreDispatchers

    fun analyticsHolder(): AnalyticsHolder

    fun loggerHolder(): LoggerHolder

    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher

    @IODispatcher
    fun ioDispatcher(): CoroutineDispatcher

    /**
     * The app `Context`. The `@ApplicationContext` Hilt qualifier stays HERE (Hilt resolves it);
     * past this point the graph binds a plain `Context` (point 1 of the Context mechanic).
     */
    @ApplicationContext
    fun applicationContext(): Context
}
