package io.github.stslex.workeeper.feature.all_trainings.ui.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.paging.PagingData
import io.github.stslex.workeeper.core.core.coroutine.scope.AppCoroutineScope
import io.github.stslex.workeeper.core.ui.kit.components.PagingUiState
import io.github.stslex.workeeper.feature.all_trainings.di.TrainingHandlerStore
import io.github.stslex.workeeper.feature.all_trainings.domain.AllTrainingsInteractor
import io.github.stslex.workeeper.feature.all_trainings.ui.mvi.model.TrainingUiModel
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

internal class ClickHandlerTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = UnconfinedTestDispatcher(testScheduler)
    private val interactor = mockk<AllTrainingsInteractor>()

    private val testScope = TestScope(testDispatcher)
    private val pagingUiState = mockk<PagingUiState<PagingData<TrainingUiModel>>>(relaxed = true)
    private val stateFlow = MutableStateFlow(
        TrainingStore.State.init(pagingUiState),
    )

    private val appCoroutineScope = AppCoroutineScope(testScope, testDispatcher, testDispatcher)

    private val store = mockk<TrainingHandlerStore>(relaxed = true) {
        every { this@mockk.state } returns stateFlow
        every { this@mockk.scope } returns appCoroutineScope

        // Mock the launch function to actually execute the coroutine
        every {
            this@mockk.launch<Any>(
                onError = any(),
                onSuccess = any(),
                workDispatcher = any(),
                eachDispatcher = any(),
                action = any(),
            )
        } answers {
            val onSuccess = arg<suspend CoroutineScope.(Any) -> Unit>(1)
            val action = arg<suspend CoroutineScope.() -> Any>(4)

            testScope.launch { runCatching { onSuccess(this, action()) } }
        }
    }

    private val handler = ClickHandler(interactor, store)

    @Test
    fun `training item click when no items selected navigates to training`() = runTest {
        val trainingUuid = Uuid.random().toString()

        handler.invoke(TrainingStore.Action.Click.TrainingItemClick(trainingUuid))

        verify(exactly = 1) {
            store.consume(
                TrainingStore.Action.Navigation.OpenTraining(
                    trainingUuid,
                ),
            )
        }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
    }

    @Test
    fun `training item click when items selected adds to selection`() = runTest {
        val existingUuid = Uuid.random().toString()
        val newUuid = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(existingUuid).toImmutableSet(),
        )

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(TrainingStore.Action.Click.TrainingItemClick(newUuid))

        verify(exactly = 1) { store.updateState(any()) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        assertEquals(2, stateFlow.value.selectedItems.size)
    }

    @Test
    fun `training item click when item already selected removes from selection`() = runTest {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(uuid1, uuid2).toImmutableSet(),
        )

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        every { store.updateState(capture(stateSlot)) } answers {
            val transform = stateSlot.captured
            val newState = transform(stateFlow.value)
            stateFlow.value = newState
        }

        handler.invoke(TrainingStore.Action.Click.TrainingItemClick(uuid1))

        verify(exactly = 1) { store.updateState(any()) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        assertEquals(1, stateFlow.value.selectedItems.size)
        assertEquals(setOf(uuid2), stateFlow.value.selectedItems)
    }

    @Test
    fun `training item long click adds item to selection`() = runTest {
        val uuid = Uuid.random().toString()

        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid))

        verify(exactly = 1) { store.updateState(any()) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
    }

    @Test
    fun `training item long click removes item when already selected`() = runTest {
        val uuid = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(uuid).toImmutableSet(),
        )

        handler.invoke(TrainingStore.Action.Click.TrainingItemLongClick(uuid))

        verify(exactly = 1) { store.updateState(any()) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
    }

    @Test
    fun `action button click when no items selected navigates to create training`() = runTest {
        stateFlow.value = stateFlow.value.copy(selectedItems = persistentSetOf())

        handler.invoke(TrainingStore.Action.Click.ActionButton)

        verify(exactly = 1) { store.consume(TrainingStore.Action.Navigation.CreateTraining) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
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

        coVerify(exactly = 1) { interactor.deleteAll(any()) }
        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        verify(exactly = 0) { store.consume(TrainingStore.Action.Navigation.CreateTraining) }
    }

    @Test
    fun `back handler clears selection when items are selected`() = runTest {
        val uuid1 = Uuid.random().toString()
        val uuid2 = Uuid.random().toString()
        stateFlow.value = stateFlow.value.copy(
            selectedItems = setOf(uuid1, uuid2).toImmutableSet(),
        )

        handler.invoke(TrainingStore.Action.Click.BackHandler)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }

        val stateSlot = slot<(TrainingStore.State) -> TrainingStore.State>()
        verify(exactly = 1) { store.updateState(capture(stateSlot)) }

        val newState = stateSlot.captured(stateFlow.value)
        assertTrue(newState.selectedItems.isEmpty())
        assertEquals(persistentSetOf<String>(), newState.selectedItems)
    }

    @Test
    fun `back handler does nothing when no items are selected`() = runTest {
        stateFlow.value = stateFlow.value.copy(selectedItems = persistentSetOf())

        handler.invoke(TrainingStore.Action.Click.BackHandler)

        verify(exactly = 1) { store.sendEvent(TrainingStore.Event.Haptic(HapticFeedbackType.Confirm)) }
        verify(exactly = 0) { store.updateState(any()) }
    }
}
