// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractor
import io.github.stslex.workeeper.feature.settings.domain.BackupInteractorImpl
import io.github.stslex.workeeper.feature.settings.mvi.handler.BackupClickHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsBackupHandler

@ContributesTo(SettingsScope::class)
interface SettingsBackupBindings {

    @Binds
    val BackupInteractorImpl.bindBackupInteractor: BackupInteractor

    companion object {

        @Provides
        internal fun provideBackupHandler(handler: BackupClickHandler): SettingsBackupHandler = handler
    }
}
