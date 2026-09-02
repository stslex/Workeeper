// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.stslex.workeeper.core.data.database.wear.installWearSyncTriggers
import kotlin.uuid.Uuid

/** v6 -> v7: durable Wear epoch/version/lease/receipt state plus target-row uniqueness. */
private const val FROM_VERSION = 6
private const val TO_VERSION = 7

object Migration7 : Migration(FROM_VERSION, TO_VERSION) {

    override suspend fun migrate(connection: SQLiteConnection) {
        failOnDuplicateTargets(connection)
        connection.execSQL(
            "ALTER TABLE session_table ADD COLUMN wear_revision INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL(
            "ALTER TABLE session_table ADD COLUMN wear_lease_generation INTEGER NOT NULL DEFAULT 0",
        )
        connection.execSQL(
            "ALTER TABLE session_table ADD COLUMN wear_receipt_command_id TEXT",
        )
        connection.execSQL(
            "ALTER TABLE session_table ADD COLUMN wear_receipt_attempt_fingerprint BLOB",
        )
        connection.execSQL(
            "ALTER TABLE session_table ADD COLUMN wear_receipt_database_epoch TEXT",
        )
        connection.execSQL(
            "ALTER TABLE session_table ADD COLUMN wear_receipt_revision INTEGER",
        )
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS wear_database_metadata (
                singleton_id INTEGER NOT NULL,
                database_epoch TEXT NOT NULL,
                PRIMARY KEY(singleton_id)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "INSERT INTO wear_database_metadata(singleton_id, database_epoch) " +
                "VALUES (0, '${Uuid.random()}')",
        )
        connection.execSQL("DROP INDEX IF EXISTS index_set_table_performed_exercise_uuid_position")
        connection.execSQL(
            "CREATE UNIQUE INDEX index_set_table_performed_exercise_uuid_position " +
                "ON set_table(performed_exercise_uuid, position)",
        )
        connection.installWearSyncTriggers()
    }

    private fun failOnDuplicateTargets(connection: SQLiteConnection) {
        connection.prepare(
            """
            SELECT performed_exercise_uuid, position
            FROM set_table
            GROUP BY performed_exercise_uuid, position
            HAVING COUNT(*) > 1
            LIMIT 1
            """.trimIndent(),
        ).use { statement ->
            check(!statement.step()) {
                "Wear Phase 1 cannot reconcile duplicate set targets without owner policy: " +
                    "performedExerciseUuid=${statement.getText(0)}, position=${statement.getLong(1)}"
            }
        }
    }
}
