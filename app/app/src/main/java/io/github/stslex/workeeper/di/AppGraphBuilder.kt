// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.database.AppDatabase

/**
 * The SINGLE construction site for the app-scope [AppGraph]. Both real callers —
 * `BaseApplication.appGraph` (prod) and [AppGraphSourceModule]'s test fallback — delegate here, so the
 * `create(...)` argument list is threaded in exactly ONE place.
 *
 * App-Scope Collapse Step 5 (5a): the DB-cascade substrate collapsed to a single [AppDatabase] `create()`
 * root. The 9 Room DAOs + `DbTransitionRunner` now derive from it graph-internally
 * (`DbCascadeBindingContainer`); the 3 `AppDatabase`-derived interface bindings are `@ContributesBinding` on
 * their impls. Callers pull the graph's BOUND-INSTANCE [AppDatabase] + [ImageStorage] (never construct) so
 * tests that swap an in-memory `AppDatabase` / a `FakeImageStorage` via the create() bound-instance roots
 * still resolve. Cycle-free: every
 * derived binding reads `AppDatabase` (root) / a direct `Dispatchers.IO` — no `@IO`→`appGraph` back-edge.
 */
internal fun buildAppGraph(
    applicationContext: Context,
    appDatabase: AppDatabase,
    imageStorage: ImageStorage,
): AppGraph = createGraphFactory<AppGraph.Factory>().create(
    applicationContext = applicationContext,
    appDatabase = appDatabase,
    imageStorage = imageStorage,
)
