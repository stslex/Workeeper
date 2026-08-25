// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.mvi.di

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.github.stslex.workeeper.core.core.di.AppScope
import io.github.stslex.workeeper.core.core.di.DefaultDispatcher
import io.github.stslex.workeeper.core.core.di.MainImmediateDispatcher
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Metro-owned `@SingleIn(AppScope)` dispatcher pair; the two `CoroutineDispatcher` deps are
 * distinguished only by their `@DefaultDispatcher` / `@MainImmediateDispatcher` qualifiers.
 */
@SingleIn(AppScope::class)
@Inject
data class StoreDispatchers(
    @DefaultDispatcher val defaultDispatcher: CoroutineDispatcher,
    @MainImmediateDispatcher val mainImmediateDispatcher: CoroutineDispatcher,
)
