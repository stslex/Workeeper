// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
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
 * Replaces the former feature-module `LiveWorkoutGraphBridgeTest` (a `@GraphExtension` cannot be
 * created standalone, so the assertion must run where the parent [AppGraph] is compiled — here, `:app`).
 *
 * live-workout is **port 13 — the last feature graph of the arc** — and the widest at 24 forced-public.
 * It owns the session WRITE path (start, add-exercise, finish, cancel, discard-adhoc), so it inherits
 * the deepest transactional stack of any extension and was the final STANDING RULE 4 boundary
 * candidate. Construction succeeds off-device, so the direct claim is made; if that ever changes the
 * claim becomes the BOUNDARY form (fail at platform static-init HAVING PASSED THROUGH the real
 * container, both halves), not a dropped claim.
 *
 * Every `assertSame` below has one operand from the EXTENSION and one from the parent. That is not
 * incidental: an assertion whose operands both come from the parent tests parent-side stability, not
 * inheritance, and passes no matter what the extension resolved (adjacent-answer witness 13).
 */
internal class LiveWorkoutExtensionIdentityTest {

    // The real parent graph provides Dispatchers.Main.immediate (DispatchersBindingContainer); a plain
    // JVM test must install a Main dispatcher before the store constructs.
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

    /**
     * The session write path is the deepest thing any extension inherits, and this is the feature that
     * mutates it. Identity — not non-null — is what separates "resolved the app's real session stack"
     * from "built its own double", and a double here would mean writes landing outside the app's
     * transaction boundary.
     */
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
        // live-workout consumes only ONE dispatcher, so assertSame alone cannot distinguish "inherited
        // the Default key" from "the parent collapsed Default and IO into one instance".
        assertNotSame(
            appGraph.ioDispatcher,
            extension.defaultDispatcher,
            "@DefaultDispatcher and @IODispatcher must remain two distinct binding keys",
        )
    }

    /** Shape B's defining property: the route arg is per-extension, never shared or stale. */
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

    /**
     * The widest route arg in the arc: TWO nullable uuids of which at least one is non-null. Both
     * legal shapes are asserted, because a bound instance of a nullable type is where a graph could
     * quietly substitute a default, and here that would silently pick the wrong entry mode.
     */
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
