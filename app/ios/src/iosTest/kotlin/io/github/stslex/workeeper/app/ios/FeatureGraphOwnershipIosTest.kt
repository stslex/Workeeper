// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class, ExperimentalComposeUiApi::class)

package io.github.stslex.workeeper.app.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.backhandler.LocalCompatNavigationEventDispatcherOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import io.github.stslex.workeeper.App
import io.github.stslex.workeeper.app.common.di.AppRootDeps
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.mvi.BaseStore
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_exercises.di.AllExercisesGraph
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStore
import io.github.stslex.workeeper.feature.all_exercises.mvi.store.AllExercisesStoreImpl
import io.github.stslex.workeeper.feature.all_exercises.ui.allExercisesGraph
import io.github.stslex.workeeper.feature.all_trainings.di.AllTrainingsGraph
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStore
import io.github.stslex.workeeper.feature.all_trainings.mvi.store.AllTrainingsStoreImpl
import io.github.stslex.workeeper.feature.all_trainings.ui.allTrainingsGraph
import io.github.stslex.workeeper.feature.app_dialogs.impl.di.AppDialogGraph
import io.github.stslex.workeeper.feature.app_dialogs.impl.mvi.store.AppDialogStoreImpl
import io.github.stslex.workeeper.feature.app_dialogs.impl.ui.AppDialogHost
import io.github.stslex.workeeper.feature.archive.di.ArchiveGraph
import io.github.stslex.workeeper.feature.archive.mvi.store.ArchiveStoreImpl
import io.github.stslex.workeeper.feature.archive.ui.archiveGraph
import io.github.stslex.workeeper.feature.exercise.di.ExerciseGraph
import io.github.stslex.workeeper.feature.exercise.ui.exerciseGraph
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStoreImpl
import io.github.stslex.workeeper.feature.exercise_chart.di.ExerciseChartGraph
import io.github.stslex.workeeper.feature.exercise_chart.mvi.store.ExerciseChartStoreImpl
import io.github.stslex.workeeper.feature.exercise_chart.ui.exerciseChartGraph
import io.github.stslex.workeeper.feature.home.di.HomeGraph
import io.github.stslex.workeeper.feature.home.mvi.store.HomeStoreImpl
import io.github.stslex.workeeper.feature.home.ui.homeGraph
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerGraph
import io.github.stslex.workeeper.feature.image_viewer.mvi.store.ImageViewerStoreImpl
import io.github.stslex.workeeper.feature.image_viewer.ui.imageViewerGraph
import io.github.stslex.workeeper.feature.live_workout.di.LiveWorkoutGraph
import io.github.stslex.workeeper.feature.live_workout.mvi.store.LiveWorkoutStoreImpl
import io.github.stslex.workeeper.feature.live_workout.ui.liveWorkoutGraph
import io.github.stslex.workeeper.feature.past_session.di.PastSessionGraph
import io.github.stslex.workeeper.feature.past_session.mvi.store.PastSessionStoreImpl
import io.github.stslex.workeeper.feature.past_session.ui.pastSessionGraph
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorGraph
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.DialogState
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStore
import io.github.stslex.workeeper.feature.plan_editor.ui.mvi.store.PlanEditorStoreImpl
import io.github.stslex.workeeper.feature.plan_editor.ui.planEditorGraph
import io.github.stslex.workeeper.feature.settings.di.SettingsGraph
import io.github.stslex.workeeper.feature.settings.mvi.store.SettingsStoreImpl
import io.github.stslex.workeeper.feature.settings.ui.settingsGraph
import io.github.stslex.workeeper.feature.single_training.di.SingleTrainingGraph
import io.github.stslex.workeeper.feature.single_training.mvi.store.SingleTrainingStoreImpl
import io.github.stslex.workeeper.feature.single_training.ui.singleTrainingsGraph
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

