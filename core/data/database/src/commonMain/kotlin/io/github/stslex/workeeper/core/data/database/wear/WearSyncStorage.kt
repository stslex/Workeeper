// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.wear

import androidx.room3.PooledConnection
import androidx.room3.executeSQL
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import io.github.stslex.workeeper.core.data.database.AppDatabase
import kotlin.uuid.Uuid

/**
 * Installs new-database trigger state and optionally rotates authority after file replacement.
 * Callers invoke this before any graph-owned listener is admitted.
 */
suspend fun prepareWearSyncStorage(
    database: AppDatabase,
    rotateDatabaseEpoch: Boolean,
): String = database.useWriterConnection { transactor ->
    transactor.immediateTransaction {
        installWearSyncTriggers()
        val freshEpoch = Uuid.random().toString()
        executeSQL(
            "INSERT OR IGNORE INTO wear_database_metadata(singleton_id, database_epoch) " +
                "VALUES (0, '$freshEpoch')",
        )
        if (rotateDatabaseEpoch) {
            executeSQL(
                "UPDATE wear_database_metadata SET database_epoch = '$freshEpoch' " +
                    "WHERE singleton_id = 0",
            )
            executeSQL(
                """
                UPDATE session_table
                SET wear_receipt_command_id = NULL,
                    wear_receipt_attempt_fingerprint = NULL,
                    wear_receipt_database_epoch = NULL,
                    wear_receipt_revision = NULL
                """.trimIndent(),
            )
        }
        val epoch = usePrepared(
            "SELECT database_epoch FROM wear_database_metadata WHERE singleton_id = 0",
        ) { statement ->
            check(statement.step()) { "Wear database epoch was not initialized" }
            statement.getText(0)
        }
        check(runCatching { Uuid.parse(epoch) }.isSuccess) { "Wear database epoch is malformed" }
        epoch
    }
}

/** Trigger-backed invalidation keeps every writer atomic with its durable Wear version. */
internal fun SQLiteConnection.installWearSyncTriggers() {
    WEAR_SYNC_TRIGGERS.forEach(::execSQL)
}

private suspend fun PooledConnection.installWearSyncTriggers() {
    WEAR_SYNC_TRIGGERS.forEach { executeSQL(it) }
}

@Suppress("LongMethod")
private val WEAR_SYNC_TRIGGERS: List<String> = listOf(
    """
    CREATE TRIGGER IF NOT EXISTS wear_set_insert_revision
    AFTER INSERT ON set_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS'
          AND uuid = (
              SELECT session_uuid FROM performed_exercise_table
              WHERE uuid = NEW.performed_exercise_uuid
          );
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_set_update_revision
    AFTER UPDATE OF performed_exercise_uuid, position, reps, weight, type ON set_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS'
          AND uuid IN (
              SELECT session_uuid FROM performed_exercise_table
              WHERE uuid IN (OLD.performed_exercise_uuid, NEW.performed_exercise_uuid)
          );
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_set_delete_revision
    AFTER DELETE ON set_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS'
          AND uuid = (
              SELECT session_uuid FROM performed_exercise_table
              WHERE uuid = OLD.performed_exercise_uuid
          );
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_performed_insert_revision
    AFTER INSERT ON performed_exercise_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS' AND uuid = NEW.session_uuid;
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_performed_update_revision
    AFTER UPDATE OF session_uuid, exercise_uuid, position, skipped ON performed_exercise_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS' AND uuid IN (OLD.session_uuid, NEW.session_uuid);
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_performed_delete_revision
    AFTER DELETE ON performed_exercise_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS' AND uuid = OLD.session_uuid;
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_session_update_revision
    AFTER UPDATE OF training_uuid, state, started_at, finished_at ON session_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE uuid = NEW.uuid;
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_training_update_revision
    AFTER UPDATE OF name, is_adhoc ON training_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS' AND training_uuid = NEW.uuid;
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_training_exercise_insert_revision
    AFTER INSERT ON training_exercise_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS'
          AND training_uuid = NEW.training_uuid
          AND EXISTS (
              SELECT 1 FROM performed_exercise_table pe
              WHERE pe.session_uuid = session_table.uuid
                AND pe.exercise_uuid = NEW.exercise_uuid
          );
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_training_exercise_update_revision
    AFTER UPDATE OF training_uuid, exercise_uuid, position, plan_sets ON training_exercise_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS'
          AND (
              (training_uuid = OLD.training_uuid AND EXISTS (
                  SELECT 1 FROM performed_exercise_table pe
                  WHERE pe.session_uuid = session_table.uuid
                    AND pe.exercise_uuid = OLD.exercise_uuid
              ))
              OR
              (training_uuid = NEW.training_uuid AND EXISTS (
                  SELECT 1 FROM performed_exercise_table pe
                  WHERE pe.session_uuid = session_table.uuid
                    AND pe.exercise_uuid = NEW.exercise_uuid
              ))
          );
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_training_exercise_delete_revision
    AFTER DELETE ON training_exercise_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS'
          AND training_uuid = OLD.training_uuid
          AND EXISTS (
              SELECT 1 FROM performed_exercise_table pe
              WHERE pe.session_uuid = session_table.uuid
                AND pe.exercise_uuid = OLD.exercise_uuid
          );
    END
    """.trimIndent(),
    """
    CREATE TRIGGER IF NOT EXISTS wear_exercise_update_revision
    AFTER UPDATE OF name, type, last_adhoc_sets ON exercise_table
    BEGIN
        UPDATE session_table
        SET wear_revision = wear_revision + 1,
            wear_receipt_command_id = NULL,
            wear_receipt_attempt_fingerprint = NULL,
            wear_receipt_database_epoch = NULL,
            wear_receipt_revision = NULL
        WHERE state = 'IN_PROGRESS'
          AND EXISTS (
              SELECT 1 FROM performed_exercise_table pe
              WHERE pe.session_uuid = session_table.uuid
                AND pe.exercise_uuid = NEW.uuid
          );
    END
    """.trimIndent(),
)
