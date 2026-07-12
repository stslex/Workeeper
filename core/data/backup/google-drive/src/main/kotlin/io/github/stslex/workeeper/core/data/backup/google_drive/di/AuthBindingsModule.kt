// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.backup.google_drive.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.data.backup.api.SnapshotExportRunner
import io.github.stslex.workeeper.core.data.backup.google_drive.SnapshotExportRunnerImpl
import javax.inject.Singleton

/**
 * Hilt bindings for the google-drive backup module. App-Scope Collapse Step 3 (PF.3) moved the entire gd
 * auth/network chain to Metro (`@ContributesBinding(AppScope)` on the impls + `@BindingContainer`s for the
 * GMS `AuthorizationClient` and ktor `HttpClient`). Only `bindSnapshotExportRunner` stays Hilt: its impl
 * (`SnapshotExportRunnerImpl`) depends on `DatabaseJsonExporter` → `AppDatabase` (the Room db-cascade,
 * deferred to Step 5), so it cannot migrate without touching that fence. Its Metro-owned deps (`BackupAuth`,
 * `SnapshotStorage`) resolve through the app/app adopt-back shims.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface AuthBindingsModule {

    @Binds
    @Singleton
    fun bindSnapshotExportRunner(impl: SnapshotExportRunnerImpl): SnapshotExportRunner
}
