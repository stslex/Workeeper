// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

/**
 * Closes [database] TERMINALLY: Room 3's `close()` is one-way and every later use throws.
 * Idempotent, which the undo re-tap relies on. See the Phase-5 startup-processor spec.
 */
fun closeAppDatabase(database: AppDatabase) {
    database.close()
}
