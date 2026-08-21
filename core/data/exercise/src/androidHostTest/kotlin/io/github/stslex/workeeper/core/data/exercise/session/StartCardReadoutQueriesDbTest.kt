// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.session

import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepositoryImpl
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepositoryImpl
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * Real-DB coverage for the three start-card readout queries (home-start-card.md §3.2–§3.4):
 * the last-finished-session anchor, the tag idle stats, and the most-forgotten template.
 * One file because the modes ship together and the seeds interlock.
 */
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class StartCardReadoutQueriesDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var sessionRepository: SessionRepositoryImpl
    private lateinit var tagRepository: TagRepositoryImpl
    private lateinit var trainingRepository: TrainingRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        sessionRepository = SessionRepositoryImpl(
            dao = env.sessionDao,
            performedExerciseDao = env.performedExerciseDao,
            setDao = env.setDao,
            trainingDao = env.trainingDao,
            exerciseDao = env.exerciseDao,
            trainingExerciseDao = env.trainingExerciseDao,
            transition = env.transition,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        tagRepository = TagRepositoryImpl(
            dao = env.tagDao,
            ioDispatcher = UnconfinedTestDispatcher(),
            transitionRunner = env.transition,
        )
        trainingRepository = TrainingRepositoryImpl(
            dao = env.trainingDao,
            trainingExerciseDao = env.trainingExerciseDao,
            tagDao = env.tagDao,
            trainingTagDao = env.trainingTagDao,
            sessionDao = env.sessionDao,
            exerciseRepository = mockk<ExerciseRepository>(relaxed = true),
            ioDispatcher = UnconfinedTestDispatcher(),
            dbTransition = env.transition,
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    // ---- observeLastFinishedSession (§3.2) ------------------------------------------------

    @Test
    fun `last finished session is the one with the greatest finish time, carrying its name`() =
        runTest {
            val older = env.seedTraining(name = "Ноги")
            val newer = env.seedTraining(name = "Спина")
            env.seedSession(older.uuid, state = SessionStateEntity.FINISHED, finishedAt = 100L)
            env.seedSession(newer.uuid, state = SessionStateEntity.FINISHED, finishedAt = 900L)
            env.seedSession(older.uuid, state = SessionStateEntity.IN_PROGRESS, finishedAt = null)

            val last = sessionRepository.observeLastFinishedSession().first()

            assertNotNull(last)
            assertEquals(900L, last?.finishedAt)
            assertEquals("Спина", last?.trainingName)
            assertEquals(false, last?.isAdhoc)
        }

    @Test
    fun `last finished session is null while nothing has ever finished`() = runTest {
        val training = env.seedTraining()
        env.seedSession(training.uuid, state = SessionStateEntity.IN_PROGRESS, finishedAt = null)

        assertNull(sessionRepository.observeLastFinishedSession().first())
    }

    // ---- observeTagIdleStats (§3.3) -------------------------------------------------------

    private suspend fun seedTaggedHistory(
        tagName: String,
        finishedAt: Long,
        skipped: Boolean = false,
    ) {
        val training = env.seedTraining(name = "T-$tagName-$finishedAt")
        val exercise = env.seedExercise()
        val tag = TagEntity(name = tagName)
        env.tagDao.insert(tag)
        val stored = env.tagDao.findByName(tagName)!!
        env.exerciseTagDao.insert(listOf(ExerciseTagEntity(exerciseUuid = exercise.uuid, tagUuid = stored.uuid)))
        val session = env.seedSession(
            training.uuid,
            state = SessionStateEntity.FINISHED,
            finishedAt = finishedAt,
        )
        env.seedPerformed(session.uuid, exercise.uuid, skipped = skipped)
    }

    @Test
    fun `tag idle stats rank the longest-idle tag first and cap at the limit`() = runTest {
        seedTaggedHistory("спина", finishedAt = 300L)
        seedTaggedHistory("ноги", finishedAt = 100L)
        seedTaggedHistory("грудь", finishedAt = 200L)
        seedTaggedHistory("плечи", finishedAt = 400L)

        val stats = tagRepository.observeTagIdleStats(limit = 3).first()

        assertEquals(listOf("ноги", "грудь", "спина"), stats.map { it.name })
        assertEquals(listOf(100L, 200L, 300L), stats.map { it.lastTrainedAt })
    }

    @Test
    fun `a tag's idle time is its LATEST training, not its first`() = runTest {
        seedTaggedHistory("спина", finishedAt = 100L)
        seedTaggedHistory("спина", finishedAt = 500L)

        val stats = tagRepository.observeTagIdleStats(limit = 3).first()

        assertEquals(1, stats.size)
        assertEquals(500L, stats.single().lastTrainedAt)
    }

    @Test
    fun `a skipped performed exercise does not count as training its group`() = runTest {
        seedTaggedHistory("спина", finishedAt = 100L)
        seedTaggedHistory("спина", finishedAt = 900L, skipped = true)

        val stats = tagRepository.observeTagIdleStats(limit = 3).first()

        // The 900L session exists but its performed row is skipped — 100L must win.
        assertEquals(100L, stats.single().lastTrainedAt)
    }

    @Test
    fun `a tag with no finished history is absent, and no tags means an empty list`() = runTest {
        // A tag linked to an exercise that was never performed in any finished session.
        val exercise = env.seedExercise()
        env.tagDao.insert(TagEntity(name = "новичок"))
        val stored = env.tagDao.findByName("новичок")!!
        env.exerciseTagDao.insert(
            listOf(ExerciseTagEntity(exerciseUuid = exercise.uuid, tagUuid = stored.uuid)),
        )

        assertEquals(emptyList<Any>(), tagRepository.observeTagIdleStats(limit = 3).first())
    }

    // ---- observeMostForgottenTemplate (§3.4, HD1) -----------------------------------------

    @Test
    fun `a never-run template outranks every template that has ever run`() = runTest {
        val ran = env.seedTraining(name = "Бег", createdAt = 10L)
        env.seedSession(ran.uuid, state = SessionStateEntity.FINISHED, finishedAt = 5L)
        env.seedTraining(name = "Новый план", createdAt = 999L)

        val forgotten = trainingRepository.observeMostForgottenTemplate().first()

        assertEquals("Новый план", forgotten?.data?.name)
        assertNull(forgotten?.lastSessionAt)
    }

    @Test
    fun `among never-run templates the oldest-created ranks first`() = runTest {
        env.seedTraining(name = "Новее", createdAt = 200L)
        env.seedTraining(name = "Старше", createdAt = 100L)

        val forgotten = trainingRepository.observeMostForgottenTemplate().first()

        assertEquals("Старше", forgotten?.data?.name)
    }

    @Test
    fun `among run templates the stalest last session wins`() = runTest {
        val fresh = env.seedTraining(name = "Свежая")
        val stale = env.seedTraining(name = "Забытая")
        env.seedSession(fresh.uuid, state = SessionStateEntity.FINISHED, finishedAt = 900L)
        env.seedSession(stale.uuid, state = SessionStateEntity.FINISHED, finishedAt = 100L)
        // A stale training's LATEST run decides, not its first.
        env.seedSession(fresh.uuid, state = SessionStateEntity.FINISHED, finishedAt = 50L)

        val forgotten = trainingRepository.observeMostForgottenTemplate().first()

        assertEquals("Забытая", forgotten?.data?.name)
        assertEquals(100L, forgotten?.lastSessionAt)
    }

    @Test
    fun `adhoc and archived rows never surface, and no templates means null`() = runTest {
        env.seedTraining(name = "Свободная", isAdhoc = true)
        env.seedTraining(name = "В архиве", archived = true)

        assertNull(trainingRepository.observeMostForgottenTemplate().first())
    }
}
