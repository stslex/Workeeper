// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.State.Mode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val interactor = mockk<ExerciseInteractor>(relaxed = true)

    private fun setup(initialState: State = State.create(uuid = "uuid-1")): TestSetup {
        val stateFlow = MutableStateFlow(initialState)
        val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }
        return TestSetup(
            stateFlow = stateFlow,
            store = store,
            handler = ClickHandler(
                interactor = interactor,
                mainDispatcher = Dispatchers.Unconfined,
                store = store,
            ),
        )
    }

    private data class TestSetup(
        val stateFlow: MutableStateFlow<State>,
        val store: ExerciseHandlerStore,
        val handler: ClickHandler,
    )

    @Test
    fun `OnTypeSelect with same type is no-op`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnTypeSelect(ExerciseTypeDataModel.WEIGHTED))
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnTypeSelect with new type emits SegmentTick haptic and updates state`() {
        val (stateFlow, store, handler) = setup()
        handler.invoke(Action.Click.OnTypeSelect(ExerciseTypeDataModel.WEIGHTLESS))
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.SegmentTick)
        assertEquals(ExerciseTypeDataModel.WEIGHTLESS, stateFlow.value.type)
    }

    @Test
    fun `OnSaveClick with blank name sets nameError without saving`() {
        val (stateFlow, store, handler) = setup(State.create(uuid = null).copy(name = ""))
        handler.invoke(Action.Click.OnSaveClick)
        assertTrue(stateFlow.value.nameError)
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnEditClick flips mode to Edit and snapshots current state`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                name = "Bench",
                type = ExerciseTypeDataModel.WEIGHTED,
                description = "Notes",
            ),
        )
        handler.invoke(Action.Click.OnEditClick)
        assertTrue(stateFlow.value.mode is Mode.Edit)
        assertEquals(false, (stateFlow.value.mode as Mode.Edit).isCreate)
        assertEquals("Bench", stateFlow.value.originalSnapshot?.name)
    }

    @Test
    fun `OnTagToggle adds tag when not selected`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                availableTags = persistentListOf(TagUiModel("tag-1", "Push")),
            ),
        )
        handler.invoke(Action.Click.OnTagToggle("tag-1"))
        assertEquals(listOf("tag-1"), stateFlow.value.tags.map { it.uuid })
    }

    @Test
    fun `OnTagToggle blocks adding when 10 tags already selected`() {
        val tags = (1..10).map { TagUiModel("tag-$it", "Tag$it") }
        val available = tags + TagUiModel("tag-11", "Tag11")
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                tags = persistentListOf<TagUiModel>().addAll(tags),
                availableTags = persistentListOf<TagUiModel>().addAll(available),
            ),
        )
        handler.invoke(Action.Click.OnTagToggle("tag-11"))
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertEquals(Event.ShowTagLimitReached, captured.captured)
        assertEquals(10, stateFlow.value.tags.size)
    }

    @Test
    fun `OnDismissArchiveBlocked is no-op`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnDismissArchiveBlocked)
        verify(exactly = 0) { store.sendEvent(any()) }
        verify(exactly = 0) { store.consume(any()) }
    }

    @Test
    fun `OnTrackNowClick emits Haptic and ShowTrackNowPending`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnTrackNowClick)
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it is Event.Haptic && it.type == HapticFeedbackType.ContextClick })
        assertTrue(events.any { it == Event.ShowTrackNowPending })
    }

    @Test
    fun `OnCancelClick from clean state navigates back`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(mode = Mode.Edit(isCreate = false)),
        )
        handler.invoke(Action.Click.OnCancelClick)
        verify { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnCancelClick from dirty state shows discard dialog`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench updated",
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeDataModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                ),
            ),
        )
        handler.invoke(Action.Click.OnCancelClick)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it == Event.ShowDiscardConfirmDialog })
    }

    @Test
    fun `OnConfirmDiscard navigates back`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(mode = Mode.Edit(isCreate = false)),
        )
        handler.invoke(Action.Click.OnConfirmDiscard)
        verify { store.consume(Action.Navigation.Back) }
    }

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(expected, (event as Event.Haptic).type)
    }
}
