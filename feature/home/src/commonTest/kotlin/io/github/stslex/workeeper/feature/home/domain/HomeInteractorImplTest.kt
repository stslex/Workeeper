// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.exercise.session.PlanUpdate
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingChangeDataModel
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository.ExercisePlanWrite
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.usecase.ObserveStartCardReadoutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

internal class HomeInteractorImplTest {

    private val sessionRepository = ActiveStatsSessionRepository()
    private val trainingRepository = UnreachedTrainingRepository()
    private val sessionConflictResolver = SessionConflictResolver(sessionRepository)
    private val commonDataStore = StartCardModeDataStore()
    private val observeStartCardReadoutUseCase = RecordingStartCardReadoutUseCase()
    private val interactor = HomeInteractorImpl(
        sessionRepository = sessionRepository,
        trainingRepository = trainingRepository,
        sessionConflictResolver = sessionConflictResolver,
        commonDataStore = commonDataStore,
        observeStartCardReadoutUseCase = observeStartCardReadoutUseCase,
        defaultDispatcher = Dispatchers.Unconfined,
    )

    @Test
    fun `observeActiveSession maps repository ActiveSessionWithStats to domain`() = runTest {
        val row = SessionRepository.ActiveSessionWithStats(
            sessionUuid = "session-1",
            trainingUuid = "training-1",
            trainingName = "Push Day",
            isAdhoc = false,
            startedAt = 123L,
            totalCount = 5,
            doneCount = 2,
        )
        sessionRepository.activeSessionWithStats = flowOf(row)

        val mapped = interactor.observeActiveSession().first()

        assertEquals(
            ActiveSessionWithStatsDomain(
                sessionUuid = "session-1",
                trainingUuid = "training-1",
                trainingName = "Push Day",
                isAdhoc = false,
                startedAt = 123L,
                totalCount = 5,
                doneCount = 2,
            ),
            mapped,
        )
    }

    @Test
    fun `observeStartCardReadout delegates to the use case with mode and now`() = runTest {
        val readout = StartCardReadoutDomain.Week(
            WeekReadoutDomain(sessionsThisWeek = 2, trainedDayIndexes = setOf(0, 3)),
        )
        observeStartCardReadoutUseCase.readout = flowOf(readout)

        val emitted = interactor
            .observeStartCardReadout(mode = StartCardModeDomain.WEEK, nowMillis = 42L)
            .first()

        assertEquals(readout, emitted)
        assertEquals(
            1,
            observeStartCardReadoutUseCase.calls.count {
                it == Triple(StartCardModeDomain.WEEK, 42L, TimeZone.currentSystemDefault())
            },
        )
    }

    @Test
    fun `observeStartCardMode maps the stored string to the domain mode`() = runTest {
        commonDataStore.storedMode = flowOf("LAGGING_GROUPS")

        assertEquals(
            StartCardModeDomain.LAGGING_GROUPS,
            interactor.observeStartCardMode().first(),
        )
    }

    @Test
    fun `observeStartCardMode falls back to WEEK for an unknown stored value`() = runTest {
        commonDataStore.storedMode = flowOf("SOMETHING_ELSE")

        assertEquals(StartCardModeDomain.WEEK, interactor.observeStartCardMode().first())
    }

    @Test
    fun `setStartCardMode forwards the persistence encoding to the data store`() = runTest {
        interactor.setStartCardMode(StartCardModeDomain.FORGOTTEN_TRAINING)

        assertEquals(1, commonDataStore.writtenModes.count { it == "FORGOTTEN_TRAINING" })
    }

    /**
     * The persistence contract (HS6): these strings are what lives in DataStore, and the
     * WEEK default is what `CommonDataStoreImpl` bakes into an absent key. Renaming an
     * entry without keeping its value would silently reset users to the default.
     */
    @Test
    fun `the storage encoding and the WEEK default are pinned`() {
        assertEquals("WEEK", StartCardModeDomain.WEEK.value)
        assertEquals("DAYS_SINCE_LAST", StartCardModeDomain.DAYS_SINCE_LAST.value)
        assertEquals("LAGGING_GROUPS", StartCardModeDomain.LAGGING_GROUPS.value)
        assertEquals("FORGOTTEN_TRAINING", StartCardModeDomain.FORGOTTEN_TRAINING.value)
        assertEquals(StartCardModeDomain.WEEK, StartCardModeDomain.fromValue("not-a-mode"))
    }

