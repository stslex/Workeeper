// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.data.exercise.training

import androidx.paging.testing.asSnapshot
import io.github.stslex.workeeper.core.data.database.converters.PlanSetsConverter
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseEntity
import io.github.stslex.workeeper.core.data.database.exercise.ExerciseTypeEntity
import io.github.stslex.workeeper.core.data.database.session.SessionEntity
import io.github.stslex.workeeper.core.data.database.session.SessionStateEntity
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.database.tag.ExerciseTagEntity
import io.github.stslex.workeeper.core.data.database.tag.TagEntity
import io.github.stslex.workeeper.core.data.database.testfixtures.RepositoryTestEnv
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.data.database.training.TrainingExerciseEntity
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
@Config(application = RepositoryTestEnv.TestApplication::class, sdk = [33])
internal class TrainingRepositoryImplDbTest {

    private lateinit var env: RepositoryTestEnv
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var repository: TrainingRepositoryImpl

    @BeforeEach
    fun setup() {
        env = RepositoryTestEnv()
        exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
        // Default: no plan inheritance from last_adhoc_sets unless a test overrides.
        coEvery { exerciseRepository.getAdhocPlan(any()) } returns null
        repository = TrainingRepositoryImpl(
            dao = env.trainingDao,
            trainingExerciseDao = env.trainingExerciseDao,
            tagDao = env.tagDao,
            trainingTagDao = env.trainingTagDao,
            sessionDao = env.sessionDao,
            exerciseRepository = exerciseRepository,
            ioDispatcher = UnconfinedTestDispatcher(),
            dbTransition = env.transition,
        )
    }

    @AfterEach
    fun teardown() {
        env.close()
    }

