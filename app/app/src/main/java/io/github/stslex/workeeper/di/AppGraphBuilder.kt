// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory

/**
 * The SINGLE construction site for the app-scope [AppGraph] (App-Scope Collapse — Phase B3, V.2
 * cleanup). Both real callers — `BaseApplication.appGraph` (prod) and [AppGraphSourceModule]'s test
 * fallback — delegate here, so the `create(...)` argument list is threaded in exactly ONE place. As the
 * bulk migration accumulates bridged `create()` inputs (DAOs/DbTransitionRunner later), a new parameter
 * is added here once instead of being kept in sync across two call sites.
 *
 * App-Scope Collapse Step 3 (PF commit 1): the `@DefaultDispatcher` / `@MainImmediateDispatcher` bridge
 * params were RETIRED — the dispatchers are now Metro-owned via `DispatchersBindingContainer`, so the
 * graph self-provides them and `create()` needs only `applicationContext`.
 */
internal fun buildAppGraph(
    applicationContext: Context,
): AppGraph = createGraphFactory<AppGraph.Factory>().create(
    applicationContext = applicationContext,
)
