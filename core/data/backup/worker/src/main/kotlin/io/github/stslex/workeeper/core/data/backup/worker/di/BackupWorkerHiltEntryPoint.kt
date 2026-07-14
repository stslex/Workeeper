// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.worker.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.backup.api.BackupStorage
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.api.notification.BackupNotificationHelper
import io.github.stslex.workeeper.core.data.backup.api.scheduling.AutoBackupController
import io.github.stslex.workeeper.core.data.backup.api.scheduling.BackupPreferencesRepository
import io.github.stslex.workeeper.core.data.database.snapshot.DatabaseSnapshotProvider

/**
 * Hilt→Metro bridge for [BackupWorker][io.github.stslex.workeeper.core.data.backup.worker.BackupWorker]
 * (App-Scope Collapse Step 2 — WorkManager Metro-WorkerFactory standup). Pulls the worker's six
 * app-scoped `@Singleton` dependencies out of the Hilt `SingletonComponent` so a plain
 * [MetroWorkerFactory] can hand them to `BackupWorker`'s constructor — mirroring the batch-bridge
 * pattern (Metro-reads-Hilt) established by `SettingsHiltEntryPoint`.
 *
 * Why a bridge and not adopt-back: `BackupWorker` is FRAMEWORK-constructed (by WorkManager via a
 * `WorkerFactory`), never graph-constructed, so the M3 assisted-graph `storeFactory` shape does not
 * apply. Design B (App-Scope Collapse Step 2A): the factory is a plain androidx `WorkerFactory` that
 * bridge-reads these six deps and constructs `BackupWorker` directly. Metro never processes
 * `BackupWorker`'s constructor; its Dagger `@AssistedInject` + `@HiltWorker` stay untouched.
 *
 * TRANSIENT SCAFFOLDING: this entry point is dual-path standup — it lives ALONGSIDE Hilt's
 * `HiltWorkerFactory`, which still owns WorkManager (Configuration.Provider unchanged) until the
 * Step 6 atomic cut flips Configuration.Provider to [MetroWorkerFactory] and drops `@HiltWorker`.
 * At that flip this bridge retires with the other `*HiltEntryPoint`s.
 *
 * Qualifier note: none of the six deps is a same-typed collider, so each accessor is bare (no
 * qualifier survives). `DatabaseSnapshotProvider` is already exposed this identical way in
 * `SettingsHiltEntryPoint`; it stays Hilt-owned here (its migration is App-Scope Collapse Step 5).
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BackupWorkerHiltEntryPoint {

    fun backupStorage(): BackupStorage

    fun databaseSnapshotProvider(): DatabaseSnapshotProvider

    fun backupPreferencesRepository(): BackupPreferencesRepository

    fun autoBackupController(): AutoBackupController

    fun backupNotificationHelper(): BackupNotificationHelper

    fun snapshotExportRunner(): SnapshotExportRunner
}
