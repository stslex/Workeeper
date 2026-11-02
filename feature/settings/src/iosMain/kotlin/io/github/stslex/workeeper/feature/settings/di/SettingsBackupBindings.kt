// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.github.stslex.workeeper.feature.settings.mvi.handler.SettingsBackupHandler
import io.github.stslex.workeeper.feature.settings.mvi.handler.UnavailableSettingsBackupHandler

@ContributesTo(SettingsScope::class)
interface SettingsBackupBindings {
    companion object {
        @Provides
        internal fun provideBackupHandler(): SettingsBackupHandler = UnavailableSettingsBackupHandler
    }
}
