// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutHandlerStore
import io.github.stslex.workeeper.feature.live_workout.domain.LiveWorkoutInteractor
import io.github.stslex.workeeper.feature.live_workout.domain.model.ExerciseTypeDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.LiveExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PerformedExerciseDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.PlanSetDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionSnapshotDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SessionStateDomain
import io.github.stslex.workeeper.feature.live_workout.domain.model.SetTypeDomain
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The load boundary against the REAL Compose-resource catalog: no static mock of `getString`,
 * a real Android resource environment, and the locale actually selecting the shipped value.
 * The Russian half lives in [CommonHandlerComposeResourceRuTest] — the Robolectric JUnit5
 * extension forbids method-level `@Config`.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class CommonHandlerComposeResourceTest {

    @Test
    fun `init resolves the reps unit from the real catalog into state`() {
        val stateFlow = loadWeightlessSessionThroughInit()

        assertEquals("reps", stateFlow.value.repsUnitLabel)
        assertEquals("12 reps", stateFlow.value.exercises.first().statusLabel)
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru")
internal class CommonHandlerComposeResourceRuTest {

    @Test
    fun `init resolves the russian reps unit from the real catalog into state`() {
        val stateFlow = loadWeightlessSessionThroughInit()

        assertEquals("повт", stateFlow.value.repsUnitLabel)
        assertEquals("12 повт", stateFlow.value.exercises.first().statusLabel)
    }
}

/**
 * Drives `Action.Common.Init` over a one-WEIGHTLESS-exercise session with real resources.
 * Only the first `launch` (the load) runs inline; the second is `startTimer`'s endless tick
 * loop, which under an inline-running mock would never return.
 */
private fun loadWeightlessSessionThroughInit(): MutableStateFlow<State> {
    var launches = 0
    val interactor = mockk<LiveWorkoutInteractor>(relaxed = true)
    val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    coEvery { interactor.loadSession("session-1") } returns weightlessSnapshot()
    val stateFlow = MutableStateFlow(
        State.create(sessionUuid = "session-1", trainingUuid = "training-1"),
    )
    val store = mockk<LiveWorkoutHandlerStore>(relaxed = true).apply {
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
            launches += 1
            if (launches == 1) {
                val onError = firstArg<suspend (Throwable) -> Unit>()
                val onSuccess = secondArg<suspend CoroutineScope.(Any?) -> Unit>()
                val action = arg<suspend CoroutineScope.() -> Any?>(4)
                runBlocking {
                    runCatching { supervisorScope { action() } }
                        .onSuccess { onSuccess(this, it) }
                        .onFailure { onError(it) }
                }
            }
            mockk<Job>(relaxed = true)
        }
    }
    CommonHandler(interactor, resourceWrapper, store).invoke(Action.Common.Init)
    return stateFlow
}

private fun weightlessSnapshot(): SessionSnapshotDomain = SessionSnapshotDomain(
    session = SessionDomain(
        uuid = "session-1",
        trainingUuid = "training-1",
        state = SessionStateDomain.IN_PROGRESS,
        startedAt = 1_000L,
        finishedAt = null,
    ),
    trainingName = "Push Day",
    isAdhoc = false,
    exercises = listOf(
        LiveExerciseDomain(
            performed = PerformedExerciseDomain(
                uuid = "pe-1",
                sessionUuid = "session-1",
                exerciseUuid = "exercise-1",
                position = 0,
                skipped = false,
                exerciseName = "Push ups",
            ),
            exerciseType = ExerciseTypeDomain.WEIGHTLESS,
            planSets = listOf(
                PlanSetDomain(weight = null, reps = 12, type = SetTypeDomain.WORK),
            ),
            performedSets = emptyList(),
            isPlanAttached = true,
        ),
    ),
    preSessionPrSnapshot = emptyMap(),
)
