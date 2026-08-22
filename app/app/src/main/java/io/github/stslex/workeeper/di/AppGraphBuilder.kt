// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.database.AppDatabase
import java.io.File

/**
 * The SINGLE construction site for the app-scope [AppGraph]. Both real callers —
 * `BaseApplication.appGraph` (prod) and `MetroTestRule` (the `:app:app` androidTest harness, which
 * installs a fresh per-test graph into `MetroTestGraphHolder`) — delegate here, so the `create(...)`
 * argument list is threaded in exactly ONE place.
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
    // Defaulted for the JVM identity tests, which build throwaway graphs whose scopes die with the
    // test process — a fresh uncancelled lifetime is exactly the pre-Phase-5 anonymous-scope
    // behavior. The two REAL callers both pass one explicitly: the AppRuntime threads the
    // generation lifetime; MetroTestRule passes a per-test lifetime it cancels in after().
    appScopeLifetime: AppScopeLifetime = AppScopeLifetime(),
    // Defaulted to the fail-fast stub for graphs built OUTSIDE a runtime host (identity tests,
    // the androidTest harness): the replacement transaction is runtime-owned, so a graph with no
    // runtime must fail LOUDLY on a swap attempt, never no-op past it.
    databaseReplacement: DatabaseReplacement = NoRuntimeDatabaseReplacement,
): AppGraph = createGraphFactory<AppGraph.Factory>().create(
    applicationContext = applicationContext,
    appDatabase = appDatabase,
    imageStorage = imageStorage,
    appScopeLifetime = appScopeLifetime,
    databaseReplacement = databaseReplacement,
)

/** The loud default for runtime-less graphs — see [buildAppGraph]'s parameter KDoc. */
private object NoRuntimeDatabaseReplacement : DatabaseReplacement {

    override suspend fun restoreFromSnapshot(
        source: File,
        beforeMutation: suspend () -> Unit,
    ): DatabaseReplacementResult =
        error("DatabaseReplacement requires a runtime host; this graph was built without one")

    override suspend fun rollbackToPreRestoreBackup(
        onCommitted: suspend () -> Unit,
    ): DatabaseReplacementResult =
        error("DatabaseReplacement requires a runtime host; this graph was built without one")
}
