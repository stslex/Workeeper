// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.exercise

import androidx.paging.testing.asSnapshot
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.PerformedExerciseEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetEntity
import io.github.stslex.workeeper.core.data.database.session.model.SetTypeEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository.SaveResult
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import kotlin.uuid.Uuid

@Suppress("LargeClass")
@ExtendWith(RobolectricExtension::class)
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class ExerciseRepositoryImplDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var imageStorage: ImageStorage
    private lateinit var repository: ExerciseRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        imageStorage = mockk<ImageStorage>(relaxed = true)
        repository = ExerciseRepositoryImpl(
            dao = env.exerciseDao,
            tagDao = env.tagDao,
            exerciseTagDao = env.exerciseTagDao,
            trainingExerciseDao = env.trainingExerciseDao,
            sessionDao = env.sessionDao,
            setDao = env.setDao,
            imageStorage = imageStorage,
            transition = env.transition,
            bgDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun `saveItem inserts a new exercise and reads back via getExercise`() = runTest {
        val uuid = Uuid.random()
        val change = exerciseChange(
            uuid = uuid,
            name = "Bench Press",
            description = "Chest press on bench",
        )

        val result = repository.saveItem(change)

        assertEquals(SaveResult.Success, result)
        val reloaded = repository.getExercise(uuid.toString())
        assertNotNull(reloaded)
        assertEquals("Bench Press", reloaded?.name)
        assertEquals("Chest press on bench", reloaded?.description)
        assertEquals(ExerciseTypeDataModel.WEIGHTED, reloaded?.type)
    }

    @Test
    fun `saveItem updates an existing exercise and persists the new fields`() = runTest {
        val uuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = uuid, name = "Bench"))

        repository.saveItem(
            exerciseChange(uuid = uuid, name = "Bench Press", description = "Updated"),
        )

        val reloaded = repository.getExercise(uuid.toString())
        assertEquals("Bench Press", reloaded?.name)
        assertEquals("Updated", reloaded?.description)
    }

    @Test
    fun `saveItem returns DuplicateName when another row already owns the name`() = runTest {
        val firstUuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = firstUuid, name = "Bench"))

        val collisionResult = repository.saveItem(
            exerciseChange(uuid = Uuid.random(), name = "BENCH"),
        )

        assertEquals(SaveResult.DuplicateName, collisionResult)
        // The original row is still present and unchanged.
        val rows = env.exerciseDao.getAllActive()
        assertEquals(1, rows.size)
        assertEquals(firstUuid, rows.single().uuid)
    }

    @Test
    fun `saveItem syncs labels by creating tags and deleting orphaned ones`() = runTest {
        val uuid = Uuid.random()
        repository.saveItem(
            exerciseChange(uuid = uuid, name = "Squat", labels = listOf("legs", "lower")),
        )

        val firstLabels = repository.getLabels(uuid.toString()).sorted()
        assertEquals(listOf("legs", "lower"), firstLabels)

        // Save again with a different label set — the old "lower" should detach.
        repository.saveItem(
            exerciseChange(uuid = uuid, name = "Squat", labels = listOf("legs", "quads")),
        )

        val updated = repository.getLabels(uuid.toString()).sorted()
        assertEquals(listOf("legs", "quads"), updated)
        // Tag rows are reused by name, so all three tag names exist in tag_table.
        val tagNames = env.tagDao.observeAll().first().map { it.name }
        assertTrue(tagNames.containsAll(listOf("legs", "lower", "quads")))
    }

    @Test
    fun `createInlineAdhocExercise inserts a new isAdhoc row when no name match`() = runTest {
        val result = repository.createInlineAdhocExercise("Skull Crushers")

        assertFalse(result.reusedExisting)
        val persisted = env.exerciseDao.getById(Uuid.parse(result.exercise.uuid))
        assertNotNull(persisted)
        assertEquals("Skull Crushers", persisted?.name)
        assertEquals(true, persisted?.isAdhoc)
    }

    @Test
    fun `createInlineAdhocExercise returns the existing row on case-insensitive collision`() =
        runTest {
            val existing = ExerciseEntity(
                uuid = Uuid.random(),
                name = "Bench Press",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
                isAdhoc = false,
            )
            env.exerciseDao.insert(existing)

            val result = repository.createInlineAdhocExercise("BENCH PRESS")

            assertTrue(result.reusedExisting)
            assertEquals(existing.uuid.toString(), result.exercise.uuid)
            // Existing rows are returned untouched — `is_adhoc` is intentionally NOT flipped.
            assertEquals(false, env.exerciseDao.getById(existing.uuid)?.isAdhoc)
        }

    @Test
    fun `getAdhocPlan returns parsed list when set, null when not`() = runTest {
        val uuid = Uuid.random()
        repository.saveItem(
            exerciseChange(
                uuid = uuid,
                name = "Bench",
                lastAdhoc = listOf(
                    PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK),
                ),
            ),
        )

        val parsed = repository.getAdhocPlan(uuid.toString())
        assertEquals(1, parsed?.size)
        assertEquals(80.0, parsed?.first()?.weight)

        val fresh = Uuid.random()
        repository.saveItem(exerciseChange(uuid = fresh, name = "Fresh", lastAdhoc = null))
        assertNull(repository.getAdhocPlan(fresh.toString()))
    }

    @Test
    fun `setAdhocPlan persists the plan and clears it when null is passed`() = runTest {
        val uuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = uuid, name = "Bench"))

        val plan = listOf(PlanSetDataModel(weight = 90.0, reps = 4, type = SetTypeDataModel.WORK))
        repository.setAdhocPlan(uuid.toString(), plan)
        assertEquals(plan, repository.getAdhocPlan(uuid.toString()))

        repository.setAdhocPlan(uuid.toString(), null)
        assertNull(repository.getAdhocPlan(uuid.toString()))
    }

    @Test
    fun `clearWeightsFromAllPlansForExercise wipes weights from last_adhoc_sets and training_exercise plans`() =
        runTest {
            val exerciseUuid = Uuid.random()
            val trainingUuid = Uuid.random()
            env.exerciseDao.insert(
                ExerciseEntity(
                    uuid = exerciseUuid,
                    name = "Pull Up",
                    type = ExerciseTypeEntity.WEIGHTED,
                    description = null,
                    imagePath = null,
                    archived = false,
                    createdAt = 0L,
                    archivedAt = null,
                    // Existing weighted plan entries should have weight cleared after the call.
                    lastAdhocSets = """[{"weight":20.0,"reps":5,"type":"WORK"}]""",
                ),
            )
            env.trainingDao.insert(
                TrainingEntity(
                    uuid = trainingUuid,
                    name = "Push",
                    description = null,
                    isAdhoc = false,
                    archived = false,
                    createdAt = 0L,
                    archivedAt = null,
                ),
            )
            env.trainingExerciseDao.insert(
                listOf(
                    TrainingExerciseEntity(
                        trainingUuid = trainingUuid,
                        exerciseUuid = exerciseUuid,
                        position = 0,
                        planSets = """[{"weight":15.0,"reps":8,"type":"WORK"}]""",
                    ),
                ),
            )

            repository.clearWeightsFromAllPlansForExercise(exerciseUuid.toString())

            val parsedAdhoc = repository.getAdhocPlan(exerciseUuid.toString())
            assertEquals(1, parsedAdhoc?.size)
            assertNull(parsedAdhoc?.first()?.weight)
            val planJson = env.trainingExerciseDao.getPlanSets(trainingUuid, exerciseUuid)
            assertNotNull(planJson)
            assertTrue(planJson!!.contains("\"weight\":null"))
        }

    @Test
    fun `deleteItem deletes the row and the image file when present`() = runTest {
        val uuid = Uuid.random()
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Bench",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = "/files/$uuid.jpg",
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )

        repository.deleteItem(uuid.toString())

        assertNull(env.exerciseDao.getById(uuid))
        coVerify(exactly = 1) { imageStorage.deleteImage("/files/$uuid.jpg") }
    }

    @Test
    fun `deleteItem skips the image storage call when imagePath is null`() = runTest {
        val uuid = Uuid.random()
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Pull",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )

        repository.deleteItem(uuid.toString())

        assertNull(env.exerciseDao.getById(uuid))
        coVerify(exactly = 0) { imageStorage.deleteImage(any()) }
    }

    @Test
    fun `deleteAllItems removes rows and only deletes image files for rows that had a path`() =
        runTest {
            val uuidA = Uuid.random()
            val uuidB = Uuid.random()
            env.exerciseDao.insert(
                ExerciseEntity(
                    uuid = uuidA,
                    name = "Bench-A",
                    type = ExerciseTypeEntity.WEIGHTED,
                    description = null,
                    imagePath = "/files/A.jpg",
                    archived = false,
                    createdAt = 0L,
                    archivedAt = null,
                    lastAdhocSets = null,
                ),
            )
            env.exerciseDao.insert(
                ExerciseEntity(
                    uuid = uuidB,
                    name = "Bench-B",
                    type = ExerciseTypeEntity.WEIGHTED,
                    description = null,
                    imagePath = null,
                    archived = false,
                    createdAt = 0L,
                    archivedAt = null,
                    lastAdhocSets = null,
                ),
            )

            repository.deleteAllItems(listOf(uuidA, uuidB))

            assertNull(env.exerciseDao.getById(uuidA))
            assertNull(env.exerciseDao.getById(uuidB))
            coVerify(exactly = 1) { imageStorage.deleteImage("/files/A.jpg") }
            // No second deleteImage call: B had a null imagePath so it was skipped.
            coVerify(exactly = 1) { imageStorage.deleteImage(any()) }
        }

    @Test
    fun `archive flips archived flag without removing the row or image`() = runTest {
        val uuid = Uuid.random()
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Bench",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = "/files/bench.jpg",
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )

        repository.archive(uuid.toString())

        val reloaded = env.exerciseDao.getById(uuid)
        assertNotNull(reloaded)
        assertTrue(reloaded!!.archived)
        assertNotNull(reloaded.archivedAt)
        coVerify(exactly = 0) { imageStorage.deleteImage(any()) }
    }

    @Test
    fun `restore flips archived flag back to false and clears archivedAt`() = runTest {
        val uuid = Uuid.random()
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Bench",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = true,
                createdAt = 0L,
                archivedAt = 100L,
                lastAdhocSets = null,
            ),
        )

        repository.restore(uuid.toString())

        val reloaded = env.exerciseDao.getById(uuid)
        assertEquals(false, reloaded?.archived)
        assertNull(reloaded?.archivedAt)
    }

    @Test
    fun `permanentDelete removes the row and the image file when present`() = runTest {
        val uuid = Uuid.random()
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = "Bench",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = "/files/$uuid.jpg",
                archived = true,
                createdAt = 0L,
                archivedAt = 1L,
                lastAdhocSets = null,
            ),
        )

        repository.permanentDelete(uuid.toString())

        assertNull(env.exerciseDao.getById(uuid))
        coVerify(exactly = 1) { imageStorage.deleteImage("/files/$uuid.jpg") }
    }

    @Test
    fun `getExercisesByUuid returns mapped models in the order DAO supplies`() = runTest {
        val first = Uuid.random()
        val second = Uuid.random()
        repository.saveItem(exerciseChange(uuid = first, name = "First"))
        repository.saveItem(exerciseChange(uuid = second, name = "Second"))

        val rows = repository.getExercisesByUuid(listOf(first.toString(), second.toString()))
            .associateBy { it.uuid }

        assertEquals(2, rows.size)
        assertEquals("First", rows[first.toString()]?.name)
        assertEquals("Second", rows[second.toString()]?.name)
    }

    @Test
    fun `getLabels returns names from the join table sorted alphabetically`() = runTest {
        val uuid = Uuid.random()
        repository.saveItem(
            exerciseChange(uuid = uuid, name = "Bench", labels = listOf("zeta", "alpha")),
        )

        val labels = repository.getLabels(uuid.toString())
        assertEquals(listOf("alpha", "zeta"), labels)
    }

    @Test
    fun `searchActiveExercises filters by query prefix and excludes specific uuids`() = runTest {
        val benchUuid = Uuid.random()
        val benchPressUuid = Uuid.random()
        val rowUuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = benchUuid, name = "Bench"))
        repository.saveItem(exerciseChange(uuid = benchPressUuid, name = "Bench Press"))
        repository.saveItem(exerciseChange(uuid = rowUuid, name = "Row"))

        val matchingBench = repository
            .searchActiveExercises(query = "bench", excludeUuids = emptySet())
            .map { it.name }
            .sorted()
        assertEquals(listOf("Bench", "Bench Press"), matchingBench)

        val excluded = repository
            .searchActiveExercises(
                query = "",
                excludeUuids = setOf(benchUuid.toString()),
            )
            .map { it.uuid }
        assertFalse(excluded.contains(benchUuid.toString()))
        assertTrue(excluded.contains(benchPressUuid.toString()))
        assertTrue(excluded.contains(rowUuid.toString()))
    }

    @Test
    fun `paged sources expose active rows, archived rows, and tag-filtered rows`() = runTest {
        val pushUuid = Uuid.random()
        val pullUuid = Uuid.random()
        val archivedUuid = Uuid.random()
        repository.saveItem(
            exerciseChange(uuid = pushUuid, name = "Push", labels = listOf("upper")),
        )
        repository.saveItem(
            exerciseChange(uuid = pullUuid, name = "Pull", labels = listOf("upper", "back")),
        )
        // Archived row is not visible in `exercises` / `pagedActiveByTags` / `pagedActiveWithStats`.
        repository.saveItem(
            exerciseChange(uuid = archivedUuid, name = "Sit-Up", archived = true),
        )

        val active = repository.exercises.asSnapshot().map { it.uuid }
        assertTrue(active.containsAll(listOf(pushUuid.toString(), pullUuid.toString())))
        assertFalse(active.contains(archivedUuid.toString()))

        val archived = repository.pagedArchived().asSnapshot().map { it.uuid }
        assertEquals(listOf(archivedUuid.toString()), archived)

        val backTagUuid = env.tagDao.observeAll().first().single { it.name == "back" }.uuid
        val backOnly = repository.pagedActiveByTags(setOf(backTagUuid.toString())).asSnapshot()
        assertEquals(listOf(pullUuid.toString()), backOnly.map { it.uuid })

        val emptyFilter = repository.pagedActiveByTags(emptySet()).asSnapshot()
        assertTrue(
            emptyFilter.map { it.uuid }
                .containsAll(listOf(pushUuid.toString(), pullUuid.toString())),
        )
    }

    @Test
    fun `pagedActiveWithStats includes session count, linked trainings count, and tag names`() =
        runTest {
            val exerciseUuid = Uuid.random()
            repository.saveItem(
                exerciseChange(
                    uuid = exerciseUuid,
                    name = "Bench Press",
                    labels = listOf("upper"),
                ),
            )
            // Wire one finished session that logs the exercise — bumps session_count.
            val trainingUuid = Uuid.random()
            env.trainingDao.insert(
                TrainingEntity(
                    uuid = trainingUuid,
                    name = "Push Day",
                    description = null,
                    isAdhoc = false,
                    archived = false,
                    createdAt = 0L,
                    archivedAt = null,
                ),
            )
            env.trainingExerciseDao.insert(
                listOf(
                    TrainingExerciseEntity(
                        trainingUuid = trainingUuid,
                        exerciseUuid = exerciseUuid,
                        position = 0,
                    ),
                ),
            )
            val sessionUuid = Uuid.random()
            env.sessionDao.insert(
                SessionEntity(
                    uuid = sessionUuid,
                    trainingUuid = trainingUuid,
                    state = SessionStateEntity.FINISHED,
                    startedAt = 0L,
                    finishedAt = 1_000L,
                ),
            )
            val performedUuid = Uuid.random()
            env.performedExerciseDao.insert(
                listOf(
                    PerformedExerciseEntity(
                        uuid = performedUuid,
                        sessionUuid = sessionUuid,
                        exerciseUuid = exerciseUuid,
                        position = 0,
                        skipped = false,
                    ),
                ),
            )
            env.setDao.insert(
                SetEntity(
                    uuid = Uuid.random(),
                    performedExerciseUuid = performedUuid,
                    position = 0,
                    reps = 5,
                    weight = 100.0,
                    type = SetTypeEntity.WORK,
                ),
            )

            val items = repository.pagedActiveWithStats(filterTagUuids = emptySet())
                .asSnapshot()
            val row = items.single { it.data.uuid == exerciseUuid.toString() }
            assertEquals(1, row.sessionCount)
            assertEquals(1, row.linkedTrainingsCount)
            assertEquals(1_000L, row.lastTrainedAt)
            assertEquals(listOf("upper"), row.tags)
        }

    @Test
    fun `observeArchivedCount emits the live count`() = runTest {
        val uuidA = Uuid.random()
        val uuidB = Uuid.random()
        repository.saveItem(exerciseChange(uuid = uuidA, name = "A", archived = true))
        repository.saveItem(exerciseChange(uuid = uuidB, name = "B", archived = false))

        assertEquals(1, repository.observeArchivedCount().first())
    }

    @Test
    fun `countSessionsUsing counts finished sessions referencing the exercise`() = runTest {
        val exerciseUuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = exerciseUuid, name = "Bench"))
        val trainingUuid = Uuid.random()
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        repeat(2) { idx ->
            val sessionUuid = Uuid.random()
            env.sessionDao.insert(
                SessionEntity(
                    uuid = sessionUuid,
                    trainingUuid = trainingUuid,
                    state = SessionStateEntity.FINISHED,
                    startedAt = 0L,
                    finishedAt = (idx + 1).toLong() * 1_000L,
                ),
            )
            env.performedExerciseDao.insert(
                listOf(
                    PerformedExerciseEntity(
                        uuid = Uuid.random(),
                        sessionUuid = sessionUuid,
                        exerciseUuid = exerciseUuid,
                        position = 0,
                        skipped = false,
                    ),
                ),
            )
        }

        assertEquals(2, repository.countSessionsUsing(exerciseUuid.toString()))
    }

    @Test
    fun `canArchive returns true when no active template uses the exercise and false otherwise`() =
        runTest {
            val exerciseUuid = Uuid.random()
            val trainingUuid = Uuid.random()
            repository.saveItem(exerciseChange(uuid = exerciseUuid, name = "Bench"))
            env.trainingDao.insert(
                TrainingEntity(
                    uuid = trainingUuid,
                    name = "Push",
                    description = null,
                    isAdhoc = false,
                    archived = false,
                    createdAt = 0L,
                    archivedAt = null,
                ),
            )

            assertTrue(repository.canArchive(exerciseUuid.toString()))

            env.trainingExerciseDao.insert(
                listOf(
                    TrainingExerciseEntity(
                        trainingUuid = trainingUuid,
                        exerciseUuid = exerciseUuid,
                        position = 0,
                    ),
                ),
            )
            assertFalse(repository.canArchive(exerciseUuid.toString()))
        }

    @Test
    fun `canPermanentlyDeleteImmediately is true when no sessions or active templates reference exercise`() =
        runTest {
            val exerciseUuid = Uuid.random()
            repository.saveItem(exerciseChange(uuid = exerciseUuid, name = "Bench"))

            assertTrue(repository.canPermanentlyDeleteImmediately(exerciseUuid.toString()))

            // Add an active template referencing the exercise — should now be false.
            val trainingUuid = Uuid.random()
            env.trainingDao.insert(
                TrainingEntity(
                    uuid = trainingUuid,
                    name = "Push",
                    description = null,
                    isAdhoc = false,
                    archived = false,
                    createdAt = 0L,
                    archivedAt = null,
                ),
            )
            env.trainingExerciseDao.insert(
                listOf(
                    TrainingExerciseEntity(
                        trainingUuid = trainingUuid,
                        exerciseUuid = exerciseUuid,
                        position = 0,
                    ),
                ),
            )
            assertFalse(repository.canPermanentlyDeleteImmediately(exerciseUuid.toString()))
        }

    @Test
    fun `getActiveTrainingsUsing returns names of templates that reference the exercise`() = runTest {
        val exerciseUuid = Uuid.random()
        val trainingUuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = exerciseUuid, name = "Bench"))
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push Day",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        env.trainingExerciseDao.insert(
            listOf(
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                ),
            ),
        )

        val names = repository.getActiveTrainingsUsing(exerciseUuid.toString())
        assertEquals(listOf("Push Day"), names)
    }

    @Test
    fun `observeLinkedTrainingsCount emits the count from real DB`() = runTest {
        val exerciseUuid = Uuid.random()
        val trainingUuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = exerciseUuid, name = "Bench"))
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        env.trainingExerciseDao.insert(
            listOf(
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                ),
            ),
        )

        assertEquals(1, repository.observeLinkedTrainingsCount(exerciseUuid.toString()).first())
    }

    @Test
    fun `observeLastTrainedAt emits the most recent finished_at and null when absent`() = runTest {
        val exerciseUuid = Uuid.random()
        val trainingUuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = exerciseUuid, name = "Bench"))
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )

        assertNull(repository.observeLastTrainedAt(exerciseUuid.toString()).first())

        // Two finished sessions; observeLastTrainedAt should report the newer.
        val firstSession = Uuid.random()
        val secondSession = Uuid.random()
        env.sessionDao.insert(
            SessionEntity(
                uuid = firstSession,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 1_000L,
            ),
        )
        env.sessionDao.insert(
            SessionEntity(
                uuid = secondSession,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 2_000L,
            ),
        )
        env.performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = Uuid.random(),
                    sessionUuid = firstSession,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    skipped = false,
                ),
                PerformedExerciseEntity(
                    uuid = Uuid.random(),
                    sessionUuid = secondSession,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    skipped = false,
                ),
            ),
        )

        assertEquals(2_000L, repository.observeLastTrainedAt(exerciseUuid.toString()).first())
    }

    @Test
    fun `getLastTrainedExerciseUuid returns most-recent or null`() = runTest {
        assertNull(repository.getLastTrainedExerciseUuid())

        val exerciseUuid = Uuid.random()
        val trainingUuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = exerciseUuid, name = "Bench"))
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        val sessionUuid = Uuid.random()
        env.sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 1_000L,
            ),
        )
        env.performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = Uuid.random(),
                    sessionUuid = sessionUuid,
                    exerciseUuid = exerciseUuid,
                    position = 0,
                    skipped = false,
                ),
            ),
        )

        assertEquals(exerciseUuid.toString(), repository.getLastTrainedExerciseUuid())
    }

    @Test
    fun `getRecentlyTrainedExercises only returns exercises with at least one logged set`() = runTest {
        val withSetUuid = Uuid.random()
        val emptyUuid = Uuid.random()
        val trainingUuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = withSetUuid, name = "WithSet"))
        repository.saveItem(exerciseChange(uuid = emptyUuid, name = "Empty"))
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        val sessionUuid = Uuid.random()
        env.sessionDao.insert(
            SessionEntity(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 1_000L,
            ),
        )
        val withSetPerformed = Uuid.random()
        val emptyPerformed = Uuid.random()
        env.performedExerciseDao.insert(
            listOf(
                PerformedExerciseEntity(
                    uuid = withSetPerformed,
                    sessionUuid = sessionUuid,
                    exerciseUuid = withSetUuid,
                    position = 0,
                    skipped = false,
                ),
                PerformedExerciseEntity(
                    uuid = emptyPerformed,
                    sessionUuid = sessionUuid,
                    exerciseUuid = emptyUuid,
                    position = 1,
                    skipped = false,
                ),
            ),
        )
        env.setDao.insert(
            SetEntity(
                uuid = Uuid.random(),
                performedExerciseUuid = withSetPerformed,
                position = 0,
                reps = 5,
                weight = 100.0,
                type = SetTypeEntity.WORK,
            ),
        )

        val recent = repository.getRecentlyTrainedExercises().map { it.uuid }
        assertEquals(listOf(withSetUuid.toString()), recent)
    }

    @Test
    fun `getRecentHistory returns most recent finished sessions first with sets attached`() = runTest {
        val exerciseUuid = Uuid.random()
        val trainingUuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = exerciseUuid, name = "Bench"))
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        listOf(1_000L, 2_000L, 3_000L).forEach { ts ->
            val sessionUuid = Uuid.random()
            val performedUuid = Uuid.random()
            env.sessionDao.insert(
                SessionEntity(
                    uuid = sessionUuid,
                    trainingUuid = trainingUuid,
                    state = SessionStateEntity.FINISHED,
                    startedAt = 0L,
                    finishedAt = ts,
                ),
            )
            env.performedExerciseDao.insert(
                listOf(
                    PerformedExerciseEntity(
                        uuid = performedUuid,
                        sessionUuid = sessionUuid,
                        exerciseUuid = exerciseUuid,
                        position = 0,
                        skipped = false,
                    ),
                ),
            )
            env.setDao.insert(
                SetEntity(
                    uuid = Uuid.random(),
                    performedExerciseUuid = performedUuid,
                    position = 0,
                    reps = 5,
                    weight = ts.toDouble(),
                    type = SetTypeEntity.WORK,
                ),
            )
        }

        val history = repository.getRecentHistory(exerciseUuid.toString(), limit = 2)
        assertEquals(2, history.size)
        assertEquals(listOf(3_000L, 2_000L), history.map { it.finishedAt })
        assertTrue(history.all { it.sets.isNotEmpty() })
    }

    @Test
    fun `bulkArchive archives allowed exercises and surfaces blocked names`() = runTest {
        val freeUuid = Uuid.random()
        val blockedUuid = Uuid.random()
        val trainingUuid = Uuid.random()
        repository.saveItem(exerciseChange(uuid = freeUuid, name = "Free"))
        repository.saveItem(exerciseChange(uuid = blockedUuid, name = "Blocked"))
        env.trainingDao.insert(
            TrainingEntity(
                uuid = trainingUuid,
                name = "Push",
                description = null,
                isAdhoc = false,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
            ),
        )
        env.trainingExerciseDao.insert(
            listOf(
                TrainingExerciseEntity(
                    trainingUuid = trainingUuid,
                    exerciseUuid = blockedUuid,
                    position = 0,
                ),
            ),
        )

        val outcome = repository.bulkArchive(setOf(freeUuid.toString(), blockedUuid.toString()))

        assertEquals(1, outcome.archivedCount)
        assertEquals(listOf("Blocked"), outcome.blockedNames)
        assertTrue(env.exerciseDao.getById(freeUuid)?.archived == true)
        assertTrue(env.exerciseDao.getById(blockedUuid)?.archived == false)
    }

    @Test
    fun `bulkPermanentDelete removes rows and deletes any image files`() = runTest {
        val withImage = Uuid.random()
        val withoutImage = Uuid.random()
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = withImage,
                name = "WithImage",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = "/files/with.jpg",
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = withoutImage,
                name = "WithoutImage",
                type = ExerciseTypeEntity.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                createdAt = 0L,
                archivedAt = null,
                lastAdhocSets = null,
            ),
        )

        repository.bulkPermanentDelete(setOf(withImage.toString(), withoutImage.toString()))

        assertNull(env.exerciseDao.getById(withImage))
        assertNull(env.exerciseDao.getById(withoutImage))
        coVerify(exactly = 1) { imageStorage.deleteImage("/files/with.jpg") }
    }

    @Test
    fun `canBulkPermanentDelete returns false for empty input and reflects per-uuid checks`() =
        runTest {
            assertFalse(repository.canBulkPermanentDelete(emptySet()))

            val freshUuid = Uuid.random()
            repository.saveItem(exerciseChange(uuid = freshUuid, name = "Fresh"))
            assertTrue(repository.canBulkPermanentDelete(setOf(freshUuid.toString())))

            // Add a session referencing the exercise — should now be false.
            val trainingUuid = Uuid.random()
            env.trainingDao.insert(
                TrainingEntity(
                    uuid = trainingUuid,
                    name = "Push",
                    description = null,
                    isAdhoc = false,
                    archived = false,
                    createdAt = 0L,
                    archivedAt = null,
                ),
            )
            val sessionUuid = Uuid.random()
            env.sessionDao.insert(
                SessionEntity(
                    uuid = sessionUuid,
                    trainingUuid = trainingUuid,
                    state = SessionStateEntity.FINISHED,
                    startedAt = 0L,
                    finishedAt = 1_000L,
                ),
            )
            env.performedExerciseDao.insert(
                listOf(
                    PerformedExerciseEntity(
                        uuid = Uuid.random(),
                        sessionUuid = sessionUuid,
                        exerciseUuid = freshUuid,
                        position = 0,
                        skipped = false,
                    ),
                ),
            )
            assertFalse(repository.canBulkPermanentDelete(setOf(freshUuid.toString())))
        }

    @Test
    fun `getUniqueExercises mirrors exercises stream regardless of query`() = runTest {
        // FIXME(behavior-question): ExerciseRepositoryImpl.getUniqueExercises ignores its
        // `query` arg and returns the same stream as `exercises`. Test pins current behavior.
        val a = Uuid.random()
        val b = Uuid.random()
        repository.saveItem(exerciseChange(uuid = a, name = "Alpha"))
        repository.saveItem(exerciseChange(uuid = b, name = "Beta"))

        val unique = repository.getUniqueExercises("doesnotmatter").asSnapshot()
        assertEquals(2, unique.size)
        assertTrue(unique.map { it.uuid }.containsAll(listOf(a.toString(), b.toString())))
    }

    private fun exerciseChange(
        uuid: Uuid,
        name: String,
        description: String? = null,
        labels: List<String> = emptyList(),
        archived: Boolean = false,
        lastAdhoc: List<PlanSetDataModel>? = null,
    ): ExerciseChangeDataModel = ExerciseChangeDataModel(
        uuid = uuid,
        name = name,
        type = ExerciseTypeDataModel.WEIGHTED,
        description = description,
        imagePath = null,
        archived = archived,
        timestamp = 0L,
        labels = labels,
        lastAdHocSets = lastAdhoc,
    )
}
