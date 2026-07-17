// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.stslex.workeeper.core.core.coroutine.asyncForEach
import io.github.stslex.workeeper.core.core.coroutine.asyncScope
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.content.Context

/**
 * ATOMICITY ORACLE — real device, real FILE-BACKED database. Answers: does the
 * `DbTransitionRunner` transaction primitive roll back writes on a throw, for the two
 * shapes production actually uses inside `transition { }`?
 *
 * ⚠️ THIS TEST MUST STAY androidTest + FILE-BACKED. Do NOT "simplify" it onto Robolectric
 * or an in-memory DB. Robolectric's shadow SQLite gave a **false NEGATIVE on this exact
 * measurement twice** — a Robolectric variant of this probe reported shape B (concurrent
 * `async` children) as NOT rolling back, when the real device proves it does. Robolectric
 * is not a valid oracle for transaction/async-child rollback semantics here; the device is.
 * See documentation/tech-debt.md → "Robolectric is not a valid oracle for transaction
 * semantics".
 *
 * Four runs, mirroring the two real production shapes with their REAL helpers (imported from
 * core:core — not hand-rolled):
 *  - CONTROL  : `withTransaction { insert; insert; throw }`            → must roll back.
 *  - NEGATIVE : write outside the transaction survives an unrelated rollback → must survive.
 *  - SHAPE A  : finishSessionAtomic's shape — sequential `asyncScope { write }` writers.
 *  - SHAPE B  : clearWeightsFromAllPlans' shape — concurrent `coroutineScope { async { write } }`.
 *
 * The transaction runner is built inline exactly as production's `DbTransitionRunner`
 * (`withTransaction { coroutineScope { block() } }`).
 */
@RunWith(AndroidJUnit4::class)
internal class AtomicRollbackDeviceTest {

    /** Named trigger so the intentional rollback throw is not a generic exception. */
    private class RollbackTrigger(message: String) : Exception(message)

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private val tagDao get() = database.tagDao

    /** Production's DbTransitionRunner shape, inline. */
    private suspend fun <T> transition(block: suspend CoroutineScope.() -> T): T =
        database.withTransaction { coroutineScope { block() } }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(PROBE_DB)
        // Real file-backed DB on the device — NOT in-memory, so transaction/connection
        // semantics match production, not Robolectric's shadow SQLite.
        database = Room.databaseBuilder(context, AppDatabase::class.java, PROBE_DB)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(PROBE_DB)
    }

    @Test
    fun control_sequentialWritesRollBackOnThrow() = runBlocking {
        runCatching {
            transition {
                tagDao.insert(TagEntity(name = "ctrl-A"))
                tagDao.insert(TagEntity(name = "ctrl-B"))
                throw RollbackTrigger("roll back control")
            }
        }
        assertEquals(
            "CONTROL: sequential writes in a transaction MUST roll back on throw",
            emptyList<TagEntity>(),
            tagDao.observeAll().first(),
        )
    }

    @Test
    fun knownNegative_outsideWriteSurvivesUnrelatedRollback() = runBlocking {
        tagDao.insert(TagEntity(name = "outside"))
        runCatching {
            transition {
                tagDao.insert(TagEntity(name = "inside-doomed"))
                throw RollbackTrigger("roll back inside only")
            }
        }
        assertEquals(
            "KNOWN-NEGATIVE: the outside write survives; the inside write rolls back",
            listOf("outside"),
            tagDao.observeAll().first().map { it.name },
        )
    }

    @Test
    fun shapeA_asyncScopeSequentialWritersRollBackOnThrow() = runBlocking {
        // finishSessionAtomic's exact shape: sequential asyncScope { write } writers.
        runCatching {
            transition {
                val a = asyncScope { tagDao.insert(TagEntity(name = "A-1")) }
                val b = asyncScope { tagDao.insert(TagEntity(name = "A-2")) }
                val c = asyncScope { tagDao.insert(TagEntity(name = "A-3")) }
                a.await(); b.await(); c.await()
                throw RollbackTrigger("roll back shape A")
            }
        }
        assertEquals(
            "SHAPE A (finishSessionAtomic): asyncScope writers MUST roll back on throw",
            emptyList<TagEntity>(),
            tagDao.observeAll().first(),
        )
    }

    @Test
    fun shapeB_concurrentAsyncWritersRollBackOnThrow() = runBlocking {
        // clearWeightsFromAllPlansForExercise's shape: concurrent async{} writers, and the
        // asyncForEach helper it also uses (both launch children of coroutineScope).
        runCatching {
            transition {
                coroutineScope {
                    val a = async { tagDao.insert(TagEntity(name = "B-1")) }
                    val b = async { tagDao.insert(TagEntity(name = "B-2")) }
                    a.await(); b.await()
                }
                listOf("B-3", "B-4").asyncForEach { tagDao.insert(TagEntity(name = it)) }
                throw RollbackTrigger("roll back shape B")
            }
        }
        assertEquals(
            "SHAPE B (clearWeightsFromAllPlans): concurrent async writers MUST roll back on throw",
            emptyList<TagEntity>(),
            tagDao.observeAll().first(),
        )
    }

    private companion object {
        const val PROBE_DB = "atomicity_probe.db"
    }
}