class FeatureGraphOwnershipIosTest {
    @Test
    fun allThirteenProductionFeaturesRetainTheirSuppliedFactoryStore() = runComposeUiTest {
        val fixture = IosGraphFixture()
        var graph: IosAppGraph? = null
        val owners = List(13) { GraphTestOwner() }
        val lifecycle = FeatureLifecycleOwner()
        val dispatch = FeatureBackDispatcher()
        val index = mutableStateOf(0)
        val revision = mutableStateOf(0)
        val visible = mutableStateOf(true)
        try {
            val activeGraph = fixture.graph().also { graph = it }
            val observations = FactoryObservations(activeGraph)
            setContent {
                if (visible.value) key(index.value) {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides owners[index.value],
                        LocalLifecycleOwner provides lifecycle,
                        LocalCompatNavigationEventDispatcherOwner provides dispatch,
                    ) {
                        AppTheme {
                            val deps = observations.factories()
                            val modifier = Modifier.testTag("factory-${revision.value}")
                            if (index.value == featureRoutes.size) AppDialogHost(deps.appDialogGraphFactory)
                            else FeatureEntry(deps, featureRoutes[index.value].second, modifier)
                        }
                    }
                }
            }
            (featureRoutes.map { it.first } + "app-dialogs").forEachIndexed { selected, name ->
                runOnIdle { index.value = selected }
                waitForIdle()
                assertEquals(1, observations.created[name]?.size, "$name must resolve its supplied factory")
                assertEquals(1, observations.arguments[name]?.size, "$name must create one graph")
                val retained = owners[selected].viewModelStore
                assertSame(observations.created.getValue(name).single(), retained[retained.keys().single()])
                if (name in assistedFeatures) {
                    assertEquals<List<Screen?>>(
                        listOf(featureRoutes[selected].second),
                        observations.arguments.getValue(name),
                    )
                }
                repeat(3) {
                    runOnIdle { revision.value++ }
                    waitForIdle()
                    assertEquals(
                        1,
                        observations.created[name]?.size,
                        "$name must retain its Store across changed content",
                    )
                    assertEquals(1, observations.arguments[name]?.size, "$name must retain one graph")
                    assertSame(observations.created.getValue(name).single(), retained[retained.keys().single()])
                }
            }
            assertEquals(13, observations.created.size)
            assertEquals(13, observations.created.values.flatten().toSet().size)
        } finally {
            closeOwnershipFixture(fixture, graph, owners, dispatch) { visible.value = false }
        }
    }

    @Test
    fun rootMountsEverySuppliedFactoryAndRoute() = runComposeUiTest {
        val fixture = IosGraphFixture()
        var graph: IosAppGraph? = null
        val owner = GraphTestOwner()
        val generations = GraphTestGenerations(owner)
        val dispatch = FeatureBackDispatcher()
        val visible = mutableStateOf(true)
        try {
            val activeGraph = fixture.graph().also { graph = it }
            val observations = FactoryObservations(activeGraph)
            setContent {
                if (visible.value) CompositionLocalProvider(
                    LocalCompatNavigationEventDispatcherOwner provides dispatch,
                ) {
                    App(generations, { observations.factories() }, {})
                }
            }
            featureRoutes.forEachIndexed { index, (name, screen) ->
                if (index > 0) runOnIdle { activeGraph.navigator.navTo(screen) }
                expectEventually("App mounts $name from the admitted generation") { name in observations.created }
                assertEquals(1, observations.arguments.getValue(name).size)
                if (name in assistedFeatures) {
                    assertEquals<List<Screen?>>(listOf(screen), observations.arguments.getValue(name))
                }
            }
            assertEquals(1, observations.arguments["app-dialogs"]?.size, "App mounts the root dialog factory")
            assertEquals(13, observations.created.size)
            assertEquals(1, generations.admitted.size)
        } finally {
            closeOwnershipFixture(fixture, graph, listOf(owner), dispatch) { visible.value = false }
        }
        assertSame(generations.admitted.single(), generations.released.single())
    }

    @Test
    fun productionGraphsDispatchSystemBackOnlyWhileIntercepting() = runComposeUiTest {
        val fixture = IosGraphFixture()
        var graph: IosAppGraph? = null
        val owners = List(3) { GraphTestOwner() }
        val lifecycle = FeatureLifecycleOwner()
        val dispatch = FeatureBackDispatcher()
        val input = DirectNavigationEventInput()
        val routes = listOf(featureRoutes[1], featureRoutes[2], featureRoutes[6])
        val selected = mutableStateOf(0)
        val visible = mutableStateOf(true)
        var fallbacks = 0
        runOnIdle { dispatch.navigationEventDispatcher.addInput(input) }
        try {
            val activeGraph = fixture.graph().also { graph = it }
            val observations = FactoryObservations(activeGraph)
            setContent {
                if (visible.value) key(selected.value) {
                    CompositionLocalProvider(
                        LocalViewModelStoreOwner provides owners[selected.value],
                        LocalLifecycleOwner provides lifecycle,
                        LocalCompatNavigationEventDispatcherOwner provides dispatch,
                    ) {
                        AppTheme {
                            BackHandler { fallbacks++ }
                            FeatureEntry(observations.factories(), routes[selected.value].second, Modifier)
                        }
                    }
                }
            }
            routes.forEachIndexed { index, (name, _) ->
                runOnIdle { selected.value = index }
                waitForIdle()
                val store = observations.created.getValue(name).single()
                if (store is PlanEditorStoreImpl) waitUntil(timeoutMillis = 10_000) { !store.state.value.isLoading }
                val before = fallbacks
                runOnIdle { input.backCompleted() }
                waitForIdle()
                assertEquals(before + 1, fallbacks, "$name must leave ordinary back to the parent")
                runOnIdle {
                    when (store) {
                        is AllTrainingsStoreImpl -> store.consume(
                            AllTrainingsStore.Action.Click.OnTrainingLongPress("selected"),
                        )
                        is AllExercisesStoreImpl -> store.consume(
                            AllExercisesStore.Action.Click.OnExerciseLongPress("selected"),
                        )
                        is PlanEditorStoreImpl -> store.consume(PlanEditorStore.Action.Click.OnAddSet)
                    }
                }
                waitUntil(timeoutMillis = 10_000) { intercepts(store) }
                waitForIdle()
                runOnIdle { input.backCompleted() }
                waitForIdle()
                expectEventually("$name dispatches its system-back action") {
                    when (store) {
                        is AllTrainingsStoreImpl -> !store.state.value.isSelecting
                        is AllExercisesStoreImpl -> !store.state.value.isSelecting
                        is PlanEditorStoreImpl -> store.state.value.dialogState == DialogState.DiscardConfirm
                        else -> false
                    }
                }
                assertEquals(before + 1, fallbacks, "$name must consume intercepted back")
                when (store) {
                    is AllTrainingsStoreImpl -> assertFalse(store.state.value.isSelecting)
                    is AllExercisesStoreImpl -> assertFalse(store.state.value.isSelecting)
                    is PlanEditorStoreImpl -> assertTrue(store.state.value.isDirty)
                }
                if (store !is PlanEditorStoreImpl) {
                    waitForIdle()
                    runOnIdle { input.backCompleted() }
                    waitForIdle()
                    assertEquals(before + 2, fallbacks, "$name must restore parent back after selection exits")
                }
            }
        } finally {
            closeOwnershipFixture(fixture, graph, owners, dispatch) { visible.value = false }
        }
    }
}

