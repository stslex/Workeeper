// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.exercise

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Covers the exercise-side unfiltered bulk readers used by the AI snapshot export:
 * [ExerciseDao.getAll] (must include adhoc + archived) and
 * [ExerciseTagDao.getTagNamesForExercises] (batch names).
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class ExerciseDaoGetAllTest : BaseDatabaseTest() {

    private val exerciseDao get() = database.exerciseDao
    private val exerciseTagDao get() = database.exerciseTagDao
    private val tagDao get() = database.tagDao

    @BeforeEach
    fun setup() {
        initDb()
    }

    @AfterEach
    fun teardown() {
        clearDb()
    }

    @Test
    fun `getAll returns active plus adhoc and archived rows`() = runTest {
        val active = exercise(name = "Squat")
        val adhoc = exercise(name = "Adhoc Curl", isAdhoc = true)
        val archived = exercise(name = "Old Press", archived = true)
        listOf(active, adhoc, archived).forEach { exerciseDao.insert(it) }

        val all = exerciseDao.getAll()

        assertEquals(
            setOf(active.uuid, adhoc.uuid, archived.uuid),
            all.map { it.uuid }.toSet(),
        )
    }

    @Test
    fun `getTagNamesForExercises groups denormalized names by exercise`() = runTest {
        val squat = exercise(name = "Squat")
        val bench = exercise(name = "Bench")
        listOf(squat, bench).forEach { exerciseDao.insert(it) }
        val legs = TagEntity(name = "legs")
        val push = TagEntity(name = "push")
        listOf(legs, push).forEach { tagDao.insert(it) }
        exerciseTagDao.insert(
            listOf(
                ExerciseTagEntity(squat.uuid, legs.uuid),
                ExerciseTagEntity(bench.uuid, push.uuid),
            ),
        )

        val byExercise = exerciseTagDao
            .getTagNamesForExercises(listOf(squat.uuid, bench.uuid))
            .groupBy({ it.exerciseUuid }, { it.name })

        assertEquals(listOf("legs"), byExercise.getValue(squat.uuid))
        assertEquals(listOf("push"), byExercise.getValue(bench.uuid))
    }

    private fun exercise(
        name: String,
        isAdhoc: Boolean = false,
        archived: Boolean = false,
    ) = ExerciseEntity(
        name = name,
        type = ExerciseTypeEntity.WEIGHTED,
        description = null,
        imagePath = null,
        archived = archived,
        createdAt = 0L,
        archivedAt = null,
        lastAdhocSets = null,
        isAdhoc = isAdhoc,
    )
}
