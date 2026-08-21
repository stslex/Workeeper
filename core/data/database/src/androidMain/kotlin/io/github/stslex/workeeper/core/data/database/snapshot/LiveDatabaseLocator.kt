// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.snapshot

import java.io.File

/**
 * Resolves the path of the live Room database file (`<databases>/app.db` on Android).
 *
 * A focused single-purpose seam so callers that only need the file path — e.g. the
 * startup-migration pre-flight peeking `PRAGMA user_version` before Room opens the
 * database — depend on exactly that, without importing Android's `Context` and without
 * pulling in the whole [DatabaseSnapshotProvider] surface. Implemented by the same
 * `DatabaseSnapshotProviderImpl`, so the `getDatabasePath` resolution lives in one place.
 */
interface LiveDatabaseLocator {

    /** The live database file on disk. May not exist yet (fresh install). */
    fun liveDatabaseFile(): File
}