private suspend fun ComposeUiTest.closeOwnershipFixture(
    fixture: IosGraphFixture,
    graph: IosAppGraph?,
    owners: List<GraphTestOwner>,
    dispatch: FeatureBackDispatcher,
    unmount: () -> Unit,
) {
    try {
        runOnIdle(unmount)
        waitForIdle()
    } finally {
        try {
            runOnIdle {
                owners.forEach { it.clear() }
                dispatch.navigationEventDispatcher.dispose()
                graph?.appScopeLifetime?.cancel()
            }
            // Store cleanup can enqueue Main work from Default; advance the Compose scheduler before joining.
            waitUntil(timeoutMillis = 10_000) { graph?.appScopeLifetime?.job?.isCompleted != false }
        } finally {
            withContext(NonCancellable) { fixture.close() }
        }
    }
}

private fun ComposeUiTest.expectEventually(message: String, condition: () -> Boolean) {
    try {
        waitUntil(timeoutMillis = 10_000, condition = condition)
    } catch (failure: ComposeTimeoutException) {
        fail(message, failure)
    }
    assertTrue(condition(), message)
}

private val assistedFeatures = setOf(
    "image-viewer",
    "plan-editor",
    "past-session",
    "exercise-chart",
    "single-training",
    "exercise",
    "live-workout",
)

private fun intercepts(store: BaseStore<*, *, *>): Boolean = when (store) {
    is AllTrainingsStoreImpl -> store.state.value.interceptBack
    is AllExercisesStoreImpl -> store.state.value.interceptBack
    is PlanEditorStoreImpl -> store.state.value.interceptBack
    else -> error("Unexpected back-handler Store")
}

