// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.settings.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractor
import io.github.stslex.workeeper.feature.settings.domain.SettingsInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface SettingsModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: SettingsInteractorImpl): SettingsInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: SettingsHandlerStoreImpl): SettingsHandlerStore
}
