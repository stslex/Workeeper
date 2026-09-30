// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.exercise.session.model.PerformedExerciseDataModel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class PerformedExerciseRepositoryImplDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var repository: PerformedExerciseRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        repository = PerformedExerciseRepositoryImpl(
            dao = env.performedExerciseDao,
            transition = env.transition,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun `insert persists rows that getBySession reads back`() = runTest {
        val training = env.seedTraining()
        val exerciseA = env.seedExercise(name = "A")
        val exerciseB = env.seedExercise(name = "B")
        val session = env.seedSession(trainingUuid = training.uuid)
        val rowA = PerformedExerciseDataModel(
            uuid = Uuid.random().toString(),
            sessionUuid = session.uuid.toString(),
            exerciseUuid = exerciseA.uuid.toString(),
            position = 0,
            skipped = false,
        )
        val rowB = PerformedExerciseDataModel(
            uuid = Uuid.random().toString(),
            sessionUuid = session.uuid.toString(),
            exerciseUuid = exerciseB.uuid.toString(),
            position = 1,
            skipped = true,
        )

        repository.insert(listOf(rowA, rowB))

        val readBack = repository.getBySession(session.uuid.toString())
            .sortedBy { it.position }
        assertEquals(listOf(rowA.uuid, rowB.uuid), readBack.map { it.uuid })
        assertEquals(false, readBack[0].skipped)
        assertEquals(true, readBack[1].skipped)
    }

    @Test
    fun `insertForSession with empty list writes nothing`() = runTest {
        val training = env.seedTraining()
        val session = env.seedSession(trainingUuid = training.uuid)

        repository.insertForSession(session.uuid.toString(), emptyList())

        assertTrue(repository.getBySession(session.uuid.toString()).isEmpty())
    }

    @Test
    fun `insertForSession seeds rows in the supplied positions with skipped false`() = runTest {
        val training = env.seedTraining()
        val first = env.seedExercise(name = "First")
        val second = env.seedExercise(name = "Second")
        val session = env.seedSession(trainingUuid = training.uuid)

        repository.insertForSession(
            sessionUuid = session.uuid.toString(),
            exerciseUuids = listOf(first.uuid.toString() to 0, second.uuid.toString() to 1),
        )

        val rows = repository.getBySession(session.uuid.toString())
            .sortedBy { it.position }
        assertEquals(listOf(0, 1), rows.map { it.position })
        assertEquals(
            listOf(first.uuid.toString(), second.uuid.toString()),
            rows.map { it.exerciseUuid },
        )
        assertTrue(rows.none { it.skipped })
    }

    @Test
    fun `setSkipped flips the persisted skipped flag`() = runTest {
        val training = env.seedTraining()
        val exercise = env.seedExercise()
        val session = env.seedSession(trainingUuid = training.uuid)
        val performed = env.seedPerformed(
            sessionUuid = session.uuid,
            exerciseUuid = exercise.uuid,
        )

        repository.setSkipped(performed.uuid.toString(), skipped = true)
        var rows = repository.getBySession(session.uuid.toString())
        assertTrue(rows.single().skipped)

        repository.setSkipped(performed.uuid.toString(), skipped = false)
        rows = repository.getBySession(session.uuid.toString())
        assertFalse(rows.single().skipped)
    }
}
