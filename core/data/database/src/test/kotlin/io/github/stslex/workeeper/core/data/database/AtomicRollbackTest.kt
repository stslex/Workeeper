// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database

import androidx.room3.immediateTransaction
import androidx.room3.useWriterConnection
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * ATOMICITY PROBE for the Room 3 transaction primitive (`DbTransitionRunner`, ported from
 * Room 2's `withTransaction {}` to `useWriterConnection { it.immediateTransaction {} }`).
 *
 * A broken transaction primitive does not crash — it silently COMMITS writes that should have
 * rolled back. Every other DB test is single-threaded, so none can detect it. This test performs
 * a SEQUENTIAL chain of DAO inserts inside the transaction (exactly the `DbTransitionRunner`
 * contract that `finishSessionAtomic` / cascade-delete use) and then throws; the writes must NOT
 * survive — sequential DAO calls join the block's transaction per Room's documented
 * `useWriterConnection { it.immediateTransaction { … } }` pattern.
 *
 * [outsideWriteSurvivesUnrelatedRollback] is the KNOWN-NEGATIVE: a write made OUTSIDE the
 * transaction survives a subsequent unrelated rollback, proving the probe's row-count assertion
 * can actually observe a surviving write (a probe that cannot fail proves nothing).
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class AtomicRollbackTest {

    private lateinit var env: RepositoryTestEnv

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun sequentialDaoWritesInsideTransactionRollBackOnThrow() = runTest {
        val boom = IllegalStateException("roll it back")
        val thrown = runCatching {
            env.transition.invoke {
                // Sequential DAO inserts — the shape production uses (finishSessionAtomic writes
                // session + performed + sets in order inside one transaction). Then the block
                // throws; all inserts must roll back → zero rows.
                env.tagDao.insert(TagEntity(name = "seq-A"))
                env.tagDao.insert(TagEntity(name = "seq-B"))
                throw boom
            }
        }.exceptionOrNull()

        assertEquals(boom, thrown, "the block's throw must propagate out of the transaction")

        val survivors = env.tagDao.observeAll().first()
        assertEquals(
            emptyList<TagEntity>(),
            survivors,
            "sequential DAO writes inside the transaction MUST NOT survive the rollback",
        )
    }

    @Test
    fun rawDocumentedPatternRollsBackOnThrow() = runTest {
        // The documented Room 3 pattern DIRECTLY (no DbTransitionRunner wrapper, no coroutineScope
        // nesting) — isolates whether any rollback failure is the wrapper's fault or Room/driver's.
        val db = env.rawDatabase()
        runCatching {
            db.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    env.tagDao.insert(TagEntity(name = "raw-A"))
                    env.tagDao.insert(TagEntity(name = "raw-B"))
                    throw IllegalStateException("roll back the raw block")
                }
            }
        }
        val survivors = env.tagDao.observeAll().first()
        assertEquals(
            emptyList<TagEntity>(),
            survivors,
            "raw useWriterConnection{immediateTransaction{}} sequential writes MUST roll back",
        )
    }

    @Test
    fun outsideWriteSurvivesUnrelatedRollback() = runTest {
        // KNOWN-NEGATIVE: write OUTSIDE any transaction, then roll back an unrelated transaction.
        // The outside write must survive — proving the row-count assertion above can observe a
        // surviving write and is therefore a real guard, not a vacuous one.
        env.tagDao.insert(TagEntity(name = "outside"))

        runCatching {
            env.transition.invoke {
                env.tagDao.insert(TagEntity(name = "inside-doomed"))
                throw IllegalStateException("roll back only the inside write")
            }
        }

        val survivors = env.tagDao.observeAll().first().map { it.name }
        assertEquals(
            listOf("outside"),
            survivors,
            "the outside write survives; the inside write rolls back",
        )
    }
}
