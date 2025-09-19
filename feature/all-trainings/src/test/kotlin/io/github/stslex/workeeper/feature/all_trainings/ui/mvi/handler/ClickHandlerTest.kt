package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.store.TrainingStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

internal class ClickHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val interactor = mockk<AllTrainingsInteractor>()
    private val store = mockk<TrainingHandlerStore>(relaxed = true)
    private val testScope = TestScope(testDispatcher)
    private val pagingUiState =
        mockk<PagingUiState<androidx.paging.PagingData<io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel>>>(
            relaxed = true
        )
    private val stateFlow = MutableStateFlow(
        TrainingStore.State.init(pagingUiState)
    )
    private val handler = ClickHandler(interactor, store)

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { store.state } returns stateFlow
        every { store.scope } returns AppCoroutineScope(testScope, testDispatcher, testDispatcher)

        // Mock the launch function to actually execute the coroutine
        every {
            store.launch<Any>(
                onError = any(),
                onSuccess = any(),
                workDispatcher = any(),
                eachDispatcher = any(),
                action = any()
            )
        } answers {
            val onSuccess = arg<suspend CoroutineScope.(Any) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any>(4)

            testScope.launch {
                try {
                    val result = action()
                    onSuccess(this, result)
                } catch (_: Exception) {
                    // Handle error if needed
                }
            }
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `training item click when no items selected navigates to training`() = runTest {
        val trainingUuid = Uuid.random().toString()

        handler.invoke(TrainingStore.Action.Click.TrainingItemClick(trainingUuid))

        verify { store.consume(TrainingStore.Action.Navigation.OpenTraining(trainingUuid)) }
    }

    @Test
    fun `training item click when items selected adds to selection`() = runTest {
        val existingUuid = Uuid.random().toString()
        val newUuid = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(existingUuid).toImmutableSet()
        )

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(TrainingStore.Action.Click.TrainingItemClick(newUuid))

        verify { store.updateState(any()) }
        assertEquals(2, stateFlow.value.selectedItems.size)
    }

    @Test
    fun `training item click when item already selected removes from selection`() = runTest {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(uuid1, uuid2).toImmutableSet()
        )

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(TrainingStore.Action.Click.TrainingItemClick(uuid1))

        verify { store.updateState(any()) }
        assertEquals(1, stateFlow.value.selectedItems.size)
        assertEquals(setOf(uuid2), stateFlow.value.selectedItems)
    }

    @Test
    fun `training item long click adds item to selection`() = runTest {
        val uuid = Uuid.random().toString()

        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid))

        verify {
            store.updateState(any())
        }
    }

    @Test
    fun `training item long click removes item when already selected`() = runTest {
        val uuid = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(uuid).toImmutableSet()
        )

        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid))

        verify {
            store.updateState(any())
        }
    }

    @Test
    fun `action button click when no items selected navigates to create training`() = runTest {
        stateFlow.value = stateFlow.value.copy(selectedItems = persistentSetOf())

        handler.invoke(TrainingStore.Action.Click.ActionButton)

        verify { store.consume(TrainingStore.Action.Navigation.CreateTraining) }
        coVerify(exactly = 0) { interactor.deleteAll(any()) }
    }

    @Test
    fun `action button click when items selected deletes them`() = runTest {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()
        val selectedItems = setOf(uuid1, uuid2).toImmutableSet()
        stateFlow.value = stateFlow.value.copy(selectedItems = selectedItems)

        coEvery { interactor.deleteAll(any()) } returns Unit

        handler.invoke(TrainingStore.Action.Click.ActionButton)

        // Wait for async execution to complete
        testScheduler.advanceUntilIdle()

        coVerify { interactor.deleteAll(any()) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.CreateTraining) }
    }
}