    /** Records each call as (mode, nowMillis, timeZone) and answers the identity's flow (D2). */
    private class RecordingStartCardReadoutUseCase : ObserveStartCardReadoutUseCase {

        val calls = mutableListOf<Triple<StartCardModeDomain, Long, TimeZone>>()
        var readout: Flow<StartCardReadoutDomain>? = null

        override fun invoke(
            mode: StartCardModeDomain,
            nowMillis: Long,
            timeZone: TimeZone,
        ): Flow<StartCardReadoutDomain> {
            calls.add(Triple(mode, nowMillis, timeZone))
            return checkNotNull(readout) { "the identity set up no readout flow" }
        }
    }

    /** Answers [homeStartCardMode] with the identity's flow and records each written mode. */
    private class StartCardModeDataStore : CommonDataStore {

        var storedMode: Flow<String>? = null
        val writtenModes = mutableListOf<String>()

        override val homeStartCardMode: Flow<String>
            get() = checkNotNull(storedMode) { "the identity set up no stored mode" }

        override suspend fun setHomeStartCardMode(value: String) {
            writtenModes.add(value)
        }

        override val homeSelectedStartDate: Nothing
            get() = unreached("homeSelectedStartDate")

        override val homeSelectedEndDate: Nothing
            get() = unreached("homeSelectedEndDate")

        override val themePreference: Nothing
            get() = unreached("themePreference")

        override suspend fun setHomeSelectedStartDate(value: Long): Nothing =
            unreached("setHomeSelectedStartDate")

        override suspend fun setHomeSelectedEndDate(value: Long): Nothing =
            unreached("setHomeSelectedEndDate")

        override suspend fun setThemePreference(value: String): Nothing =
            unreached("setThemePreference")
    }

    /**
     * Answers [observeActiveSessionWithStats] with the identity's flow. The real
     * [SessionConflictResolver] wraps this fake and no identity calls it.
     */
    private class ActiveStatsSessionRepository : SessionRepository {

        var activeSessionWithStats: Flow<SessionRepository.ActiveSessionWithStats?>? = null

        override fun observeActiveSessionWithStats(): Flow<SessionRepository.ActiveSessionWithStats?> =
            checkNotNull(activeSessionWithStats) { "the identity set up no active-session flow" }

        override fun observeActive(): Nothing = unreached("observeActive")

        override fun observeAnyActiveSession(): Nothing = unreached("observeAnyActiveSession")

        override suspend fun getAnyActiveSession(): Nothing = unreached("getAnyActiveSession")

        override suspend fun getActiveSessionProgress(): Nothing =
            unreached("getActiveSessionProgress")

        override suspend fun getActive(): Nothing = unreached("getActive")

        override fun pagedRecentWithStats(): Nothing = unreached("pagedRecentWithStats")

        override fun observeFinishedTimesBetween(
            startInclusive: Long,
            endExclusive: Long,
        ): Nothing = unreached("observeFinishedTimesBetween")

        override fun observeLastFinishedSession(): Nothing = unreached("observeLastFinishedSession")

        override suspend fun getSessionDetail(sessionUuid: String): Nothing =
            unreached("getSessionDetail")

        override fun pagedFinished(): Nothing = unreached("pagedFinished")

        override fun pagedFinishedByTraining(trainingUuid: String): Nothing =
            unreached("pagedFinishedByTraining")

        override suspend fun getRecentFinishedByTraining(trainingUuid: String, limit: Int): Nothing =
            unreached("getRecentFinishedByTraining")

        override suspend fun getById(uuid: String): Nothing = unreached("getById")

        override suspend fun startSession(trainingUuid: String): Nothing = unreached("startSession")

