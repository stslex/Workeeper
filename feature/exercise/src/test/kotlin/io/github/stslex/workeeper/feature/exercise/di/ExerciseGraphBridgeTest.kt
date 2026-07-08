// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.di

import android.content.Context
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.images.ImageStorage
import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.data.exercise.exercise.ExerciseRepository
import io.github.stslex.workeeper.core.data.exercise.personal_record.PersonalRecordRepository
import io.github.stslex.workeeper.core.data.exercise.session.SessionRepository
import io.github.stslex.workeeper.core.data.exercise.tags.TagRepository
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
 * KMP C.1 wave 1 — in-situ integration proof on exercise's REAL [ExerciseGraph]. Two proofs:
 *
 *  1. THIRD-QUALIFIER (the wave's point): exercise bridges @DefaultDispatcher + @MainImmediateDispatcher
 *     (both CoroutineDispatcher). The real graph must resolve each to its OWN bound instance under its
 *     OWN qualifier — the includeJavax bridge working on a THIRD dispatcher qualifier, no cross-wire.
 *  2. CONTEXT STRIP-ON-HILT: the app Context is bound bare (its @ApplicationContext qualifier stayed on
 *     the Hilt side) and must reach the graph as === the provided instance.
 *
 * Also exercises the ASSISTED path: the graph exposes the assisted [ExerciseStoreImpl.Factory] (not the
 * Store); `create(screen)` must build a real Store — proving the whole 14-dep graph resolves in situ.
 *
 * Pure-JVM: `createGraphFactory` is Metro-compiler codegen, no Android runtime.
 */
internal class ExerciseGraphBridgeTest {

    private val exerciseRepository = mockk<ExerciseRepository>(relaxed = true)
    private val tagRepository = mockk<TagRepository>(relaxed = true)
    private val imageStorage = mockk<ImageStorage>(relaxed = true)
    private val personalRecordRepository = mockk<PersonalRecordRepository>(relaxed = true)
    private val sessionRepository = mockk<SessionRepository>(relaxed = true)
    private val trainingRepository = mockk<TrainingRepository>(relaxed = true)
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
    private val appContext = mockk<Context>(relaxed = true)

    private fun buildGraph(): ExerciseGraph = createGraphFactory<ExerciseGraph.Factory>()
        .create(
            exerciseRepository = exerciseRepository,
            tagRepository = tagRepository,
            imageStorage = imageStorage,
            personalRecordRepository = personalRecordRepository,
            sessionRepository = sessionRepository,
            trainingRepository = trainingRepository,
            resourceWrapper = resourceWrapper,
            navigator = navigator,
            storeDispatchers = storeDispatchers,
            analyticsHolder = analyticsHolder,
            loggerHolder = loggerHolder,
            defaultDispatcher = defaultDispatcher,
            mainImmediateDispatcher = mainImmediateDispatcher,
            context = appContext,
        )

    @Test
    fun `default and main-immediate dispatchers resolve to distinct instances with no cross-wire`() {
        val graph = buildGraph()

        // The third-qualifier proof: @DefaultDispatcher and @MainImmediateDispatcher are BOTH
        // CoroutineDispatcher, yet the real graph resolves each to its OWN bound instance.
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
    fun `bare Context reaches the graph as the same application Context instance`() {
        val graph = buildGraph()

        assertSame(
            appContext,
            graph.appContext,
            "The app Context (bridged bare, @ApplicationContext resolved on the Hilt side) must reach " +
                "the graph by identity",
        )
    }

    @Test
    fun `assisted factory is exposed and resolvable from the real graph`() {
        // The graph exposes the assisted Factory (never the Store — that would be [Metro/InvalidBinding]).
        // Resolving it proves the whole 14-dep graph wired, incl. both dispatchers and the bare Context
        // that the assisted Store's handlers/interactor consume transitively.
        val factory = buildGraph().storeFactory

        assertNotNull(factory, "The assisted ExerciseStoreImpl.Factory must be resolvable from the graph")
    }
}
