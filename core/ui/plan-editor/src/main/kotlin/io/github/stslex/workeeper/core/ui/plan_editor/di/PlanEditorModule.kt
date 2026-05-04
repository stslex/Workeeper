// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.core.ui.plan_editor.domain.PlanEditorInteractorImpl

@Module
@InstallIn(ViewModelComponent::class)
internal interface PlanEditorModule {

    @Binds
    @ViewModelScoped
    fun bindInteractor(impl: PlanEditorInteractorImpl): PlanEditorInteractor

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: PlanEditorHandlerStoreImpl): PlanEditorHandlerStore
}
