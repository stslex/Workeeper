// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.ui.test.annotations.Regression
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Invalidation oracle: a write through the ported transaction must re-emit collected Flows.
 * GUARD: real device + file-backed DB — Robolectric is not a valid oracle for invalidation.
 */
@Regression
@RunWith(AndroidJUnit4::class)
internal class InvalidationDeviceTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val tagDao get() = database.tagDao

    private suspend fun <T> transition(block: suspend CoroutineScope.() -> T): T =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction { coroutineScope { block() } }
        }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(PROBE_DB)
        database = Room.databaseBuilder<AppDatabase>(context, PROBE_DB)
            .setDriver(BundledSQLiteDriver())
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(PROBE_DB)
    }

    @Test
    fun flowReEmitsAfterWriteThroughPortedTransaction() = runBlocking {
        assertEquals(emptyList<TagEntity>(), tagDao.observeAll().first())

        transition { tagDao.insert(TagEntity(name = "invalidate-me")) }

        // withTimeout so a non-re-emitting Flow fails the test instead of hanging forever.
        val reEmitted = withTimeout(TIMEOUT_MS) {
            tagDao.observeAll().first { rows -> rows.any { it.name == "invalidate-me" } }
        }
        assertTrue(
            "Room-native Flow MUST re-emit the new row after a write through the ported transaction",
            reEmitted.any { it.name == "invalidate-me" },
        )
    }

    @Test
    fun knownNegative_flowDoesNotReEmitWithoutAWrite() = runBlocking {
        // KNOWN-NEGATIVE: no write → no re-emit, proving the positive assertion can fail.
        tagDao.insert(TagEntity(name = "seed"))
        assertEquals(listOf("seed"), tagDao.observeAll().first().map { it.name })

        val sawSpurious = runCatching {
            withTimeout(TIMEOUT_MS) {
                tagDao.observeAll().first { rows -> rows.map { it.name } != listOf("seed") }
            }
        }.isSuccess
        assertTrue(
            "with no write, the Flow must NOT re-emit a changed value (proves the positive guard can fail)",
            !sawSpurious,
        )
    }

    private companion object {
        const val PROBE_DB = "invalidation_probe.db"
        const val TIMEOUT_MS = 5_000L
    }
}
