// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.CoroutineDispatcher

/**
 * The SINGLE construction site for the app-scope [AppGraph] (App-Scope Collapse — Phase B3, V.2
 * cleanup). Both real callers — `BaseApplication.appGraph` (prod) and [AppGraphSourceModule]'s test
 * fallback — delegate here, so the `create(...)` argument list is threaded in exactly ONE place. As the
 * bulk migration accumulates bridged `create()` inputs (dispatchers now, DAOs/DbTransitionRunner later),
 * a new parameter is added here once instead of being kept in sync across two call sites.
 *
 * The bridged inputs are the still-Hilt-owned app-scoped bindings the graph needs at construction (each
 * retired from this signature when its owning module migrates to Metro). The callers pull them from Hilt
 * (an `EntryPointAccessors` bridge in prod, `@Provides` params in the test module) and hand them here.
 */
internal fun buildAppGraph(
    applicationContext: Context,
    defaultDispatcher: CoroutineDispatcher,
    mainImmediateDispatcher: CoroutineDispatcher,
): AppGraph = createGraphFactory<AppGraph.Factory>().create(
    applicationContext = applicationContext,
    defaultDispatcher = defaultDispatcher,
    mainImmediateDispatcher = mainImmediateDispatcher,
)
