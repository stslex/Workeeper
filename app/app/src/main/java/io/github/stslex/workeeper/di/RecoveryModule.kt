// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.feature.app_dialogs.api.actions.AppDialogActions
import io.github.stslex.workeeper.recovery.AppDialogActionsImpl
import javax.inject.Singleton

/**
 * App-graph wiring for the restore-recovery surface. Binds the
 * cross-feature [AppDialogActions] producer interface to the implementation
 * that reaches across `RestoreRecoveryCoordinator`,
 * `RestoreStateRepository`, `RecoveryDiagnosticsExporter`, and
 * `AppDialogPublisher`.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface RecoveryModule {

    @Binds
    @Singleton
    fun bindAppDialogActions(impl: AppDialogActionsImpl): AppDialogActions
}
