// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutGraph
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Identity claims for the live-workout `@GraphExtension`: every `assertSame` has one operand from
 * the extension, so it tests inheritance rather than parent-side stability.
 * See documentation/graph-extension-arc/HANDOFF.md.
 */
internal class LiveWorkoutExtensionIdentityTest {

    // GUARD: Store construction reads the parent graph's Main.immediate binding.
    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildAppGraph(): AppGraph = createGraphFactory<AppGraph.Factory>()
        .create(
            applicationContext = mockk<Context>(relaxed = true),
            appDatabase = mockk(relaxed = true),
            imageStorage = mockk(relaxed = true),
            appScopeLifetime = AppScopeLifetime(),
            databaseReplacement = mockk(relaxed = true),
        )

    private fun AppGraph.liveWorkout(
        sessionUuid: String? = null,
        trainingUuid: String? = null,
    ): LiveWorkoutGraph = asContribution<LiveWorkoutGraph.Factory>()
        .createLiveWorkoutGraph(
            Screen.LiveWorkout(sessionUuid = sessionUuid, trainingUuid = trainingUuid),
        )

    @Test
    fun `extension resolves the store through the parent graph`() {
        val store = buildAppGraph().liveWorkout(sessionUuid = "s-1").liveWorkoutStore

        assertNotNull(store, "The contributed extension must resolve LiveWorkoutStoreImpl from the parent")
    }

    @Test
    fun `store's app-scoped deps are the SAME instances the parent holds`() {
        val appGraph = buildAppGraph()

        val store = appGraph.liveWorkout(sessionUuid = "s-1").liveWorkoutStore

        assertSame(
            appGraph.analyticsHolder,
            store.analyticsHolder,
            "AnalyticsHolder in the extension-built store must be the parent graph's instance",
        )
        assertSame(
            appGraph.loggerHolder,
            store.loggerHolder,
            "LoggerHolder in the extension-built store must be the parent graph's instance",
        )
    }

    @Test
    fun `the session write path is inherited from the parent, not rebuilt`() {
        val appGraph = buildAppGraph()

        val extension = appGraph.liveWorkout(sessionUuid = "s-1")

        assertSame(
            appGraph.sessionRepository,
            extension.sessionRepository,
            "SessionRepository in the extension must be the PARENT's instance, not a double",
        )
    }

    @Test
    fun `the qualified dispatcher is inherited and is not the IO key`() {
        val appGraph = buildAppGraph()

        val extension = appGraph.liveWorkout(sessionUuid = "s-1")

        assertSame(
            appGraph.defaultDispatcher,
            extension.defaultDispatcher,
            "@DefaultDispatcher in the extension must be the parent graph's instance",
        )
        // One dispatcher consumed: assertSame alone cannot rule out Default and IO collapsing.
        assertNotSame(
            appGraph.ioDispatcher,
            extension.defaultDispatcher,
            "@DefaultDispatcher and @IODispatcher must remain two distinct binding keys",
        )
    }

    @Test
    fun `each extension carries its own route arg into the store state`() {
        val appGraph = buildAppGraph()

        val first = appGraph.liveWorkout(sessionUuid = "s-1").liveWorkoutStore
        val second = appGraph.liveWorkout(sessionUuid = "s-2").liveWorkoutStore

        assertNotSame(first, second, "each createLiveWorkoutGraph(screen) must build a distinct Store")
        assertEquals(
            "s-1",
            first.state.value.sessionUuid,
            "The first extension's Store must seed initialState from ITS OWN bound route arg",
        )
        assertEquals(
            "s-2",
            second.state.value.sessionUuid,
            "The second extension's arg must not be shared with or overwritten by the first",
        )
    }

    /** Both arms asserted: a defaulted nullable bound instance would pick the wrong mode. */
    @Test
    fun `both arms of the two-nullable-uuid route arg survive the binding`() {
        val appGraph = buildAppGraph()

        val bySession = appGraph.liveWorkout(sessionUuid = "s-9").liveWorkoutStore
        val byTraining = appGraph.liveWorkout(trainingUuid = "t-9").liveWorkoutStore

        assertEquals("s-9", bySession.state.value.sessionUuid, "session arm must carry its sessionUuid")
        assertNull(bySession.state.value.trainingUuid, "session arm's trainingUuid must stay null")

        assertEquals("t-9", byTraining.state.value.trainingUuid, "training arm must carry its trainingUuid")
        assertNull(byTraining.state.value.sessionUuid, "training arm's sessionUuid must stay null")
    }
}
