// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import io.github.stslex.workeeper.core.data.database.migration.MIGRATIONS

/**
 * Non-Hilt construction of the [AppDatabase] `create()` bound-instance root. App-Scope Collapse
 * Step 6 (P-DBROOT), STAGED add-only.
 *
 * This MUST live in `core:data:database`: [MIGRATIONS] is `internal` to this module (R4,
 * `MigrationsRegistry.kt`), so the `Room.databaseBuilder(...).addMigrations(*MIGRATIONS)` call cannot be
 * relocated to app/app. A plain top-level factory (mirroring app/app's `buildAppGraph`) — NOT a Metro
 * `@Provides`/`@ContributesBinding`: `AppDatabase` already enters the app graph as a `create(appDatabase =
 * ...)` bound instance, so a Metro binding here would DUPLICATE it and fail Metro's duplicate-binding
 * check. A plain function contributes to no graph, so there is zero dup-binding risk — it is authored,
 * compiles, and is not yet wired.
 *
 * **STAGED, not the live feed.** `CoreDatabaseModule.provideAppDatabase` (Hilt) is still the prod
 * construction, bridge-read into `create()` by `BaseApplication`. The atomic cut swaps that feed to call
 * this factory instead (and deletes the Hilt provider). The body is byte-identical to the Hilt one
 * (same builder, same `MIGRATIONS`, same no-`fallbackToDestructiveMigration` — migration failure routes
 * to the recovery flows, never a silent wipe).
 *
 * **Room-free (P-DBROOT invariant).** `Room.databaseBuilder(...).build()` returns the handle WITHOUT
 * opening the SQLite file — the `SupportSQLiteOpenHelper` opens lazily on the first
 * `openHelper.{writable,readable}Database` access. So constructing the DB here does not trigger a
 * migration; `RecoveryActivity`'s Room-free bootstrap safety (Phase-0.9) is preserved on this non-Hilt
 * path exactly as on the Hilt one.
 */
fun buildAppDatabase(context: Context): AppDatabase = Room
    .databaseBuilder(
        context,
        AppDatabase::class.java,
        AppDatabase.NAME,
    )
    // Room 3 requires an explicit driver; AndroidSQLiteDriver is the framework SQLite
    // implementation Room 2.8.4 used implicitly, so the on-disk format is unchanged.
    .setDriver(AndroidSQLiteDriver())
    .apply { MIGRATIONS.forEach { addMigrations(it) } }
    .build()
