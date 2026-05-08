// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanEditorBodyAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.DiscardTarget
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Event
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
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
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                type = ExerciseTypeUiModel.WEIGHTED,
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 50.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )
        handler.invoke(Action.Click.OnTypeSelect(ExerciseTypeUiModel.WEIGHTLESS))
        assertTrue(stateFlow.value.dialogState is DialogState.TypeChangeConfirm)
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
    fun `OnEditPlanClick navigates to PlanEditor route`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnEditPlanClick)
        verify(exactly = 1) {
            store.consume(Action.Navigation.OpenPlanEditor(exerciseUuid = "uuid-1"))
        }
    }

    @Test
    fun `OnEditPlanClick is a no-op when uuid is null`() {
        val (_, store, handler) = setup(State.create(uuid = null))
        handler.invoke(Action.Click.OnEditPlanClick)
        verify(exactly = 0) { store.consume(any<Action.Navigation.OpenPlanEditor>()) }
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
    fun `OnTrackNowClick emits ContextClick haptic`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnTrackNowClick)
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it is Event.Haptic && it.type == HapticFeedbackType.ContextClick })
    }

    @Test
    fun `OnTrackNowResumeConfirm with no pending conflict is a no-op`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnTrackNowResumeConfirm)
        verify(exactly = 0) { store.consume(any<Action.Navigation.OpenLiveWorkout>()) }
    }

    @Test
    fun `OnTrackNowResumeConfirm consumes OpenLiveWorkout with active session uuid`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                dialogState = DialogState.ActiveSessionConflict(
                    sessionUuid = "active-1",
                    activeSessionName = "Push Day",
                    progressLabel = "0 of 0",
                ),
            ),
        )
        handler.invoke(Action.Click.OnTrackNowResumeConfirm)
        verify { store.consume(Action.Navigation.OpenLiveWorkout("active-1")) }
    }

    @Test
    fun `OnTrackNowConflictDismiss clears the active session conflict dialog`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                dialogState = DialogState.ActiveSessionConflict(
                    sessionUuid = "active-1",
                    activeSessionName = "Push Day",
                    progressLabel = "0 of 0",
                ),
            ),
        )
        handler.invoke(Action.Click.OnTrackNowConflictDismiss)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
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
        val (stateFlow, store, handler) = setup(
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
        val dialog = stateFlow.value.dialogState
        assertTrue(dialog is DialogState.DiscardConfirm)
        assertEquals(DiscardTarget.FLIP_TO_READ, (dialog as DialogState.DiscardConfirm).target)
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
    fun `OnPermanentDeleteMenuClick surfaces the PermanentDeleteConfirm dialog when eligible`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                canPermanentlyDelete = true,
                name = "Bench",
            ),
        )
        handler.invoke(Action.Click.OnPermanentDeleteMenuClick)
        assertTrue(stateFlow.value.dialogState is DialogState.PermanentDeleteConfirm)
    }

    @Test
    fun `OnEditImageClick opens the image source picker dialog`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(Action.Click.OnEditImageClick)
        assertEquals(DialogState.ImageSourcePicker, stateFlow.value.dialogState)
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
            PendingImage.RemoveExisting,
            stateFlow.value.pendingImage,
        )
    }

    @Test
    fun `OnImageSourceDialogDismiss hides the source dialog`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(dialogState = DialogState.ImageSourcePicker),
        )
        handler.invoke(Action.Click.OnImageSourceDialogDismiss)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnPermissionDeniedDialogDismiss hides the permission dialog`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = "uuid-1").copy(dialogState = DialogState.PermissionDenied),
        )
        handler.invoke(Action.Click.OnPermissionDeniedDialogDismiss)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `OnCameraPermissionDenied surfaces the permission denied dialog`() {
        val (stateFlow, _, handler) = setup()
        handler.invoke(Action.Click.OnCameraPermissionDenied)
        assertEquals(DialogState.PermissionDenied, stateFlow.value.dialogState)
    }

    @Test
    fun `OnPermissionDeniedSettingsClick emits NavigateOpenAppSettings`() {
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(dialogState = DialogState.PermissionDenied),
        )
        handler.invoke(Action.Click.OnPermissionDeniedSettingsClick)
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue(events.any { it is Event.NavigateOpenAppSettings })
    }

    @Test
    fun `OnImageThumbnailClick with committed path consumes OpenImageViewer with the path`() {
        val path = "/data/user/0/app/files/exercise_images/uuid-1.jpg"
        val (_, store, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                imagePath = path,
                imageLastModified = 100L,
            ),
        )
        handler.invoke(Action.Click.OnImageThumbnailClick)
        verify { store.consume(Action.Navigation.OpenImageViewer(path)) }
    }

    @Test
    fun `OnImageThumbnailClick with no image is a no-op`() {
        val (_, store, handler) = setup()
        handler.invoke(Action.Click.OnImageThumbnailClick)
        verify(exactly = 0) { store.consume(any<Action.Navigation.OpenImageViewer>()) }
        verify(exactly = 0) { store.sendEvent(any()) }
    }

    @Test
    fun `OnAdhocPlanEditorAction OnAddSet appends a default set to the in-memory plan`() {
        val (stateFlow, _, handler) = setup(State.create(uuid = null))

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnAddSet),
        )

        val plan = stateFlow.value.adhocPlan
        assertEquals(1, plan?.size)
        assertEquals(SetTypeUiModel.WORK, plan?.first()?.type)
    }

    @Test
    fun `OnAdhocPlanEditorAction OnSetRemove on the only row normalizes the plan back to null`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(PlanEditorBodyAction.OnSetRemove(0)),
        )

        // Empty draft is normalized to null so `state.adhocPlan == null` continues to mean
        // "no default plan attached" — matches the persisted shape on `last_adhoc_sets`.
        assertNull(stateFlow.value.adhocPlan)
    }

    @Test
    fun `OnAdhocPlanEditorAction OnSetWeightChange routes through the reducer`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(
                PlanEditorBodyAction.OnSetWeightChange(index = 0, value = 95.0),
            ),
        )

        assertEquals(95.0, stateFlow.value.adhocPlan?.first()?.weight)
    }

    @Test
    fun `OnAdhocPlanEditorAction OnSetRepsChange routes through the reducer`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = null, reps = 5, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(
                PlanEditorBodyAction.OnSetRepsChange(index = 0, value = 12),
            ),
        )

        assertEquals(12, stateFlow.value.adhocPlan?.first()?.reps)
    }

    @Test
    fun `OnAdhocPlanEditorAction OnSetTypeChange routes through the reducer`() {
        val (stateFlow, _, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
            ),
        )

        handler.invoke(
            Action.Click.OnAdhocPlanEditorAction(
                PlanEditorBodyAction.OnSetTypeChange(
                    index = 0,
                    value = SetTypeUiModel.FAILURE,
                ),
            ),
        )

        assertEquals(SetTypeUiModel.FAILURE, stateFlow.value.adhocPlan?.first()?.type)
    }

    @Test
    fun `OnCancelClick from create-mode with edited plan surfaces POP_SCREEN discard dialog`() {
        val (_, store, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
                originalAdhocPlan = null,
            ),
        )

        handler.invoke(Action.Click.OnCancelClick)

        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue((store.state.value.dialogState as? DialogState.DiscardConfirm)?.target == DiscardTarget.POP_SCREEN)
    }

    @Test
    fun `OnBackClick from create-mode with edited plan surfaces POP_SCREEN discard dialog`() {
        val (_, store, handler) = setup(
            State.create(uuid = null).copy(
                adhocPlan = persistentListOf(
                    PlanSetUiModel(weight = 80.0, reps = 8, type = SetTypeUiModel.WORK),
                ),
                originalAdhocPlan = null,
            ),
        )

        handler.invoke(Action.Click.OnBackClick)

        verify(exactly = 0) { store.consume(Action.Navigation.Back) }
        val events = mutableListOf<Event>()
        verify { store.sendEvent(capture(events)) }
        assertTrue((store.state.value.dialogState as? DialogState.DiscardConfirm)?.target == DiscardTarget.POP_SCREEN)
    }

    @Test
    fun `OnEditPlanClick still navigates to full-screen route in edit-mode for existing exercise`() {
        // Read-mode "Edit default plan" card — preserved from the v2.4 D1 migration.
        val (_, store, handler) = setup(State.create(uuid = "uuid-1"))

        handler.invoke(Action.Click.OnEditPlanClick)

        verify(exactly = 1) {
            store.consume(Action.Navigation.OpenPlanEditor(exerciseUuid = "uuid-1"))
        }
    }

    private fun assertHaptic(event: Event, expected: HapticFeedbackType) {
        assertTrue(event is Event.Haptic, "expected Event.Haptic but got $event")
        assertEquals(expected, (event as Event.Haptic).type)
    }
}
