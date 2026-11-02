// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteConnection
import io.github.stslex.workeeper.core.data.database.migration.MIGRATIONS
import io.github.stslex.workeeper.core.data.database.migration.reportReconciledTargets

internal fun buildAppDatabase(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase = builder
    // A missing migration must fail closed; never add a destructive fallback.
    .apply { MIGRATIONS.forEach { addMigrations(it) } }
    .addCallback(
        object : RoomDatabase.Callback() {
            override suspend fun onOpen(connection: SQLiteConnection) {
                // Report only after Room commits the migration; see Phase 7.20A's production contract.
                reportReconciledTargets()
            }
        },
    )
    .build()
