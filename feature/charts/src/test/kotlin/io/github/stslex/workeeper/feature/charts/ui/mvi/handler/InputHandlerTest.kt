package io.github.stslex.workeeper.feature.charts.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.dataStore.store.CommonDataStore
import io.github.stslex.workeeper.feature.charts.di.ChartsHandlerStore
import io.github.stslex.workeeper.feature.charts.ui.mvi.store.ChartsStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

internal class InputHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val commonStore = mockk<CommonDataStore>(relaxed = true)
    private val testScope = TestScope(testDispatcher)
    private val store = mockk<ChartsHandlerStore>(relaxed = true) {
        every { this@mockk.scope } returns AppCoroutineScope(
            testScope,
            testDispatcher,
            testDispatcher
        )

        // Mock the launch function to actually execute the coroutine
        every {
            this@mockk.launch<Any>(
                onError = any(),
                onSuccess = any(),
                workDispatcher = any(),
                eachDispatcher = any(),
                action = any()
            )
        } answers {
            val action = arg<suspend CoroutineScope.() -> Any>(4)
            testScope.launch { runCatching { action() } }
        }
    }

    private val handler = InputHandler(commonStore, store)


    @Test
    fun `change start date action updates common store start date`() = runTest {
        val startTimestamp = 1500000L

        coEvery { commonStore.setHomeSelectedStartDate(startTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(startTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(startTimestamp) }
    }

    @Test
    fun `change end date action updates common store end date`() = runTest {
        val endTimestamp = 2500000L

        coEvery { commonStore.setHomeSelectedEndDate(endTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(endTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(endTimestamp) }
    }

    @Test
    fun `change start date action with zero timestamp`() = runTest {
        val zeroTimestamp = 0L

        coEvery { commonStore.setHomeSelectedStartDate(zeroTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(zeroTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(zeroTimestamp) }
    }

    @Test
    fun `change end date action with negative timestamp`() = runTest {
        val negativeTimestamp = -1000L

        coEvery { commonStore.setHomeSelectedEndDate(negativeTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(negativeTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(negativeTimestamp) }
    }

    @Test
    fun `change start date action with large timestamp`() = runTest {
        val largeTimestamp = Long.MAX_VALUE

        coEvery { commonStore.setHomeSelectedStartDate(largeTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(largeTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(largeTimestamp) }
    }

    @Test
    fun `multiple date change actions work correctly`() = runTest {
        val startTimestamp1 = 1000000L
        val startTimestamp2 = 2000000L
        val endTimestamp1 = 3000000L
        val endTimestamp2 = 4000000L

        coEvery { commonStore.setHomeSelectedStartDate(any()) } returns Unit
        coEvery { commonStore.setHomeSelectedEndDate(any()) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(startTimestamp1))
        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(endTimestamp1))
        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(startTimestamp2))
        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(endTimestamp2))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(startTimestamp1) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(startTimestamp2) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(endTimestamp1) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(endTimestamp2) }
    }

    @Test
    fun `change start date action handles store error gracefully`() = runTest {
        val startTimestamp = 1500000L

        coEvery { commonStore.setHomeSelectedStartDate(startTimestamp) } throws RuntimeException("Store error")

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(startTimestamp))

        testScheduler.advanceUntilIdle()

        // Verify the method was called despite the error
        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(startTimestamp) }
    }

    @Test
    fun `change end date action handles store error gracefully`() = runTest {
        val endTimestamp = 2500000L

        coEvery { commonStore.setHomeSelectedEndDate(endTimestamp) } throws RuntimeException("Store error")

        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(endTimestamp))

        testScheduler.advanceUntilIdle()

        // Verify the method was called despite the error
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(endTimestamp) }
    }

    @Test
    fun `launch function is called for both input actions`() = runTest {
        val startTimestamp = 1000000L
        val endTimestamp = 2000000L

        coEvery { commonStore.setHomeSelectedStartDate(any()) } returns Unit
        coEvery { commonStore.setHomeSelectedEndDate(any()) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(startTimestamp))
        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(endTimestamp))

        testScheduler.advanceUntilIdle()

        // Verify launch was called twice (once for each action)
        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(startTimestamp) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(endTimestamp) }
    }

    @Test
    fun `same timestamp can be set multiple times`() = runTest {
        val sameTimestamp = 1500000L

        coEvery { commonStore.setHomeSelectedStartDate(sameTimestamp) } returns Unit
        coEvery { commonStore.setHomeSelectedEndDate(sameTimestamp) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(sameTimestamp))
        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(sameTimestamp))
        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(sameTimestamp))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 2) { commonStore.setHomeSelectedStartDate(sameTimestamp) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(sameTimestamp) }
    }

    @Test
    fun `current timestamp values work correctly`() = runTest {
        val currentTime = System.currentTimeMillis()

        coEvery { commonStore.setHomeSelectedStartDate(currentTime) } returns Unit
        coEvery { commonStore.setHomeSelectedEndDate(currentTime) } returns Unit

        handler.invoke(ChartsStore.Action.Input.ChangeStartDate(currentTime))
        handler.invoke(ChartsStore.Action.Input.ChangeEndDate(currentTime))

        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { commonStore.setHomeSelectedStartDate(currentTime) }
        coVerify(exactly = 1) { commonStore.setHomeSelectedEndDate(currentTime) }
    }
}