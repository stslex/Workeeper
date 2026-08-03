// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.plan_editor.mvi.handler

import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorHandlerStore
import io.github.stslex.workeeper.feature.plan_editor.domain.PlanEditorInteractor
import io.github.stslex.workeeper.feature.plan_editor.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanEditorLoadResult
import io.github.stslex.workeeper.feature.plan_editor.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.plan_editor.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.handler.CommonHandler
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Action
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.ErrorType
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.Event
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * `isLoading`'s lifecycle, and the reason it is worth a test file of its own.
 *
 * `PlanEditorGraph` withholds the whole screen while the flag is true (§26, "A route does not
 * compose until it has loaded"). That consumer is what makes these assertions claims about the app
 * rather than about a field (§27: "a gate whose subject is unconsumed is vacuous in precisely the
 * way a green one is") — and what makes the failure branch below the rule's precondition rather
 * than a tidiness question.
 *
 * The consumer, named per §27's discriminator: `PlanEditorGraph.PlanEditorContent`'s
 * `if (state.isLoading) return`, which is what decides whether `PlanEditorScreen` is composed at
 * all. Not a test — the branch that renders.
 */
internal class CommonHandlerTest {

    private val interactor = mockk<PlanEditorInteractor>(relaxed = true)

    /**
     * The store mock runs `launchDefault` **synchronously** and routes a throw to `onError`,
     * because that routing is the thing under test: the production default for `onError` is `{}`
     * (B17, B21), so a mock that swallowed the throw would report the defect as fixed.
     */
    private fun setup(initial: State): Triple<MutableStateFlow<State>, PlanEditorHandlerStore, CommonHandler> {
        val stateFlow = MutableStateFlow(initial)
        val store = mockk<PlanEditorHandlerStore>(relaxed = true).apply {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
            every { launchDefault<Any?>(any(), any(), any()) } answers {
                val onError = firstArg<suspend (Throwable) -> Unit>()
                val onSuccess = secondArg<suspend CoroutineScope.(Any?) -> Unit>()
                val action = thirdArg<suspend CoroutineScope.() -> Any?>()
                runBlocking {
                    runCatching { action(this) }
                        .onSuccess { onSuccess(this, it) }
                        .onFailure { onError(it) }
                }
                mockk<Job>(relaxed = true)
            }
        }
        return Triple(stateFlow, store, CommonHandler(interactor, store))
    }

    private fun existingInitial(): State = State.init(
        mode = State.Mode.Exercise(exerciseUuid = "exercise-1"),
        seedType = ExerciseTypeUiModel.WEIGHTED,
        seedPlan = persistentListOf(),
    )

    @Test
    fun `a load that throws clears isLoading, or the route is composed on nothing forever`() {
        coEvery { interactor.loadPlan(any(), any()) } throws IllegalStateException("db down")
        val (stateFlow, store, handler) = setup(existingInitial())
        // Precondition rather than assertion: an Existing route starts loading by construction.
        assertTrue(stateFlow.value.isLoading)

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
        verify(exactly = 1) { store.sendEvent(Event.ShowError(ErrorType.LoadFailed)) }
    }

    @Test
    fun `NotFound clears isLoading and reports, same reason`() {
        coEvery { interactor.loadPlan(any(), any()) } returns PlanEditorLoadResult.NotFound
        val (stateFlow, store, handler) = setup(existingInitial())

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
        verify(exactly = 1) { store.sendEvent(Event.ShowError(ErrorType.LoadFailed)) }
    }

    @Test
    fun `a successful load clears isLoading and hydrates the type the seed guessed wrong`() {
        // The seed is WEIGHTED for every Existing route (the real value is on disk), so this is
        // the case the route gate exists for: without it the toggle draws WEIGHTED and flips here,
        // in front of the user. `PlanEditorGraph` withholds the screen until `isLoading` clears.
        coEvery { interactor.loadPlan(any(), any()) } returns PlanEditorLoadResult.Success(
            exerciseName = "Подтягивания",
            type = ExerciseTypeDomain.WEIGHTLESS,
            plan = listOf(PlanSetDomain(weight = null, reps = 8, type = SetTypeDomain.WORK)),
        )
        val (stateFlow, _, handler) = setup(existingInitial())
        assertEquals(ExerciseTypeUiModel.WEIGHTED, stateFlow.value.type)

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.type)
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.initialType)
        assertEquals(1, stateFlow.value.draft.size)
        // initialDraft moves with draft, so the screen does not open dirty.
        assertEquals(stateFlow.value.draft, stateFlow.value.initialDraft)
    }

    @Test
    fun `Draft mode never loads, so it is never withheld`() {
        val (stateFlow, _, handler) = setup(
            State.init(
                mode = State.Mode.Draft,
                seedType = ExerciseTypeUiModel.WEIGHTLESS,
                seedPlan = persistentListOf(),
            ),
        )

        handler.invoke(Action.Common.Init)

        assertFalse(stateFlow.value.isLoading)
        assertEquals(ExerciseTypeUiModel.WEIGHTLESS, stateFlow.value.type)
    }
}