    @Test
    fun `updateTraining inserts a new training row when no existing record matches`() = runTest {
        val uuid = Uuid.random()

        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = uuid.toString(),
                name = "Push Day",
                description = "Chest+Shoulders",
                timestamp = 1_000L,
            ),
        )

        val persisted = env.trainingDao.getById(uuid)
        assertNotNull(persisted)
        assertEquals("Push Day", persisted?.name)
        assertEquals("Chest+Shoulders", persisted?.description)
        assertEquals(false, persisted?.isAdhoc)
    }

    @Test
    fun `updateTraining updates existing training fields and replaces label set`() = runTest {
        val uuid = Uuid.random()
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = uuid.toString(),
                name = "Push",
                timestamp = 1L,
                labels = listOf("upper", "morning"),
            ),
        )

        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = uuid.toString(),
                name = "Push Day",
                description = "renamed",
                timestamp = 1L,
                labels = listOf("upper", "evening"),
            ),
        )

        val persisted = env.trainingDao.getById(uuid)
        assertEquals("Push Day", persisted?.name)
        assertEquals("renamed", persisted?.description)

        val labels = env.trainingTagDao.getTagNames(uuid).sorted()
        assertEquals(listOf("evening", "upper"), labels)
    }

    @Test
    fun `updateTraining preserves existing plan_sets across re-saves and inherits when newly attached`() =
        runTest {
            val trainingUuid = Uuid.random()
            val keptExerciseUuid = Uuid.random()
            val newExerciseUuid = Uuid.random()
            val keptExerciseUuidStr = keptExerciseUuid.toString()
            val newExerciseUuidStr = newExerciseUuid.toString()

            // Seed library exercises so the FK from training_exercise resolves.
            seedLibraryExercise(keptExerciseUuid, "Bench")
            seedLibraryExercise(newExerciseUuid, "Press")

            val firstSave = TrainingChangeDataModel(
                uuid = trainingUuid.toString(),
                name = "Push",
                timestamp = 1L,
                exerciseUuids = listOf(keptExerciseUuidStr),
            )
            // A first save is a new attachment, so the plan is inherited from adhocPlan.
            coEvery { exerciseRepository.getAdhocPlan(keptExerciseUuidStr) } returns listOf(
                PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
            )
            repository.updateTraining(firstSave)

            val initialPlan = env.trainingExerciseDao.getPlanSets(trainingUuid, keptExerciseUuid)
            assertNotNull(initialPlan)
            assertTrue(initialPlan!!.contains("\"weight\":100.0"))

            coEvery { exerciseRepository.getAdhocPlan(keptExerciseUuidStr) } returns listOf(
                PlanSetDataModel(weight = 999.0, reps = 1, type = SetTypeDataModel.WORK),
            )
            coEvery { exerciseRepository.getAdhocPlan(newExerciseUuidStr) } returns listOf(
                PlanSetDataModel(weight = 60.0, reps = 8, type = SetTypeDataModel.WORK),
            )
            repository.updateTraining(
                firstSave.copy(exerciseUuids = listOf(keptExerciseUuidStr, newExerciseUuidStr)),
            )

            val keptPlan = env.trainingExerciseDao.getPlanSets(trainingUuid, keptExerciseUuid)
            assertTrue(keptPlan!!.contains("\"weight\":100.0"))
            assertFalse(keptPlan.contains("999.0"))

            val newPlan = env.trainingExerciseDao.getPlanSets(trainingUuid, newExerciseUuid)
            assertTrue(newPlan!!.contains("\"weight\":60.0"))
        }

    @Test
    fun `updateTraining preserves an explicitly empty plan_sets and does not refall back to adhocPlan`() =
        runTest {
            val trainingUuid = Uuid.random()
            val exerciseUuid = Uuid.random()
            seedLibraryExercise(exerciseUuid, "Bench")
            // Seed a user-cleared `[]` plan by hand, since updateTraining is the SUT.
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
                        planSets = "[]",
                    ),
                ),
            )
            // A non-null adhocPlan must not overwrite the existing empty plan.
            coEvery { exerciseRepository.getAdhocPlan(exerciseUuid.toString()) } returns listOf(
                PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK),
            )

            repository.updateTraining(
                TrainingChangeDataModel(
                    uuid = trainingUuid.toString(),
                    name = "Push",
                    timestamp = 0L,
                    exerciseUuids = listOf(exerciseUuid.toString()),
                ),
            )

            assertEquals("[]", env.trainingExerciseDao.getPlanSets(trainingUuid, exerciseUuid))
        }

    @Test
    fun `updateTraining preserves an existing null plan_sets across re-saves`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedLibraryExercise(exerciseUuid, "Bench")
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
                    planSets = null,
                ),
            ),
        )
        coEvery { exerciseRepository.getAdhocPlan(exerciseUuid.toString()) } returns listOf(
            PlanSetDataModel(weight = 60.0, reps = 12, type = SetTypeDataModel.WORK),
        )

        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = trainingUuid.toString(),
                name = "Push",
                timestamp = 0L,
                exerciseUuids = listOf(exerciseUuid.toString()),
            ),
        )

        // A deliberately-cleared row keeps its null; adhocPlan is not re-applied late.
        assertNull(env.trainingExerciseDao.getPlanSets(trainingUuid, exerciseUuid))
    }

    @Test
    fun `updateName changes name without touching label or exercise tables`() = runTest {
        val uuid = Uuid.random()
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = uuid.toString(),
                name = "Original",
                timestamp = 0L,
                labels = listOf("tag1"),
            ),
        )
        val labelsBefore = env.trainingTagDao.getTagNames(uuid)

        repository.updateName(uuid.toString(), "Updated Header")

        assertEquals("Updated Header", env.trainingDao.getById(uuid)?.name)
        assertEquals(labelsBefore, env.trainingTagDao.getTagNames(uuid))
    }

    @Test
    fun `removeTraining and permanentDelete delete the row from the table`() = runTest {
        val uuid = Uuid.random()
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = uuid.toString(),
                name = "Push",
                timestamp = 0L,
            ),
        )
        repository.removeTraining(uuid.toString())
        assertNull(env.trainingDao.getById(uuid))

        val secondUuid = Uuid.random()
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = secondUuid.toString(),
                name = "Pull",
                timestamp = 0L,
            ),
        )
        repository.permanentDelete(secondUuid.toString())
        assertNull(env.trainingDao.getById(secondUuid))
    }

    @Test
    fun `removeAll deletes the listed trainings in one call`() = runTest {
        val a = Uuid.random()
        val b = Uuid.random()
        val survivor = Uuid.random()
        listOf(a, b, survivor).forEach { uuid ->
            repository.updateTraining(
                TrainingChangeDataModel(
                    uuid = uuid.toString(),
                    name = "name-$uuid",
                    timestamp = 0L,
                ),
            )
        }

        repository.removeAll(listOf(a.toString(), b.toString()))

        assertNull(env.trainingDao.getById(a))
        assertNull(env.trainingDao.getById(b))
        assertNotNull(env.trainingDao.getById(survivor))
    }

    // The fixture nests `coroutineScope` inside `withTransaction`, so the `async` children
    // inherit Room's `TransactionElement` and reuse the connection. See documentation/testing.md.
    @Test
    fun `getTraining returns the data model with labels and exerciseUuids`() = runTest {
        val trainingUuid = Uuid.random()
        val firstExerciseUuid = Uuid.random()
        val secondExerciseUuid = Uuid.random()
        seedLibraryExercise(firstExerciseUuid, "First")
        seedLibraryExercise(secondExerciseUuid, "Second")

        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = trainingUuid.toString(),
                name = "Push Day",
                timestamp = 1L,
                labels = listOf("upper"),
                exerciseUuids = listOf(
                    firstExerciseUuid.toString(),
                    secondExerciseUuid.toString(),
                ),
            ),
        )

        val result = repository.getTraining(trainingUuid.toString())

        assertNotNull(result)
        assertEquals("Push Day", result?.name)
        assertEquals(listOf("upper"), result?.labels)
        assertEquals(
            listOf(firstExerciseUuid.toString(), secondExerciseUuid.toString()),
            result?.exerciseUuids,
        )
    }

    @Test
    fun `getTraining returns null when the row does not exist`() = runTest {
        assertNull(repository.getTraining(Uuid.random().toString()))
    }

    @Test
    fun `getTraining returns the data model with empty labels when the training has no tags`() =
        runTest {
            val trainingUuid = Uuid.random()
            val exerciseUuid = Uuid.random()
            seedLibraryExercise(exerciseUuid, "Bench")
            repository.updateTraining(
                TrainingChangeDataModel(
                    uuid = trainingUuid.toString(),
                    name = "Push Day",
                    timestamp = 1L,
                    labels = emptyList(),
                    exerciseUuids = listOf(exerciseUuid.toString()),
                ),
            )

            val result = repository.getTraining(trainingUuid.toString())

            assertNotNull(result)
            assertEquals("Push Day", result?.name)
            assertEquals(emptyList<String>(), result?.labels)
            assertEquals(listOf(exerciseUuid.toString()), result?.exerciseUuids)
        }

    @Test
    fun `getTraining returns the data model with empty exerciseUuids when the training has no exercises`() =
        runTest {
            val trainingUuid = Uuid.random()
            repository.updateTraining(
                TrainingChangeDataModel(
                    uuid = trainingUuid.toString(),
                    name = "Pull Day",
                    timestamp = 1L,
                    labels = listOf("upper", "back"),
                    exerciseUuids = emptyList(),
                ),
            )

            val result = repository.getTraining(trainingUuid.toString())

            assertNotNull(result)
            assertEquals("Pull Day", result?.name)
            assertEquals(listOf("upper", "back").sorted(), result?.labels?.sorted())
            assertEquals(emptyList<String>(), result?.exerciseUuids)
        }

    @Test
    fun `subscribeForTraining emits the data model and falls back to a placeholder for missing rows`() =
        runTest {
            val missingUuid = Uuid.random()
            val missing = repository.subscribeForTraining(missingUuid.toString()).first()
            assertEquals(missingUuid.toString(), missing.uuid)
            assertEquals("", missing.name)

            val realUuid = Uuid.random()
            repository.updateTraining(
                TrainingChangeDataModel(
                    uuid = realUuid.toString(),
                    name = "Push",
                    timestamp = 1L,
                    labels = listOf("upper"),
                ),
            )
            val emitted = repository.subscribeForTraining(realUuid.toString()).first()
            assertEquals("Push", emitted.name)
            assertEquals(listOf("upper"), emitted.labels)
        }

    @Test
    fun `archive flips archived and writes archivedAt and restore reverses`() = runTest {
        val uuid = Uuid.random()
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = uuid.toString(),
                name = "Push",
                timestamp = 0L,
            ),
        )

        repository.archive(uuid.toString())
        var reloaded = env.trainingDao.getById(uuid)
        assertTrue(reloaded?.archived == true)
        assertNotNull(reloaded?.archivedAt)

        repository.restore(uuid.toString())
        reloaded = env.trainingDao.getById(uuid)
        assertEquals(false, reloaded?.archived)
        assertNull(reloaded?.archivedAt)
    }

    @Test
    fun `pagedArchived emits archived rows, getTrainingsUnique emits active rows`() = runTest {
        val activeUuid = Uuid.random()
        val archivedUuid = Uuid.random()
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = activeUuid.toString(),
                name = "Active",
                timestamp = 0L,
            ),
        )
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = archivedUuid.toString(),
                name = "Archived",
                archived = true,
                timestamp = 0L,
            ),
        )

        val activeSnap = repository.getTrainingsUnique("any").asSnapshot().map { it.uuid }
        assertEquals(listOf(activeUuid.toString()), activeSnap)

        val archivedSnap = repository.pagedArchived().asSnapshot().map { it.uuid }
        assertEquals(listOf(archivedUuid.toString()), archivedSnap)
    }

    @Test
    fun `observeArchivedCount returns the live count of archived rows`() = runTest {
        val first = Uuid.random()
        val second = Uuid.random()
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = first.toString(),
                name = "First",
                archived = true,
                timestamp = 0L,
            ),
        )
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = second.toString(),
                name = "Second",
                archived = false,
                timestamp = 0L,
            ),
        )

        assertEquals(1, repository.observeArchivedCount().first())
    }

    @Test
    fun `countSessionsUsing returns count of finished sessions for a training`() = runTest {
        val trainingUuid = Uuid.random()
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = trainingUuid.toString(),
                name = "Push",
                timestamp = 0L,
            ),
        )
        repeat(3) { idx ->
            env.sessionDao.insert(
                SessionEntity(
                    uuid = Uuid.random(),
                    trainingUuid = trainingUuid,
                    state = SessionStateEntity.FINISHED,
                    startedAt = 0L,
                    finishedAt = (idx + 1).toLong() * 1_000L,
                ),
            )
        }
        env.sessionDao.insert(
            SessionEntity(
                uuid = Uuid.random(),
                trainingUuid = trainingUuid,
                state = SessionStateEntity.IN_PROGRESS,
                startedAt = 0L,
                finishedAt = null,
            ),
        )

        assertEquals(3, repository.countSessionsUsing(trainingUuid.toString()))
    }

    @Test
    fun `pagedActiveWithStats includes derived stats and respects empty-tags filter`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedLibraryExercise(exerciseUuid, "Bench")
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = trainingUuid.toString(),
                name = "Push Day",
                timestamp = 0L,
                labels = listOf("upper"),
                exerciseUuids = listOf(exerciseUuid.toString()),
            ),
        )
        env.sessionDao.insert(
            SessionEntity(
                uuid = Uuid.random(),
                trainingUuid = trainingUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 4_000L,
            ),
        )
        val activeSessionUuid = Uuid.random()
        env.sessionDao.insert(
            SessionEntity(
                uuid = activeSessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateEntity.IN_PROGRESS,
                startedAt = 5_000L,
                finishedAt = null,
            ),
        )

        val items = repository.pagedActiveWithStats(filterTagUuids = emptySet()).asSnapshot()
        val row = items.single { it.data.uuid == trainingUuid.toString() }
        assertEquals(1, row.exerciseCount)
        assertEquals(4_000L, row.lastSessionAt)
        assertTrue(row.isActive)
        assertEquals(activeSessionUuid.toString(), row.activeSessionUuid)
        assertEquals(5_000L, row.activeSessionStartedAt)
        assertEquals(listOf("upper"), row.data.labels)
    }

    @Test
    fun `observeRecentTemplates returns trainings ordered by recency`() = runTest {
        val olderUuid = Uuid.random()
        val newerUuid = Uuid.random()
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = olderUuid.toString(),
                name = "Older",
                timestamp = 0L,
            ),
        )
        repository.updateTraining(
            TrainingChangeDataModel(
                uuid = newerUuid.toString(),
                name = "Newer",
                timestamp = 0L,
            ),
        )
        env.sessionDao.insert(
            SessionEntity(
                uuid = Uuid.random(),
                trainingUuid = olderUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 1_000L,
            ),
        )
        env.sessionDao.insert(
            SessionEntity(
                uuid = Uuid.random(),
                trainingUuid = newerUuid,
                state = SessionStateEntity.FINISHED,
                startedAt = 0L,
                finishedAt = 2_000L,
            ),
        )

        val list = repository.observeRecentTemplates(limit = 5).first()
        assertEquals(
            listOf(newerUuid.toString(), olderUuid.toString()),
            list.map { it.data.uuid },
        )
    }

    @Test
    fun `bulkArchive archives allowed and surfaces blocked names when active session present`() =
        runTest {
            val freeUuid = Uuid.random()
            val blockedUuid = Uuid.random()
            repository.updateTraining(
                TrainingChangeDataModel(
                    uuid = freeUuid.toString(),
                    name = "Free",
                    timestamp = 0L,
                ),
            )
            repository.updateTraining(
                TrainingChangeDataModel(
                    uuid = blockedUuid.toString(),
                    name = "Blocked",
                    timestamp = 0L,
                ),
            )
            env.sessionDao.insert(
                SessionEntity(
                    uuid = Uuid.random(),
                    trainingUuid = blockedUuid,
                    state = SessionStateEntity.IN_PROGRESS,
                    startedAt = 0L,
                    finishedAt = null,
                ),
            )

            val outcome = repository.bulkArchive(setOf(freeUuid.toString(), blockedUuid.toString()))

            assertEquals(1, outcome.archivedCount)
            assertEquals(listOf("Blocked"), outcome.blockedNames)
            assertEquals(true, env.trainingDao.getById(freeUuid)?.archived)
            assertEquals(false, env.trainingDao.getById(blockedUuid)?.archived)
        }

    @Test
    fun `bulkArchive with an empty input is a no-op`() = runTest {
        val outcome = repository.bulkArchive(emptySet())

        assertEquals(0, outcome.archivedCount)
        assertTrue(outcome.blockedNames.isEmpty())
    }

    @Test
    fun `bulkPermanentDelete removes the listed rows`() = runTest {
        val a = Uuid.random()
        val b = Uuid.random()
        repository.updateTraining(
            TrainingChangeDataModel(uuid = a.toString(), name = "A", timestamp = 0L),
        )
        repository.updateTraining(
            TrainingChangeDataModel(uuid = b.toString(), name = "B", timestamp = 0L),
        )

        repository.bulkPermanentDelete(setOf(a.toString(), b.toString()))

        assertNull(env.trainingDao.getById(a))
        assertNull(env.trainingDao.getById(b))
    }

    @Test
    fun `canBulkPermanentDelete returns false for empty input or for active or finished trainings`() =
        runTest {
            assertFalse(repository.canBulkPermanentDelete(emptySet()))

            val cleanUuid = Uuid.random()
            repository.updateTraining(
                TrainingChangeDataModel(
                    uuid = cleanUuid.toString(),
                    name = "Clean",
                    timestamp = 0L,
                ),
            )
            assertTrue(repository.canBulkPermanentDelete(setOf(cleanUuid.toString())))

            env.sessionDao.insert(
                SessionEntity(
                    uuid = Uuid.random(),
                    trainingUuid = cleanUuid,
                    state = SessionStateEntity.FINISHED,
                    startedAt = 0L,
                    finishedAt = 1_000L,
                ),
            )
            assertFalse(repository.canBulkPermanentDelete(setOf(cleanUuid.toString())))
        }

    /** The training row and every listed plan commit in one transaction — happy path. */
    @Test
    fun `updateTrainingWithPlans persists the training and every plan`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedLibraryExercise(exerciseUuid, "Bench")

        repository.updateTrainingWithPlans(
            training = TrainingChangeDataModel(
                uuid = trainingUuid.toString(),
                name = "Push Day",
                timestamp = 1_000L,
                exerciseUuids = listOf(exerciseUuid.toString()),
            ),
            plans = listOf(
                TrainingRepository.ExercisePlanWrite(
                    exerciseUuid = exerciseUuid.toString(),
                    planSets = listOf(
                        PlanSetDataModel(weight = 60.0, reps = 10, type = SetTypeDataModel.WORK),
                    ),
                ),
            ),
        )

        assertNotNull(env.trainingDao.getById(trainingUuid))
        val row = env.trainingExerciseDao.getByTraining(trainingUuid).single()
        val persistedPlan = PlanSetsConverter.fromJson(row.planSets)
        assertEquals(1, persistedPlan?.size)
        assertEquals(60.0, persistedPlan?.single()?.weight)
        assertEquals(10, persistedPlan?.single()?.reps)
    }

    /**
     * Auto-prune on the training save path, with a tag held alive only by an exercise link —
     * the fixture that covers the `exercise_tag_table` conjunct on its own.
     */
    @Test
    fun `updateTrainingWithPlans prunes its orphaned tag and keeps the exercise-held one`() =
        runTest {
            val exerciseUuid = Uuid.random()
            seedLibraryExercise(exerciseUuid, "Bench")
            val sharedTag = TagEntity(name = "shared")
            env.tagDao.insert(sharedTag)
            env.exerciseTagDao.insert(
                listOf(ExerciseTagEntity(exerciseUuid = exerciseUuid, tagUuid = sharedTag.uuid)),
            )

            val trainingUuid = Uuid.random()
            repository.updateTrainingWithPlans(
                training = TrainingChangeDataModel(
                    uuid = trainingUuid.toString(),
                    name = "Push Day",
                    timestamp = 1_000L,
                    labels = listOf("shared", "solo"),
                ),
                plans = emptyList(),
            )
            repository.updateTrainingWithPlans(
                training = TrainingChangeDataModel(
                    uuid = trainingUuid.toString(),
                    name = "Push Day",
                    timestamp = 2_000L,
                    labels = emptyList(),
                ),
                plans = emptyList(),
            )

            val tagNames = env.tagDao.observeAll().first().map { it.name }
            assertEquals(listOf("shared"), tagNames)
        }

    /**
     * The transactional guarantee: the plan write throws after the rows are written and
     * nothing survives. The DAO spy keeps every other call real.
     */
    @Test
    fun `updateTrainingWithPlans leaves nothing behind when a plan write throws`() = runTest {
        val trainingUuid = Uuid.random()
        val exerciseUuid = Uuid.random()
        seedLibraryExercise(exerciseUuid, "Bench")
        val throwingDao = spyk(env.trainingExerciseDao)
        coEvery { throwingDao.updatePlanSets(any(), any(), any()) } throws
            IllegalStateException("simulated plan-write failure")
        val throwingRepository = TrainingRepositoryImpl(
            dao = env.trainingDao,
            trainingExerciseDao = throwingDao,
            tagDao = env.tagDao,
            trainingTagDao = env.trainingTagDao,
            sessionDao = env.sessionDao,
            exerciseRepository = exerciseRepository,
            ioDispatcher = UnconfinedTestDispatcher(),
            dbTransition = env.transition,
        )

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                throwingRepository.updateTrainingWithPlans(
                    training = TrainingChangeDataModel(
                        uuid = trainingUuid.toString(),
                        name = "Push Day",
                        timestamp = 1_000L,
                        exerciseUuids = listOf(exerciseUuid.toString()),
                    ),
                    plans = listOf(
                        TrainingRepository.ExercisePlanWrite(
                            exerciseUuid = exerciseUuid.toString(),
                            planSets = emptyList(),
                        ),
                    ),
                )
            }
        }

        assertNull(env.trainingDao.getById(trainingUuid))
        assertTrue(env.trainingExerciseDao.getByTraining(trainingUuid).isEmpty())
    }

    private suspend fun seedLibraryExercise(uuid: Uuid, name: String) {
        env.exerciseDao.insert(
            ExerciseEntity(
                uuid = uuid,
                name = name,
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

    @Suppress("unused")
    private fun unusedHelpers(): TrainingExerciseEntity = TrainingExerciseEntity(
        trainingUuid = Uuid.random(),
        exerciseUuid = Uuid.random(),
        position = 0,
    )
}
