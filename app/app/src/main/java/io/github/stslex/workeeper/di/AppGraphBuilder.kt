// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacement
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementEffects
import io.github.stslex.workeeper.core.data.backup.api.DatabaseReplacementResult
import io.github.stslex.workeeper.core.data.backup.api.restore.UndoRef
import io.github.stslex.workeeper.core.data.database.AppDatabase
import java.io.File

/**
 * The single construction site for the app-scope [AppGraph]: the `create(...)` argument list is
 * threaded in exactly one place. DAOs and `DbTransitionRunner` derive from the [AppDatabase] root.
 */
internal fun buildAppGraph(
    applicationContext: Context,
    appDatabase: AppDatabase,
    imageStorage: ImageStorage,
    // Defaulted for throwaway test graphs; hosts pass a lifetime they own and cancel.
    appScopeLifetime: AppScopeLifetime = AppScopeLifetime(),
    // Defaulted to the fail-fast stub: the transaction is runtime-owned, so a graph without a
    // runtime must fail loudly on a swap rather than no-op past it.
    databaseReplacement: DatabaseReplacement = NoRuntimeDatabaseReplacement,
): AppGraph = createGraphFactory<AppGraph.Factory>().create(
    applicationContext = applicationContext,
    appDatabase = appDatabase,
    imageStorage = imageStorage,
    appScopeLifetime = appScopeLifetime,
    databaseReplacement = databaseReplacement,
)

/** The loud default for runtime-less graphs. */
private object NoRuntimeDatabaseReplacement : DatabaseReplacement {

    override suspend fun restoreFromSnapshot(
        source: File,
        effects: DatabaseReplacementEffects,
    ): DatabaseReplacementResult =
        error("DatabaseReplacement requires a runtime host; this graph was built without one")

    override suspend fun rollbackFromUndo(
        sourceRef: UndoRef,
        effects: DatabaseReplacementEffects,
    ): DatabaseReplacementResult =
        error("DatabaseReplacement requires a runtime host; this graph was built without one")
}
