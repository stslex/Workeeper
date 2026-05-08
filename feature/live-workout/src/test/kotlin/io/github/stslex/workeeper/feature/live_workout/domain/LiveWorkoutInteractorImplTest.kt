// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain

import io.github.stslex.workeeper.core.core.logger.FirebaseCrashlyticsHolder
import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository.InlineAdhocResult
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.session.model.PerformedExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionStateDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetTypeDomain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@Suppress("MaximumLineLength")
internal class LiveWorkoutInteractorImplTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val performedExerciseRepository = mockk<PerformedExerciseRepository>(relaxed = true)
    private val setRepository = mockk<SetRepository>(relaxed = true)
    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val trainingExerciseRepository = mockk<TrainingExerciseRepository>(relaxed = true)
    private val personalRecordRepository = mockk<PersonalRecordRepository>(relaxed = true).apply {
        every { observePersonalRecords(any()) } returns flowOf(emptyMap())
        every { observePersonalRecordsBatch(any()) } returns flowOf(emptyMap())
    }

    private val interactor = LiveWorkoutInteractorImpl(
        sessionRepository = sessionRepository,
        performedExerciseRepository = performedExerciseRepository,
        setRepository = setRepository,
        exerciseRepository = exerciseRepository,
        trainingRepository = trainingRepository,
        trainingExerciseRepository = trainingExerciseRepository,
        personalRecordRepository = personalRecordRepository,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @BeforeEach
    fun stubFirebaseCrashlytics() {
        // `loadSession`'s `traceExecutionTime` calls fan out to `Log.i { ... }`, which
        // unconditionally resolves the singleton `Firebase.crashlytics` and crashes
        // without an initialised `FirebaseApp`. Stub the holder so the logging path
        // is a no-op in tests.
        mockkObject(FirebaseCrashlyticsHolder)
        every { FirebaseCrashlyticsHolder.log(any()) } returns Unit
        every { FirebaseCrashlyticsHolder.recordException(any(), any()) } returns Unit
    }

    @AfterEach
    fun unstubFirebaseCrashlytics() {
        unmockkObject(FirebaseCrashlyticsHolder)
    }

    @Test
    fun `startSession reuses an in-progress session for the same training`() = runTest {
        val trainingUuid = "training-1"
        coEvery { sessionRepository.getAnyActiveSession() } returns
            io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo(
                sessionUuid = "session-existing",
                trainingUuid = trainingUuid,
                startedAt = 0L,
            )

        val resolved = interactor.startSession(trainingUuid)

        assertEquals("session-existing", resolved)
        coVerify(exactly = 0) {
            sessionRepository.startSessionWithExercises(any(), any())
        }
    }

    @Test
    fun `startSession seeds performed exercises ordered by position`() = runTest {
        val trainingUuid = "training-1"
        coEvery { sessionRepository.getAnyActiveSession() } returns null
        coEvery { trainingExerciseRepository.getRowsForTraining(trainingUuid) } returns listOf(
            TrainingExerciseRepository.TrainingExerciseRow(
                exerciseUuid = "ex-2",
                position = 1,
                planSets = null,
            ),
            TrainingExerciseRepository.TrainingExerciseRow(
                exerciseUuid = "ex-1",
                position = 0,
                planSets = null,
            ),
        )
        val captured = slot<List<Pair<String, Int>>>()
        coEvery {
            sessionRepository.startSessionWithExercises(eq(trainingUuid), capture(captured))
        } returns SessionDataModel(
            uuid = "session-new",
            trainingUuid = trainingUuid,
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 0L,
            finishedAt = null,
        )

        val resolved = interactor.startSession(trainingUuid)

        assertEquals("session-new", resolved)
        assertEquals(listOf("ex-1" to 0, "ex-2" to 1), captured.captured)
    }

    @Test
    fun `finishSession sends PlanUpdate per non-skipped exercise and finishes atomically`() =
        runTest {
            val sessionUuid = "session-1"
            val trainingUuid = "training-1"
            coEvery { sessionRepository.getById(sessionUuid) } returns SessionDataModel(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateDataModel.IN_PROGRESS,
                startedAt = 1_000L,
                finishedAt = null,
            )
            coEvery { trainingRepository.getTraining(trainingUuid) } returns TrainingDataModel(
                uuid = trainingUuid,
                name = "Push Day",
                description = null,
                isAdhoc = false,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                labels = emptyList(),
                exerciseUuids = listOf("ex-1", "ex-2"),
            )
            coEvery { performedExerciseRepository.getBySession(sessionUuid) } returns listOf(
                PerformedExerciseDataModel(
                    uuid = "pe-1",
                    sessionUuid = sessionUuid,
                    exerciseUuid = "ex-1",
                    position = 0,
                    skipped = false,
                ),
                PerformedExerciseDataModel(
                    uuid = "pe-2",
                    sessionUuid = sessionUuid,
                    exerciseUuid = "ex-2",
                    position = 1,
                    skipped = true,
                ),
            )
            coEvery { setRepository.getByPerformedExercise("pe-1") } returns listOf(
                SetsDataModel(
                    uuid = "s-1",
                    reps = 5,
                    weight = 100.0,
                    type = SetsDataType.WORK,
                    position = 0,
                ),
                SetsDataModel(
                    uuid = "s-2",
                    reps = 5,
                    weight = 100.0,
                    type = SetsDataType.WORK,
                    position = 1,
                ),
            )
            coEvery { trainingExerciseRepository.getPlan(trainingUuid, "ex-1") } returns listOf(
                PlanSetDataModel(weight = 90.0, reps = 5, type = SetTypeDataModel.WORK),
            )
            val captured = slot<List<PlanUpdate>>()
            coEvery {
                sessionRepository.finishSessionAtomic(
                    eq(sessionUuid),
                    any(),
                    capture(captured),
                    any(),
                )
            } returns true

            val result = interactor.finishSession(sessionUuid)

            // Only the non-skipped exercise contributes a PlanUpdate.
            assertEquals(1, captured.captured.size)
            val update = captured.captured.single()
            assertEquals("ex-1", update.exerciseUuid)
            assertEquals(false, update.isAdhoc)
            // PlanUpdateRule promotes the larger performed list to the new plan.
            assertEquals(
                listOf(
                    PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
                    PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK),
                ),
                update.newPlan,
            )

            assertEquals(2, result?.setsLogged)
            assertEquals(1, result?.doneCount)
            assertEquals(2, result?.totalCount)
            assertEquals(1, result?.skippedCount)
        }

    @Test
    fun `finishSession marks PlanUpdate as adhoc when training is adhoc`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        coEvery { sessionRepository.getById(sessionUuid) } returns SessionDataModel(
            uuid = sessionUuid,
            trainingUuid = trainingUuid,
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 1_000L,
            finishedAt = null,
        )
        coEvery { trainingRepository.getTraining(trainingUuid) } returns TrainingDataModel(
            uuid = trainingUuid,
            name = "Track now",
            description = null,
            isAdhoc = true,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            labels = emptyList(),
            exerciseUuids = listOf("ex-1"),
        )
        coEvery { performedExerciseRepository.getBySession(sessionUuid) } returns listOf(
            PerformedExerciseDataModel(
                uuid = "pe-1",
                sessionUuid = sessionUuid,
                exerciseUuid = "ex-1",
                position = 0,
                skipped = false,
            ),
        )
        coEvery { setRepository.getByPerformedExercise("pe-1") } returns listOf(
            SetsDataModel(
                uuid = "s-1",
                reps = 8,
                weight = 50.0,
                type = SetsDataType.WORK,
                position = 0,
            ),
        )
        coEvery { exerciseRepository.getAdhocPlan("ex-1") } returns null
        val captured = slot<List<PlanUpdate>>()
        coEvery {
            sessionRepository.finishSessionAtomic(eq(sessionUuid), any(), capture(captured), any())
        } returns true

        interactor.finishSession(sessionUuid)

        val update = captured.captured.single()
        assertEquals("ex-1", update.exerciseUuid)
        assertEquals(true, update.isAdhoc)
        assertEquals(
            listOf(PlanSetDataModel(weight = 50.0, reps = 8, type = SetTypeDataModel.WORK)),
            update.newPlan,
        )
    }

    @Test
    fun `finishSession returns null when session is gone after preload`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        coEvery { sessionRepository.getById(sessionUuid) } returns SessionDataModel(
            uuid = sessionUuid,
            trainingUuid = trainingUuid,
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 0L,
            finishedAt = null,
        )
        coEvery { trainingRepository.getTraining(trainingUuid) } returns TrainingDataModel(
            uuid = trainingUuid,
            name = "Push Day",
            description = null,
            isAdhoc = false,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            labels = emptyList(),
            exerciseUuids = emptyList(),
        )
        coEvery { performedExerciseRepository.getBySession(sessionUuid) } returns emptyList()
        coEvery {
            sessionRepository.finishSessionAtomic(any(), any(), any(), any())
        } returns false

        val result = interactor.finishSession(sessionUuid)

        assertEquals(null, result)
    }

    @Test
    fun `cancelSession deletes the session row`() = runTest {
        interactor.cancelSession("session-7")
        coVerify(exactly = 1) { sessionRepository.deleteSession("session-7") }
    }

    @Test
    fun `setSkipped also wipes any logged sets when skipping`() = runTest {
        interactor.setSkipped("pe-1", skipped = true)
        coVerify(exactly = 1) { performedExerciseRepository.setSkipped("pe-1", true) }
        coVerify(exactly = 1) { setRepository.deleteAllForPerformedExercise("pe-1") }
    }

    @Test
    fun `setSkipped with false does not wipe sets`() = runTest {
        interactor.setSkipped("pe-1", skipped = false)
        coVerify(exactly = 1) { performedExerciseRepository.setSkipped("pe-1", false) }
        coVerify(exactly = 0) { setRepository.deleteAllForPerformedExercise(any()) }
    }

    @Test
    fun `loadSession with non-adhoc training and null trainingExercise plan falls back to batch adhocPlans`() =
        runTest {
            val sessionUuid = "session-1"
            val trainingUuid = "training-1"
            val exerciseUuid = "ex-1"
            val adhoc =
                listOf(PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK))
            seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
            // Batch API: training plan map carries `exerciseUuid -> null` so the loadSession
            // fallback resolves it via `getAdhocPlans` ONLY for the null entries.
            coEvery {
                trainingExerciseRepository.getPlans(trainingUuid, listOf(exerciseUuid))
            } returns mapOf(exerciseUuid to null)
            coEvery {
                exerciseRepository.getAdhocPlans(listOf(exerciseUuid))
            } returns mapOf(exerciseUuid to adhoc)

            val snapshot = interactor.loadSession(sessionUuid)

            assertEquals(
                listOf(PlanSetDomain(weight = 80.0, reps = 5, type = SetTypeDomain.WORK)),
                snapshot?.exercises?.single()?.planSets,
            )
            coVerify(exactly = 1) { exerciseRepository.getAdhocPlans(listOf(exerciseUuid)) }
        }

    @Test
    fun `loadSession with non-adhoc training and empty plan returns empty without adhocPlans fallback`() =
        runTest {
            val sessionUuid = "session-1"
            val trainingUuid = "training-1"
            val exerciseUuid = "ex-1"
            seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
            // Empty list is treated as "user deliberately cleared the plan" — preserved as
            // empty in the snapshot, NOT replaced by an adhoc fallback.
            coEvery {
                trainingExerciseRepository.getPlans(trainingUuid, listOf(exerciseUuid))
            } returns mapOf(exerciseUuid to emptyList())

            val snapshot = interactor.loadSession(sessionUuid)

            assertEquals(emptyList<PlanSetDomain>(), snapshot?.exercises?.single()?.planSets)
            // Crucially, with no null entries in the training plans map, getAdhocPlans is
            // not called at all — Phase 6's "Empty nullExerciseUuids" assertion.
            coVerify(exactly = 0) { exerciseRepository.getAdhocPlans(any()) }
        }

    @Test
    fun `loadSession with non-adhoc training and non-empty trainingExercise plan returns it as-is`() =
        runTest {
            val sessionUuid = "session-1"
            val trainingUuid = "training-1"
            val exerciseUuid = "ex-1"
            val plan =
                listOf(PlanSetDataModel(weight = 100.0, reps = 3, type = SetTypeDataModel.WORK))
            seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
            coEvery {
                trainingExerciseRepository.getPlans(trainingUuid, listOf(exerciseUuid))
            } returns mapOf(exerciseUuid to plan)

            val snapshot = interactor.loadSession(sessionUuid)

            assertEquals(
                listOf(PlanSetDomain(weight = 100.0, reps = 3, type = SetTypeDomain.WORK)),
                snapshot?.exercises?.single()?.planSets,
            )
            // No null entries → getAdhocPlans is NOT called.
            coVerify(exactly = 0) { exerciseRepository.getAdhocPlans(any()) }
        }

    @Test
    fun `loadSession with adhoc training uses getAdhocPlans directly without trainingExercise lookup`() =
        runTest {
            val sessionUuid = "session-1"
            val trainingUuid = "training-1"
            val exerciseUuid = "ex-1"
            val adhoc =
                listOf(PlanSetDataModel(weight = 80.0, reps = 5, type = SetTypeDataModel.WORK))
            seedAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
            coEvery {
                exerciseRepository.getAdhocPlans(listOf(exerciseUuid))
            } returns mapOf(exerciseUuid to adhoc)

            val snapshot = interactor.loadSession(sessionUuid)

            assertEquals(
                listOf(PlanSetDomain(weight = 80.0, reps = 5, type = SetTypeDomain.WORK)),
                snapshot?.exercises?.single()?.planSets,
            )
            // Adhoc path does not consult the training_exercise table at all.
            coVerify(exactly = 0) {
                trainingExerciseRepository.getPlan(any(), any())
            }
            coVerify(exactly = 0) {
                trainingExerciseRepository.getPlans(any(), any())
            }
        }

    @Test
    fun `loadSession PR snapshot uses observePersonalRecordsBatch`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        val exerciseUuid = "ex-1"
        seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
        coEvery {
            trainingExerciseRepository.getPlans(trainingUuid, listOf(exerciseUuid))
        } returns mapOf(exerciseUuid to emptyList())

        interactor.loadSession(sessionUuid)

        // Refactor switched the PR pre-snapshot from `observePersonalRecords` (combine-of-N)
        // to `observePersonalRecordsBatch` (single Room query). Verify the right one is
        // used and the legacy combine path stays untouched.
        verify(exactly = 1) {
            personalRecordRepository.observePersonalRecordsBatch(any())
        }
        verify(exactly = 0) {
            personalRecordRepository.observePersonalRecords(any())
        }
    }

    @Test
    fun `loadSession reads performed sets via batch getByPerformedExercises`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        val exerciseUuid = "ex-1"
        seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
        coEvery {
            trainingExerciseRepository.getPlans(trainingUuid, listOf(exerciseUuid))
        } returns mapOf(exerciseUuid to emptyList())

        interactor.loadSession(sessionUuid)

        // The refactor consolidated the per-performed-exercise loop into one batch read.
        coVerify(exactly = 1) {
            setRepository.getByPerformedExercises(listOf("pe-1"))
        }
        coVerify(exactly = 0) {
            setRepository.getByPerformedExercise(any())
        }
    }

    private suspend fun seedNonAdhocLoad(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
    ) {
        seedLoad(sessionUuid, trainingUuid, exerciseUuid, isAdhoc = false)
    }

    private suspend fun seedAdhocLoad(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
    ) {
        seedLoad(sessionUuid, trainingUuid, exerciseUuid, isAdhoc = true)
    }

    private suspend fun seedLoad(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuid: String,
        isAdhoc: Boolean,
    ) {
        coEvery { sessionRepository.getById(sessionUuid) } returns SessionDataModel(
            uuid = sessionUuid,
            trainingUuid = trainingUuid,
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 1_000L,
            finishedAt = null,
        )
        coEvery { trainingRepository.getTraining(trainingUuid) } returns TrainingDataModel(
            uuid = trainingUuid,
            name = "Push Day",
            description = null,
            isAdhoc = isAdhoc,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            labels = emptyList(),
            exerciseUuids = listOf(exerciseUuid),
        )
        coEvery { performedExerciseRepository.getBySession(sessionUuid) } returns listOf(
            PerformedExerciseDataModel(
                uuid = "pe-1",
                sessionUuid = sessionUuid,
                exerciseUuid = exerciseUuid,
                position = 0,
                skipped = false,
            ),
        )
        coEvery { exerciseRepository.getExercisesByUuid(listOf(exerciseUuid)) } returns listOf(
            ExerciseDataModel(
                uuid = exerciseUuid,
                name = "Bench",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                lastAdhocSets = null,
            ),
        )
        coEvery { setRepository.getByPerformedExercise("pe-1") } returns emptyList()
        // New batch-API default: empty performed-set map by default; tests that need a
        // populated map override this stub.
        coEvery { setRepository.getByPerformedExercises(listOf("pe-1")) } returns emptyMap()
    }

    @Test
    fun `createAdhocSession delegates to repository and surfaces both UUIDs`() = runTest {
        coEvery {
            sessionRepository.createAdhocSession(
                name = "Quick start",
                exerciseUuids = listOf("ex-1", "ex-2"),
            )
        } returns SessionRepository.AdhocSessionResult(
            sessionUuid = "session-new",
            trainingUuid = "training-new",
        )

        val result = interactor.createAdhocSession(
            name = "Quick start",
            exerciseUuids = listOf("ex-1", "ex-2"),
        )

        assertEquals("session-new", result.sessionUuid)
        assertEquals("training-new", result.trainingUuid)
    }

    @Test
    fun `createAdhocSession with empty exercise list still produces a session`() = runTest {
        coEvery {
            sessionRepository.createAdhocSession(name = "", exerciseUuids = emptyList())
        } returns SessionRepository.AdhocSessionResult(
            sessionUuid = "blank-session",
            trainingUuid = "blank-training",
        )

        val result = interactor.createAdhocSession(name = "", exerciseUuids = emptyList())

        assertEquals("blank-session", result.sessionUuid)
        assertEquals("blank-training", result.trainingUuid)
    }

    @Test
    fun `addExerciseToActiveSession forwards all three UUIDs verbatim`() = runTest {
        interactor.addExerciseToActiveSession(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
            exerciseUuid = "ex-mid",
        )

        coVerify(exactly = 1) {
            sessionRepository.addExerciseToActiveSession(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
                exerciseUuid = "ex-mid",
            )
        }
    }

    @Test
    fun `discardAdhocSession delegates to repository`() = runTest {
        interactor.discardAdhocSession(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
        )

        coVerify(exactly = 1) {
            sessionRepository.discardAdhocSession(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
            )
        }
    }

    @Test
    fun `cancelSession on adhoc training cascades through discardAdhocSession`() = runTest {
        coEvery { sessionRepository.getById("session-adhoc") } returns SessionDataModel(
            uuid = "session-adhoc",
            trainingUuid = "training-adhoc",
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 0L,
            finishedAt = null,
        )
        coEvery { trainingRepository.getTraining("training-adhoc") } returns adhocTraining(
            uuid = "training-adhoc",
            isAdhoc = true,
        )

        interactor.cancelSession("session-adhoc")

        coVerify(exactly = 1) {
            sessionRepository.discardAdhocSession(
                sessionUuid = "session-adhoc",
                trainingUuid = "training-adhoc",
            )
        }
        coVerify(exactly = 0) { sessionRepository.deleteSession(any()) }
    }

    @Test
    fun `cancelSession on library training only deletes the session`() = runTest {
        coEvery { sessionRepository.getById("session-lib") } returns SessionDataModel(
            uuid = "session-lib",
            trainingUuid = "training-lib",
            state = SessionStateDataModel.IN_PROGRESS,
            startedAt = 0L,
            finishedAt = null,
        )
        coEvery { trainingRepository.getTraining("training-lib") } returns adhocTraining(
            uuid = "training-lib",
            isAdhoc = false,
        )

        interactor.cancelSession("session-lib")

        coVerify(exactly = 1) { sessionRepository.deleteSession("session-lib") }
        coVerify(exactly = 0) { sessionRepository.discardAdhocSession(any(), any()) }
    }

    @Test
    fun `createInlineAdhocExercise unwraps the repository envelope`() = runTest {
        coEvery {
            exerciseRepository.createInlineAdhocExercise("Skull Crushers")
        } returns InlineAdhocResult(
            exercise = ExerciseDataModel(
                uuid = "ex-new",
                name = "Skull Crushers",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                lastAdhocSets = null,
            ),
            reusedExisting = false,
        )

        val result = interactor.createInlineAdhocExercise("Skull Crushers")

        assertEquals("ex-new", result.exerciseUuid)
        assertEquals("Skull Crushers", result.name)
        assertEquals(ExerciseTypeDomain.WEIGHTED, result.type)
        assertEquals(false, result.reusedExisting)
    }

    @Test
    fun `updateTrainingName forwards uuid and name to repository`() = runTest {
        interactor.updateTrainingName("training-1", "Push Day")

        coVerify(exactly = 1) {
            trainingRepository.updateName("training-1", "Push Day")
        }
    }

    @Test
    fun `searchExercisesForPicker maps repo entries to picker-local DTOs`() = runTest {
        coEvery {
            exerciseRepository.searchActiveExercises(
                query = "bench",
                excludeUuids = setOf("ex-already-in"),
            )
        } returns listOf(
            ExerciseDataModel(
                uuid = "ex-1",
                name = "Bench Press",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                lastAdhocSets = null,
            ),
            ExerciseDataModel(
                uuid = "ex-2",
                name = "Bench Press (Incline)",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = null,
                imagePath = null,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                lastAdhocSets = null,
            ),
        )

        val results = interactor.searchExercisesForPicker(
            query = "bench",
            excludedUuids = setOf("ex-already-in"),
        )

        assertEquals(listOf("ex-1", "ex-2"), results.map { it.uuid })
        assertEquals(listOf("Bench Press", "Bench Press (Incline)"), results.map { it.name })
    }

    private fun adhocTraining(uuid: String, isAdhoc: Boolean): TrainingDataModel =
        TrainingDataModel(
            uuid = uuid,
            name = "Track now: Bench Press",
            description = null,
            isAdhoc = isAdhoc,
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            labels = emptyList(),
            exerciseUuids = emptyList(),
        )
}
