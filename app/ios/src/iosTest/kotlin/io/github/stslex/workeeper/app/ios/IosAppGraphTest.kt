// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package io.github.stslex.workeeper.app.ios

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.github.stslex.workeeper.App
import io.github.stslex.workeeper.app.common.di.AppUiPhase
import io.github.stslex.workeeper.core.data.dataStore.core.IosDataStorePathResolver
import io.github.stslex.workeeper.core.data.database.training.TrainingEntity
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.app_dialogs.api.model.AppDialog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class IosAppGraphTest {
    @Test
    fun qualifiedDispatchersReachProductionConsumers() {
        val fixture = IosGraphFixture()
        try {
            val graph = fixture.graph()
            assertSame(Dispatchers.Default, graph.storeDispatchers.defaultDispatcher)
            assertSame(Dispatchers.Main.immediate, graph.storeDispatchers.mainImmediateDispatcher)
            assertSame(Dispatchers.Main, graph.mainDispatcher)
            assertSame(Dispatchers.Main.immediate, graph.mainImmediateDispatcher)
            assertSame(Dispatchers.Default, graph.defaultDispatcher)
            assertSame(Dispatchers.IO, graph.ioDispatcher)
            assertSame(graph.defaultDispatcher, graph.storeDispatchers.defaultDispatcher)
            assertSame(graph.mainImmediateDispatcher, graph.storeDispatchers.mainImmediateDispatcher)
            val settings = graph.settingsGraphFactory.createSettingsGraph()
            assertSame(graph.defaultDispatcher, settings.defaultDispatcher)
            assertSame(graph.ioDispatcher, settings.ioDispatcher)
            val exercise = graph.exerciseGraphFactory.createExerciseGraph(Screen.Exercise(null))
            assertSame(graph.defaultDispatcher, exercise.defaultDispatcher)
            assertSame(graph.mainImmediateDispatcher, exercise.mainImmediateDispatcher)
            assertSame(fixture.database, graph.appDatabase)
        } finally {
            runBlocking { fixture.close() }
        }
    }

    @Test
    fun allThirteenFactoriesConstructRealStores() = runComposeUiTest {
        val fixture = IosGraphFixture()
        val graph = fixture.graph()
        val owner = GraphTestOwner()
        val session = Uuid.random().toString()
        val stores = linkedMapOf(
            "home" to graph.homeGraphFactory.createHomeGraph().homeStore,
            "all-trainings" to graph.allTrainingsGraphFactory.createAllTrainingsGraph().allTrainingsStore,
            "all-exercises" to graph.allExercisesGraphFactory.createAllExercisesGraph().allExercisesStore,
            "settings" to graph.settingsGraphFactory.createSettingsGraph().settingsStore,
            "archive" to graph.archiveGraphFactory.createArchiveGraph().archiveStore,
            "image-viewer" to graph.imageViewerGraphFactory.createImageViewerGraph(
                Screen.ExerciseImage("file:///unused-graph-test.jpg"),
            ).imageViewerStore,
            "plan-editor" to graph.planEditorGraphFactory.createPlanEditorGraph(
                Screen.PlanEditor.Existing(null, Uuid.random().toString(), null),
            ).planEditorStore,
            "past-session" to graph.pastSessionGraphFactory.createPastSessionGraph(
                Screen.PastSession(session),
            ).pastSessionStore,
            "exercise-chart" to graph.exerciseChartGraphFactory.createExerciseChartGraph(
                Screen.ExerciseChart(null),
            ).exerciseChartStore,
            "single-training" to graph.singleTrainingGraphFactory.createSingleTrainingGraph(
                Screen.Training(null),
            ).singleTrainingStore,
            "exercise" to graph.exerciseGraphFactory.createExerciseGraph(Screen.Exercise(null)).exerciseStore,
            "live-workout" to graph.liveWorkoutGraphFactory.createLiveWorkoutGraph(
                Screen.LiveWorkout(null, null),
            ).liveWorkoutStore,
            "app-dialogs" to graph.appDialogGraphFactory.createAppDialogGraph().appDialogStore,
        )
        try {
            assertEquals(13, stores.size)
            assertEquals(13, stores.values.toSet().size)
            assertTrue(stores.values.all { it.name.isNotBlank() })
            println("Constructed real feature Stores: ${stores.keys.joinToString()}")
        } finally {
            runOnIdle {
                stores.values.forEach { it.dispose() }
                owner.clear()
            }
            fixture.close()
        }
    }

    @Test
    fun navigatorIsSharedAndRestartReachesHost() {
        val fixture = IosGraphFixture()
        try {
            val graph = fixture.graph()
            assertSame(graph.navigatorEventBus, graph.navigator)
            assertEquals(0, fixture.restartHost.requests)
            graph.navigator.restartApp()
            assertEquals(1, fixture.restartHost.requests)
        } finally {
            runBlocking { fixture.close() }
        }
    }

    @Test
    fun unadmittedRootSkipsGraphResolution() = runComposeUiTest {
        val fixture = IosGraphFixture()
        val graph = fixture.graph()
        val owner = GraphTestOwner()
        val generations = GraphTestGenerations(owner)
        val visible = mutableStateOf(true)
        val revision = mutableStateOf(0)
        var resolves = 0
        generations.appUiPhases.value = AppUiPhase.Transitioning
        setContent {
            val current = revision.value
            if (visible.value) App(
                generations,
                {
                    resolves++
                    graph
                },
                { check(current >= 0) },
            )
        }
        try {
            onNodeWithTag("AppTransitioning").assertIsDisplayed()
            assertEquals(0, resolves)
            assertTrue(generations.gate.awaitRetired(1, timeoutMillis = 1_000))
            runOnIdle {
                generations.appUiPhases.value = AppUiPhase.Generation(1, owner)
            }
            waitForIdle()
            assertEquals(0, resolves)
            onNodeWithTag("HomeGraph").assertDoesNotExist()
            runOnIdle {
                generations.appUiPhases.value = AppUiPhase.Generation(2, owner)
            }
            waitForIdle()
            onNodeWithTag("HomeGraph").assertIsDisplayed()
            repeat(3) {
                runOnIdle { revision.value++ }
                waitForIdle()
            }
            assertEquals(1, resolves)
            assertEquals(1, generations.admitted.size)
        } finally {
            runOnIdle { visible.value = false }
            waitForIdle()
            runOnIdle { owner.clear() }
            fixture.close()
        }
        assertSame(generations.admitted.single(), generations.released.single())
    }

    @Test
    fun admittedRootShowsTrainingFromOwnedDatabase() = runComposeUiTest {
        val fixture = IosGraphFixture()
        val graph = fixture.graph()
        val owner = GraphTestOwner()
        val generations = GraphTestGenerations(owner)
        val visible = mutableStateOf(true)
        val active = training("Active ${Uuid.random()}", archived = false)
        val archived = training("Archived ${Uuid.random()}", archived = true)
        withContext(Dispatchers.IO) {
            fixture.database.trainingDao.insert(active)
            fixture.database.trainingDao.insert(archived)
            assertEquals(active, fixture.database.trainingDao.getById(active.uuid))
            assertEquals(archived, fixture.database.trainingDao.getById(archived.uuid))
        }
        setContent { if (visible.value) App(generations, { graph }, {}) }
        try {
            onNodeWithTag("HomeGraph").assertIsDisplayed()
            runOnIdle { graph.navigator.navTo(Screen.BottomBar.AllTrainings) }
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithText(active.name, useUnmergedTree = true).fetchSemanticsNodes().size == 1
            }
            onNodeWithText(active.name, useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText(archived.name, useUnmergedTree = true).assertDoesNotExist()
        } finally {
            runOnIdle { visible.value = false }
            waitForIdle()
            runOnIdle { owner.clear() }
            fixture.close()
        }
    }

    @Test
    fun preferencesAndDialogsSurviveGraphRecreation() {
        val fixture = IosGraphFixture()
        try {
            val first = fixture.graph()
            val dialog = AppDialog.UndoRestoreSuccess()
            runBlocking {
                first.commonDataStore.setThemePreference("DARK")
                first.appDialogRepository.publish(dialog)
                assertEquals(dialog, first.appDialogRepository.currentDialog.first())
                first.appScopeLifetime.cancelAndJoin()
                val successor = fixture.graph()
                assertNotSame(first, successor)
                assertEquals("DARK", successor.commonDataStore.themePreference.first())
                assertEquals(dialog, successor.appDialogRepository.currentDialog.first())
                successor.appDialogRepository.dismiss(dialog)
                assertNull(successor.appDialogRepository.currentDialog.first())
            }
            listOf("common_prefs", "app_dialogs_prefs").forEach { name ->
                val path = IosDataStorePathResolver().resolve(name).toString()
                assertEquals("${NSHomeDirectory()}/Documents/datastore/$name.preferences_pb", path)
                assertTrue(NSFileManager.defaultManager.fileExistsAtPath(path), path)
            }
        } finally {
            runBlocking { fixture.close() }
        }
    }

    @Test
    fun generationLifetimesAreIndependent() = runComposeUiTest {
        val fixture = IosGraphFixture()
        val first = fixture.graph()
        val successor = fixture.graph()
        val owner = GraphTestOwner()
        val generations = GraphTestGenerations(owner)
        val visible = mutableStateOf(true)
        val active = training("Successor ${Uuid.random()}", archived = false)
        withContext(Dispatchers.IO) { fixture.database.trainingDao.insert(active) }
        val started = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val child = first.appScopeLifetime.childScope(Dispatchers.Default).launch {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                completed.complete(Unit)
            }
        }
        setContent { if (visible.value) App(generations, { successor }, {}) }
        try {
            onNodeWithTag("HomeGraph").assertIsDisplayed()
            started.await()
            first.appScopeLifetime.cancelAndJoin()
            completed.await()
            assertTrue(child.isCompleted)
            assertFalse(first.appScopeLifetime.isActive)
            assertTrue(successor.appScopeLifetime.isActive)
            assertNotSame(first.navigator, successor.navigator)
            runOnIdle { successor.navigator.navTo(Screen.BottomBar.AllTrainings) }
            waitUntil(timeoutMillis = 15_000) {
                onAllNodesWithText(active.name, useUnmergedTree = true).fetchSemanticsNodes().size == 1
            }
            onNodeWithText(active.name, useUnmergedTree = true).assertIsDisplayed()
            assertSame(fixture.database, successor.appDatabase)
        } finally {
            runOnIdle { visible.value = false }
            waitForIdle()
            runOnIdle { owner.clear() }
            fixture.close()
        }
    }

    @Test
    fun mountedRootHoldsAdmissionUntilDisposal() = runComposeUiTest {
        val owner = GraphTestOwner()
        val fixture = IosGraphFixture()
        val generations: GraphTestGenerations
        val remainingGrants: Int
        try {
            val graph = fixture.graph()
            generations = GraphTestGenerations(owner)
            val visible = mutableStateOf(true)
            var resolves = 0
            try {
                setContent {
                    if (visible.value) App(
                        generations,
                        {
                            resolves++
                            graph
                        },
                        {},
                    )
                }
                onNodeWithTag("HomeGraph").assertIsDisplayed()
                assertEquals(1, resolves)
                assertEquals(1, generations.gate.admittedCount(1), "mounted root must hold its issuing gate's grant")
                assertFalse(generations.gate.awaitRetired(1, timeoutMillis = 50))
                assertEquals(1, generations.gate.admittedCount(1))

                runOnIdle { generations.appUiPhases.value = AppUiPhase.Transitioning }
                onNodeWithTag("AppTransitioning").assertIsDisplayed()
                waitForIdle()
                assertEquals(0, generations.gate.admittedCount(1))
                assertSame(generations.admitted.single(), generations.released.single())
                assertTrue(generations.gate.awaitRetired(1, timeoutMillis = 1_000))

                runOnIdle { generations.appUiPhases.value = AppUiPhase.Generation(1, owner) }
                waitForIdle()
                onNodeWithTag("HomeGraph").assertDoesNotExist()
                assertEquals(1, resolves, "a retired publication must resolve no graph dependencies")
                assertEquals(1, generations.admitted.size)
                assertEquals(0, generations.gate.admittedCount(1))

                runOnIdle { generations.appUiPhases.value = AppUiPhase.Transitioning }
                waitForIdle()
                runOnIdle {
                    generations.gate.reopen(1)
                    generations.appUiPhases.value = AppUiPhase.Generation(1, owner)
                }
                onNodeWithTag("HomeGraph").assertIsDisplayed()
                assertEquals(2, resolves, "same-ID reopening must resolve the graph exactly once more")
                assertEquals(1, generations.gate.admittedCount(1))
            } finally {
                runOnIdle { visible.value = false }
                waitForIdle()
                remainingGrants = generations.gate.admittedCount(1)
            }
        } finally {
            try {
                runOnUiThread { owner.clear() }
            } finally {
                withContext(NonCancellable) { fixture.close() }
            }
        }
        assertEquals(0, remainingGrants)
        assertEquals(2, generations.admitted.size)
        assertEquals(generations.admitted, generations.released)
    }
}

private fun training(name: String, archived: Boolean) = TrainingEntity(
    name = name,
    description = null,
    isAdhoc = false,
    archived = archived,
    createdAt = 1_793_664_000_000,
    archivedAt = if (archived) 1_793_664_000_001 else null,
)
