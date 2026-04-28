// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.handler

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.mvi.store.ExerciseStore.DiscardTarget
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ClickHandlerTest {

    private val interactor = mockk<ExerciseInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val context = mockk<Context>(relaxed = true).apply {
        every { packageName } returns "io.github.stslex.workeeper.test"
        every { checkPermission(any(), any(), any()) } returns PackageManager.PERMISSION_GRANTED
    }

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
                resourceWrapper = resourceWrapper,
                context = context,
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
        handler.invoke(Action.Click.OnTypeSelect(ExerciseTypeUiModel.WEIGHTED))
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnTypeSelect with new type emits SegmentTick haptic and updates state`() {
        val (stateFlow, store, handler) = setup()
        handler.invoke(Action.Click.OnTypeSelect(ExerciseTypeUiModel.WEIGHTLESS))
        val captured = slot<Event>()
        verify { store.sendEvent(capture(captured)) }
        assertHaptic(captured.captured, HapticFeedbackType.SegmentTick)
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.type)
    }

    @Test
    fun `OnTypeSelect from WEIGHTED to WEIGHTLESS with weighted plan asks for confirm`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                type = ExerciseTypeUiModel.WEIGHTED,
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )
        handler.invoke(Action.Click.OnTypeSelect(ExerciseTypeUiModel.WEIGHTLESS))
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it is Event.ShowTypeChangeConfirm })
        assertEquals(ExerciseTypeUiModel.WEIGHTED, stateFlow.value.type)
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.pendingTypeChange)
    }

    @Test
    fun `OnTypeChangeConfirm wipes weights from adhoc plan`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                type = ExerciseTypeUiModel.WEIGHTED,
                pendingTypeChange = ExerciseTypeUiModel.WEIGHTLESS,
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
                    PlanSetUiModel(weight = 60.0, reps = 6, type = SetTypeUiModel.FAILURE),
                ),
            ),
        )
        handler.invoke(Action.Click.OnTypeChangeConfirm)
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.type)
        assertEquals(null, stateFlow.value.pendingTypeChange)
        assertTrue(stateFlow.value.adhocPlan?.all { it.weight == null } == true)
    }

    @Test
    fun `OnTypeChangeDismiss clears pending type change`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(pendingTypeChange = ExerciseTypeUiModel.WEIGHTLESS),
        )
        handler.invoke(Action.Click.OnTypeChangeDismiss)
        assertNull(stateFlow.value.pendingTypeChange)
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
                type = ExerciseTypeUiModel.WEIGHTED,
                description = "Notes",
            ),
        )
        handler.invoke(Action.Click.OnEditClick)
        assertTrue(stateFlow.value.mode is Mode.Edit)
        assertEquals(false, (stateFlow.value.mode as Mode.Edit).isCreate)
        assertEquals("Bench", stateFlow.value.originalSnapshot?.name)
    }

    @Test
    fun `OnEditPlanClick stages an empty editor target when no adhoc plan`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(Action.Click.OnEditPlanClick)
        val target = stateFlow.value.planEditorTarget
        assertNotNull(target)
        assertEquals(0, target?.draft?.size)
        assertEquals(0, target?.initialPlan?.size)
    }

    @Test
    fun `OnEditPlanClick seeds the editor draft from existing adhoc plan`() {
        val seed = persistentListOf(
            PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
        )
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(adhocPlan = seed),
        )
        handler.invoke(Action.Click.OnEditPlanClick)
        assertEquals(seed, stateFlow.value.planEditorTarget?.draft)
        assertEquals(seed, stateFlow.value.planEditorTarget?.initialPlan)
    }

    @Test
    fun `OnConfirmDiscard with PLAN_EDITOR closes the editor`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                planEditorTarget = State.PlanEditorTarget(
                    initialPlan = persistentListOf(),
                    draft = persistentListOf(
                        PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
                    ),
                ),
            ),
        )
        handler.invoke(Action.Click.OnConfirmDiscard(DiscardTarget.PLAN_EDITOR))
        assertNull(stateFlow.value.planEditorTarget)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnBackClick with dirty plan editor surfaces PLAN_EDITOR discard target`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                planEditorTarget = State.PlanEditorTarget(
                    initialPlan = persistentListOf(),
                    draft = persistentListOf(
                        PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
                    ),
                ),
            ),
        )
        handler.invoke(Action.Click.OnBackClick)
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(
            events.any {
                it is Event.ShowDiscardConfirmDialog && it.target == DiscardTarget.PLAN_EDITOR
            },
        )
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
        assertTrue(captured.captured is Event.ShowTagLimitReached)
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
        assertTrue(events.any { it is Event.ShowTrackNowPending })
    }

    @Test
    fun `OnCancelClick from clean Edit on existing flips to Read mode`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(mode = Mode.Edit(isCreate = false)),
        )
        handler.invoke(Action.Click.OnCancelClick)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        assertEquals(Mode.Read, stateFlow.value.mode)
    }

    @Test
    fun `OnCancelClick from clean create mode pops back`() {
        val (_, store, handler) = setup(
            State.create(uuid = null),
        )
        handler.invoke(Action.Click.OnCancelClick)
        verify { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnCancelClick from dirty Edit on existing shows FLIP_TO_READ discard dialog`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench updated",
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                ),
            ),
        )
        handler.invoke(Action.Click.OnCancelClick)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(
            events.any {
                it is Event.ShowDiscardConfirmDialog && it.target == DiscardTarget.FLIP_TO_READ
            },
        )
    }

    @Test
    fun `OnConfirmDiscard with POP_SCREEN navigates back`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(mode = Mode.Edit(isCreate = false)),
        )
        handler.invoke(Action.Click.OnConfirmDiscard(DiscardTarget.POP_SCREEN))
        verify { store.consume(Action.Navigation.Back) }
    }

    @Test
    fun `OnConfirmDiscard with FLIP_TO_READ flips mode without popping`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench edited",
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "",
                    tagUuids = emptyList(),
                ),
            ),
        )
        handler.invoke(Action.Click.OnConfirmDiscard(DiscardTarget.FLIP_TO_READ))
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        assertEquals(Mode.Read, stateFlow.value.mode)
        assertEquals("Bench", stateFlow.value.name)
    }

    @Test
    fun `OnBackClick in clean Edit on existing flips to Read mode`() {
        val (stateFlow, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(mode = Mode.Edit(isCreate = false)),
        )
        handler.invoke(Action.Click.OnBackClick)
        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        assertEquals(Mode.Read, stateFlow.value.mode)
    }

    @Test
    fun `OnPermanentDeleteMenuClick is no-op when not eligible`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(canPermanentlyDelete = false),
        )
        handler.invoke(Action.Click.OnPermanentDeleteMenuClick)
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnPermanentDeleteMenuClick emits ShowPermanentDeleteConfirm when eligible`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                canPermanentlyDelete = true,
                name = "Bench",
            ),
        )
        handler.invoke(Action.Click.OnPermanentDeleteMenuClick)
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it is Event.ShowPermanentDeleteConfirm })
    }

    @Test
    fun `OnEditImageClick opens the source dialog`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(Action.Click.OnEditImageClick)
        assertTrue(stateFlow.value.sourceDialogVisible)
    }

    @Test
    fun `OnRemoveImageClick stages a RemoveExisting pending image`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                imagePath = "/files/old.jpg",
                imageLastModified = 100L,
            ),
        )
        handler.invoke(Action.Click.OnRemoveImageClick)
        assertEquals(
            io.github.stslex.workeeper.feature.exercise.mvi.model.PendingImage.RemoveExisting,
            stateFlow.value.pendingImage,
        )
    }

    @Test
    fun `OnImageSourceDialogDismiss hides the source dialog`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(sourceDialogVisible = true),
        )
        handler.invoke(Action.Click.OnImageSourceDialogDismiss)
        assertEquals(false, stateFlow.value.sourceDialogVisible)
    }

    @Test
    fun `OnPermissionDeniedDialogDismiss hides the permission dialog`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(permissionDeniedDialogVisible = true),
        )
        handler.invoke(Action.Click.OnPermissionDeniedDialogDismiss)
        assertEquals(false, stateFlow.value.permissionDeniedDialogVisible)
    }

    @Test
    fun `OnCameraPermissionDenied surfaces the permission denied dialog`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(Action.Click.OnCameraPermissionDenied)
        assertTrue(stateFlow.value.permissionDeniedDialogVisible)
    }

    @Test
    fun `OnPermissionDeniedSettingsClick emits NavigateOpenAppSettings`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(permissionDeniedDialogVisible = true),
        )
        handler.invoke(Action.Click.OnPermissionDeniedSettingsClick)
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it is Event.NavigateOpenAppSettings })
    }

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(expected, (event as Event.Haptic).type)
    }
}
