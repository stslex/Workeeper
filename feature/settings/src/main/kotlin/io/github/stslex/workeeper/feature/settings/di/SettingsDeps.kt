// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

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
import kotlinx.coroutines.CoroutineDispatcher

/**
 * feature/settings' domain tail for the god-object split (variant A, mechanism A). The WIDEST per-consumer
 * interface (12 accessors) — but still narrow in the sense that matters: it is read by **Settings alone**.
 * The god-object knew about all 15 readers; `SettingsDeps` knows about one. Width is not god-object-ness,
 * so this is NOT sub-split, and it is deliberately NOT shared with Recovery/Worker (those get their own
 * interfaces — type overlap on backup accessors is not a shared interface).
 *
 * Names ONLY the app-scope deps NOT covered by the two γ-spine interfaces (`StoreCoreDeps` {analytics,
 * logger, dispatchers} + `NavigatorDeps` {navigator}) — Settings' backup slice + platform/dataStore/db
 * accessors + the two qualified dispatchers. The backup slice spans FIVE owning modules
 * (`core:core`, `core:core-android`, `core:data:dataStore`, `core:data:backup:api`, `core:data:database`);
 * `feature/settings` already depends on every one, so this interface names them with no new edge and no
 * cycle.
 *
 * NOT here (composition-sourced, never from the app graph, stay direct `create(...)` args):
 * `appDialogPublisher` (feature-api holder seam) and the app `Context` (from `LocalContext`).
 *
 * TWO DISPATCHERS: Settings reads **`@DefaultDispatcher`** AND **`@IODispatcher`** (verified from
 * `SettingsGraph.Factory.create` — note this is a DIFFERENT pair than exercise's Default+MainImmediate).
 * Each carries its own qualifier verbatim — Metro matches by (type + qualifier); a mis-qualified accessor
 * would collide/mis-wire the pair silently (a wrong-but-present qualifier still compiles, since AppGraph
 * exposes every dispatcher qualifier distinctly).
 */
interface SettingsDeps {
    val platformInfoProvider: PlatformInfoProvider
    val commonDataStore: CommonDataStore
    val backupAuth: BackupAuth
    val backupStorage: BackupStorage
    val snapshotExportRunner: SnapshotExportRunner
    val databaseSnapshotProvider: DatabaseSnapshotProvider
    val restoreStateRepository: RestoreStateRepository
    val backupPreferencesRepository: BackupPreferencesRepository
    val autoBackupController: AutoBackupController
    val tempFileProvider: TempFileProvider

    @DefaultDispatcher
    val defaultDispatcher: CoroutineDispatcher

    @IODispatcher
    val ioDispatcher: CoroutineDispatcher
}