        override suspend fun startSessionWithExercises(
            trainingUuid: String,
            exerciseUuids: List<Pair<String, Int>>,
        ): Nothing = unreached("startSessionWithExercises")

        override suspend fun resumeSession(sessionUuid: String): Nothing = unreached("resumeSession")

        override suspend fun finishSession(sessionUuid: String, finishedAt: Long): Nothing =
            unreached("finishSession")

        override suspend fun finishSessionAtomic(
            sessionUuid: String,
            finishedAt: Long,
            planUpdates: List<PlanUpdate>,
            newTrainingName: String?,
            discardedSetUuids: List<String>,
        ): Nothing = unreached("finishSessionAtomic")

        override suspend fun deleteSession(uuid: String): Nothing = unreached("deleteSession")

        override suspend fun createAdhocSession(name: String, exerciseUuids: List<String>): Nothing =
            unreached("createAdhocSession")

        override suspend fun addExerciseToActiveSession(
            sessionUuid: String,
            trainingUuid: String,
            exerciseUuid: String,
            attachToPlan: Boolean,
        ): Nothing = unreached("addExerciseToActiveSession")

        override suspend fun discardAdhocSession(sessionUuid: String, trainingUuid: String): Nothing =
            unreached("discardAdhocSession")

        override suspend fun removeExerciseFromSession(
            performedExerciseUuid: String,
            exerciseUuid: String,
            trainingUuid: String?,
            removeFromPlan: Boolean,
        ): Nothing = unreached("removeExerciseFromSession")

        override fun pagedHistoryByExercise(exerciseUuid: String): Nothing =
            unreached("pagedHistoryByExercise")

        override suspend fun getHistoryByExercise(exerciseUuid: String): Nothing =
            unreached("getHistoryByExercise")
    }

    /** No identity reaches the training repository: every member fails fast. */
    private class UnreachedTrainingRepository : TrainingRepository {

        override fun getTrainingsUnique(query: String): Nothing = unreached("getTrainingsUnique")

        override suspend fun updateTraining(training: TrainingChangeDataModel): Nothing =
            unreached("updateTraining")

        override suspend fun updateTrainingWithPlans(
            training: TrainingChangeDataModel,
            plans: List<ExercisePlanWrite>,
        ): Nothing = unreached("updateTrainingWithPlans")

        override suspend fun updateName(uuid: String, name: String): Nothing =
            unreached("updateName")

        override suspend fun removeTraining(uuid: String): Nothing = unreached("removeTraining")

        override suspend fun getTraining(uuid: String): Nothing = unreached("getTraining")

        override fun subscribeForTraining(uuid: String): Nothing = unreached("subscribeForTraining")

        override suspend fun removeAll(uuids: List<String>): Nothing = unreached("removeAll")

        override suspend fun archive(uuid: String): Nothing = unreached("archive")

        override suspend fun restore(uuid: String): Nothing = unreached("restore")

        override suspend fun permanentDelete(uuid: String): Nothing = unreached("permanentDelete")

        override fun pagedArchived(): Nothing = unreached("pagedArchived")

        override fun observeArchivedCount(): Nothing = unreached("observeArchivedCount")

        override suspend fun countSessionsUsing(trainingUuid: String): Nothing =
            unreached("countSessionsUsing")

        override fun pagedActiveWithStats(filterTagUuids: Set<String>): Nothing =
            unreached("pagedActiveWithStats")

        override fun observeRecentTemplates(limit: Int): Nothing = unreached("observeRecentTemplates")

        override fun observeMostForgottenTemplate(): Nothing =
            unreached("observeMostForgottenTemplate")

        override suspend fun bulkArchive(uuids: Set<String>): Nothing = unreached("bulkArchive")

        override suspend fun bulkPermanentDelete(uuids: Set<String>): Nothing =
            unreached("bulkPermanentDelete")

        override suspend fun canBulkPermanentDelete(uuids: Set<String>): Nothing =
            unreached("canBulkPermanentDelete")
    }

    private companion object {

        fun unreached(member: String): Nothing =
            error("$member must not be used by HomeInteractorImplTest")
    }
}
