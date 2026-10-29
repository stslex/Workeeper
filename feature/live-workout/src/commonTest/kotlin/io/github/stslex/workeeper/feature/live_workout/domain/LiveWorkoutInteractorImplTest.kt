// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.domain

import io.github.stslex.workeeper.core.data.database.sets.PlanSetDataModel
import io.github.stslex.workeeper.core.data.database.sets.SetTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository.InlineAdhocResult
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataModel
import io.github.stslex.workeeper.core.data.exercise.exercise.model.SetsDataType
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordDataModel
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.ExternalSessionWrite
import io.github.stslex.workeeper.core.data.exercise.session.ExternalSessionWrites
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.session.model.ActiveSessionInfo
import io.github.stslex.workeeper.core.data.exercise.session.model.PerformedExerciseDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionDataModel
import io.github.stslex.workeeper.core.data.exercise.session.model.SessionStateDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExternalSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExternalSkipDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExternalWriteDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetTypeDomain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

internal class LiveWorkoutInteractorImplTest {

    private val sessionRepository = RecordingSessionRepository()
    private val performedExerciseRepository = RecordingPerformedExerciseRepository()
    private val setRepository = RecordingSetRepository()
    private val exerciseRepository = RecordingExerciseRepository()
    private val trainingRepository = RecordingTrainingRepository()
    private val trainingExerciseRepository = RecordingTrainingExerciseRepository()
    private val personalRecordRepository = RecordingPersonalRecordRepository().apply {
        observePersonalRecordsBatch.on(ANY) { flowOf(emptyMap()) }
    }
    private val externalSessionWrites = ExternalSessionWrites()

