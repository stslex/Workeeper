// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection

/**
 * Hands SQLite the statistics its query planner needs, so it stops choosing the wrong table to
 * drive a join from.
 *
 * Without `sqlite_stat1` SQLite plans on guesswork, and the guess it made in production was to
 * drive the live-workout personal-record read from an index over a column with two distinct
 * values — walking every finished session the user had ever logged instead of the handful of
 * exercises the query asked about. The measured plans, timings and their build type are in
 * `documentation/feature-specs/v3-redesign-spec.md` §27, "A BAD QUERY PLAN CAN BE A MISSING FACT".
 *
 * **This changes no SQL and no result** — rows, order and every ranking rule are identical, only
 * the access path moves. That is what makes it a startup call rather than a rewrite of a query
 * whose ranking `PrRuleParityTest` covers.
 *
 * GUARD: **`ANALYZE`, not `PRAGMA optimize`.** The pragma is the usual advice and it reaches the
 * same plan, but only for tables the CALLING CONNECTION has already queried — at startup, before
 * that connection has read anything, it is a no-op. The mask bit that lifts the restriction
 * (`0x10`) needs SQLite 3.46, far past what `minSdk 28` can assume.
 *
 * GUARD: it holds the WRITER connection for its duration, so overlapping it with a screen's reads
 * is free only under WAL. Room's default journal mode is `AUTOMATIC`, which resolves to WAL except
 * on `isLowRamDevice` hardware, where it is `TRUNCATE` — which is why `BaseApplication` skips the
 * call there rather than this function assuming it. A caller that runs this alongside reads owes
 * that check; anything that pins the journal mode changes the arithmetic for both.
 *
 * Takes the database as a PARAMETER rather than being an extension on it: `:app:app` holds the
 * `AppDatabase` (it threads it into the graph) but does not have `room3` on its compile classpath,
 * and resolving an extension on a Room type there fails on the unreachable `RoomDatabase` supertype.
 * A plain function only needs the type to be nameable, which it already is.
 */
suspend fun refreshQueryPlannerStatistics(database: AppDatabase) {
    database.useWriterConnection { connection ->
        connection.executeSQL("ANALYZE")
    }
}
