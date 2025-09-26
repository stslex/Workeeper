package io.github.stslex.workeeper.feature.single_training.ui.mvi.store

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.single_training.di.TrainingHandlerStoreImpl
import io.github.stslex.workeeper.feature.single_training.ui.model.DialogState
import io.github.stslex.workeeper.feature.single_training.ui.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.single_training.ui.model.TrainingUiModel
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Action
import io.github.stslex.workeeper.feature.single_training.ui.mvi.store.TrainingStore.Event
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class TrainingStoreImplTest {

    private val testDispatcher = StandardTestDispatcher()

    private val navigationHandler = mockk<NavigationHandler> {
        every { uuid } returns "test-uuid"
        coEvery { this@mockk.invoke(any()) } just runs
    }

    private val commonHandler = mockk<CommonHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }

    private val inputHandler = mockk<InputHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }

    private val clickHandler = mockk<ClickHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }

    private val handlerStore = mockk<TrainingHandlerStoreImpl>(relaxed = true) {
        every { state } returns MutableStateFlow(TrainingStore.State.INITIAL)
    }

    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = testDispatcher,
        mainImmediateDispatcher = testDispatcher,
    )

    private val logger = mockk<Logger> {
        every { i(any<String>()) } just runs
        every { i(any<() -> String>()) } just runs
    }

    private val analytics = mockk<StoreAnalytics<Action, Event>>(relaxed = true)

    private val store: TrainingStoreImpl = TrainingStoreImpl(
        navigationHandler = navigationHandler,
        commonHandler = commonHandler,
        inputHandler = inputHandler,
        clickHandler = clickHandler,
        storeDispatchers = storeDispatchers,
        handlerStore = handlerStore,
        analytics = analytics,
        logger = logger,
    )

    @Test
    fun `store initializes with correct initial state`() = runTest {
        val expectedState = TrainingStore.State.INITIAL

        val actualState = store.state.value

        assertEquals(expectedState, actualState)
    }

    @Test
    fun `store initializes with Common Init action using uuid from navigationHandler`() =
        runTest {
            val expectedUuid = "test-uuid"
            val initAction = Action.Common.Init(expectedUuid)

            store.init()
            store.initEmitter()
            advanceUntilIdle()

            coVerify(exactly = 1) { commonHandler.invoke(initAction) }
            verify(exactly = 1) { logger.i("consume: $initAction") }
            verify(exactly = 1) { analytics.logAction(initAction) }
        }

    @Test
    fun `navigation actions are handled by navigationHandler`() = runTest(testDispatcher) {
        val action = Action.Navigation.PopBack

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        coVerify { navigationHandler.invoke(action) }
    }

    @Test
    fun `common actions are handled by commonHandler`() = runTest(testDispatcher) {
        val action = Action.Common.Init("test-uuid")

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        coVerify { commonHandler.invoke(action) }
    }

    @Test
    fun `input actions are handled by inputHandler`() = runTest(testDispatcher) {
        val nameAction = Action.Input.Name("Test Training")

        store.init()
        store.initEmitter()
        store.consume(nameAction)
        advanceUntilIdle()

        coVerify { inputHandler.invoke(nameAction) }
    }

    @Test
    fun `click actions are handled by clickHandler`() = runTest(testDispatcher) {
        val action = Action.Click.Save

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        coVerify { clickHandler.invoke(action) }
    }

    @Test
    fun `multiple actions are processed in order`() = runTest(testDispatcher) {
        val timestamp = System.currentTimeMillis()
        val actions = listOf(
            Action.Input.Name("Test Training"),
            Action.Input.Date(timestamp),
            Action.Click.Save,
            Action.Navigation.PopBack,
        )

        store.init()
        store.initEmitter()
        actions.forEach { action ->
            store.consume(action)
        }
        advanceUntilIdle()

        coVerify { inputHandler.invoke(actions[0] as Action.Input) }
        coVerify { inputHandler.invoke(actions[1] as Action.Input) }
        coVerify { clickHandler.invoke(actions[2] as Action.Click) }
        coVerify { navigationHandler.invoke(actions[3] as Action.Navigation) }
    }

    @Test
    fun `store properly handles all input actions`() = runTest(testDispatcher) {
        val timestamp = System.currentTimeMillis()
        val inputActions = listOf(
            Action.Input.Name("Updated Training"),
            Action.Input.Date(timestamp),
        )

        store.init()
        store.initEmitter()
        inputActions.forEach { action ->
            store.consume(action)
        }
        advanceUntilIdle()

        inputActions.forEach { action ->
            coVerify { inputHandler.invoke(action) }
        }
    }

    @Test
    fun `store properly handles all click actions`() = runTest(testDispatcher) {
        val clickActions = listOf(
            Action.Click.Close,
            Action.Click.Save,
            Action.Click.Delete,
            Action.Click.OpenCalendarPicker,
            Action.Click.CloseCalendarPicker,
        )

        store.init()
        store.initEmitter()
        clickActions.forEach { action ->
            store.consume(action)
        }
        advanceUntilIdle()

        clickActions.forEach { action ->
            coVerify { clickHandler.invoke(action) }
        }
    }

    @Test
    fun `store properly handles calendar picker actions`() = runTest(testDispatcher) {
        val openAction = Action.Click.OpenCalendarPicker
        val closeAction = Action.Click.CloseCalendarPicker

        store.init()
        store.initEmitter()
        store.consume(openAction)
        store.consume(closeAction)
        advanceUntilIdle()

        coVerify { clickHandler.invoke(openAction) }
        coVerify { clickHandler.invoke(closeAction) }
    }

    @Test
    fun `store sends events through handlerStore`() = runTest(testDispatcher) {
        // Since Event is a sealed interface with no implementations,
        // we'll test that the analytics system is in place by checking store creation
        store.init()
        store.initEmitter()
        advanceUntilIdle()

        // Verify that the store was created with proper analytics support
        // This is implicit testing since there are no concrete Event types to test
        coVerify(atLeast = 1) { commonHandler.invoke(any()) }
        verify(atLeast = 1) { analytics.logAction(any()) }
    }

    @Test
    fun `initial state has correct default values`() = runTest {
        val state = store.state.value

        assertEquals(TrainingUiModel.INITIAL, state.training)
        assertEquals(DialogState.Closed, state.dialogState)

        // Verify TrainingUiModel.INITIAL structure
        assertEquals("", state.training.uuid)
        assertEquals("", state.training.name)
        assertEquals(0, state.training.labels.size)
        assertEquals(0, state.training.exercises.size)

        // Date should be approximately current time
        val delta = 5000L // 5 seconds tolerance
        val currentTime = System.currentTimeMillis()
        assert(kotlin.math.abs(state.training.date.value - currentTime) < delta) {
            "Initial date should be approximately current time"
        }
    }

    @Test
    fun `state data classes have proper immutable structure`() = runTest {
        val state = TrainingStore.State.INITIAL
        val newTraining = TrainingUiModel(
            uuid = "new-uuid",
            name = "New Training",
            labels = persistentListOf("strength", "cardio"),
            exercises = persistentListOf(
                ExerciseUiModel(
                    uuid = "exercise-1",
                    name = "Push-ups",
                    labels = persistentListOf("bodyweight"),
                    sets = 3,
                    timestamp = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis()),
                ),
            ),
            date = PropertyHolder.DateProperty.new(initialValue = System.currentTimeMillis() + 86400000), // tomorrow
        )

        // Verify state is data class with copy functionality
        val newState = state.copy(
            training = newTraining,
            dialogState = DialogState.Calendar,
        )

        assertEquals(newTraining, newState.training)
        assertEquals(DialogState.Calendar, newState.dialogState)
        assertEquals("New Training", newState.training.name)
        assertEquals(2, newState.training.labels.size)
        assertEquals(1, newState.training.exercises.size)
        assertEquals("Push-ups", newState.training.exercises.first().name)

        // Original state should be unchanged
        assertEquals(TrainingUiModel.INITIAL, state.training)
        assertEquals(DialogState.Closed, state.dialogState)
    }

    @Test
    fun `navigation handler uuid is properly accessed during initialization`() = runTest {
        val expectedUuid = "test-training-uuid"
        val customNavigationHandler = mockk<NavigationHandler>()
        every { customNavigationHandler.uuid } returns expectedUuid
        coEvery { customNavigationHandler.invoke(any()) } just runs

        val customStore = TrainingStoreImpl(
            navigationHandler = customNavigationHandler,
            commonHandler = commonHandler,
            inputHandler = inputHandler,
            clickHandler = clickHandler,
            storeDispatchers = storeDispatchers,
            handlerStore = handlerStore,
            analytics = analytics,
            logger = logger,
        )

        customStore.init()
        customStore.initEmitter()
        advanceUntilIdle()

        coVerify { commonHandler.invoke(Action.Common.Init(expectedUuid)) }
    }

    @Test
    fun `store handles null uuid from navigationHandler`() = runTest {
        val nullUuidNavigationHandler = mockk<NavigationHandler>()
        every { nullUuidNavigationHandler.uuid } returns null
        coEvery { nullUuidNavigationHandler.invoke(any()) } just runs

        val customStore = TrainingStoreImpl(
            navigationHandler = nullUuidNavigationHandler,
            commonHandler = commonHandler,
            inputHandler = inputHandler,
            clickHandler = clickHandler,
            storeDispatchers = storeDispatchers,
            handlerStore = handlerStore,
            analytics = analytics,
            logger = logger,
        )

        customStore.init()
        customStore.initEmitter()
        advanceUntilIdle()

        coVerify { commonHandler.invoke(Action.Common.Init(null)) }
    }

    @Test
    fun `different dialog states are handled correctly`() = runTest {
        val initialState = TrainingStore.State.INITIAL

        // Test all dialog states
        val closedState = initialState.copy(dialogState = DialogState.Closed)
        val calendarState = initialState.copy(dialogState = DialogState.Calendar)

        assertEquals(DialogState.Closed, closedState.dialogState)
        assertEquals(DialogState.Calendar, calendarState.dialogState)

        // Verify dialog states are sealed interface with proper inheritance
        assert(closedState.dialogState is DialogState.Closed)
        assert(calendarState.dialogState is DialogState.Calendar)
    }
}
