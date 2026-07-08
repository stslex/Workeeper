// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.di

import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.PerformedExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.session.SetRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * KMP C.1 wave 3 — in-situ proof on live-workout's REAL [LiveWorkoutGraph] (the largest feature, 13
 * bound instances + 6 handlers + 2 feature-scoped mappers). ASSISTED shape: the graph exposes the
 * assisted Factory (never the Store); resolving it proves all 13 bridged singletons wired and the
 * feature-scoped @SingleIn nodes (ExercisePickerHandler / LiveSetMutator / StateStatusMapper) resolve.
 */
internal class LiveWorkoutGraphBridgeTest {

    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val performedExerciseRepository = mockk<PerformedExerciseRepository>(relaxed = true)
    private val personalRecordRepository = mockk<PersonalRecordRepository>(relaxed = true)
    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val setRepository = mockk<SetRepository>(relaxed = true)
    private val trainingExerciseRepository = mockk<TrainingExerciseRepository>(relaxed = true)
    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val navigator = mockk<Navigator>(relaxed = true)
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Unconfined,
        mainImmediateDispatcher = Dispatchers.Unconfined,
    )
    private val analyticsHolder = AnalyticsHolder()
    private val loggerHolder = LoggerHolder()
    private val defaultDispatcher = Dispatchers.Default

    @Test
    fun `assisted factory is exposed and resolvable from the real graph`() {
        val graph = createGraphFactory<LiveWorkoutGraph.Factory>()
            .create(
                exerciseRepository = exerciseRepository,
                performedExerciseRepository = performedExerciseRepository,
                personalRecordRepository = personalRecordRepository,
                sessionRepository = sessionRepository,
                setRepository = setRepository,
                trainingExerciseRepository = trainingExerciseRepository,
                trainingRepository = trainingRepository,
                resourceWrapper = resourceWrapper,
                navigator = navigator,
                storeDispatchers = storeDispatchers,
                analyticsHolder = analyticsHolder,
                loggerHolder = loggerHolder,
                defaultDispatcher = defaultDispatcher,
            )
        assertNotNull(graph.storeFactory, "The assisted LiveWorkoutStoreImpl.Factory must be resolvable")
    }
}
