// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.mvi.handler

import io.github.stslex.workeeper.core.ui.kit.strings.ComposeStringResolver
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
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
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.LiveSetMutator
import io.github.stslex.workeeper.feature.live_workout.mvi.mapper.StateStatusMapper
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveWorkoutCopy
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.Action
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStore.State
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

/**
 * The load boundary against both REAL catalogs: the feature's strings through the real reader on
 * the app Context, and kit's Compose-resource catalog through the real resolver. No mock of
 * either, a real Android resource environment, and the locale actually selecting the shipped
 * values. The Russian half lives in [CommonHandlerComposeResourceRuTest] — the Robolectric JUnit5
 * extension forbids method-level `@Config`.
 */
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33])
internal class CommonHandlerComposeResourceTest {

    @Test
    fun `init resolves the reps unit from the real catalog into state`() {
        val stateFlow = loadWeightlessSessionThroughInit()

        assertEquals("reps", stateFlow.value.liveCopy.repsUnit)
        assertEquals("12 reps", stateFlow.value.exercises.first().statusLabel)
    }

    @Test
    fun `init resolves the whole copy verbatim from the real catalog`() {
        val stateFlow = loadWeightlessSessionThroughInit()

        assertEquals(ENGLISH_COPY, stateFlow.value.liveCopy)
    }
}

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [33], qualifiers = "ru")
internal class CommonHandlerComposeResourceRuTest {

    @Test
    fun `init resolves the russian reps unit from the real catalog into state`() {
        val stateFlow = loadWeightlessSessionThroughInit()

        assertEquals("повт", stateFlow.value.liveCopy.repsUnit)
        assertEquals("12 повт", stateFlow.value.exercises.first().statusLabel)
    }

    @Test
    fun `init resolves the whole russian copy verbatim from the real catalog`() {
        val stateFlow = loadWeightlessSessionThroughInit()

        assertEquals(RUSSIAN_COPY, stateFlow.value.liveCopy)
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
    coEvery { interactor.loadSession("session-1") } returns weightlessSnapshot()
    every { interactor.observeExternalWrites(any()) } returns emptyFlow()
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
    CommonHandler(
        interactor = interactor,
        copyReader = LiveWorkoutCopyReader(RuntimeEnvironment.getApplication()),
        stringResolver = ComposeStringResolver(),
        setMutator = LiveSetMutator(StateStatusMapper()),
        storeDispatchers = StoreDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined),
        store = store,
    ).invoke(Action.Common.Init)
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

/** Section B2.1's EN column, field by field: the catalog text as Android returns it. */
private val ENGLISH_COPY = LiveWorkoutCopy(
    trainingNamePlaceholder = "Untitled",
    statusSkipped = "skipped",
    statusNoPlan = "no plan",
    repsUnit = "reps",
    metaTemplate = "%1\$d of %2\$d exercises · %3\$d of %4\$d sets",
    metaSkippedTemplate = "%1\$s · %2\$d skipped",
    resetTitle = "Reset sets?",
    resetBody = "All logged sets for this exercise will be deleted. This cannot be undone.",
    resetConfirm = "Reset",
    resetDismiss = "Cancel",
    emptyFinishDiscard = "Discard",
    emptyFinishContinue = "Continue edit",
    cancelTitle = "Cancel session?",
    cancelBody = "All progress will be deleted. This cannot be undone.",
    cancelConfirm = "Cancel",
    cancelDismiss = "Keep going",
    deleteSessionUnnamed = "Workout",
    deleteSessionProgressTemplate = "%1\$d of %2\$d exercises done · %3\$d sets logged",
    finishNameLabel = "Workout name",
    finishNameRequired = "Name is required",
    finishSetsCountTemplate = "%1\$d",
    finishExercisesTemplate = "%1\$d of %2\$d done",
    finishExercisesWithSkippedTemplate = "%1\$d of %2\$d done · %3\$d skipped",
    finishPrWeightedTemplate = "%1\$s × %2\$d",
    finishPrWeightlessTemplate = "%1\$d reps",
    toastSetAdded = "Set added",
    toastSetRemoved = "Set removed",
    toastExerciseRemovedTemplate = "“%1\$s” removed from plan",
    toastExerciseRemovedFromWorkoutTemplate = "“%1\$s” removed from workout",
    errorInvalidReps = "Reps must be greater than 0",
    errorFinishFailed = "Could not finish session",
)

/** Section B2.1's RU column, field by field. */
private val RUSSIAN_COPY = LiveWorkoutCopy(
    trainingNamePlaceholder = "Без названия",
    statusSkipped = "пропущено",
    statusNoPlan = "плана нет",
    repsUnit = "повт",
    metaTemplate = "%1\$d из %2\$d упражнений · %3\$d из %4\$d подходов",
    metaSkippedTemplate = "%1\$s · пропущено %2\$d",
    resetTitle = "Сбросить подходы?",
    resetBody = "Все записанные подходы этого упражнения будут удалены. Это нельзя отменить.",
    resetConfirm = "Сбросить",
    resetDismiss = "Отмена",
    emptyFinishDiscard = "Удалить",
    emptyFinishContinue = "Продолжить",
    cancelTitle = "Отменить сессию?",
    cancelBody = "Весь прогресс будет удалён. Это нельзя отменить.",
    cancelConfirm = "Отменить",
    cancelDismiss = "Продолжить",
    deleteSessionUnnamed = "Тренировка",
    deleteSessionProgressTemplate = "%1\$d из %2\$d упражнений · %3\$d подходов записано",
    finishNameLabel = "Название тренировки",
    finishNameRequired = "Введите название",
    finishSetsCountTemplate = "%1\$d",
    finishExercisesTemplate = "%1\$d из %2\$d",
    finishExercisesWithSkippedTemplate = "%1\$d из %2\$d · %3\$d пропущено",
    finishPrWeightedTemplate = "%1\$s × %2\$d",
    finishPrWeightlessTemplate = "%1\$d повт",
    toastSetAdded = "Подход добавлен",
    toastSetRemoved = "Подход удалён",
    toastExerciseRemovedTemplate = "«%1\$s» удалено из плана",
    toastExerciseRemovedFromWorkoutTemplate = "«%1\$s» удалено из тренировки",
    errorInvalidReps = "Повторения должны быть больше 0",
    errorFinishFailed = "Не удалось завершить сессию",
)