private val featureRoutes: List<Pair<String, Screen>> = listOf(
    "home" to Screen.BottomBar.Home,
    "all-trainings" to Screen.BottomBar.AllTrainings,
    "all-exercises" to Screen.BottomBar.AllExercises,
    "settings" to Screen.Settings,
    "archive" to Screen.Archive,
    "image-viewer" to Screen.ExerciseImage("file:///factory-retention.jpg", editable = true),
    "plan-editor" to Screen.PlanEditor.Existing(
        "00000000-0000-4000-8000-000000007361",
        "00000000-0000-4000-8000-000000007364",
        "00000000-0000-4000-8000-000000007362",
    ),
    "past-session" to Screen.PastSession("00000000-0000-4000-8000-000000007364"),
    "exercise-chart" to Screen.ExerciseChart("00000000-0000-4000-8000-000000007365"),
    "single-training" to Screen.Training("00000000-0000-4000-8000-000000007366"),
    "exercise" to Screen.Exercise("00000000-0000-4000-8000-000000007367"),
    "live-workout" to Screen.LiveWorkout(
        "00000000-0000-4000-8000-000000007368",
        "00000000-0000-4000-8000-000000007369",
    ),
)

@Composable
private fun FeatureEntry(deps: AppRootDeps, screen: Screen, modifier: Modifier) {
    val provider = entryProvider<NavKey> {
        with(NavGraphScope(this, deps.navigatorEventBus)) {
            homeGraph(deps.homeGraphFactory, modifier)
            allTrainingsGraph(deps.allTrainingsGraphFactory, modifier)
            allExercisesGraph(deps.allExercisesGraphFactory, modifier)
            settingsGraph(deps.settingsGraphFactory, modifier)
            archiveGraph(deps.archiveGraphFactory, modifier)
            imageViewerGraph(deps.imageViewerGraphFactory, modifier)
            planEditorGraph(deps.planEditorGraphFactory, modifier)
            pastSessionGraph(deps.pastSessionGraphFactory, modifier)
            exerciseChartGraph(deps.exerciseChartGraphFactory, modifier)
            singleTrainingsGraph(deps.singleTrainingGraphFactory, modifier)
            exerciseGraph(deps.exerciseGraphFactory, modifier)
            liveWorkoutGraph(deps.liveWorkoutGraphFactory, modifier)
        }
    }
    provider(screen).Content()
}

private class FactoryObservations(private val graph: AppRootDeps) {
    val created = mutableMapOf<String, MutableList<BaseStore<*, *, *>>>()

    val arguments = mutableMapOf<String, MutableList<Screen?>>()

