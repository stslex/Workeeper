// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.migration

import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MigrationGraphTest {

    @Test
    fun `empty migrations and from equals to returns true`() {
        assertTrue(hasMigrationPath(migrations = emptyArray(), from = 3, to = 3))
    }

    @Test
    fun `empty migrations and from differs from to returns false`() {
        assertFalse(hasMigrationPath(migrations = emptyArray(), from = 1, to = 2))
    }

    @Test
    fun `linear chain reaches every forward version`() {
        val migrations = arrayOf(
            stubMigration(1, 2),
            stubMigration(2, 3),
            stubMigration(3, 4),
        )
        assertTrue(hasMigrationPath(migrations, from = 1, to = 4))
        assertTrue(hasMigrationPath(migrations, from = 2, to = 4))
        assertTrue(hasMigrationPath(migrations, from = 3, to = 4))
    }

    @Test
    fun `missing intermediate edge breaks the path`() {
        // Edges 1 to 2 and 3 to 4 exist but (2, 3) is missing — no chain from 1 to 4.
        val migrations = arrayOf(
            stubMigration(1, 2),
            stubMigration(3, 4),
        )
        assertFalse(hasMigrationPath(migrations, from = 1, to = 4))
    }

    @Test
    fun `downgrade direction always returns false`() {
        val migrations = arrayOf(
            stubMigration(1, 2),
            stubMigration(2, 3),
        )
        assertFalse(hasMigrationPath(migrations, from = 3, to = 1))
        assertFalse(hasMigrationPath(migrations, from = 2, to = 1))
    }

    @Test
    fun `direct edge skipping intermediate is accepted`() {
        // 1 to 3 exists directly; the 2 to 3 edge is irrelevant for the 1 to 3 query.
        val migrations = arrayOf(
            stubMigration(1, 3),
            stubMigration(2, 3),
        )
        assertTrue(hasMigrationPath(migrations, from = 1, to = 3))
    }

    @Test
    fun `cycle does not cause infinite loop`() {
        // Defensive — Room migrations should never form a cycle, but the BFS must
        // still terminate if a developer mis-registers one.
        val migrations = arrayOf(
            stubMigration(1, 2),
            stubMigration(2, 1),
            stubMigration(2, 3),
        )
        assertTrue(hasMigrationPath(migrations, from = 1, to = 3))
    }

    @Test
    fun `target below reachable version does not over-explore`() {
        // 1 to 5 exists; we ask for 1 to 3. The BFS must not follow the 1 to 5
        // edge (neighbor > to) and must still report unreachable when no shorter
        // path exists.
        val migrations = arrayOf(stubMigration(1, 5))
        assertFalse(hasMigrationPath(migrations, from = 1, to = 3))
    }

    private fun stubMigration(start: Int, end: Int): Migration =
        object : Migration(start, end) {
            override fun migrate(connection: SQLiteConnection) = Unit
        }
}
