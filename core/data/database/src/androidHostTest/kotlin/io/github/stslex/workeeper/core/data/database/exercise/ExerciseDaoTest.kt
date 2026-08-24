// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.exercise

import android.database.sqlite.SQLiteConstraintException
import androidx.paging.PagingSource
import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class ExerciseDaoTest : BaseDatabaseTest() {

    private val dao
        get() = database.exerciseDao

    @BeforeEach
    fun setup() {
        initDb()
    }

    @AfterEach
    fun teardown() {
        clearDb()
    }

    @Test
    fun `insert with case-insensitive duplicate name throws SQLiteConstraintException`() = runTest {
        dao.insert(
            ExerciseEntity(
                uuid = Uuid.random(),
                name = "Bench Press",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )

        assertThrows(SQLiteConstraintException::class.java) {
            kotlinx.coroutines.runBlocking {
                dao.insert(
                    ExerciseEntity(
                        uuid = Uuid.random(),
                        name = "bench press",
                        type = ExerciseTypeEntity.WEIGHTED,
                        description = null,
                        imagePath = null,
                        archived = false,
                        createdAt = 0L,
                        archivedAt = null,
                        lastAdhocSets = null,
                    ),
                )
            }
        }
    }

    @Test
    fun `updateLastAdhocSets persists then getById returns stored json`() = runTest {
        val uuid = Uuid.random()
        dao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Squat",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )

        val json = """[{"weight":140.0,"reps":3,"type":"WORK"}]"""
        dao.updateLastAdhocSets(uuid, json)

        val reloaded = dao.getById(uuid)
        assertNotNull(reloaded)
        assertEquals(json, reloaded?.lastAdhocSets)
    }

    @Test
    fun `getAdhocPlansBatch with empty input returns empty list`() = runTest {
        val rows = dao.getAdhocPlansBatch(emptyList())

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `getAdhocPlansBatch returns rows with parsed JSON and null entries preserved`() = runTest {
        val withPlanUuid = Uuid.random()
        val withNullPlanUuid = Uuid.random()
        val unknownUuid = Uuid.random()
        dao.insert(
            ExerciseEntity(
                uuid = withPlanUuid,
                name = "Bench Press",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = """[{"weight":80.0,"reps":5,"type":"WORK"}]""",
            ),
        )
        dao.insert(
            ExerciseEntity(
                uuid = withNullPlanUuid,
                name = "Squat",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )

        val rows = dao.getAdhocPlansBatch(listOf(withPlanUuid, withNullPlanUuid, unknownUuid))

        // Existing rows are returned regardless of whether last_adhoc_sets is null;
        // unknown uuids are silently absent (no error).
        assertEquals(2, rows.size)
        val byUuid = rows.associateBy { it.uuid }
        assertEquals(
            """[{"weight":80.0,"reps":5,"type":"WORK"}]""",
            byUuid[withPlanUuid]?.lastAdhocSets,
        )
        assertNull(byUuid[withNullPlanUuid]?.lastAdhocSets)
        assertNull(byUuid[unknownUuid])
    }

    @Test
    fun `getAdhocPlansBatch row uuid matches the requested input`() = runTest {
        val uuid = Uuid.random()
        dao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Bench",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )

        val rows = dao.getAdhocPlansBatch(listOf(uuid))

        assertEquals(uuid, rows.single().uuid)
    }

    @Test
    fun `updateLastAdhocSets with null clears column`() = runTest {
        val uuid = Uuid.random()
        dao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Deadlift",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = """[{"weight":180.0,"reps":1,"type":"WORK"}]""",
            ),
        )

        dao.updateLastAdhocSets(uuid, null)

        assertNull(dao.getById(uuid)?.lastAdhocSets)
    }

    private suspend fun PagingSource<Int, ExerciseEntity>.loadAll(): List<ExerciseEntity> {
        val result = load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 50,
                placeholdersEnabled = false,
            ),
        )
        return (result as PagingSource.LoadResult.Page).data
    }
}
