// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The count `Migration7` reports, and the rule that a clean database reports nothing. The
 * end-to-end migration behaviour lives in the device suite (`AppDatabaseMigrationTest`); this
 * covers the number that leaves the process, because the field incidence of the duplicate defect
 * is only knowable if it is right.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class Migration7ReconciliationTest {

    private lateinit var connection: SQLiteConnection

    @BeforeEach
    fun setUp() {
        // The hand-off is a process-wide object; start every test from empty.
        ReconciledTargetsReport.drain()
        connection = AndroidSQLiteDriver().open(":memory:")
        // The v6 shape of the table: same columns, same NON-unique target index.
        connection.execSQL(
            """
            CREATE TABLE set_table (
                uuid TEXT NOT NULL,
                performed_exercise_uuid TEXT NOT NULL,
                position INTEGER NOT NULL,
                reps INTEGER NOT NULL,
                weight REAL,
                type TEXT NOT NULL,
                PRIMARY KEY(uuid)
            )
            """.trimIndent(),
        )
        connection.execSQL(
            "CREATE INDEX index_set_table_performed_exercise_uuid_position " +
                "ON set_table(performed_exercise_uuid, position)",
        )
    }

    @AfterEach
    fun tearDown() {
        connection.close()
    }

    @Test
    fun `a database with no duplicate reconciles nothing and reports nothing`() {
        seed(rowId = 1, uuid = "a", performed = "p1", position = 0)
        seed(rowId = 2, uuid = "b", performed = "p1", position = 1)

        val reconciled = connection.reconcileDuplicateTargets()

        assertEquals(0, reconciled)
        assertNull(reconciliationReport(reconciled))
        assertEquals(listOf("a" to 0L, "b" to 1L), positions())
    }

    @Test
    fun `the reported count is exactly the number of rows repositioned`() {
        // p1 loses two rows (one per contested position), p2 loses one; p3 is clean throughout.
        seed(rowId = 1, uuid = "a", performed = "p1", position = 0)
        seed(rowId = 2, uuid = "b", performed = "p1", position = 0)
        seed(rowId = 3, uuid = "c", performed = "p1", position = 1)
        seed(rowId = 4, uuid = "d", performed = "p1", position = 1)
        seed(rowId = 5, uuid = "e", performed = "p2", position = 7)
        seed(rowId = 6, uuid = "f", performed = "p2", position = 7)
        seed(rowId = 7, uuid = "g", performed = "p3", position = 0)

        val reconciled = connection.reconcileDuplicateTargets()

        assertEquals(3, reconciled)
        assertEquals(3, reconciliationReport(reconciled)?.reconciledRows)
        assertEquals(
            listOf(
                "a" to 0L,
                "b" to 2L,
                "c" to 1L,
                "d" to 3L,
                "e" to 7L,
                "f" to 8L,
                // An exercise that never had a duplicate is not read into the plan at all.
                "g" to 0L,
            ),
            positions(),
        )
    }

    /**
     * The count leaves the migration transaction exactly once. Room commits and only then opens,
     * so a second open — or a rolled-back attempt that never reached one — must find nothing.
     */
    @Test
    fun `the pending count is yielded once and a second open finds nothing`() {
        ReconciledTargetsReport.record(3)

        assertEquals(3, ReconciledTargetsReport.drain())
        assertEquals(0, ReconciledTargetsReport.drain())
        assertNull(reconciliationReport(ReconciledTargetsReport.drain()))
    }

    /**
     * A commit that fails leaves its count undrained; the retry that follows must report its own
     * result rather than adding to the abandoned one.
     */
    @Test
    fun `a retried migration replaces the count its rolled-back attempt left behind`() {
        ReconciledTargetsReport.record(3)

        ReconciledTargetsReport.record(2)

        assertEquals(2, ReconciledTargetsReport.drain())
    }

    private fun seed(rowId: Long, uuid: String, performed: String, position: Int) {
        connection.execSQL(
            "INSERT INTO set_table(rowid, uuid, performed_exercise_uuid, position, reps, " +
                "weight, type) VALUES ($rowId, '$uuid', '$performed', $position, 8, 100.0, 'WORK')",
        )
    }

    private fun positions(): List<Pair<String, Long>> {
        val rows = mutableListOf<Pair<String, Long>>()
        connection.prepare("SELECT uuid, position FROM set_table ORDER BY uuid")
            .use { statement ->
                while (statement.step()) {
                    rows += statement.getText(0) to statement.getLong(1)
                }
            }
        return rows
    }
}
