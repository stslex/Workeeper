// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v5 → v6: adds `exercise_table.is_adhoc` and sweeps orphan ad-hoc training rows left by
 * older Track Now cancel flows. Both changes are non-destructive.
 */
private const val FROM_VERSION = 5
private const val TO_VERSION = 6

object Migration6 : Migration(FROM_VERSION, TO_VERSION) {

    override suspend fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE exercise_table ADD COLUMN is_adhoc INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS index_exercise_table_is_adhoc " +
                "ON exercise_table(is_adhoc)",
        )
        connection.execSQL(
            """
            DELETE FROM training_table
            WHERE is_adhoc = 1
              AND uuid NOT IN (SELECT training_uuid FROM session_table)
            """.trimIndent(),
        )
    }
}
