// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.mvi.handler

import android.net.Uri
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.exercise.di.ExerciseHandlerStore
import io.github.stslex.workeeper.feature.exercise.domain.ExerciseInteractor
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseDomain
import io.github.stslex.workeeper.feature.exercise.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ImageDisplay
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.PendingImage
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.State.Mode
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
     * `ExerciseGraph` withholds the whole screen while `isLoading` is true (§26, "A route does not
     * compose until it has loaded"), and that gate is the consumer these two cases exist for —
     * without a reader an assertion about the flag is vacuous. Named per §27's discriminator: the
     * `if (state.isLoading) return@navComponentScreenWithResults` that decides whether
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
    fun `a re-fired Init preserves a dirty draft and its staged image removal`() {
        coEvery { interactor.getExercise("uuid-1") } returns ExerciseDomain(
            uuid = "uuid-1",
            name = "Bench persisted",
            type = ExerciseTypeDomain.WEIGHTED,
            description = "Refreshed notes",
            imagePath = "/files/bench.jpg",
            archived = false,
            archivedAt = null,
            timestamp = 0L,
            lastAdhocSets = null,
        )
        val (stateFlow, handler) = setup(
            State.create(uuid = "uuid-1").copy(
                mode = Mode.Edit(isCreate = false),
                name = "Bench draft",
                description = "Unsaved notes",
                imagePath = "/files/bench.jpg",
                pendingImage = PendingImage.RemoveExisting,
                originalSnapshot = State.Snapshot(
                    name = "Bench",
                    type = ExerciseTypeUiModel.WEIGHTED,
                    description = "Persisted notes",
                    tagUuids = emptyList(),
                    adhocPlan = null,
                ),
            ),
        )

        handler.invoke(Action.Common.Init)

        assertEquals("Bench draft", stateFlow.value.name)
        assertEquals("Unsaved notes", stateFlow.value.description)
        assertEquals(PendingImage.RemoveExisting, stateFlow.value.pendingImage)
        assertEquals(ImageDisplay.None, stateFlow.value.effectiveImageDisplay)
        assertEquals("Bench persisted", stateFlow.value.originalSnapshot?.name)
        assertEquals("Refreshed notes", stateFlow.value.originalSnapshot?.description)
        assertTrue(stateFlow.value.hasChanges)
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

    /**
     * The image viewer's return path, which moved out of `ExerciseGraph` and into this
     * handler. The graph now forwards the raw request name and nothing else; resolving it
     * and choosing the action happens here.
     *
     * These assert the **observable effect** — the action the handler dispatches — and never
     * the absence of a throw. `AppCoroutineScopeImpl.launch(flow, …)` applies
     * `.catch { onError(it) }`, so a broken result path inside a Store surfaces as a screen
     * quietly holding default state; a test written around "it did not throw" would pass
     * against a handler that did nothing at all.
     */
    private fun requestSetup(): Pair<ExerciseHandlerStore, CommonHandler> {
        val store = mockk<ExerciseHandlerStore>(relaxed = true).apply {
            every { state } returns MutableStateFlow(State.create(uuid = "uuid-1"))
        }
        return store to CommonHandler(interactor, resourceWrapper, store)
    }

    @Test
    fun `a REPLACE request reopens the image source picker`() {
        val (store, handler) = requestSetup()

        handler.invoke(
            Action.Common.ImageRequestReceived(Screen.ExerciseImageRequest.REPLACE.name),
        )

        verify(exactly = 1) { store.consume(Action.Click.OnEditImageClick) }
    }

    @Test
    fun `a REMOVE request stages the removal`() {
        val (store, handler) = requestSetup()

        handler.invoke(
            Action.Common.ImageRequestReceived(Screen.ExerciseImageRequest.REMOVE.name),
        )

        verify(exactly = 1) { store.consume(Action.Click.OnRemoveImageClick) }
    }

    @Test
    fun `an unrecognised request name is dropped rather than acted on`() {
        val (store, handler) = requestSetup()

        handler.invoke(Action.Common.ImageRequestReceived("PHOTOSHOP"))

        verify(exactly = 0) { store.consume(any()) }
    }

    /**
     * The reason the viewer hands back an enum name rather than a free string: every verb
     * the enum declares must resolve to an action here. A third verb added on the viewer's
     * side alone fails this rather than silently doing nothing at runtime.
     */
    @Test
    fun `every declared request verb resolves to an action`() {
        Screen.ExerciseImageRequest.entries.forEach { request ->
            val (store, handler) = requestSetup()

            handler.invoke(Action.Common.ImageRequestReceived(request.name))

            verify(exactly = 1) { store.consume(any()) }
        }
    }
}
