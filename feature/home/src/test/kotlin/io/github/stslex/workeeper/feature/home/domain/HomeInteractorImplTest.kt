// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.domain

import io.github.stslex.workeeper.core.data.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.feature.home.domain.model.ActiveSessionWithStatsDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardModeDomain
import io.github.stslex.workeeper.feature.home.domain.model.StartCardReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.model.WeekReadoutDomain
import io.github.stslex.workeeper.feature.home.domain.usecase.ObserveStartCardReadoutUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class HomeInteractorImplTest {

    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val sessionConflictResolver = mockk<SessionConflictResolver>(relaxed = true)
    private val commonDataStore = mockk<CommonDataStore>(relaxed = true)
    private val observeStartCardReadoutUseCase =
        mockk<ObserveStartCardReadoutUseCase>(relaxed = true)
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
        every { sessionRepository.observeActiveSessionWithStats() } returns flowOf(row)

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
        every {
            observeStartCardReadoutUseCase(mode = StartCardModeDomain.WEEK, nowMillis = 42L)
        } returns flowOf(readout)

        val emitted = interactor
            .observeStartCardReadout(mode = StartCardModeDomain.WEEK, nowMillis = 42L)
            .first()

        assertEquals(readout, emitted)
        verify(exactly = 1) {
            observeStartCardReadoutUseCase(mode = StartCardModeDomain.WEEK, nowMillis = 42L)
        }
    }

    @Test
    fun `observeStartCardMode maps the stored string to the domain mode`() = runTest {
        every { commonDataStore.homeStartCardMode } returns flowOf("LAGGING_GROUPS")

        assertEquals(
            StartCardModeDomain.LAGGING_GROUPS,
            interactor.observeStartCardMode().first(),
        )
    }

    @Test
    fun `observeStartCardMode falls back to WEEK for an unknown stored value`() = runTest {
        every { commonDataStore.homeStartCardMode } returns flowOf("SOMETHING_ELSE")

        assertEquals(StartCardModeDomain.WEEK, interactor.observeStartCardMode().first())
    }

    @Test
    fun `setStartCardMode forwards the persistence encoding to the data store`() = runTest {
        coEvery { commonDataStore.setHomeStartCardMode(any()) } returns Unit

        interactor.setStartCardMode(StartCardModeDomain.FORGOTTEN_TRAINING)

        coVerify(exactly = 1) { commonDataStore.setHomeStartCardMode("FORGOTTEN_TRAINING") }
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
}
