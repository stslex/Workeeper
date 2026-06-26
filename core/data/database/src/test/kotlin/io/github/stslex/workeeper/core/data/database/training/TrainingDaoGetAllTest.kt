// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.database.training

import io.github.stslex.workeeper.core.data.database.BaseDatabaseTest
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.tag.TrainingTagEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Covers the training-side unfiltered bulk readers used by the AI snapshot export:
 * [TrainingDao.getAll] (must include adhoc + archived), [TrainingExerciseDao.getAll]
 * (plan rows in order), and [TrainingTagDao.getTagNamesForTrainings] (batch names).
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = BaseDatabaseTest.TestApplication::class, sdk = [33])
internal class TrainingDaoGetAllTest : BaseDatabaseTest() {

    private val trainingDao get() = database.trainingDao
    private val trainingExerciseDao get() = database.trainingExerciseDao
    private val trainingTagDao get() = database.trainingTagDao
    private val exerciseDao get() = database.exerciseDao
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
    fun `getAll returns templates plus adhoc and archived rows`() = runTest {
        val template = training(name = "Template")
        val adhoc = training(name = "Adhoc", isAdhoc = true)
        val archived = training(name = "Archived", archived = true, archivedAt = 10L)
        listOf(template, adhoc, archived).forEach { trainingDao.insert(it) }

        val all = trainingDao.getAll()

        assertEquals(
            setOf(template.uuid, adhoc.uuid, archived.uuid),
            all.map { it.uuid }.toSet(),
        )
    }

    @Test
    fun `training-exercise getAll returns every plan row in plan order`() = runTest {
        val training = training(name = "Push")
        trainingDao.insert(training)
        val bench = exercise(name = "Bench")
        val ohp = exercise(name = "OHP")
        listOf(bench, ohp).forEach { exerciseDao.insert(it) }
        trainingExerciseDao.insert(
            listOf(
                TrainingExerciseEntity(training.uuid, ohp.uuid, position = 1),
                TrainingExerciseEntity(training.uuid, bench.uuid, position = 0),
            ),
        )

        val all = trainingExerciseDao.getAll()

        assertEquals(listOf(bench.uuid, ohp.uuid), all.map { it.exerciseUuid })
    }

    @Test
    fun `getTagNamesForTrainings groups denormalized names by training`() = runTest {
        val legs = training(name = "Legs")
        val push = training(name = "Push")
        listOf(legs, push).forEach { trainingDao.insert(it) }
        val heavy = TagEntity(name = "heavy")
        val barbell = TagEntity(name = "barbell")
        listOf(heavy, barbell).forEach { tagDao.insert(it) }
        trainingTagDao.insert(
            listOf(
                TrainingTagEntity(legs.uuid, heavy.uuid),
                TrainingTagEntity(legs.uuid, barbell.uuid),
                TrainingTagEntity(push.uuid, barbell.uuid),
            ),
        )

        val byTraining = trainingTagDao
            .getTagNamesForTrainings(listOf(legs.uuid, push.uuid))
            .groupBy({ it.trainingUuid }, { it.name })

        assertEquals(listOf("barbell", "heavy"), byTraining.getValue(legs.uuid))
        assertEquals(listOf("barbell"), byTraining.getValue(push.uuid))
    }

    private fun training(
        name: String,
        isAdhoc: Boolean = false,
        archived: Boolean = false,
        archivedAt: Long? = null,
    ) = TrainingEntity(
        name = name,
        description = null,
        isAdhoc = isAdhoc,
        archived = archived,
        createdAt = 0L,
        archivedAt = archivedAt,
    )

    private fun exercise(name: String) = ExerciseEntity(
        name = name,
        type = ExerciseTypeEntity.WEIGHTED,
        description = null,
        imagePath = null,
        archived = false,
        createdAt = 0L,
        archivedAt = null,
        lastAdhocSets = null,
        isAdhoc = false,
    )
}
