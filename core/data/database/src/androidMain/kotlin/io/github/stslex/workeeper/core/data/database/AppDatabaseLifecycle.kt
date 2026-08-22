// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

/**
 * Closes [database] — TERMINALLY: Room 3's `close()` is a one-way `CloseBarrier` (measured on
 * device, `kmp-phase-5-startup-processor.md` §7.1); every later connection use on this object
 * throws `SQLITE_MISUSE "Connection pool is closed"`. The ONLY legitimate caller is the runtime
 * host's replacement transaction, which ends the generation this database belongs to and mints a
 * successor from [buildAppDatabase] (spec §8.4/§8.5).
 *
 * A plain function in THIS module for the same reason as [refreshQueryPlannerStatistics]:
 * `:app:app` is deliberately Room-free on its compile classpath — DB-touching operations live
 * beside the database and take it as a parameter. Idempotent (`close()` on a closed database is
 * a no-op), which the undo re-tap path relies on.
 */
fun closeAppDatabase(database: AppDatabase) {
    database.close()
}
