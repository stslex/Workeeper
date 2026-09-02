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
        // GUARD: reconciliation runs FIRST, while the target index is still non-unique. The
        // CREATE UNIQUE INDEX below aborts the whole migration on any surviving duplicate, and
        // released 1.50.0 can produce them (non-transactional check-then-act in
        // `SetRepositoryImpl.upsert`). Moving the index above this call turns an upgrade into a
        // permanent crash loop on affected devices.
        connection.reconcileDuplicateTargets()
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
}

/**
 * Makes every `(performed_exercise_uuid, position)` target unique without losing a row.
 *
 * Per performed exercise that owns at least one contested position: the lowest-`rowid` row of each
 * position keeps it, and every other row is appended after that exercise's current maximum
 * position — in ascending `rowid` order, one step at a time, across all of its contested groups.
 * Only `position` is ever written; no row is deleted, merged, or otherwise altered, so the table's
 * row count is identical before and after.
 *
 * The accepted, deliberate consequence is that a set which was half of a duplicate pair surfaces
 * as an extra trailing set of its exercise, including in a session that is currently
 * `IN_PROGRESS`. That is the price of losing nothing.
 *
 * @return how many rows were repositioned; `0` when the database held no duplicate.
 */
internal fun SQLiteConnection.reconcileDuplicateTargets(): Int {
    val moves = planTargetReconciliation(readContestedTargetRows())
    applyTargetMoves(moves)
    return moves.size
}

/** One `set_table` row, reduced to what the reconciliation rule reads. */
private data class TargetRow(
    val rowId: Long,
    val performedExerciseUuid: String,
    val position: Long,
)

/** A single `position` rewrite, addressed by `rowid` so row identity cannot drift. */
private data class TargetMove(
    val rowId: Long,
    val position: Long,
)

/**
 * Every row of every performed exercise that owns a contested position — not only the contested
 * rows, because the tail counter starts from that exercise's true maximum position.
 *
 * The `ORDER BY` is load-bearing twice over: it makes one exercise's rows contiguous, and inside
 * an exercise it puts the lowest-`rowid` row of each position first.
 */
private fun SQLiteConnection.readContestedTargetRows(): List<TargetRow> {
    val rows = mutableListOf<TargetRow>()
    prepare(
        """
        SELECT rowid, performed_exercise_uuid, position
        FROM set_table
        WHERE performed_exercise_uuid IN (
            SELECT performed_exercise_uuid
            FROM set_table
            GROUP BY performed_exercise_uuid, position
            HAVING COUNT(*) > 1
        )
        ORDER BY performed_exercise_uuid, position, rowid
        """.trimIndent(),
    ).use { statement ->
        while (statement.step()) {
            rows += TargetRow(
                rowId = statement.getLong(0),
                performedExerciseUuid = statement.getText(1),
                position = statement.getLong(2),
            )
        }
    }
    return rows
}

/**
 * Pure, total function from the ordered read to the rewrites. It walks lists only — no hash or
 * set container is consulted anywhere — so byte-identical input files always plan identically.
 */
private fun planTargetReconciliation(rows: List<TargetRow>): List<TargetMove> =
    rows.performedExerciseRuns().flatMap(::planOnePerformedExercise)

/** Splits the `performed_exercise_uuid`-ordered read into one contiguous list per exercise. */
private fun List<TargetRow>.performedExerciseRuns(): List<List<TargetRow>> {
    val runs = mutableListOf<List<TargetRow>>()
    var start = 0
    while (start < size) {
        val uuid = this[start].performedExerciseUuid
        var end = start
        while (end < size && this[end].performedExerciseUuid == uuid) end++
        runs += subList(start, end).toList()
        start = end
    }
    return runs
}

/**
 * [rows] are one exercise's rows ordered by `(position, rowid)`, so a row is a loser exactly when
 * the row before it holds the same position. Losers are then re-sorted by `rowid` because the tail
 * counter runs across the exercise's groups, not within one of them.
 */
private fun planOnePerformedExercise(rows: List<TargetRow>): List<TargetMove> {
    val losers = rows.filterIndexed { index, row ->
        index > 0 && rows[index - 1].position == row.position
    }
    if (losers.isEmpty()) return emptyList()
    val tailStart = rows.maxOf(TargetRow::position)
    return losers
        .sortedBy(TargetRow::rowId)
        .mapIndexed { index, row -> TargetMove(row.rowId, tailStart + index + 1) }
}

private fun SQLiteConnection.applyTargetMoves(moves: List<TargetMove>) {
    if (moves.isEmpty()) return
    prepare("UPDATE set_table SET position = ? WHERE rowid = ?").use { statement ->
        moves.forEach { move ->
            statement.reset()
            statement.bindLong(1, move.position)
            statement.bindLong(2, move.rowId)
            statement.step()
        }
    }
}
