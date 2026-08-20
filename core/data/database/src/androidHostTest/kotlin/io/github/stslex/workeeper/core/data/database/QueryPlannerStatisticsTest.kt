// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.useReaderConnection
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The gate for [refreshQueryPlannerStatistics], and it exists because the failure mode is silence.
 *
 * The call it wraps is one SQL word. Misspell it, drop it, or call it on a connection that cannot
 * write, and nothing throws at the call site the app uses — `warmQueryPlanner` catches, by design,
 * so that a corrupt database cannot take the process down at launch. What is left is an app that
 * quietly keeps the bad query plan it was meant to fix, on every device, forever. So the assertion
 * is not that the function returns: it is that `sqlite_stat1` exists AFTERWARDS and did not exist
 * BEFORE.
 *
 * What it deliberately does NOT assert is the resulting query plan. `EXPLAIN QUERY PLAN` output is
 * a string SQLite is free to reword, the plan depends on data volume this fixture has no business
 * synthesising, and the plan that matters was measured on a device with a long-term history — that
 * measurement lives in the function's KDoc and in §27, where a number belongs, not in an assertion
 * that would pass on an empty table.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class QueryPlannerStatisticsTest : BaseDatabaseTest() {

    @BeforeEach
    fun setup() {
        initDb()
    }

    @AfterEach
    fun tearDown() {
        clearDb()
    }

    @Test
    fun `the statistics table does not exist until the warm-up asks for it`() = runTest {
        assertEquals(0, statisticsTableCount(), "a fresh database already had planner statistics")

        refreshQueryPlannerStatistics(database)

        assertTrue(
            statisticsTableCount() > 0,
            "ANALYZE did not run: the planner keeps guessing, and the guess it makes is to drive " +
                "the PR read from every finished session the user has ever logged",
        )
    }

    @Test
    fun `running it twice is not an error, because it runs on every start`() = runTest {
        refreshQueryPlannerStatistics(database)
        refreshQueryPlannerStatistics(database)

        assertTrue(statisticsTableCount() > 0)
    }

    private suspend fun statisticsTableCount(): Int = database.useReaderConnection { connection ->
        connection.usePrepared(
            "SELECT COUNT(*) FROM sqlite_master WHERE name = 'sqlite_stat1'",
        ) { statement ->
            if (statement.step()) statement.getInt(0) else 0
        }
    }
}
