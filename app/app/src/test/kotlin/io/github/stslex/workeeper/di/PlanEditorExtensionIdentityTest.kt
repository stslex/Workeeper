// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.di

import android.content.Context
import dev.zacsweers.metro.asContribution
import dev.zacsweers.metro.createGraphFactory
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
 * Replaces the former feature-module `PlanEditorGraphBridgeTest` (a `@GraphExtension` cannot be created
 * standalone, so the assertion must run where the parent [AppGraph] is compiled — here, `:app`).
 *
 * plan-editor is the SECOND shape-B port and the first whose route arg is a **sealed parent**
 * (`Screen.PlanEditor`, with `Existing` / `Draft` subtypes) rather than a flat data class. Beyond the
 * standard claims it therefore pins shape B's defining property against a sealed arg: each extension
 * carries its OWN route arg into the Store's initial state, and two extensions built from the same
 * parent do not share it. The arg is asserted through `State.mode`, which is where
 * `Screen.PlanEditor.toInitialState()` lands it.
 *
 * NOT covered here, deliberately: two extensions built from DIFFERENT subtypes of the sealed parent.
 * `Screen.PlanEditor.Draft` takes an `ExerciseTypeUiModel`, which `core:ui:navigation` exposes only as
 * an `implementation` dep, so `:app`'s test source cannot construct one. Adding a module dependency to
 * reach it would change the build graph for a test — the two-`Existing` case below already pins
 * per-extension independence, and the subtype dimension adds no binding-resolution risk beyond it.
 */
internal class PlanEditorExtensionIdentityTest {

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

        // Identity, not just non-null: the extension inherits the parent's app-scoped singletons.
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
