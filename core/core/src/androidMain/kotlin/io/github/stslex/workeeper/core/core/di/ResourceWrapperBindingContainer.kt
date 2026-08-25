// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.di

import android.content.Context
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.resources.AndroidResourceWrapper
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper

/**
 * Metro provides-factory container for `ResourceWrapper`, mirroring [DispatchersBindingContainer].
 * GUARD: object and func stay public — an internal container silently fails to aggregate.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object ResourceWrapperBindingContainer {

    @Provides
    @SingleIn(AppScope::class)
    fun provideResourceWrapper(
        context: Context,
    ): ResourceWrapper = AndroidResourceWrapper(context)
}
