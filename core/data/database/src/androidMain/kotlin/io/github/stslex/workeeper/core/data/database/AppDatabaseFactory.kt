// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.stslex.workeeper.core.data.database.migration.MIGRATIONS

/**
 * Production construction of the [AppDatabase] `create()` bound-instance root, Hilt-free (App-Scope
 * Collapse Step 6, P-DBROOT). This is the ONLY place the app builds its Room database:
 * `BaseApplication.appGraph` calls `buildAppDatabase(applicationContext)` and threads the result into
 * `buildAppGraph(...)` as the `appDatabase` root. (androidTest installs an in-memory `AppDatabase` at
 * that same `create()` root via `MetroTestRule`, so the test path never reaches this function.)
 *
 * This MUST live in `core:data:database`: [MIGRATIONS] is `internal` to this module (R4,
 * `MigrationsRegistry.kt`), so the `Room.databaseBuilder(...)` + `addMigrations(...)` chain cannot be
 * relocated to app/app. A plain top-level factory (mirroring app/app's `buildAppGraph`) — NOT a Metro
 * `@Provides`/`@ContributesBinding`: `AppDatabase` enters the app graph as a `create(appDatabase = ...)`
 * bound instance, so a Metro binding here would DUPLICATE it and fail Metro's duplicate-binding check.
 * A plain function contributes to no graph, so there is zero dup-binding risk.
 *
 * **The absent `fallbackToDestructiveMigration` is deliberate — never add it here.** Every entry of
 * [MIGRATIONS] is registered on this builder and on no other; this chain IS the app's migration policy.
 * Adding `fallbackToDestructiveMigration*()` to make an in-progress schema change compile would DROP and
 * recreate every user's workout database on the next version mismatch. A missing or failing migration
 * must stay a hard failure so it routes to the Scenario 2 startup-migration recovery flow (see
 * `BaseApplication.handleRecoveryPreflightChain`), never a silent wipe.
 *
 * **Room-free (P-DBROOT invariant).** `Room.databaseBuilder(...).build()` returns the handle WITHOUT
 * opening the SQLite file — Room 3 opens the `SQLiteConnection` lazily on the first DAO / pragma
 * access (via the configured `SQLiteDriver`). So constructing the DB here does not trigger a
 * migration; `RecoveryActivity`'s Room-free bootstrap safety (Phase-0.9) holds even though building
 * `AppGraph` calls this factory eagerly.
 */
fun buildAppDatabase(context: Context): AppDatabase = Room
    .databaseBuilder<AppDatabase>(
        context = context,
        name = AppDatabase.NAME,
    )
    // Room 3 requires an explicit driver. BundledSQLiteDriver ships one SQLite build (3.50.x)
    // to every device instead of the per-OEM, per-API-level system one — the main-db and WAL
    // file formats are frozen, so existing installations open unchanged. The snapshot/ package
    // still opens the same file through framework SQLite (android.database.sqlite) for its
    // pre-migration peek and checkpoint; that cross-library interop is deliberate and its
    // paths are exercised by the recovery flow, not by this builder.
    .setDriver(BundledSQLiteDriver())
    .apply { MIGRATIONS.forEach { addMigrations(it) } }
    .build()
