// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.image_viewer.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.ui.navigation.Screen.ExerciseImageRequest
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerHandlerStore
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Action
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.Event
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStore.State
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.test.Test
import kotlin.test.assertEquals

internal class ClickHandlerTest {

    private fun setup(
        initialState: State = State.create("model", editable = true),
    ): TestSetup {
        val store = FakeImageViewerHandlerStore(initialState)
        return TestSetup(store, ClickHandler(store))
    }

    private data class TestSetup(
        val store: FakeImageViewerHandlerStore,
        val handler: ClickHandler,
    )

    /**
     * The viewer's verbs are a REQUEST, so only a caller that can honour one may be offered it —
     * guarded in the handler even though the `⋮` is already hidden on a non-editable route.
     */
    @Test
    fun `a non-editable route cannot open the verbs sheet`() {
        val (store, handler) = setup(State.create("model", editable = false))

        handler.invoke(Action.Click.OnMenuClick)

        assertEquals(State.SheetState.Hidden, store.state.value.sheetState)
        assertEquals(emptyList(), store.events)
        assertEquals(emptyList(), store.consumedActions)
    }

    /** The other direction, so the guard is a gate and not an off switch. */
    @Test
    fun `an editable route opens the verbs sheet`() {
        val (store, handler) = setup()

        handler.invoke(Action.Click.OnMenuClick)

        assertEquals(State.SheetState.Menu, store.state.value.sheetState)
        assertEquals(
            listOf<Event>(Event.HapticClick(HapticFeedbackType.ContextClick)),
            store.events,
        )
        assertEquals(emptyList(), store.consumedActions)
    }

    @Test
    fun `Replace pops with a REPLACE request and closes the sheet in the same transition`() {
        val (store, handler) = setup(
            State.create("model", editable = true).copy(sheetState = State.SheetState.Menu),
        )

        handler.invoke(Action.Click.OnReplaceClick)

        assertEquals(State.SheetState.Hidden, store.state.value.sheetState)
        assertEquals(
            listOf<Action>(Action.Navigation.BackWithRequest(ExerciseImageRequest.REPLACE)),
            store.consumedActions,
        )
    }

    @Test
    fun `Remove pops with a REMOVE request`() {
        val (store, handler) = setup(
            State.create("model", editable = true).copy(sheetState = State.SheetState.Menu),
        )

        handler.invoke(Action.Click.OnRemoveClick)

        assertEquals(State.SheetState.Hidden, store.state.value.sheetState)
        assertEquals(
            listOf<Action>(Action.Navigation.BackWithRequest(ExerciseImageRequest.REMOVE)),
            store.consumedActions,
        )
    }

    @Test
    fun `OnBackClick emits ContextClick haptic and consumes Navigation Back`() {
        val (store, handler) = setup()

        handler.invoke(Action.Click.OnBackClick)

        assertEquals(
            listOf<Event>(Event.HapticClick(HapticFeedbackType.ContextClick)),
            store.events,
        )
        assertEquals(listOf<Action>(Action.Navigation.Back), store.consumedActions)
    }

    @Test
    fun `OnDoubleTap from MIN_SCALE jumps to DOUBLE_TAP_TARGET_SCALE and resets offsets`() {
        val (store, handler) = setup(
            State.create("model", editable = true).copy(
                scale = State.MIN_SCALE,
                offsetX = 0f,
                offsetY = 0f,
            ),
        )

        handler.invoke(Action.Click.OnDoubleTap)

        assertEquals(State.DOUBLE_TAP_TARGET_SCALE, store.state.value.scale)
        assertEquals(0f, store.state.value.offsetX)
        assertEquals(0f, store.state.value.offsetY)
    }

    @Test
    fun `OnDoubleTap from any scale above MIN_SCALE collapses to MIN_SCALE and resets offsets`() {
        val (store, handler) = setup(
            State.create("model", editable = true).copy(
                scale = 3f,
                offsetX = 100f,
                offsetY = -40f,
            ),
        )

        handler.invoke(Action.Click.OnDoubleTap)

        assertEquals(State.MIN_SCALE, store.state.value.scale)
        assertEquals(0f, store.state.value.offsetX)
        assertEquals(0f, store.state.value.offsetY)
    }

    @Test
    fun `OnDoubleTap emits ContextClick haptic`() {
        val (store, handler) = setup()

        handler.invoke(Action.Click.OnDoubleTap)

        assertEquals(
            listOf<Event>(Event.HapticClick(HapticFeedbackType.ContextClick)),
            store.events,
        )
        assertEquals(emptyList(), store.consumedActions)
    }
}

internal class FakeImageViewerHandlerStore(
    initialState: State,
) : ImageViewerHandlerStore {

    private val stateFlow = MutableStateFlow(initialState)
    val events = mutableListOf<Event>()
    val consumedActions = mutableListOf<Action>()
    var stateUpdateCount: Int = 0
        private set

    override val state: StateFlow<State> = stateFlow

    override val lastAction: Action?
        get() = error("lastAction must not be read by image-viewer handlers")

    override val logger: Logger
        get() = error("logger must not be read by image-viewer handlers")

    override fun sendEvent(event: Event) {
        events += event
    }

    override fun consume(action: Action) {
        consumedActions += action
    }

    override fun updateState(update: (State) -> State) {
        stateUpdateCount += 1
        stateFlow.value = update(stateFlow.value)
    }

    override suspend fun consumeOnMain(action: Action): Nothing =
        error("consumeOnMain must not be used by image-viewer handlers")

    override suspend fun updateStateImmediate(update: suspend (State) -> State): Nothing =
        error("updateStateImmediate(update) must not be used by image-viewer handlers")

    override suspend fun updateStateImmediate(state: State): Nothing =
        error("updateStateImmediate(state) must not be used by image-viewer handlers")

    override fun <T> launch(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        action: suspend CoroutineScope.() -> T,
    ): Job = error("launch must not be used by image-viewer handlers")

    override fun <T> launchDefault(
        onError: suspend (Throwable) -> Unit,
        onSuccess: suspend CoroutineScope.(T) -> Unit,
        action: suspend CoroutineScope.() -> T,
    ): Job = error("launchDefault must not be used by image-viewer handlers")

    override fun <T> Flow<T>.launch(
        onError: suspend (cause: Throwable) -> Unit,
        workDispatcher: CoroutineDispatcher?,
        eachDispatcher: CoroutineDispatcher?,
        each: suspend (T) -> Unit,
    ): Job = error("Flow.launch must not be used by image-viewer handlers")
}
