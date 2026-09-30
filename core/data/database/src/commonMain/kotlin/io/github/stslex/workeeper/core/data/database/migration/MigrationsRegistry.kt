// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import androidx.room3.migration.Migration

/**
 * Live schema version of `AppDatabase`, read by its `@Database(version = …)`. Bump in lock-step
 * with a new `Migration(N, N + 1)` in [MIGRATIONS]; the registry test fails one without the other.
 */
const val APP_DATABASE_VERSION: Int = 7

/**
 * Single source of truth for every Room migration registered on the live database —
 * `buildAppDatabase` is the sole registration site. Append new entries here and only here.
 */
internal val MIGRATIONS: Array<Migration> = arrayOf(
    Migration6,
    Migration7,
)

/**
 * Lowest schema version with a registered migration, derived from [MIGRATIONS]. Versions 1-4
 * predate the Play Store release and are not migratable — see tech-debt.md.
 */
internal val MIN_SUPPORTED_SCHEMA_VERSION: Int =
    MIGRATIONS.minOf { it.startVersion }
