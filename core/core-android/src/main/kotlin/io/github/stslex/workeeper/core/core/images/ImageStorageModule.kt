// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.images

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.di.IODispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageStorageModule {

    // App-Scope Collapse Step 6 (P-IMGROOT): construction delegates to the non-Hilt `buildImageStorage`
    // factory (staged in-module), so the single construction site is exercised in prod while Hilt still
    // OWNS the binding + bridge-feeds `create()`. Converted from `@Binds ImageStorageImpl` to a delegating
    // `@Provides` so the factory is the construction path. The atomic cut deletes this @Provides and calls
    // `buildImageStorage` directly at the graph seam; the androidTest `FakeImageStorage` swap stays a
    // `create()` bound-instance override (via TestInfraModule's @TestInstallIn(replaces=[ImageStorageModule]),
    // which replaces by module reference — object-vs-abstract-class is irrelevant), untouched by this change.
    @Provides
    @Singleton
    fun provideImageStorage(
        @ApplicationContext context: Context,
        @IODispatcher ioDispatcher: CoroutineDispatcher,
    ): ImageStorage = buildImageStorage(context, ioDispatcher)
}
