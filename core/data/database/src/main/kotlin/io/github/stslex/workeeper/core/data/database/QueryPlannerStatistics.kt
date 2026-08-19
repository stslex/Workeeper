// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.executeSQL
import androidx.room3.useWriterConnection

/**
 * Hands SQLite the statistics its query planner needs, so it stops choosing the wrong table to
 * drive a join from.
 *
 * ## The defect this exists for, measured rather than reasoned
 *
 * Without `sqlite_stat1` SQLite plans on guesswork, and its guess is that any usable index is as
 * good as any other. The live-workout personal-record read is where that bites hardest. Its
 * predicates are a 13-element `exercise_uuid IN (...)` list and `session_table.state = 'FINISHED'`,
 * and the planner picked the second — an index over a column with **two distinct values**, i.e. half
 * the table. Profiled on a device seeded to a long-term shape (1451 sessions, 8713 performed rows,
 * 42 660 sets):
 *
 * ```
 * before                                          after
 * SEARCH sn USING index_session_table_state       SEARCH pe USING index_..._exercise_uuid
 *   -> every FINISHED session ever                  -> only the session's own exercises
 * SEARCH pe USING index_..._session_uuid_position SEARCH sn USING PRIMARY KEY
 * USE TEMP B-TREE FOR ORDER BY                    USE TEMP B-TREE FOR RIGHT PART OF ORDER BY
 * 15ms / 5ms                                      5ms / 3ms
 * ```
 *
 * The second line of that table is the one that keeps paying: with the driver ordered by
 * `exercise_uuid`, the leading `ORDER BY` term is satisfied by the index, so one global sort over
 * every candidate row becomes a small sort within each exercise.
 *
 * **This changes no SQL and no logic.** The rows returned, their order, and every ranking rule are
 * byte-identical — only the access path changes. That is the whole reason it is a startup call
 * rather than a rewrite of a query whose ranking is covered by `PrRuleParityTest`.
 *
 * ## Why ANALYZE, on every start, rather than `PRAGMA optimize`
 *
 * `PRAGMA optimize` is the usual advice and it reaches the same plan — but only for tables the
 * CALLING CONNECTION has already queried, and the mask bit that lifts that restriction
 * (`0x10`) needs SQLite 3.46, well past anything `minSdk 28` can assume. Called at startup, before
 * that connection has read anything, it is a no-op. `ANALYZE` has no such precondition.
 *
 * The cost that makes "every start" affordable was measured on the same seeded database:
 * **15ms**. It is run off the main thread and its result is durable, so a start that is killed
 * before it finishes simply leaves the previous statistics in place.
 *
 * Fresh statistics on every start is also the point rather than a side effect: the numbers go stale
 * as the user trains, and stale statistics are how the planner talked itself into scanning the
 * whole history in the first place.
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