    private val interactor = LiveWorkoutInteractorImpl(
        sessionRepository = sessionRepository,
        performedExerciseRepository = performedExerciseRepository,
        setRepository = setRepository,
        exerciseRepository = exerciseRepository,
        trainingRepository = trainingRepository,
        trainingExerciseRepository = trainingExerciseRepository,
        personalRecordRepository = personalRecordRepository,
        externalSessionWrites = externalSessionWrites,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `startSession reuses an in-progress session for the same training`() = runTest {
        val trainingUuid = "training-1"
        sessionRepository.getAnyActiveSession.on {
            ActiveSessionInfo(
                sessionUuid = "session-existing",
                trainingUuid = trainingUuid,
                startedAt = 0L,
            )
        }

        val resolved = interactor.startSession(trainingUuid)

        assertEquals("session-existing", resolved)
        assertEquals(0, sessionRepository.count("startSessionWithExercises", ANY, ANY))
    }

    @Test
    fun `startSession seeds performed exercises ordered by position`() = runTest {
        val trainingUuid = "training-1"
        sessionRepository.getAnyActiveSession.on { null }
        trainingExerciseRepository.getRowsForTraining.on(trainingUuid) {
            listOf(
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
        }
        sessionRepository.startSessionWithExercises.on(trainingUuid, ANY) {
            SessionDataModel(
                uuid = "session-new",
                trainingUuid = trainingUuid,
                state = SessionStateDataModel.IN_PROGRESS,
                startedAt = 0L,
                finishedAt = null,
            )
        }

        val resolved = interactor.startSession(trainingUuid)

        assertEquals("session-new", resolved)
        assertEquals(listOf("ex-1" to 0, "ex-2" to 1), sessionRepository.started.last())
    }

    @Test
    fun `finishSession sends PlanUpdate per non-skipped exercise and finishes atomically`() =
        runTest {
            val sessionUuid = "session-1"
            val trainingUuid = "training-1"
            sessionRepository.getById.on(sessionUuid) {
                SessionDataModel(
                    uuid = sessionUuid,
                    trainingUuid = trainingUuid,
                    state = SessionStateDataModel.IN_PROGRESS,
                    startedAt = 1_000L,
                    finishedAt = null,
                )
            }
            trainingRepository.getTraining.on(trainingUuid) {
                TrainingDataModel(
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
            }
            performedExerciseRepository.getBySession.on(sessionUuid) {
                listOf(
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
            }
            setRepository.getByPerformedExercise.on("pe-1") {
                listOf(
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
            }
            trainingExerciseRepository.getPlan.on(trainingUuid, "ex-1") {
                listOf(
                    PlanSetDataModel(weight = 90.0, reps = 5, type = SetTypeDataModel.WORK),
                )
            }
            // Both exercises have a plan row, so both plans write back to the training.
            trainingExerciseRepository.getPlans.on(trainingUuid, listOf("ex-1", "ex-2")) {
                mapOf("ex-1" to null, "ex-2" to null)
            }
            sessionRepository.finishSessionAtomic.on(sessionUuid, ANY, ANY, ANY, ANY) { true }

            val result = interactor.finishSession(sessionUuid)

            // CM8: the stub matched only an empty discard list.
            assertEquals(emptyList<String>(), sessionRepository.finished.last().discardedSetUuids)
            assertEquals(1, sessionRepository.finished.last().planUpdates.size)
            val update = sessionRepository.finished.last().planUpdates.single()
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
        sessionRepository.getById.on(sessionUuid) {
            SessionDataModel(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateDataModel.IN_PROGRESS,
                startedAt = 1_000L,
                finishedAt = null,
            )
        }
        trainingRepository.getTraining.on(trainingUuid) {
            TrainingDataModel(
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
        }
        performedExerciseRepository.getBySession.on(sessionUuid) {
            listOf(
                PerformedExerciseDataModel(
                    uuid = "pe-1",
                    sessionUuid = sessionUuid,
                    exerciseUuid = "ex-1",
                    position = 0,
                    skipped = false,
                ),
            )
        }
        setRepository.getByPerformedExercise.on("pe-1") {
            listOf(
                SetsDataModel(
                    uuid = "s-1",
                    reps = 8,
                    weight = 50.0,
                    type = SetsDataType.WORK,
                    position = 0,
                ),
            )
        }
        exerciseRepository.getAdhocPlan.on("ex-1") { null }
        sessionRepository.finishSessionAtomic.on(sessionUuid, ANY, ANY, ANY, ANY) { true }

        interactor.finishSession(sessionUuid)

        // CM8: the stub matched only an empty discard list.
        assertEquals(emptyList<String>(), sessionRepository.finished.last().discardedSetUuids)
        val update = sessionRepository.finished.last().planUpdates.single()
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
        sessionRepository.getById.on(sessionUuid) {
            SessionDataModel(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateDataModel.IN_PROGRESS,
                startedAt = 0L,
                finishedAt = null,
            )
        }
        trainingRepository.getTraining.on(trainingUuid) {
            TrainingDataModel(
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
        }
        performedExerciseRepository.getBySession.on(sessionUuid) { emptyList() }
        sessionRepository.finishSessionAtomic.on(ANY, ANY, ANY, ANY, ANY) { false }

        val result = interactor.finishSession(sessionUuid)

        assertEquals(null, result)
    }

    @Test
    fun `cancelSession deletes the session row`() = runTest {
        // CM8: the relaxed mock answered a session; the fake is seeded with one.
        sessionRepository.getById.on("session-7") {
            SessionDataModel(
                uuid = "session-7",
                trainingUuid = "training-7",
                state = SessionStateDataModel.IN_PROGRESS,
                startedAt = 0L,
                finishedAt = null,
            )
        }
        interactor.cancelSession("session-7")
        assertEquals(1, sessionRepository.count("deleteSession", "session-7"))
    }

    @Test
    fun `setSkipped never touches set rows in either direction`() = runTest {
        // Skip is reversible in place — the flag flips, the logged sets survive.
        interactor.setSkipped("pe-1", skipped = true)
        assertEquals(1, performedExerciseRepository.count("setSkipped", "pe-1", true))
        interactor.setSkipped("pe-1", skipped = false)
        assertEquals(1, performedExerciseRepository.count("setSkipped", "pe-1", false))
        assertEquals(0, setRepository.count("deleteAllForPerformedExercise", ANY))
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
            // The plan map carries `uuid -> null`, so the fallback resolves only null entries.
            trainingExerciseRepository.getPlans.on(trainingUuid, listOf(exerciseUuid)) { mapOf(exerciseUuid to null) }
            exerciseRepository.getAdhocPlans.on(listOf(exerciseUuid)) { mapOf(exerciseUuid to adhoc) }

            val snapshot = interactor.loadSession(sessionUuid)

            assertEquals(
                listOf(PlanSetDomain(weight = 80.0, reps = 5, type = SetTypeDomain.WORK)),
                snapshot?.exercises?.single()?.planSets,
            )
            assertEquals(1, exerciseRepository.count("getAdhocPlans", listOf(exerciseUuid)))
        }

    @Test
    fun `loadSession reads isPlanAttached from key presence not from plan nullability`() =
        runTest {
            // Only key presence tells them apart: `attached` has a NULL-plan row, `oneOff` none.
            val sessionUuid = "session-1"
            val trainingUuid = "training-1"
            seedNonAdhocLoad(sessionUuid, trainingUuid, "ex-attached", "ex-one-off")
            trainingExerciseRepository.getPlans.on(trainingUuid, listOf("ex-attached", "ex-one-off")) {
                mapOf("ex-attached" to null)
            }
            exerciseRepository.getAdhocPlans.on(ANY) { emptyMap() }

            val snapshot = interactor.loadSession(sessionUuid)

            val byExercise = snapshot?.exercises.orEmpty()
                .associateBy { it.performed.exerciseUuid }
            assertEquals(true, byExercise["ex-attached"]?.isPlanAttached)
            assertEquals(false, byExercise["ex-one-off"]?.isPlanAttached)
            // Both have a null plan — proving the flag did not come from plan nullability.
            assertEquals(null, byExercise["ex-attached"]?.planSets)
            assertEquals(null, byExercise["ex-one-off"]?.planSets)
        }

    @Test
    fun `loadSession on an adhoc training treats every exercise as plan-attached`() = runTest {
        // An adhoc training has no template, so the one-off affordance must not appear.
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        val exerciseUuid = "ex-1"
        seedAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
        exerciseRepository.getAdhocPlans.on(listOf(exerciseUuid)) { emptyMap() }

        val snapshot = interactor.loadSession(sessionUuid)

        assertEquals(true, snapshot?.exercises?.single()?.isPlanAttached)
    }

    @Test
    fun `finishSession routes a one-off plan to the exercise instead of the training`() = runTest {
        // Without this routing the template write matches zero rows and the sets are lost.
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        sessionRepository.getById.on(sessionUuid) {
            SessionDataModel(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateDataModel.IN_PROGRESS,
                startedAt = 1_000L,
                finishedAt = null,
            )
        }
        trainingRepository.getTraining.on(trainingUuid) {
            TrainingDataModel(
                uuid = trainingUuid,
                name = "Push Day",
                description = null,
                isAdhoc = false,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                labels = emptyList(),
                exerciseUuids = listOf("ex-one-off"),
            )
        }
        performedExerciseRepository.getBySession.on(sessionUuid) {
            listOf(
                PerformedExerciseDataModel(
                    uuid = "pe-1",
                    sessionUuid = sessionUuid,
                    exerciseUuid = "ex-one-off",
                    position = 0,
                    skipped = false,
                ),
            )
        }
        setRepository.getByPerformedExercise.on("pe-1") {
            listOf(
                SetsDataModel(
                    uuid = "s-1",
                    reps = 8,
                    weight = 40.0,
                    type = SetsDataType.WORK,
                    position = 0,
                ),
            )
        }
        // No key for the exercise: no training_exercise row, i.e. a one-off.
        trainingExerciseRepository.getPlans.on(trainingUuid, listOf("ex-one-off")) { emptyMap() }
        exerciseRepository.getAdhocPlan.on("ex-one-off") { null }
        sessionRepository.finishSessionAtomic.on(sessionUuid, ANY, ANY, ANY, ANY) { true }

        interactor.finishSession(sessionUuid)

        // CM8: the stub matched only an empty discard list.
        assertEquals(emptyList<String>(), sessionRepository.finished.last().discardedSetUuids)
        // `isAdhoc = true` on PlanUpdate means "write to exercise.last_adhoc_sets".
        val update = sessionRepository.finished.last().planUpdates.single()
        assertEquals("ex-one-off", update.exerciseUuid)
        assertEquals(true, update.isAdhoc)
        assertEquals(0, trainingExerciseRepository.count("getPlan", trainingUuid, "ex-one-off"))
    }

    @Test
    fun `finishSession discards a persisted zero-rep set and leaves it out of the plan`() =
        runTest {
            // Defence-in-depth: no production writer persists reps <= 0; seeded directly here.
            val sessionUuid = "session-1"
            val trainingUuid = "training-1"
            sessionRepository.getById.on(sessionUuid) {
                SessionDataModel(
                    uuid = sessionUuid,
                    trainingUuid = trainingUuid,
                    state = SessionStateDataModel.IN_PROGRESS,
                    startedAt = 1_000L,
                    finishedAt = null,
                )
            }
            trainingRepository.getTraining.on(trainingUuid) {
                TrainingDataModel(
                    uuid = trainingUuid,
                    name = "Push Day",
                    description = null,
                    isAdhoc = false,
                    archived = false,
                    archivedAt = null,
                    timestamp = 0L,
                    labels = emptyList(),
                    exerciseUuids = listOf("ex-1"),
                )
            }
            performedExerciseRepository.getBySession.on(sessionUuid) {
                listOf(
                    PerformedExerciseDataModel(
                        uuid = "pe-1",
                        sessionUuid = sessionUuid,
                        exerciseUuid = "ex-1",
                        position = 0,
                        skipped = false,
                    ),
                )
            }
            setRepository.getByPerformedExercise.on("pe-1") {
                listOf(
                    SetsDataModel(
                        uuid = "s-1",
                        reps = 5,
                        weight = 100.0,
                        type = SetsDataType.WORK,
                        position = 0,
                    ),
                    SetsDataModel(
                        uuid = "s-2",
                        reps = 0,
                        weight = null,
                        type = SetsDataType.WORK,
                        position = 1,
                    ),
                )
            }
            trainingExerciseRepository.getPlans.on(trainingUuid, listOf("ex-1")) { mapOf("ex-1" to null) }
            trainingExerciseRepository.getPlan.on(trainingUuid, "ex-1") { null }
            exerciseRepository.getAdhocPlan.on("ex-1") { null }
            sessionRepository.finishSessionAtomic.on(sessionUuid, ANY, ANY, ANY, ANY) { true }

            val result = interactor.finishSession(sessionUuid)

            // The zero-rep row goes to the transaction, so a failed finish rolls it back.
            assertEquals(listOf("s-2"), sessionRepository.finished.last().discardedSetUuids)
            assertEquals(0, setRepository.count("deleteByPerformedAndPosition", ANY, ANY))
            assertEquals(1, result?.discardedUnfilledSets)
            assertEquals(1, result?.setsLogged)
            assertEquals(
                listOf(PlanSetDataModel(weight = 100.0, reps = 5, type = SetTypeDataModel.WORK)),
                sessionRepository.finished.last().planUpdates.single().newPlan,
            )
        }

    @Test
    fun `loadSession with non-adhoc training and empty plan returns empty without adhocPlans fallback`() =
        runTest {
            val sessionUuid = "session-1"
            val trainingUuid = "training-1"
            val exerciseUuid = "ex-1"
            seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
            // An empty list means "deliberately cleared" — preserved, never adhoc-filled.
            trainingExerciseRepository.getPlans.on(trainingUuid, listOf(exerciseUuid)) {
                mapOf(exerciseUuid to emptyList())
            }

            val snapshot = interactor.loadSession(sessionUuid)

            assertEquals(emptyList<PlanSetDomain>(), snapshot?.exercises?.single()?.planSets)
            // With no null entries in the plans map, getAdhocPlans is not called at all.
            assertEquals(0, exerciseRepository.count("getAdhocPlans", ANY))
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
            trainingExerciseRepository.getPlans.on(trainingUuid, listOf(exerciseUuid)) { mapOf(exerciseUuid to plan) }

            val snapshot = interactor.loadSession(sessionUuid)

            assertEquals(
                listOf(PlanSetDomain(weight = 100.0, reps = 3, type = SetTypeDomain.WORK)),
                snapshot?.exercises?.single()?.planSets,
            )
            assertEquals(0, exerciseRepository.count("getAdhocPlans", ANY))
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
            exerciseRepository.getAdhocPlans.on(listOf(exerciseUuid)) { mapOf(exerciseUuid to adhoc) }

            val snapshot = interactor.loadSession(sessionUuid)

            assertEquals(
                listOf(PlanSetDomain(weight = 80.0, reps = 5, type = SetTypeDomain.WORK)),
                snapshot?.exercises?.single()?.planSets,
            )
            // Adhoc path does not consult the training_exercise table at all.
            assertEquals(0, trainingExerciseRepository.count("getPlan", ANY, ANY))
            assertEquals(0, trainingExerciseRepository.count("getPlans", ANY, ANY))
        }

    @Test
    fun `loadSession PR snapshot uses observePersonalRecordsBatch`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        val exerciseUuid = "ex-1"
        seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
        trainingExerciseRepository.getPlans.on(trainingUuid, listOf(exerciseUuid)) {
            mapOf(exerciseUuid to emptyList())
        }

        interactor.loadSession(sessionUuid)

        // Perf guard: the PR pre-snapshot must stay on the single-query batch API.
        assertEquals(1, personalRecordRepository.count("observePersonalRecordsBatch", ANY))
    }

    @Test
    fun `loadSession reads performed sets via batch getByPerformedExercises`() = runTest {
        val sessionUuid = "session-1"
        val trainingUuid = "training-1"
        val exerciseUuid = "ex-1"
        seedNonAdhocLoad(sessionUuid, trainingUuid, exerciseUuid)
        trainingExerciseRepository.getPlans.on(trainingUuid, listOf(exerciseUuid)) {
            mapOf(exerciseUuid to emptyList())
        }

        interactor.loadSession(sessionUuid)

        assertEquals(1, setRepository.count("getByPerformedExercises", listOf("pe-1")))
        assertEquals(0, setRepository.count("getByPerformedExercise", ANY))
    }

    private suspend fun seedNonAdhocLoad(
        sessionUuid: String,
        trainingUuid: String,
        vararg exerciseUuids: String,
    ) {
        seedLoad(sessionUuid, trainingUuid, exerciseUuids.toList(), isAdhoc = false)
    }

    private suspend fun seedAdhocLoad(
        sessionUuid: String,
        trainingUuid: String,
        vararg exerciseUuids: String,
    ) {
        seedLoad(sessionUuid, trainingUuid, exerciseUuids.toList(), isAdhoc = true)
    }

    private suspend fun seedLoad(
        sessionUuid: String,
        trainingUuid: String,
        exerciseUuids: List<String>,
        isAdhoc: Boolean,
    ) {
        val performedUuids = exerciseUuids.indices.map { index -> "pe-${index + 1}" }
        sessionRepository.getById.on(sessionUuid) {
            SessionDataModel(
                uuid = sessionUuid,
                trainingUuid = trainingUuid,
                state = SessionStateDataModel.IN_PROGRESS,
                startedAt = 1_000L,
                finishedAt = null,
            )
        }
        trainingRepository.getTraining.on(trainingUuid) {
            TrainingDataModel(
                uuid = trainingUuid,
                name = "Push Day",
                description = null,
                isAdhoc = isAdhoc,
                archived = false,
                archivedAt = null,
                timestamp = 0L,
                labels = emptyList(),
                exerciseUuids = exerciseUuids,
            )
        }
        performedExerciseRepository.getBySession.on(sessionUuid) {
            exerciseUuids.mapIndexed { index, uuid ->
                PerformedExerciseDataModel(
                    uuid = performedUuids[index],
                    sessionUuid = sessionUuid,
                    exerciseUuid = uuid,
                    position = index,
                    skipped = false,
                )
            }
        }
        exerciseRepository.getExercisesByUuid.on(exerciseUuids) {
            exerciseUuids.map { uuid ->
                ExerciseDataModel(
                    uuid = uuid,
                    name = "Bench",
                    type = ExerciseTypeDataModel.WEIGHTED,
                    description = null,
                    imagePath = null,
                    archived = false,
                    archivedAt = null,
                    timestamp = 0L,
                    lastAdhocSets = null,
                )
            }
        }
        performedUuids.forEach { performedUuid ->
            setRepository.getByPerformedExercise.on(performedUuid) { emptyList() }
        }
        // Empty performed-set map by default; tests needing a populated map override this.
        setRepository.getByPerformedExercises.on(performedUuids) { emptyMap() }
    }

    @Test
    fun `createAdhocSession delegates to repository and surfaces both UUIDs`() = runTest {
        sessionRepository.createAdhocSession.on("Quick start", listOf("ex-1", "ex-2")) {
            SessionRepository.AdhocSessionResult(
                sessionUuid = "session-new",
                trainingUuid = "training-new",
            )
        }

        val result = interactor.createAdhocSession(
            name = "Quick start",
            exerciseUuids = listOf("ex-1", "ex-2"),
        )

        assertEquals("session-new", result.sessionUuid)
        assertEquals("training-new", result.trainingUuid)
    }

    @Test
    fun `createAdhocSession with empty exercise list still produces a session`() = runTest {
        sessionRepository.createAdhocSession.on("", emptyList<String>()) {
            SessionRepository.AdhocSessionResult(
                sessionUuid = "blank-session",
                trainingUuid = "blank-training",
            )
        }

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

        assertEquals(
            1,
            sessionRepository.count("addExerciseToActiveSession", "session-1", "training-1", "ex-mid", true),
        )
    }

    @Test
    fun `discardAdhocSession delegates to repository`() = runTest {
        interactor.discardAdhocSession(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
        )

        assertEquals(1, sessionRepository.count("discardAdhocSession", "session-1", "training-1"))
    }

    @Test
    fun `cancelSession on adhoc training cascades through discardAdhocSession`() = runTest {
        sessionRepository.getById.on("session-adhoc") {
            SessionDataModel(
                uuid = "session-adhoc",
                trainingUuid = "training-adhoc",
                state = SessionStateDataModel.IN_PROGRESS,
                startedAt = 0L,
                finishedAt = null,
            )
        }
        trainingRepository.getTraining.on("training-adhoc") {
            adhocTraining(
                uuid = "training-adhoc",
                isAdhoc = true,
            )
        }

        interactor.cancelSession("session-adhoc")

        assertEquals(1, sessionRepository.count("discardAdhocSession", "session-adhoc", "training-adhoc"))
        assertEquals(0, sessionRepository.count("deleteSession", ANY))
    }

    @Test
    fun `cancelSession on library training only deletes the session`() = runTest {
        sessionRepository.getById.on("session-lib") {
            SessionDataModel(
                uuid = "session-lib",
                trainingUuid = "training-lib",
                state = SessionStateDataModel.IN_PROGRESS,
                startedAt = 0L,
                finishedAt = null,
            )
        }
        trainingRepository.getTraining.on("training-lib") {
            adhocTraining(
                uuid = "training-lib",
                isAdhoc = false,
            )
        }

        interactor.cancelSession("session-lib")

        assertEquals(1, sessionRepository.count("deleteSession", "session-lib"))
        assertEquals(0, sessionRepository.count("discardAdhocSession", ANY, ANY))
    }

    @Test
    fun `createInlineAdhocExercise unwraps the repository envelope`() = runTest {
        exerciseRepository.createInlineAdhocExercise.on("Skull Crushers") {
            InlineAdhocResult(
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
        }

        val result = interactor.createInlineAdhocExercise("Skull Crushers")

        assertEquals("ex-new", result.exerciseUuid)
        assertEquals("Skull Crushers", result.name)
        assertEquals(ExerciseTypeDomain.WEIGHTED, result.type)
        assertEquals(false, result.reusedExisting)
    }

    @Test
    fun `updateTrainingName forwards uuid and name to repository`() = runTest {
        interactor.updateTrainingName("training-1", "Push Day")

        assertEquals(1, trainingRepository.count("updateName", "training-1", "Push Day"))
    }

    @Test
    fun `searchExercisesForPicker maps repo entries to picker-local DTOs`() = runTest {
        exerciseRepository.searchActiveExercises.on("bench", setOf("ex-already-in")) {
            listOf(
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
        }

        val results = interactor.searchExercisesForPicker(
            query = "bench",
            excludedUuids = setOf("ex-already-in"),
        )

        assertEquals(listOf("ex-1", "ex-2"), results.map { it.uuid })
        assertEquals(listOf("Bench Press", "Bench Press (Incline)"), results.map { it.name })
    }

    @Test
    fun `external set writes reach only the screen of their own session mapped as written`() = runTest {
        val received = mutableListOf<ExternalWriteDomain>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            interactor.observeExternalWrites("session-1").collect { received += it }
        }

        externalSessionWrites.publish(externalWrite(sessionUuid = "session-2", position = 0))
        externalSessionWrites.publish(externalWrite(sessionUuid = "session-1", position = 1))

        assertEquals<List<ExternalWriteDomain>>(
            listOf(
                ExternalSetDomain(
                    performedExerciseUuid = "pe-1",
                    position = 1,
                    set = PlanSetDomain(weight = 82.5, reps = 6, type = SetTypeDomain.FAILURE),
                ),
            ),
            received,
        )
    }

    @Test
    fun `external skips reach only the screen of their own session in arrival order with the sets`() = runTest {
        val received = mutableListOf<ExternalWriteDomain>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            interactor.observeExternalWrites("session-1").collect { received += it }
        }

        externalSessionWrites.publish(externalSkip(sessionUuid = "session-2", skipped = true))
        externalSessionWrites.publish(externalSkip(sessionUuid = "session-1", skipped = true))
        externalSessionWrites.publish(externalWrite(sessionUuid = "session-1", position = 1))
        externalSessionWrites.publish(externalSkip(sessionUuid = "session-1", skipped = false))

        assertEquals(
            listOf(
                ExternalSkipDomain(performedExerciseUuid = "pe-1", skipped = true),
                ExternalSetDomain(
                    performedExerciseUuid = "pe-1",
                    position = 1,
                    set = PlanSetDomain(weight = 82.5, reps = 6, type = SetTypeDomain.FAILURE),
                ),
                ExternalSkipDomain(performedExerciseUuid = "pe-1", skipped = false),
            ),
            received,
        )
    }

    @Test
    fun `a watch set write and its domain model print their type only`() {
        assertEquals(
            "ExternalSessionWrite.SetMarked",
            externalWrite(sessionUuid = "session-1", position = 1).toString(),
        )
        assertEquals(
            "ExternalSetDomain",
            ExternalSetDomain(
                performedExerciseUuid = "pe-1",
                position = 1,
                set = PlanSetDomain(weight = 82.5, reps = 6, type = SetTypeDomain.FAILURE),
            ).toString(),
        )
    }

    private fun externalSkip(sessionUuid: String, skipped: Boolean) = ExternalSessionWrite.SkipChanged(
        sessionUuid = sessionUuid,
        performedExerciseUuid = "pe-1",
        skipped = skipped,
    )

    private fun externalWrite(sessionUuid: String, position: Int) = ExternalSessionWrite.SetMarked(
        sessionUuid = sessionUuid,
        performedExerciseUuid = "pe-1",
        position = position,
        weight = 82.5,
        reps = 6,
        type = SetsDataType.FAIL,
    )

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

    /** One repository call, as a verify reads it. */
    private data class Call(val member: String, val args: List<Any?>)

    /** A repository fake's log: every call in order, so a verify is a count over [calls] (CM8). */
    private abstract class RecordingRepository {

        val calls = mutableListOf<Call>()

        /** The calls of [member] whose arguments match [args], where [ANY] matches every value. */
        fun count(member: String, vararg args: Any?): Int =
            calls.count { it.member == member && matches(it.args, args.toList()) }

        protected fun record(member: String, vararg args: Any?): List<Any?> {
            calls += Call(member, args.toList())
            return args.toList()
        }
    }

    /**
     * One member's seeds, matched as the stubs matched: argument by argument, [ANY] for any value, the
     * latest seed first; an unmatched call gets the member's own answer.
     */
    private class Seeds<V> {

        private val entries = mutableListOf<Pair<List<Any?>, () -> V>>()

        fun on(vararg args: Any?, answer: () -> V) {
            entries += args.toList() to answer
        }

        fun answer(args: List<Any?>, otherwise: () -> V): V =
            (entries.lastOrNull { (expected, _) -> matches(args, expected) }?.second ?: otherwise)()
    }

    private companion object {

        /** MockK's `any()`: matches every argument value. */
        val ANY = Any()

        fun matches(actual: List<Any?>, expected: List<Any?>): Boolean =
            actual.size == expected.size &&
                actual.zip(expected).all { (value, wanted) -> wanted === ANY || wanted == value }

        /** CM11: a member no identity reaches. */
        fun unreached(member: String): Nothing = error("no identity reaches $member")

        /** A member an identity reaches only after seeding it. */
        fun unanswered(member: String): Nothing = error("no identity seeds this call of $member")
    }

    private class RecordingSessionRepository : RecordingRepository(), SessionRepository {

        val getAnyActiveSession = Seeds<ActiveSessionInfo?>()
        val startSessionWithExercises = Seeds<SessionDataModel>()
        val getById = Seeds<SessionDataModel?>()
        val finishSessionAtomic = Seeds<Boolean>()
        val createAdhocSession = Seeds<SessionRepository.AdhocSessionResult>()

        /** Each `startSessionWithExercises` call's exercise pairs, in call order (the base's slot). */
        val started = mutableListOf<List<Pair<String, Int>>>()

        /** Each `finishSessionAtomic` call, in call order (the base's slots). */
        val finished = mutableListOf<Finish>()

        data class Finish(val planUpdates: List<PlanUpdate>, val discardedSetUuids: List<String>)

        override suspend fun getAnyActiveSession(): ActiveSessionInfo? =
            getAnyActiveSession.answer(record("getAnyActiveSession")) { unanswered("getAnyActiveSession") }

        override suspend fun startSessionWithExercises(
            trainingUuid: String,
            exerciseUuids: List<Pair<String, Int>>,
        ): SessionDataModel {
            started += exerciseUuids
            return startSessionWithExercises.answer(record("startSessionWithExercises", trainingUuid, exerciseUuids)) {
                unanswered("startSessionWithExercises")
            }
        }

        override suspend fun getById(uuid: String): SessionDataModel? =
            getById.answer(record("getById", uuid)) { unanswered("getById") }

        override suspend fun finishSessionAtomic(
            sessionUuid: String,
            finishedAt: Long,
            planUpdates: List<PlanUpdate>,
            newTrainingName: String?,
            discardedSetUuids: List<String>,
        ): Boolean {
            finished += Finish(planUpdates, discardedSetUuids)
            val args = record(
                "finishSessionAtomic",
                sessionUuid,
                finishedAt,
                planUpdates,
                newTrainingName,
                discardedSetUuids,
            )
            return finishSessionAtomic.answer(args) { unanswered("finishSessionAtomic") }
        }

        override suspend fun deleteSession(uuid: String) {
            record("deleteSession", uuid)
        }

        override suspend fun createAdhocSession(
            name: String,
            exerciseUuids: List<String>,
        ): SessionRepository.AdhocSessionResult =
            createAdhocSession.answer(record("createAdhocSession", name, exerciseUuids)) {
                unanswered("createAdhocSession")
            }

        /** The relaxed answer: the identity that reaches it verifies only the call. */
        override suspend fun addExerciseToActiveSession(
            sessionUuid: String,
            trainingUuid: String,
            exerciseUuid: String,
            attachToPlan: Boolean,
        ): SessionRepository.AddExerciseResult {
            record("addExerciseToActiveSession", sessionUuid, trainingUuid, exerciseUuid, attachToPlan)
            return SessionRepository.AddExerciseResult(
                performedExerciseUuid = "",
                planSets = null,
                isPlanAttached = false,
            )
        }

        override suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String) {
            record("discardAdhocSession", sessionUuid, trainingUuid)
        }

        override fun observeActive(): Nothing = unreached("observeActive")

        override fun observeAnyActiveSession(): Nothing = unreached("observeAnyActiveSession")

        override fun observeActiveSessionWithStats(): Nothing = unreached("observeActiveSessionWithStats")

        override suspend fun getActiveSessionProgress(): Nothing = unreached("getActiveSessionProgress")

        override suspend fun getActive(): Nothing = unreached("getActive")

        override fun pagedRecentWithStats(): Nothing = unreached("pagedRecentWithStats")

        override fun observeFinishedTimesBetween(startInclusive: Long, endExclusive: Long): Nothing =
            unreached("observeFinishedTimesBetween")

        override fun observeLastFinishedSession(): Nothing = unreached("observeLastFinishedSession")

        override suspend fun getSessionDetail(sessionUuid: String): Nothing = unreached("getSessionDetail")

        override fun pagedFinished(): Nothing = unreached("pagedFinished")

        override fun pagedFinishedByTraining(trainingUuid: String): Nothing = unreached("pagedFinishedByTraining")

        override suspend fun getRecentFinishedByTraining(trainingUuid: String, limit: Int): Nothing =
            unreached("getRecentFinishedByTraining")

        override suspend fun startSession(trainingUuid: String): Nothing = unreached("startSession")

        override suspend fun resumeSession(sessionUuid: String): Nothing = unreached("resumeSession")

        override suspend fun finishSession(sessionUuid: String, finishedAt: Long): Nothing = unreached("finishSession")

        override suspend fun removeExerciseFromSession(
            performedExerciseUuid: String,
            exerciseUuid: String,
            trainingUuid: String?,
            removeFromPlan: Boolean,
        ): Nothing = unreached("removeExerciseFromSession")

        override fun pagedHistoryByExercise(exerciseUuid: String): Nothing = unreached("pagedHistoryByExercise")

        override suspend fun getHistoryByExercise(exerciseUuid: String): Nothing = unreached("getHistoryByExercise")
    }

    private class RecordingPerformedExerciseRepository : RecordingRepository(), PerformedExerciseRepository {

        val getBySession = Seeds<List<PerformedExerciseDataModel>>()

        override suspend fun getBySession(sessionUuid: String): List<PerformedExerciseDataModel> =
            getBySession.answer(record("getBySession", sessionUuid)) { unanswered("getBySession") }

        override suspend fun setSkipped(uuid: String, skipped: Boolean) {
            record("setSkipped", uuid, skipped)
        }

        override suspend fun insert(rows: List<PerformedExerciseDataModel>): Nothing = unreached("insert")

        override suspend fun insertForSession(sessionUuid: String, exerciseUuids: List<Pair<String, Int>>): Nothing =
            unreached("insertForSession")
    }

    private class RecordingSetRepository : RecordingRepository(), SetRepository {

        val getByPerformedExercise = Seeds<List<SetsDataModel>>()
        val getByPerformedExercises = Seeds<Map<String, List<SetsDataModel>>>()

        override suspend fun getByPerformedExercise(performedExerciseUuid: String): List<SetsDataModel> =
            getByPerformedExercise.answer(record("getByPerformedExercise", performedExerciseUuid)) {
                unanswered("getByPerformedExercise")
            }

        override suspend fun getByPerformedExercises(
            performedExerciseUuids: List<String>,
        ): Map<String, List<SetsDataModel>> =
            getByPerformedExercises.answer(record("getByPerformedExercises", performedExerciseUuids)) {
                unanswered("getByPerformedExercises")
            }

        override suspend fun deleteByPerformedAndPosition(performedExerciseUuid: String, position: Int) {
            record("deleteByPerformedAndPosition", performedExerciseUuid, position)
        }

        override suspend fun deleteAllForPerformedExercise(performedExerciseUuid: String) {
            record("deleteAllForPerformedExercise", performedExerciseUuid)
        }

        override suspend fun getLastFinishedSet(exerciseUuid: String): Nothing = unreached("getLastFinishedSet")

        override suspend fun insert(performedExerciseUuid: String, set: SetsDataModel): Nothing = unreached("insert")

        override suspend fun update(performedExerciseUuid: String, set: SetsDataModel): Nothing = unreached("update")

        override suspend fun reorderSets(performedExerciseUuid: String, orderedSetUuids: List<String>): Nothing =
            unreached("reorderSets")

        override suspend fun delete(uuid: String): Nothing = unreached("delete")

        override suspend fun upsert(
            performedExerciseUuid: String,
            position: Int,
            weight: Double?,
            reps: Int,
            type: SetsDataType,
        ): Nothing = unreached("upsert")

        override suspend fun hasAnyForPerformed(performedExerciseUuid: String): Nothing =
            unreached("hasAnyForPerformed")

        override suspend fun countByPerformedExercise(performedExerciseUuid: String): Nothing =
            unreached("countByPerformedExercise")
    }

    private class RecordingExerciseRepository : RecordingRepository(), ExerciseRepository {

        val getExercisesByUuid = Seeds<List<ExerciseDataModel>>()
        val getAdhocPlans = Seeds<Map<String, List<PlanSetDataModel>?>>()
        val getAdhocPlan = Seeds<List<PlanSetDataModel>?>()
        val createInlineAdhocExercise = Seeds<ExerciseRepository.InlineAdhocResult>()
        val searchActiveExercises = Seeds<List<ExerciseDataModel>>()

        override suspend fun getExercisesByUuid(uuids: List<String>): List<ExerciseDataModel> =
            getExercisesByUuid.answer(record("getExercisesByUuid", uuids)) { unanswered("getExercisesByUuid") }

        override suspend fun getAdhocPlans(uuids: List<String>): Map<String, List<PlanSetDataModel>?> =
            getAdhocPlans.answer(record("getAdhocPlans", uuids)) { unanswered("getAdhocPlans") }

        override suspend fun getAdhocPlan(exerciseUuid: String): List<PlanSetDataModel>? =
            getAdhocPlan.answer(record("getAdhocPlan", exerciseUuid)) { unanswered("getAdhocPlan") }

        override suspend fun createInlineAdhocExercise(name: String): ExerciseRepository.InlineAdhocResult =
            createInlineAdhocExercise.answer(record("createInlineAdhocExercise", name)) {
                unanswered("createInlineAdhocExercise")
            }

        override suspend fun searchActiveExercises(query: String, excludeUuids: Set<String>): List<ExerciseDataModel> =
            searchActiveExercises.answer(record("searchActiveExercises", query, excludeUuids)) {
                unanswered("searchActiveExercises")
            }

        override fun getUniqueExercises(query: String): Nothing = unreached("getUniqueExercises")

        override suspend fun getExercise(uuid: String): Nothing = unreached("getExercise")

        override suspend fun saveItem(item: ExerciseChangeDataModel): Nothing = unreached("saveItem")

        override suspend fun setAdhocPlan(exerciseUuid: String, planSets: List<PlanSetDataModel>?): Nothing =
            unreached("setAdhocPlan")

        override suspend fun setExerciseType(exerciseUuid: String, type: ExerciseTypeDataModel): Nothing =
            unreached("setExerciseType")

        override suspend fun clearWeightsFromAllPlansForExercise(exerciseUuid: String): Nothing =
            unreached("clearWeightsFromAllPlansForExercise")

        override suspend fun deleteItem(uuid: String): Nothing = unreached("deleteItem")

        override suspend fun deleteAllItems(uuids: List<Uuid>): Nothing = unreached("deleteAllItems")

        override suspend fun archive(uuid: String): Nothing = unreached("archive")

        override suspend fun restore(uuid: String): Nothing = unreached("restore")

        override suspend fun permanentDelete(uuid: String): Nothing = unreached("permanentDelete")

        override suspend fun canArchive(uuid: String): Nothing = unreached("canArchive")

        override suspend fun canPermanentlyDeleteImmediately(uuid: String): Nothing =
            unreached("canPermanentlyDeleteImmediately")

        override suspend fun getActiveTrainingsUsing(exerciseUuid: String): Nothing =
            unreached("getActiveTrainingsUsing")

        override fun pagedArchived(): Nothing = unreached("pagedArchived")

        override fun observeArchivedCount(): Nothing = unreached("observeArchivedCount")

        override suspend fun countSessionsUsing(exerciseUuid: String): Nothing = unreached("countSessionsUsing")

        override fun observeLinkedTrainingsCount(exerciseUuid: String): Nothing =
            unreached("observeLinkedTrainingsCount")

        override fun observeLastTrainedAt(exerciseUuid: String): Nothing = unreached("observeLastTrainedAt")

        override fun pagedActiveByTags(tagUuids: Set<String>): Nothing = unreached("pagedActiveByTags")

        override fun pagedActiveWithStats(filterTagUuids: Set<String>): Nothing = unreached("pagedActiveWithStats")

        override suspend fun getRecentHistory(exerciseUuid: String, limit: Int): Nothing = unreached("getRecentHistory")

        override suspend fun getLastTrainedExerciseUuid(): Nothing = unreached("getLastTrainedExerciseUuid")

        override suspend fun getRecentlyTrainedExercises(): Nothing = unreached("getRecentlyTrainedExercises")

        override suspend fun getLabels(exerciseUuid: String): Nothing = unreached("getLabels")

        override suspend fun bulkArchive(uuids: Set<String>): Nothing = unreached("bulkArchive")

        override suspend fun bulkPermanentDelete(uuids: Set<String>): Nothing = unreached("bulkPermanentDelete")

        override suspend fun canBulkPermanentDelete(uuids: Set<String>): Nothing = unreached("canBulkPermanentDelete")

        override val exercises: Nothing get() = unreached("exercises")
    }

    private class RecordingTrainingRepository : RecordingRepository(), TrainingRepository {

        val getTraining = Seeds<TrainingDataModel?>()

        override suspend fun updateName(uuid: String, name: String) {
            record("updateName", uuid, name)
        }

        /**
         * The relaxed answer, where an identity reaches it unseeded: no training, the branch the relaxed
         * mock's non-ad-hoc training took.
         */
        override suspend fun getTraining(uuid: String): TrainingDataModel? =
            getTraining.answer(record("getTraining", uuid)) { null }

        override fun getTrainingsUnique(query: String): Nothing = unreached("getTrainingsUnique")

        override suspend fun updateTraining(training: TrainingChangeDataModel): Nothing = unreached("updateTraining")

        override suspend fun updateTrainingWithPlans(
            training: TrainingChangeDataModel,
            plans: List<TrainingRepository.ExercisePlanWrite>,
        ): Nothing = unreached("updateTrainingWithPlans")

        override suspend fun removeTraining(uuid: String): Nothing = unreached("removeTraining")

        override fun subscribeForTraining(uuid: String): Nothing = unreached("subscribeForTraining")

        override suspend fun removeAll(uuids: List<String>): Nothing = unreached("removeAll")

        override suspend fun archive(uuid: String): Nothing = unreached("archive")

        override suspend fun restore(uuid: String): Nothing = unreached("restore")

        override suspend fun permanentDelete(uuid: String): Nothing = unreached("permanentDelete")

        override fun pagedArchived(): Nothing = unreached("pagedArchived")

        override fun observeArchivedCount(): Nothing = unreached("observeArchivedCount")

        override suspend fun countSessionsUsing(trainingUuid: String): Nothing = unreached("countSessionsUsing")

        override fun pagedActiveWithStats(filterTagUuids: Set<String>): Nothing = unreached("pagedActiveWithStats")

        override fun observeRecentTemplates(limit: Int): Nothing = unreached("observeRecentTemplates")

        override fun observeMostForgottenTemplate(): Nothing = unreached("observeMostForgottenTemplate")

        override suspend fun bulkArchive(uuids: Set<String>): Nothing = unreached("bulkArchive")

        override suspend fun bulkPermanentDelete(uuids: Set<String>): Nothing = unreached("bulkPermanentDelete")

        override suspend fun canBulkPermanentDelete(uuids: Set<String>): Nothing = unreached("canBulkPermanentDelete")
    }

    private class RecordingTrainingExerciseRepository : RecordingRepository(), TrainingExerciseRepository {

        val getPlan = Seeds<List<PlanSetDataModel>?>()
        val getPlans = Seeds<Map<String, List<PlanSetDataModel>?>>()
        val getRowsForTraining = Seeds<List<TrainingExerciseRepository.TrainingExerciseRow>>()

        override suspend fun getPlan(trainingUuid: String, exerciseUuid: String): List<PlanSetDataModel>? =
            getPlan.answer(record("getPlan", trainingUuid, exerciseUuid)) { unanswered("getPlan") }

        /** The relaxed answer, where an identity reaches it unseeded: no plan row at all. */
        override suspend fun getPlans(
            trainingUuid: String,
            exerciseUuids: List<String>,
        ): Map<String, List<PlanSetDataModel>?> =
            getPlans.answer(record("getPlans", trainingUuid, exerciseUuids)) { emptyMap() }

        override suspend fun getRowsForTraining(
            trainingUuid: String,
        ): List<TrainingExerciseRepository.TrainingExerciseRow> =
            getRowsForTraining.answer(record("getRowsForTraining", trainingUuid)) { unanswered("getRowsForTraining") }

        override suspend fun setPlan(
            trainingUuid: String,
            exerciseUuid: String,
            planSets: List<PlanSetDataModel>?,
        ): Nothing = unreached("setPlan")

        override suspend fun attachExercise(
            trainingUuid: String,
            exerciseUuid: String,
            planSets: List<PlanSetDataModel>?,
        ): Nothing = unreached("attachExercise")

        override suspend fun detachExercise(trainingUuid: String, exerciseUuid: String): Nothing =
            unreached("detachExercise")
    }

    private class RecordingPersonalRecordRepository : RecordingRepository(), PersonalRecordRepository {

        val observePersonalRecordsBatch = Seeds<Flow<Map<String, PersonalRecordDataModel>>>()

        override fun observePersonalRecordsBatch(
            exerciseUuids: Set<String>,
        ): Flow<Map<String, PersonalRecordDataModel>> =
            observePersonalRecordsBatch.answer(record("observePersonalRecordsBatch", exerciseUuids)) {
                unanswered("observePersonalRecordsBatch")
            }

        override suspend fun getPersonalRecord(exerciseUuid: String): Nothing = unreached("getPersonalRecord")

        override fun observePersonalRecord(exerciseUuid: String): Nothing = unreached("observePersonalRecord")

        override fun observePrSetUuids(exerciseUuids: Set<String>): Nothing = unreached("observePrSetUuids")
    }
}
