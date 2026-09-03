// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection

/**
 * Runs `ANALYZE` so SQLite's planner has `sqlite_stat1` and stops driving joins from the wrong
 * table. No SQL and no result change, only the access path. See v3-redesign-spec.md §27.
 *
 * GUARD: `ANALYZE`, not `PRAGMA optimize` — the pragma is a no-op on a connection that has not
 * yet read the tables. GUARD: it holds the WRITER connection, so a caller that overlaps it with
 * reads owes the `isLowRamDevice` check that keeps the journal mode on WAL.
 */
suspend fun refreshQueryPlannerStatistics(database: AppDatabase) {
    database.useWriterConnection { connection ->
        connection.executeSQL("ANALYZE")
    }
}
