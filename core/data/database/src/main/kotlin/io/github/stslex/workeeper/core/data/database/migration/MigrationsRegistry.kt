// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import androidx.room.migration.Migration

/**
 * Live schema version of `AppDatabase`. The `@Database(version = APP_DATABASE_VERSION)`
 * annotation on `AppDatabase` reads from this constant so the registry test, the
 * Room compile-time schema export, and any runtime version probe agree on a single
 * source of truth.
 *
 * Bump in lock-step with adding a new `Migration(N, N + 1)` to [MIGRATIONS]; the
 * registry test fails any commit that bumps one without the other.
 */
const val APP_DATABASE_VERSION: Int = 6

/**
 * Single source of truth for every Room migration registered on the live database.
 *
 * `CoreDatabaseModule` spreads this into `Room.databaseBuilder.addMigrations(*MIGRATIONS)`,
 * pre-restore checks consult it via [hasMigrationPath], and the registry test in the
 * test source set introspects it to assert that every consecutive version pair from
 * [MIN_SUPPORTED_SCHEMA_VERSION] forward has a registered path.
 *
 * Append new entries here (and only here) when a schema bump lands. Never duplicate
 * the list inside the DI module or any other call-site — divergence between the
 * builder and this array is exactly the bug the registry test prevents.
 */
internal val MIGRATIONS: Array<Migration> = arrayOf(
    Migration6,
)

/**
 * Lowest schema version for which a migration is registered in [MIGRATIONS].
 * Versions 1-4 are not migratable because they predate the Play Store release —
 * destructive resets were acceptable during pre-production development. See
 * `documentation/tech-debt.md` → "pre-Play-Store schema history".
 *
 * Derived from [MIGRATIONS] at runtime; if a future PR adds Migration_1_2 / 2_3 /
 * 3_4 / 4_5 (e.g. to support migrating very old debug installs), this constant
 * updates automatically.
 */
internal val MIN_SUPPORTED_SCHEMA_VERSION: Int =
    MIGRATIONS.minOf { it.startVersion }
