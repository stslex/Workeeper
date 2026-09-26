package io.github.stslex.workeeper.wear.mvi

import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.wear.protocol.NumericField
import io.github.stslex.workeeper.wear.ambient.WearAmbientState
import io.github.stslex.workeeper.wear.di.WearHandlerStore
import io.github.stslex.workeeper.wear.domain.WearInteractor
import io.github.stslex.workeeper.wear.domain.WearInteractorImpl
import io.github.stslex.workeeper.wear.mvi.handler.ClickHandler
import io.github.stslex.workeeper.wear.mvi.handler.CommonHandler
import io.github.stslex.workeeper.wear.mvi.handler.InputHandler
import io.github.stslex.workeeper.wear.mvi.handler.NavigationHandler
import io.github.stslex.workeeper.wear.mvi.mapper.WearPresentationMapper
import io.github.stslex.workeeper.wear.mvi.store.WearPlatformState
import io.github.stslex.workeeper.wear.mvi.store.WearPresentation
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Action
import io.github.stslex.workeeper.wear.mvi.store.WearStore.Event
import io.github.stslex.workeeper.wear.mvi.store.WearStore.State
import io.github.stslex.workeeper.wear.runtime.ControllerAction
import io.github.stslex.workeeper.wear.runtime.RuntimeTestEnvironment
import io.github.stslex.workeeper.wear.ui.WearSurfaceMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class WearHandlerTest {
    private val env = RuntimeTestEnvironment().also { it.accept() }
    private val state = MutableStateFlow(
        State(presentation = WearPresentation(WearSurfaceMapper.map(env.owner.snapshot.value))),
    )
    private val store = mockk<WearHandlerStore>(relaxed = true).apply {
        every { state } returns this@WearHandlerTest.state
        every { updateState(any()) } answers {
            val update = firstArg<(State) -> State>()
            this@WearHandlerTest.state.value = update(this@WearHandlerTest.state.value)
        }
    }
    private val interactor = WearInteractorImpl(env.owner)
    private val common = CommonHandler(
        store,
        interactor,
        WearPresentationMapper(),
        StoreDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined),
    ).also { handler ->
        every { store.consume(any()) } answers { handler.invoke(firstArg<Action.Common>()) }
    }

    @Test
    fun platformExitClosesTheEditorUsingTheSynchronousExpiryResult() {
        val ambient = WearAmbientState(isAmbient = true)
        state.value = state.value.copy(
            editor = NumericField.WEIGHT,
            platform = WearPlatformState(ambient = ambient),
            presentation = state.value.presentation.copy(ambient = ambient),
        )
        env.now = 121_000L
        env.owner.onWake()
        common.invoke(Action.Common.PlatformChanged(WearPlatformState()))
        assertFalse(state.value.presentation.ambient.isAmbient)
        assertFalse(state.value.presentation.model.controlsEnabled)
        assertNull(state.value.editor)
    }

    @Test
    fun ambientKeepsTheEditorUntilInteractiveEligibilityCanBeDecided() {
        state.value = state.value.copy(editor = NumericField.REPS)
        env.now = 121_000L
        env.owner.onWake()
        common.invoke(Action.Common.PlatformChanged(WearPlatformState(ambient = WearAmbientState(isAmbient = true))))
        assertEquals(NumericField.REPS, state.value.editor)
        assertFalse(state.value.presentation.model.controlsEnabled)
    }

    @Test
    fun openingAWeightEditorRequiresAWeightedModel() {
        val presentation = state.value.presentation
        state.value = state.value.copy(
            presentation = presentation.copy(model = presentation.model.copy(weighted = false)),
        )
        ClickHandler(store, interactor).invoke(Action.Click.Edit(NumericField.WEIGHT))
        assertNull(state.value.editor)
    }

    @Test
    fun draftInputAppliesEveryRelativeStepToTheCurrentRuntimeDraft() {
        state.value = state.value.copy(editor = NumericField.REPS)
        env.owner.onAction(ControllerAction.SetReps(12))
        InputHandler(store, interactor).invoke(Action.Input.Draft(NumericField.REPS, 3))
        assertEquals(15, env.owner.snapshot.value.workout.draft?.reps)
    }

    @Test
    fun aStaticPreviewCannotIssueACompletion() {
        val commands = mockk<WearInteractor>(relaxed = true)
        state.value = state.value.copy(presentation = state.value.presentation.copy(preview = true))
        ClickHandler(store, commands).invoke(Action.Click.Complete)
        verify(exactly = 0) { commands.perform(any()) }
    }

    @Test
    fun aStaticPreviewCannotEditTheProcessDraft() {
        val commands = mockk<WearInteractor>(relaxed = true)
        state.value = state.value.copy(
            editor = NumericField.REPS,
            presentation = state.value.presentation.copy(preview = true),
        )
        InputHandler(store, commands).invoke(Action.Input.Draft(NumericField.REPS, 2))
        verify(exactly = 0) { commands.perform(any()) }
    }

    @Test
    fun lateInputCannotEditAClosedOrDifferentEditor() {
        val commands = mockk<WearInteractor>(relaxed = true)
        val handler = InputHandler(store, commands)
        handler.invoke(Action.Input.Draft(NumericField.REPS, 2))
        state.value = state.value.copy(editor = NumericField.WEIGHT)
        handler.invoke(Action.Input.Draft(NumericField.REPS, 2))
        verify(exactly = 0) { commands.perform(any()) }
    }

    @Test
    fun notificationClickRequestsAPlatformEffect() {
        ClickHandler(store, interactor).invoke(Action.Click.EnableNotifications)
        verify(exactly = 1) { store.sendEvent(Event.EnableNotificationsRequested) }
    }

    @Test
    fun editorBackClearsOnlyTheScreenEditor() {
        env.owner.onAction(ControllerAction.SetReps(12))
        state.value = state.value.copy(editor = NumericField.REPS)
        NavigationHandler(store).invoke(Action.Navigation.CloseEditor)
        assertNull(state.value.editor)
        assertEquals(12, env.owner.snapshot.value.workout.draft?.reps)
    }

    @Test
    fun loggedActionsDoNotSerializeWorkoutOrDraftValues() {
        assertEquals("PresentationChanged", Action.Common.PresentationChanged(state.value.presentation).toString())
        assertEquals("Draft", Action.Input.Draft(NumericField.WEIGHT, 99).toString())
        assertEquals(
            "PlatformChanged",
            Action.Common.PlatformChanged(WearPlatformState(previewId = "sensitive")).toString(),
        )
    }
}
