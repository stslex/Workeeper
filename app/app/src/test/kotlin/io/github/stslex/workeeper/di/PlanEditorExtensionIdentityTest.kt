// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
import io.github.stslex.workeeper.core.core.coroutine.scope.AppScopeLifetime
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorGraph
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore.State
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Identity claims for the plan-editor `@GraphExtension`, whose route arg is a sealed parent read
 * back through `State.mode`. See documentation/graph-extension-arc/HANDOFF.md.
 */
internal class PlanEditorExtensionIdentityTest {

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

    private fun AppGraph.planEditor(screen: Screen.PlanEditor) =
        asContribution<PlanEditorGraph.Factory>().createPlanEditorGraph(screen)

    private fun existing(exerciseUuid: String) = Screen.PlanEditor.Existing(
        performedExerciseUuid = null,
        exerciseUuid = exerciseUuid,
        trainingUuid = null,
    )

    @Test
    fun `extension resolves the store through the parent graph`() {
        val store = buildAppGraph().planEditor(existing("ex-1")).planEditorStore

        assertNotNull(store, "The contributed extension must resolve PlanEditorStoreImpl from the parent")
    }

    @Test
    fun `store's app-scoped deps are the SAME instances the parent holds`() {
        val appGraph = buildAppGraph()

        val store = appGraph.planEditor(existing("ex-1")).planEditorStore

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
    fun `each extension carries its own route arg into the store state`() {
        val appGraph = buildAppGraph()

        val first = appGraph.planEditor(existing("ex-1")).planEditorStore
        val second = appGraph.planEditor(existing("ex-2")).planEditorStore

        assertEquals(
            State.Mode.Exercise(exerciseUuid = "ex-1"),
            first.state.value.mode,
            "The first extension's Store must seed initialState from ITS OWN bound route arg",
        )
        assertEquals(
            State.Mode.Exercise(exerciseUuid = "ex-2"),
            second.state.value.mode,
            "The second extension's arg must not be shared with or overwritten by the first",
        )
    }
}
