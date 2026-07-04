// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.platform

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlatformModule {

    @Binds
    @Singleton
    abstract fun bindPlatformInfoProvider(
        impl: AndroidPlatformInfoProvider,
    ): PlatformInfoProvider

    @Binds
    @Singleton
    abstract fun bindTempFileProvider(
        impl: AndroidTempFileProvider,
    ): TempFileProvider
}
