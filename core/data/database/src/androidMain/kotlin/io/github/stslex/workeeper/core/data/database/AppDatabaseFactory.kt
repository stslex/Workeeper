// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.github.stslex.workeeper.core.data.database.migration.MIGRATIONS
import io.github.stslex.workeeper.core.data.database.migration.reportReconciledTargets

/**
 * The only place the app builds its Room database; `BaseApplication` threads the result into the
 * graph as the `appDatabase` bound instance. It must live here because [MIGRATIONS] is internal to
 * this module. GUARD: never add `fallbackToDestructiveMigration` — a missing or failing migration
 * must stay a hard failure that routes to the Scenario 2 recovery flow, never a silent wipe.
 */
fun buildAppDatabase(context: Context): AppDatabase = Room
    .databaseBuilder<AppDatabase>(
        context = context,
        name = AppDatabase.NAME,
    )
    // Room 3 requires an explicit driver; BundledSQLiteDriver ships one SQLite build to every
    // device instead of the per-OEM system one, at frozen main-db and WAL file formats.
    .setDriver(BundledSQLiteDriver())
    .apply { MIGRATIONS.forEach { addMigrations(it) } }
    .addCallback(
        object : RoomDatabase.Callback() {
            /**
             * Room runs migrations inside `BEGIN EXCLUSIVE TRANSACTION` and reaches this only
             * after the commit, so a reconciliation count reported here belongs to an upgrade
             * that actually landed rather than to an attempt that may yet roll back.
             */
            override suspend fun onOpen(connection: SQLiteConnection) {
                reportReconciledTargets()
            }
        },
    )
    .build()
