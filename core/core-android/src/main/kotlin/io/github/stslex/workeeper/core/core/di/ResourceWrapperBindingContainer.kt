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
 * `ResourceWrapper` lives in a Metro provides-factory container — the same mechanic as
 * [DispatchersBindingContainer].
 *
 * `@BindingContainer @ContributesTo(AppScope)` makes it Metro-owned; the app graph auto-aggregates it
 * cross-module. The one factory dep is the app `Context`, resolved from the graph's
 * `create(applicationContext)` bound instance (it constructs the Android impl, so this lives in
 * core:core-android, never the KMP `commonMain` that compiles to iOS). PUBLIC container + func — an
 * `internal` container silently fails to aggregate cross-module (guarded by `ContributesToScopeRule`).
 *
 * `ResourceWrapper` is public in core:core `commonMain`, so no visibility widening is needed.
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
