// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
internal interface ImageViewerModule {

    @Binds
    @ViewModelScoped
    fun bindHandlerStore(impl: ImageViewerHandlerStoreImpl): ImageViewerHandlerStore
}
