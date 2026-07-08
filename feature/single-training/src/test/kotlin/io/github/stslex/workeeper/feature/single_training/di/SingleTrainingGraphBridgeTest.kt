// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.di

import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionConflictResolver
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.training.TrainingRepository
import io.github.stslex.workeeper.core.ui.mvi.di.StoreDispatchers
import io.github.stslex.workeeper.core.ui.mvi.holders.AnalyticsHolder
import io.github.stslex.workeeper.core.ui.mvi.holders.LoggerHolder
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * KMP C.1 wave 1 — in-situ integration proof on single-training's REAL [SingleTrainingGraph].
 *
 * Pure dispatcher-collision flip (no Context): single-training bridges @DefaultDispatcher +
 * @MainImmediateDispatcher (both CoroutineDispatcher). The real graph must resolve each to its OWN
 * bound instance under its OWN qualifier — no cross-wire. Also exercises the ASSISTED path: the
 * graph exposes the assisted [SingleTrainingStoreImpl.Factory] (not the Store).
 *
 * Pure-JVM: `createGraphFactory` is Metro-compiler codegen, no Android runtime.
 */
internal class SingleTrainingGraphBridgeTest {

    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
    private val trainingExerciseRepository = mockk<TrainingExerciseRepository>(relaxed = true)
    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val sessionConflictResolver = mockk<SessionConflictResolver>(relaxed = true)
    private val resourceWrapper = mockk<ResourceWrapper>(relaxed = true)
    private val navigator = mockk<Navigator>(relaxed = true)
    private val storeDispatchers = StoreDispatchers(
        defaultDispatcher = Dispatchers.Unconfined,
        mainImmediateDispatcher = Dispatchers.Unconfined,
    )
    private val analyticsHolder = AnalyticsHolder()
    private val loggerHolder = LoggerHolder()

    // Two DISTINCT dispatcher instances so === identity distinguishes them unambiguously.
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default
    private val mainImmediateDispatcher: CoroutineDispatcher = Dispatchers.Main

    private fun buildGraph(): SingleTrainingGraph = createGraphFactory<SingleTrainingGraph.Factory>()
        .create(
            trainingRepository = trainingRepository,
            trainingExerciseRepository = trainingExerciseRepository,
            exerciseRepository = exerciseRepository,
            tagRepository = tagRepository,
            sessionRepository = sessionRepository,
            sessionConflictResolver = sessionConflictResolver,
            resourceWrapper = resourceWrapper,
            navigator = navigator,
            storeDispatchers = storeDispatchers,
            analyticsHolder = analyticsHolder,
            loggerHolder = loggerHolder,
            defaultDispatcher = defaultDispatcher,
            mainImmediateDispatcher = mainImmediateDispatcher,
        )

    @Test
    fun `default and main-immediate dispatchers resolve to distinct instances with no cross-wire`() {
        val graph = buildGraph()

        assertSame(
            defaultDispatcher,
            graph.defaultDispatcher,
            "@DefaultDispatcher must resolve to the default instance",
        )
        assertSame(
            mainImmediateDispatcher,
            graph.mainImmediateDispatcher,
            "@MainImmediateDispatcher must resolve to the main-immediate instance — not cross-wired",
        )
    }

    @Test
    fun `assisted factory is exposed and resolvable from the real graph`() {
        val factory = buildGraph().storeFactory

        assertNotNull(
            factory,
            "The assisted SingleTrainingStoreImpl.Factory must be resolvable from the graph",
        )
    }
}