    fun factories(): AppRootDeps = object : AppRootDeps by graph {
        override val homeGraphFactory = HomeGraph.Factory {
            arguments.getOrPut("home") { mutableListOf() }.add(null)
            val delegate = graph.homeGraphFactory.createHomeGraph()
            object : HomeGraph by delegate {
                override val homeStore: HomeStoreImpl
                    get() = delegate.homeStore.also { store ->
                        created.getOrPut("home") { mutableListOf() }.add(store)
                    }
            }
        }
        override val allTrainingsGraphFactory = AllTrainingsGraph.Factory {
            arguments.getOrPut("all-trainings") { mutableListOf() }.add(null)
            val delegate = graph.allTrainingsGraphFactory.createAllTrainingsGraph()
            object : AllTrainingsGraph by delegate {
                override val allTrainingsStore: AllTrainingsStoreImpl
                    get() = delegate.allTrainingsStore.also { store ->
                        created.getOrPut("all-trainings") { mutableListOf() }.add(store)
                    }
            }
        }
        override val allExercisesGraphFactory = AllExercisesGraph.Factory {
            arguments.getOrPut("all-exercises") { mutableListOf() }.add(null)
            val delegate = graph.allExercisesGraphFactory.createAllExercisesGraph()
            object : AllExercisesGraph by delegate {
                override val allExercisesStore: AllExercisesStoreImpl
                    get() = delegate.allExercisesStore.also { store ->
                        created.getOrPut("all-exercises") { mutableListOf() }.add(store)
                    }
            }
        }
        override val settingsGraphFactory = SettingsGraph.Factory {
            arguments.getOrPut("settings") { mutableListOf() }.add(null)
            val delegate = graph.settingsGraphFactory.createSettingsGraph()
            object : SettingsGraph by delegate {
                override val settingsStore: SettingsStoreImpl
                    get() = delegate.settingsStore.also { store ->
                        created.getOrPut("settings") { mutableListOf() }.add(store)
                    }
            }
        }
        override val archiveGraphFactory = ArchiveGraph.Factory {
            arguments.getOrPut("archive") { mutableListOf() }.add(null)
            val delegate = graph.archiveGraphFactory.createArchiveGraph()
            object : ArchiveGraph by delegate {
                override val archiveStore: ArchiveStoreImpl
                    get() = delegate.archiveStore.also { store ->
                        created.getOrPut("archive") { mutableListOf() }.add(store)
                    }
            }
        }
        override val imageViewerGraphFactory = ImageViewerGraph.Factory { screen ->
            arguments.getOrPut("image-viewer") { mutableListOf() }.add(screen)
            val delegate = graph.imageViewerGraphFactory.createImageViewerGraph(screen)
            object : ImageViewerGraph by delegate {
                override val imageViewerStore: ImageViewerStoreImpl
                    get() = delegate.imageViewerStore.also { store ->
                        created.getOrPut("image-viewer") { mutableListOf() }.add(store)
                    }
            }
        }
        override val planEditorGraphFactory = PlanEditorGraph.Factory { screen ->
            arguments.getOrPut("plan-editor") { mutableListOf() }.add(screen)
            val delegate = graph.planEditorGraphFactory.createPlanEditorGraph(screen)
            object : PlanEditorGraph by delegate {
                override val planEditorStore: PlanEditorStoreImpl
                    get() = delegate.planEditorStore.also { store ->
                        created.getOrPut("plan-editor") { mutableListOf() }.add(store)
                    }
            }
        }
        override val pastSessionGraphFactory = PastSessionGraph.Factory { screen ->
            arguments.getOrPut("past-session") { mutableListOf() }.add(screen)
            val delegate = graph.pastSessionGraphFactory.createPastSessionGraph(screen)
            object : PastSessionGraph by delegate {
                override val pastSessionStore: PastSessionStoreImpl
                    get() = delegate.pastSessionStore.also { store ->
                        created.getOrPut("past-session") { mutableListOf() }.add(store)
                    }
            }
        }
        override val exerciseChartGraphFactory = ExerciseChartGraph.Factory { screen ->
            arguments.getOrPut("exercise-chart") { mutableListOf() }.add(screen)
            val delegate = graph.exerciseChartGraphFactory.createExerciseChartGraph(screen)
            object : ExerciseChartGraph by delegate {
                override val exerciseChartStore: ExerciseChartStoreImpl
                    get() = delegate.exerciseChartStore.also { store ->
                        created.getOrPut("exercise-chart") { mutableListOf() }.add(store)
                    }
            }
        }
        override val singleTrainingGraphFactory = SingleTrainingGraph.Factory { screen ->
            arguments.getOrPut("single-training") { mutableListOf() }.add(screen)
            val delegate = graph.singleTrainingGraphFactory.createSingleTrainingGraph(screen)
            object : SingleTrainingGraph by delegate {
                override val singleTrainingStore: SingleTrainingStoreImpl
                    get() = delegate.singleTrainingStore.also { store ->
                        created.getOrPut("single-training") { mutableListOf() }.add(store)
                    }
            }
        }
        override val exerciseGraphFactory = ExerciseGraph.Factory { screen ->
            arguments.getOrPut("exercise") { mutableListOf() }.add(screen)
            val delegate = graph.exerciseGraphFactory.createExerciseGraph(screen)
            object : ExerciseGraph by delegate {
                override val exerciseStore: ExerciseStoreImpl
                    get() = delegate.exerciseStore.also { store ->
                        created.getOrPut("exercise") { mutableListOf() }.add(store)
                    }
            }
        }
        override val liveWorkoutGraphFactory = LiveWorkoutGraph.Factory { screen ->
            arguments.getOrPut("live-workout") { mutableListOf() }.add(screen)
            val delegate = graph.liveWorkoutGraphFactory.createLiveWorkoutGraph(screen)
            object : LiveWorkoutGraph by delegate {
                override val liveWorkoutStore: LiveWorkoutStoreImpl
                    get() = delegate.liveWorkoutStore.also { store ->
                        created.getOrPut("live-workout") { mutableListOf() }.add(store)
                    }
            }
        }
        override val appDialogGraphFactory = AppDialogGraph.Factory {
            arguments.getOrPut("app-dialogs") { mutableListOf() }.add(null)
            val delegate = graph.appDialogGraphFactory.createAppDialogGraph()
            object : AppDialogGraph by delegate {
                override val appDialogStore: AppDialogStoreImpl
                    get() = delegate.appDialogStore.also { store ->
                        created.getOrPut("app-dialogs") { mutableListOf() }.add(store)
                    }
            }
        }
    }
}

private class FeatureLifecycleOwner : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this).apply { currentState = Lifecycle.State.STARTED }
}

private class FeatureBackDispatcher : NavigationEventDispatcherOwner {
    override val navigationEventDispatcher = NavigationEventDispatcher()
}
