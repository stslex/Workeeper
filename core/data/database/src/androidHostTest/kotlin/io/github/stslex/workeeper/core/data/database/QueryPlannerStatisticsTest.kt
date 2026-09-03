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
 * Gate for [refreshQueryPlannerStatistics]: `sqlite_stat1` must be absent before and present
 * after. Asserts nothing about the query plan, on purpose — see documentation/testing.md.
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
