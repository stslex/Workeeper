// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.core.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * The four app `CoroutineDispatcher`s as a Metro provides-factory container, auto-aggregated by the
 * app graph. GUARD: object and funcs stay public — an internal container silently drops out.
 */
@BindingContainer
@ContributesTo(AppScope::class)
object DispatchersBindingContainer {

    @Provides
    @SingleIn(AppScope::class)
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @SingleIn(AppScope::class)
    @MainImmediateDispatcher
    fun provideMainImmediateDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @SingleIn(AppScope::class)
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @SingleIn(AppScope::class)
    @IODispatcher
    fun provideIODispatcher(): CoroutineDispatcher = Dispatchers.IO
}
