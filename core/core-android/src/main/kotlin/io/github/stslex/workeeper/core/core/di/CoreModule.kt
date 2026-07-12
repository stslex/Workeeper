// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.stslex.workeeper.core.core.resources.AndroidResourceWrapper
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import javax.inject.Singleton

/**
 * Remaining Hilt bindings for core:core-android. App-Scope Collapse Step 3 (Phase PF commit 1) moved the
 * four CoroutineDispatcher `@Provides` out of here into the Metro [DispatchersBindingContainer]
 * (`@ContributesTo(AppScope)`) — they are now Metro-owned; still-Hilt consumers resolve them via the
 * qualified adopt-back shims in `AppGraphAdoptBackModule`. `ResourceWrapper` stays Hilt this pass
 * (a DEFER-C provides-factory migrated in PF.2).
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreModule {

    @Provides
    @Singleton
    fun provideResourceWrapper(
        @ApplicationContext context: Context,
    ): ResourceWrapper = AndroidResourceWrapper(context)
}
