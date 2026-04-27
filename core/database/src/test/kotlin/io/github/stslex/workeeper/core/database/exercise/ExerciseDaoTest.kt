// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.database.exercise

import android.database.sqlite.SQLiteConstraintException
import androidx.paging.PagingSource
import io.github.stslex.workeeper.core.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.database.tag.TagEntity
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
    private val tagDao
        get() = database.tagDao
    private val exerciseTagDao
        get() = database.exerciseTagDao

    @BeforeEach
    fun setup() {
        initDb()
    }

    @AfterEach
    fun teardown() {
        clearDb()
    }

    @Test
    fun `pagedActiveByAllTags returns only exercises tagged with all selected tags`() = runTest {
        val tagPushUuid = Uuid.random()
        val tagPullUuid = Uuid.random()
        tagDao.insert(TagEntity(uuid = tagPushUuid, name = "Push"))
        tagDao.insert(TagEntity(uuid = tagPullUuid, name = "Pull"))

        val benchUuid = Uuid.random()
        val rowUuid = Uuid.random()
        val pullupUuid = Uuid.random()

        listOf(
            ExerciseEntity(
                uuid = benchUuid,
                name = "Bench",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
            ExerciseEntity(
                uuid = rowUuid,
                name = "Row",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
            ExerciseEntity(
                uuid = pullupUuid,
                name = "Pull-up",
                type = ExerciseTypeEntity.WEIGHTLESS,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        ).forEach { dao.insert(it) }

        // Bench has Push only.
        // Row has Push + Pull.
        // Pull-up has Pull only.
        exerciseTagDao.insert(
            listOf(
                ExerciseTagEntity(exerciseUuid = benchUuid, tagUuid = tagPushUuid),
                ExerciseTagEntity(exerciseUuid = rowUuid, tagUuid = tagPushUuid),
                ExerciseTagEntity(exerciseUuid = rowUuid, tagUuid = tagPullUuid),
                ExerciseTagEntity(exerciseUuid = pullupUuid, tagUuid = tagPullUuid),
            ),
        )

        val result = dao
            .pagedActiveByAllTags(listOf(tagPushUuid, tagPullUuid), tagCount = 2)
            .loadAll()

        assertEquals(1, result.size, "AND filter should match only Row")
        assertEquals("Row", result.first().name)
    }

    @Test
    fun `pagedActiveByAllTags excludes archived exercises`() = runTest {
        val tagUuid = Uuid.random()
        tagDao.insert(TagEntity(uuid = tagUuid, name = "Push"))

        val activeUuid = Uuid.random()
        val archivedUuid = Uuid.random()

        dao.insert(
            ExerciseEntity(
                uuid = activeUuid,
                name = "Active",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        dao.insert(
            ExerciseEntity(
                uuid = archivedUuid,
                name = "Archived",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = true,
                createdAt = 0L,
                archivedAt = 1L,
            ),
        )
        exerciseTagDao.insert(
            listOf(
                ExerciseTagEntity(exerciseUuid = activeUuid, tagUuid = tagUuid),
                ExerciseTagEntity(exerciseUuid = archivedUuid, tagUuid = tagUuid),
            ),
        )

        val result = dao.pagedActiveByAllTags(listOf(tagUuid), tagCount = 1).loadAll()
        assertEquals(1, result.size)
        assertEquals("Active", result.first().name)
    }

    @Test
    fun `pagedActiveByAllTags with single tag matches any exercise that has it`() = runTest {
        val tagUuid = Uuid.random()
        tagDao.insert(TagEntity(uuid = tagUuid, name = "Push"))

        val a = Uuid.random()
        val b = Uuid.random()
        dao.insert(
            ExerciseEntity(
                uuid = a,
                name = "A",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        dao.insert(
            ExerciseEntity(
                uuid = b,
                name = "B",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        exerciseTagDao.insert(
            listOf(
                ExerciseTagEntity(exerciseUuid = a, tagUuid = tagUuid),
                ExerciseTagEntity(exerciseUuid = b, tagUuid = tagUuid),
            ),
        )

        val result = dao.pagedActiveByAllTags(listOf(tagUuid), tagCount = 1).loadAll()
        assertEquals(2, result.size)
        assertTrue(result.map { it.name }.containsAll(listOf("A", "B")))
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
            ),
        )

        val json = """[{"weight":140.0,"reps":3,"type":"WORK"}]"""
        dao.updateLastAdhocSets(uuid, json)

        val reloaded = dao.getById(uuid)
        assertNotNull(reloaded)
        assertEquals(json, reloaded?.lastAdhocSets)
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
