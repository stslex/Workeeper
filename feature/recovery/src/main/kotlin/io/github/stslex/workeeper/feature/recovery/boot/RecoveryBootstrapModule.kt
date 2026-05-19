// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.recovery.boot

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.feature.recovery.RestoreDialogChoiceObserver

/**
 * Hilt binding for the cross-module [RecoveryBootstrap] marker. Binds the
 * internal `RestoreDialogChoiceObserver` singleton to the public marker so
 * `AppDialogObserverBootstrapEntryPoint.recoveryBootstrap()` resolves without
 * exposing the observer class across the module boundary.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class RecoveryBootstrapModule {

    @Binds
    internal abstract fun bindRecoveryBootstrap(
        impl: RestoreDialogChoiceObserver,
    ): RecoveryBootstrap
}
