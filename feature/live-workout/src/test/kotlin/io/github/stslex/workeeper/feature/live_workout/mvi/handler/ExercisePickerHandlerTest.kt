// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.logger.Logger
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.model.ExerciseTypeDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerAction
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExercisePickerUiModel
import io.github.stslex.workeeper.core.ui.plan_editor.model.ExerciseTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State.ExercisePickerSheetState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ExercisePickerHandlerTest {

    private val interactor = mockk<LiveWorkoutInteractor>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)

    @Test
    fun `OnDismiss flips picker to Hidden`() {
        val state = MutableStateFlow(stateWithVisiblePicker())

        handler(state).invoke(ExercisePickerAction.OnDismiss)

        assertEquals(ExercisePickerSheetState.Hidden, state.value.exercisePickerSheet)
    }

    @Test
    fun `OnQueryChange surfaces query immediately and dispatches a load coroutine`() {
        val state = MutableStateFlow(stateWithVisiblePicker())
        val store = handlerStore(state)

        ExercisePickerHandler(interactor, resourceWrapper, store)
            .invoke(ExercisePickerAction.OnQueryChange("bench"))

        val visible = state.value.exercisePickerSheet as ExercisePickerSheetState.Visible
        assertEquals("bench", visible.query)
        verify {
            store.launch(
                any(),
                any(),
                any(),
                any(),
                any<suspend CoroutineScope.() -> Any?>(),
            )
        }
    }

    @Test
    fun `OnExerciseSelect is no-op when add is already in flight`() {
        val state = MutableStateFlow(
            stateWithVisiblePicker().copy(isAddExerciseInFlight = true),
        )
        val store = handlerStore(state)

        ExercisePickerHandler(interactor, resourceWrapper, store)
            .invoke(ExercisePickerAction.OnExerciseSelect("u1"))

        coVerify(exactly = 0) {
            interactor.addExerciseToActiveSession(any(), any(), any())
        }
    }

    @Test
    fun `OnExerciseSelect with finish-in-flight is also blocked`() {
        val state = MutableStateFlow(
            stateWithVisiblePicker().copy(isFinishInFlight = true),
        )
        val store = handlerStore(state)

        ExercisePickerHandler(interactor, resourceWrapper, store)
            .invoke(ExercisePickerAction.OnExerciseSelect("u1"))

        coVerify(exactly = 0) {
            interactor.addExerciseToActiveSession(any(), any(), any())
        }
    }

    @Test
    fun `OnCreateNewExercise with blank name is no-op`() {
        val state = MutableStateFlow(stateWithVisiblePicker())
        val store = handlerStore(state)

        ExercisePickerHandler(interactor, resourceWrapper, store)
            .invoke(ExercisePickerAction.OnCreateNewExercise("   "))

        coVerify(exactly = 0) {
            interactor.createInlineAdhocExercise(any())
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun onCreateNewExercise_freshAdhoc_skipsPrBaseline() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        coEvery {
            interactor.createInlineAdhocExercise("Skull Crushers")
        } returns LiveWorkoutInteractor.InlineAdhocResult(
            exerciseUuid = "ex-inline-1",
            name = "Skull Crushers",
            type = ExerciseTypeDataModel.WEIGHTED,
            reusedExisting = false,
        )
        coEvery {
            interactor.addExerciseToActiveSession("session-1", "training-1", "ex-inline-1")
        } returns LiveWorkoutInteractor.AddExerciseResult(
            performedExerciseUuid = "pe-inline-1",
            planSets = null,
        )

        val handler = ExercisePickerHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )

        handler.invoke(ExercisePickerAction.OnCreateNewExercise("Skull Crushers"))
        advanceUntilIdle()

        assertEquals(
            listOf("ex-inline-1"),
            state.value.exercises.map { it.exerciseUuid },
        )
        // reusedExisting = false → no history → PR baseline fetch is skipped.
        coVerify(exactly = 0) {
            interactor.fetchPrSnapshotForExercise(any(), any())
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun onCreateNewExercise_collisionWithLibrary_fetchesPrBaseline() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        coEvery {
            interactor.createInlineAdhocExercise("Bench Press")
        } returns LiveWorkoutInteractor.InlineAdhocResult(
            exerciseUuid = "ex-library-1",
            name = "Bench Press",
            type = ExerciseTypeDataModel.WEIGHTED,
            reusedExisting = true,
        )
        coEvery {
            interactor.addExerciseToActiveSession("session-1", "training-1", "ex-library-1")
        } returns LiveWorkoutInteractor.AddExerciseResult(
            performedExerciseUuid = "pe-library-1",
            planSets = null,
        )

        val handler = ExercisePickerHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )

        handler.invoke(ExercisePickerAction.OnCreateNewExercise("Bench Press"))
        advanceUntilIdle()

        // reusedExisting = true → existing row may have history → PR baseline is fetched.
        coVerify(exactly = 1) {
            interactor.fetchPrSnapshotForExercise(
                exerciseUuid = "ex-library-1",
                type = ExerciseTypeDataModel.WEIGHTED,
            )
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun loadResults_partialMatch_showsCreateCta() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        coEvery {
            interactor.searchExercisesForPicker(query = "жим груди", excludedUuids = emptySet())
        } returns listOf(
            LiveWorkoutInteractor.ExercisePickerEntry(
                uuid = "u-incline",
                name = "жим груди под наклоном",
                type = ExerciseTypeDataModel.WEIGHTED,
            ),
        )
        every {
            resourceWrapper.getString(
                io.github.stslex.workeeper.feature.live_workout.R.string
                    .feature_live_workout_picker_create_format,
                "жим груди",
            )
        } returns "Create \"жим груди\""

        val handler = ExercisePickerHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )
        handler.invoke(ExercisePickerAction.OnQueryChange("жим груди"))
        advanceUntilIdle()

        val visible = state.value.exercisePickerSheet as ExercisePickerSheetState.Visible
        assertEquals("Create \"жим груди\"", visible.createCtaLabel)
        // Partial-match case: noMatchHeadline must stay null because results is non-empty.
        assertEquals(null, visible.noMatchHeadline)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun loadResults_exactMatchCaseInsensitive_hidesCreateCta() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        coEvery {
            interactor.searchExercisesForPicker(query = "BENCH PRESS", excludedUuids = emptySet())
        } returns listOf(
            LiveWorkoutInteractor.ExercisePickerEntry(
                uuid = "u-bench",
                name = "Bench Press",
                type = ExerciseTypeDataModel.WEIGHTED,
            ),
        )

        val handler = ExercisePickerHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )
        handler.invoke(ExercisePickerAction.OnQueryChange("BENCH PRESS"))
        advanceUntilIdle()

        val visible = state.value.exercisePickerSheet as ExercisePickerSheetState.Visible
        // Exact case-insensitive match → CTA suppressed (DB-level dedupe would kick in).
        assertEquals(null, visible.createCtaLabel)
        assertEquals(null, visible.noMatchHeadline)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun loadResults_emptyResults_showsHeadlineAndCta() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        coEvery {
            interactor.searchExercisesForPicker(query = "skull crushers", excludedUuids = emptySet())
        } returns emptyList()
        every {
            resourceWrapper.getString(
                io.github.stslex.workeeper.feature.live_workout.R.string
                    .feature_live_workout_picker_no_match_format,
                "skull crushers",
            )
        } returns "No exercises match \"skull crushers\""
        every {
            resourceWrapper.getString(
                io.github.stslex.workeeper.feature.live_workout.R.string
                    .feature_live_workout_picker_create_format,
                "skull crushers",
            )
        } returns "Create \"skull crushers\""

        val handler = ExercisePickerHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )
        handler.invoke(ExercisePickerAction.OnQueryChange("skull crushers"))
        advanceUntilIdle()

        val visible = state.value.exercisePickerSheet as ExercisePickerSheetState.Visible
        // Genuinely empty list — both indicators surface together (Q3: explicit no-match).
        assertEquals("No exercises match \"skull crushers\"", visible.noMatchHeadline)
        assertEquals("Create \"skull crushers\"", visible.createCtaLabel)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun loadResults_blankQuery_hidesBothLabels() = runTest {
        val state = MutableStateFlow(stateWithVisiblePicker())
        coEvery {
            interactor.searchExercisesForPicker(query = "   ", excludedUuids = emptySet())
        } returns emptyList()

        val handler = ExercisePickerHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            store = ExecutingLiveWorkoutHandlerStore(state, this),
        )
        handler.invoke(ExercisePickerAction.OnQueryChange("   "))
        advanceUntilIdle()

        val visible = state.value.exercisePickerSheet as ExercisePickerSheetState.Visible
        assertEquals(null, visible.noMatchHeadline)
        assertEquals(null, visible.createCtaLabel)
    }

    @Test
    fun `OnExerciseSelect for an unknown uuid does nothing`() {
        val state = MutableStateFlow(
            stateWithVisiblePicker().copy(
                exercisePickerSheet = ExercisePickerSheetState.Visible(
                    query = "",
                    results = persistentListOf(
                        ExercisePickerUiModel("u1", "Bench Press", ExerciseTypeUiModel.WEIGHTED),
                    ),
                    noMatchHeadline = null,
                    createCtaLabel = null,
                ),
            ),
        )
        val store = handlerStore(state)

        ExercisePickerHandler(interactor, resourceWrapper, store)
            .invoke(ExercisePickerAction.OnExerciseSelect("unknown-uuid"))

        coVerify(exactly = 0) {
            interactor.addExerciseToActiveSession(any(), any(), any())
        }
    }

    private fun handler(stateFlow: MutableStateFlow<State>): ExercisePickerHandler =
        ExercisePickerHandler(
            interactor = interactor,
            resourceWrapper = resourceWrapper,
            store = handlerStore(stateFlow),
        )

    private fun handlerStore(stateFlow: MutableStateFlow<State>): LiveWorkoutHandlerStore =
        mockk(relaxed = true) {
            every { state } returns stateFlow
            every { updateState(any()) } answers {
                val update = firstArg<(State) -> State>()
                stateFlow.value = update(stateFlow.value)
            }
        }

    private class ExecutingLiveWorkoutHandlerStore(
        private val stateFlow: MutableStateFlow<State>,
        private val scope: CoroutineScope,
    ) : LiveWorkoutHandlerStore {

        override val state: StateFlow<State> = stateFlow
        override val lastAction: io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action? =
            null
        override val logger: Logger = mockk(relaxed = true)

        override fun sendEvent(
            event: io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Event,
        ) = Unit

        override fun consume(
            action: io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action,
        ) = Unit

        override suspend fun consumeOnMain(
            action: io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action,
        ) = Unit

        override fun updateState(update: (State) -> State) {
            stateFlow.value = update(stateFlow.value)
        }

        override suspend fun updateStateImmediate(update: suspend (State) -> State) {
            stateFlow.value = update(stateFlow.value)
        }

        override suspend fun updateStateImmediate(state: State) {
            stateFlow.value = state
        }

        override fun <T> launch(
            onError: suspend (Throwable) -> Unit,
            onSuccess: suspend CoroutineScope.(T) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            action: suspend CoroutineScope.() -> T,
        ): Job = scope.launch {
            try {
                val result = action()
                onSuccess(result)
            } catch (throwable: Throwable) {
                onError(throwable)
            }
        }

        override fun <T> Flow<T>.launch(
            onError: suspend (cause: Throwable) -> Unit,
            workDispatcher: CoroutineDispatcher?,
            eachDispatcher: CoroutineDispatcher?,
            each: suspend (T) -> Unit,
        ): Job = scope.launch {
            try {
                collect { each(it) }
            } catch (throwable: Throwable) {
                onError(throwable)
            }
        }
    }

    private fun stateWithVisiblePicker(): State = State.create(
        sessionUuid = "session-1",
        trainingUuid = "training-1",
    ).copy(
        isLoading = false,
        exercisePickerSheet = ExercisePickerSheetState.Visible(
            query = "",
            results = persistentListOf(),
            noMatchHeadline = null,
            createCtaLabel = null,
        ),
    )
}
