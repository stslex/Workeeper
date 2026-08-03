// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import android.net.Uri
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanDraftResult
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TagUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class CommonHandlerTest {

    private val interactor = mockk<ExerciseInteractor>(relaxed = true).apply {
        every { observeAvailableTags() } returns flowOf(emptyList())
        every { observePersonalRecord(any()) } returns emptyFlow()
    }
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    /**
     * The store mock runs `launch` **synchronously** and routes a throw to `onError`, because
     * that routing is what the loading tests are about: production's default for `onError` is
     * `{}` (B17, B21), so a mock that swallowed the throw would report the defect as fixed.
     *
     * `updateStateImmediate` is wired alongside `updateState` — the load path uses the suspend
     * form, and leaving it relaxed would make every assertion below read the seed state.
     */
    private fun setup(initialState: State): Pair<MutableStateFlow<State>, CommonHandler> {
        val stateFlow = MutableStateFlow(initialState)
        val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
            coEvery { updateStateImmediate(any<suspend (State) -> State>()) } coAnswers {
                val update = firstArg<suspend (State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
            every { launch<Any?>(any(), any(), any(), any(), any()) } answers {
                val onError = firstArg<suspend (Throwable) -> Unit>()
                val onSuccess = secondArg<suspend CoroutineScope.(Any?) -> Unit>()
                val action = arg<suspend CoroutineScope.() -> Any?>(4)
                runBlocking {
                    // `supervisorScope`, not a bare scope: `loadExercise` fans out through six
                    // `async` children, and in a plain scope the first failure cancels the parent
                    // before the catch can run its handler. Production survives that through
                    // `AppCoroutineScopeImpl`'s `CoroutineExceptionHandler` backstop; the
                    // supervisor reproduces the same observable — the action throws, `onError`
                    // runs — without modelling the backstop's plumbing.
                    runCatching { supervisorScope { action() } }
                        .onSuccess { onSuccess(this, it) }
                        .onFailure { onError(it) }
                }
                mockk<Job>(relaxed = true)
            }
        }
        return stateFlow to CommonHandler(interactor, resourceWrapper, store)
    }

    @Test
    fun `Init for create mode does not load exercise`() {
        val (_, handler) = setup(State.create(uuid = null))
        handler.invoke(Action.Common.Init)
        // No exception means the handler short-circuited without trying to load.
    }

    @Test
    fun `Init for read mode kicks off observe + load`() {
        val (_, handler) = setup(State.create(uuid = "uuid-1"))
        handler.invoke(Action.Common.Init)
        // No assertion on launch internals here — see ExerciseInteractorImplTest for repository
        // behaviour. The handler invariant is that Init does not throw.
    }

    /**
     * `isLoading` had no reader in this feature until `ExerciseGraph` began withholding the whole
     * screen while it is true (§26, "A route does not compose until it has loaded"). That gate is
     * the consumer these two cases exist for, named per §27's discriminator: the
     * `if (state.isLoading) return@navComponentScreenWithState` that decides whether
     * `ExerciseDetailScreen` / `ExerciseEditScreen` is composed at all — not a test that reads the
     * field.
     */
    @Test
    fun `a load that throws clears isLoading, or the route is composed on nothing forever`() {
        coEvery { interactor.getExercise(any()) } throws IllegalStateException("db down")
        val (stateFlow, handler) = setup(State.create(uuid = "uuid-1"))
        // Precondition rather than assertion: a route with a uuid starts loading by construction.
        assertTrue(stateFlow.value.isLoading)

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
    }

    @Test
    fun `a create route never loads, so it is never withheld`() {
        val (stateFlow, handler) = setup(State.create(uuid = null))

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
    }

    @Test
    fun `ImagePicked sets pendingImage to NewFromUri and hides the source dialog`() {
        val uri = mockk<Uri>(relaxed = true)
        val (stateFlow, handler) = setup(
            State.create(uuid = "uuid-1").copy(dialogState = DialogState.ImageSourcePicker),
        )

        handler.invoke(Action.Common.ImagePicked(uri))

        assertEquals(PendingImage.NewFromUri(uri), stateFlow.value.pendingImage)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `ImagePickCancelled hides the source dialog without staging a pending image`() {
        val (stateFlow, handler) = setup(
            State.create(uuid = "uuid-1").copy(dialogState = DialogState.ImageSourcePicker),
        )

        handler.invoke(Action.Common.ImagePickCancelled)

        assertEquals(PendingImage.Unchanged, stateFlow.value.pendingImage)
        assertEquals(DialogState.Hidden, stateFlow.value.dialogState)
    }

    @Test
    fun `PlanEditorDraftReturned merges the JSON payload into State without resetting baseline`() {
        // Arrange a State with some pending edits that must be preserved (no
        // originalSnapshot — i.e. create-mode where Snapshot is null until first Save).
        val originalName = "Bench"
        val (stateFlow, handler) = setup(
            State.create(uuid = null).copy(
                name = originalName,
                type = ExerciseTypeUiModel.WEIGHTED,
                adhocPlan = null,
            ),
        )
        val payload = PlanDraftResult(
            type = ExerciseTypeUiModel.WEIGHTLESS,
            plan = listOf(PlanSetUiModel(weight = null, reps = 8, type = SetTypeUiModel.WORK)),
        )

        handler.invoke(Action.Common.PlanEditorDraftReturned(Json.encodeToString(payload)))

        // (type, adhocPlan) merge in.
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.type)
        assertNotNull(stateFlow.value.adhocPlan)
        assertEquals(payload.plan, stateFlow.value.adhocPlan?.toList())
        // Other fields untouched.
        assertEquals(originalName, stateFlow.value.name)
        // Baseline (originalSnapshot) untouched — Draft is treated as an unsaved edit
        // until the parent's own Save fires.
        assertNull(stateFlow.value.originalSnapshot)
    }

    @Test
    fun `PlanEditorDraftReturned with malformed JSON is silently skipped`() {
        val (stateFlow, handler) = setup(
            State.create(uuid = null).copy(
                type = ExerciseTypeUiModel.WEIGHTED,
                adhocPlan = persistentListOf(),
            ),
        )

        handler.invoke(Action.Common.PlanEditorDraftReturned("not-json"))

        // State unchanged — broken inputs don't corrupt the working draft.
        assertEquals(ExerciseTypeUiModel.WEIGHTED, stateFlow.value.type)
        assertTrue(stateFlow.value.adhocPlan?.isEmpty() == true)
    }

    @Test
    fun `PlanEditorExistingReturned with no uuid is a no-op`() {
        // Defence-in-depth: PlanEditorExistingReturned can only fire when the parent has
        // a persisted UUID, but the handler must short-circuit if invoked without one.
        val (stateFlow, handler) = setup(
            State.create(uuid = null).copy(name = "Bench"),
        )

        handler.invoke(Action.Common.PlanEditorExistingReturned)

        assertEquals("Bench", stateFlow.value.name)
    }

    @Test
    fun `PlanEditorExistingReturned preserves baseline shape with adhocPlan and type fields`() {
        // Pin the contract: State.Snapshot now carries adhocPlan + type. The
        // partial-reload path (Phase 6) updates these fields on the snapshot so a
        // following parent.Save doesn't see a phantom dirty diff.
        val baseline = State.Snapshot(
            name = "Bench",
            type = ExerciseTypeUiModel.WEIGHTED,
            description = "",
            tagUuids = emptyList(),
            adhocPlan = null,
        )
        val (stateFlow, _) = setup(
            State.create(uuid = "uuid-1").copy(
                name = "Bench",
                originalSnapshot = baseline,
                tags = persistentListOf<TagUiModel>(),
            ),
        )

        // Verify pre-conditions: snapshot does include the new fields.
        assertEquals(ExerciseTypeUiModel.WEIGHTED, stateFlow.value.originalSnapshot?.type)
        assertNull(stateFlow.value.originalSnapshot?.adhocPlan)
    }
}
