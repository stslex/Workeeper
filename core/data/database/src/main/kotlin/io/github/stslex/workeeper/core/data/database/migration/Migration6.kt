// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v5 → v6 — Quick start workout (v2.3) groundwork.
 *
 * Two changes, both non-destructive:
 *   1. `exercise_table` gains `is_adhoc INTEGER NOT NULL DEFAULT 0`. Inline-created
 *      exercises (Quick start picker, Track Now) carry `is_adhoc = 1` until the parent
 *      session is finished; library queries filter on `is_adhoc = 0` so they stay hidden
 *      from the picker / All Exercises until they graduate.
 *   2. Retroactive sweep of orphan ad-hoc training rows left over from prior Track Now
 *      cancel flows that deleted the session but not the parent training. Only ad-hoc
 *      rows with no surviving session reference are deleted; library trainings and
 *      ad-hoc rows still tied to a session are preserved.
 *
 * `training_exercise_table` rows attached to deleted training rows cascade via the
 * existing FK on `training_uuid`; no explicit cleanup is required there.
 */
private const val FROM_VERSION = 5
private const val TO_VERSION = 6

internal object Migration6 : Migration(FROM_VERSION, TO_VERSION) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE exercise_table ADD COLUMN is_adhoc INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_exercise_table_is_adhoc " +
                "ON exercise_table(is_adhoc)",
        )
        db.execSQL(
            """
            DELETE FROM training_table
            WHERE is_adhoc = 1
              AND uuid NOT IN (SELECT training_uuid FROM session_table)
            """.trimIndent(),
        )
    }
}
