package io.github.stslex.workeeper.feature.exercise.ui.mvi.store

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.MenuItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.mvi.StoreAnalytics
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStoreImpl
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ClickHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.InputHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetUiType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SetsUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
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
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
internal class ExerciseStoreImplTest {

    private val testDispatcher = StandardTestDispatcher()

    private val component = mockk<ExerciseComponent> {
        every { uuid } returns null
        every { trainingUuid } returns null
        coEvery { this@mockk.invoke(any()) } just runs
    }
    private val clickHandler = mockk<ClickHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }
    private val inputHandler = mockk<InputHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }
    private val commonHandler = mockk<CommonHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }
    private val navigationHandler = mockk<NavigationHandler> {
        coEvery { this@mockk.invoke(any()) } just runs
    }
    private val storeEmitter = mockk<ExerciseHandlerStoreImpl>(relaxed = true) {
        every { state } returns MutableStateFlow(ExerciseStore.State.INITIAL)
    }
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = testDispatcher,
        mainImmediateDispatcher = testDispatcher
    )

    private val logger = mockk<Logger> {
        every { i(any<String>()) } just runs
        every { i(any<() -> String>()) } just runs
    }

    private val analytics = mockk<StoreAnalytics<Action, Event>> {
        every { logEvent(any()) } just runs
        every { logAction(any()) } just runs
    }

    private val store: ExerciseStoreImpl = ExerciseStoreImpl(
        component = component,
        clickHandler = clickHandler,
        inputHandler = inputHandler,
        commonHandler = commonHandler,
        navigationHandler = navigationHandler,
        storeDispatchers = storeDispatchers,
        storeEmitter = storeEmitter,
        logger = logger,
        analytics = analytics
    )

    @Test
    fun `store initializes with correct initial state`() = runTest {
        val expectedState = ExerciseStore.State.INITIAL

        val actualState = store.state.value

        assertEquals(expectedState, actualState)
    }

    @Test
    fun `store initializes with Init action when component data is null`() = runTest {
        every { component.uuid } returns null
        every { component.trainingUuid } returns null
        val initAction = Action.Common.Init(uuid = null, trainingUuid = null)

        store.init()
        store.initEmitter()

        coVerify(exactly = 1) { commonHandler.invoke(initAction) }
        verify(exactly = 1) { logger.i("consume: $initAction") }
        verify(exactly = 1) { analytics.logAction(initAction) }
    }

    @Test
    fun `store initializes with Init action when component data is provided`() = runTest {
        every { component.uuid } returns "test-uuid"
        every { component.trainingUuid } returns null

        // Create a new store instance after setting up the mock
        val testStore = ExerciseStoreImpl(
            component = component,
            clickHandler = clickHandler,
            inputHandler = inputHandler,
            commonHandler = commonHandler,
            navigationHandler = navigationHandler,
            storeDispatchers = storeDispatchers,
            storeEmitter = storeEmitter,
            logger = logger,
            analytics = analytics
        )

        testStore.init()
        testStore.initEmitter()

        advanceUntilIdle()

        coVerify {
            commonHandler.invoke(
                Action.Common.Init(
                    uuid = "test-uuid",
                    trainingUuid = null
                )
            )
        }
    }

    @Test
    fun `navigation actions are handled by component`() = runTest(testDispatcher) {
        val action = Action.Navigation.Back

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        coVerify { component.invoke(action) }
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
    fun `input actions are handled by inputHandler`() = runTest(testDispatcher) {
        val action = Action.Input.PropertyName("Test Exercise")

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        coVerify { inputHandler.invoke(action) }
    }

    @Test
    fun `common actions are handled by commonHandler`() = runTest(testDispatcher) {
        val action = Action.Common.Init(uuid = null, trainingUuid = null)

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        coVerify { commonHandler.invoke(action) }
    }

    @Test
    fun `navigation middleware actions are handled by navigationHandler`() =
        runTest(testDispatcher) {
            val action = Action.NavigationMiddleware.BackWithConfirmation

            store.init()
            store.initEmitter()
            store.consume(action)
            advanceUntilIdle()

            coVerify { navigationHandler.invoke(action) }
        }

    @Test
    fun `multiple actions are processed in order`() = runTest(testDispatcher) {
        val actions = listOf(
            Action.Input.PropertyName("Test"),
            Action.Click.Save,
            Action.Navigation.Back
        )

        store.init()
        store.initEmitter()
        actions.forEach { action ->
            store.consume(action)
        }
        advanceUntilIdle()

        coVerify { inputHandler.invoke(actions[0] as Action.Input) }
        coVerify { clickHandler.invoke(actions[1] as Action.Click) }
        coVerify { component.invoke(actions[2] as Action.Navigation) }
    }

    @Test
    fun `store properly delegates dialog sets input actions`() = runTest(testDispatcher) {
        val weightAction = Action.Input.DialogSets.Weight("75.5")
        val repsAction = Action.Input.DialogSets.Reps("10")

        store.init()
        store.initEmitter()
        store.consume(weightAction)
        store.consume(repsAction)
        advanceUntilIdle()

        coVerify { inputHandler.invoke(weightAction) }
        coVerify { inputHandler.invoke(repsAction) }
    }

    @Test
    fun `store properly delegates dialog sets click actions`() = runTest(testDispatcher) {
        val set = SetsUiModel(
            uuid = Uuid.random().toString(),
            reps = PropertyHolder.IntProperty(initialValue = 10),
            weight = PropertyHolder.DoubleProperty(initialValue = 50.0),
            type = SetUiType.WORK
        )
        val openEditAction = Action.Click.DialogSets.OpenEdit(set)
        val openCreateAction = Action.Click.DialogSets.OpenCreate
        val dismissAction = Action.Click.DialogSets.DismissSetsDialog(set)
        val deleteAction = Action.Click.DialogSets.DeleteButton("uuid-123")
        val saveAction = Action.Click.DialogSets.SaveButton(set)
        val cancelAction = Action.Click.DialogSets.CancelButton

        store.init()
        store.initEmitter()
        store.consume(openEditAction)
        store.consume(openCreateAction)
        store.consume(dismissAction)
        store.consume(deleteAction)
        store.consume(saveAction)
        store.consume(cancelAction)
        advanceUntilIdle()

        coVerify { clickHandler.invoke(openEditAction) }
        coVerify { clickHandler.invoke(openCreateAction) }
        coVerify { clickHandler.invoke(dismissAction) }
        coVerify { clickHandler.invoke(deleteAction) }
        coVerify { clickHandler.invoke(saveAction) }
        coVerify { clickHandler.invoke(cancelAction) }
    }

    @Test
    fun `store properly handles menu variant click actions`() = runTest(testDispatcher) {
        // Given
        val exerciseUiModel = ExerciseUiModel(
            uuid = "menu-item-uuid",
            name = "Test Exercise",
            sets = persistentListOf(),
            timestamp = 100
        )
        val menuItem = MenuItem(
            uuid = "menu-uuid",
            text = "Test Exercise",
            itemModel = exerciseUiModel
        )
        val openMenuAction = Action.Click.OpenMenuVariants
        val closeMenuAction = Action.Click.CloseMenuVariants
        val menuItemClickAction = Action.Click.OnMenuItemClick(menuItem)

        store.init()
        store.initEmitter()
        store.consume(openMenuAction)
        store.consume(closeMenuAction)
        store.consume(menuItemClickAction)
        advanceUntilIdle()

        coVerify { clickHandler.invoke(openMenuAction) }
        coVerify { clickHandler.invoke(closeMenuAction) }
        coVerify { clickHandler.invoke(menuItemClickAction) }
    }

    @Test
    fun `store handles time input action`() = runTest(testDispatcher) {
        val timestamp = System.currentTimeMillis()
        val action = Action.Input.Time(timestamp)

        store.init()
        store.initEmitter()
        store.consume(action)
        advanceUntilIdle()

        coVerify { inputHandler.invoke(action) }
    }

    @Test
    fun `store handles all click actions correctly`() = runTest(testDispatcher) {
        val clickActions = listOf(
            Action.Click.Save,
            Action.Click.Cancel,
            Action.Click.Delete,
            Action.Click.ConfirmedDelete,
            Action.Click.PickDate,
            Action.Click.CloseDialog
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
    fun `store sends events through storeEmitter`() = runTest(testDispatcher) {
        val event = Event.InvalidParams

        store.init()
        store.initEmitter()
        store.sendEvent(event)
        advanceUntilIdle()

        verify { analytics.logEvent(event) }
    }

    @Test
    fun `store sends different event types correctly`() = runTest(testDispatcher) {
        val events = listOf(
            Event.InvalidParams,
            Event.Snackbar(SnackbarType.DISMISS),
            Event.Snackbar(SnackbarType.DELETE),
            Event.HapticClick
        )

        store.init()
        store.initEmitter()
        events.forEach { event ->
            store.sendEvent(event)
        }
        advanceUntilIdle()

        events.forEach { event ->
            verify { analytics.logEvent(event) }
        }
    }

}