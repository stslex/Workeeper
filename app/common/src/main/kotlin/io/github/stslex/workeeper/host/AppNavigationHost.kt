// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.mvi.performance.PerformanceMetricsRecorder
import io.github.stslex.workeeper.core.ui.mvi.performance.RecordAction
import io.github.stslex.workeeper.core.ui.navigation.NavGraphScope
import io.github.stslex.workeeper.core.ui.navigation.NavResultsSource
import io.github.stslex.workeeper.core.ui.navigation.NavigatorHolder
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.all_exercises.ui.allExercisesGraph
import io.github.stslex.workeeper.feature.all_trainings.ui.allTrainingsGraph
import io.github.stslex.workeeper.feature.archive.ui.archiveGraph
import io.github.stslex.workeeper.feature.exercise.ui.exerciseGraph
import io.github.stslex.workeeper.feature.exercise_chart.ui.exerciseChartGraph
import io.github.stslex.workeeper.feature.home.ui.homeGraph
import io.github.stslex.workeeper.feature.image_viewer.di.ImageViewerGraph
import io.github.stslex.workeeper.feature.image_viewer.ui.imageViewerGraph
import io.github.stslex.workeeper.feature.live_workout.ui.liveWorkoutGraph
import io.github.stslex.workeeper.feature.past_session.ui.pastSessionGraph
import io.github.stslex.workeeper.feature.plan_editor.di.PlanEditorGraph
import io.github.stslex.workeeper.feature.plan_editor.ui.planEditorGraph
import io.github.stslex.workeeper.feature.settings.ui.settingsGraph
import io.github.stslex.workeeper.feature.single_training.ui.singleTrainingsGraph

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AppNavigationHost(
    navigatorHolder: NavigatorHolder,
    results: NavResultsSource,
    imageViewerGraphFactory: ImageViewerGraph.Factory,
    planEditorGraphFactory: PlanEditorGraph.Factory,
    modifier: Modifier = Modifier,
) {
    SharedTransitionLayout(
        modifier = modifier,
    ) {
        // The display's own corner shape, clipped onto every destination that paints the theme
        // background; it only becomes visible once a predictive-back preview shrinks the window.
        val screenShape = displayCornerShape()

        // GUARD: Nav3 remembers each NavEntry by the back stack, so the first modifier captured for
        // Home outlives entryProvider recompositions. Keep the draw color live without replacing
        // the entry, its ViewModel store, or saveable state.
        val sceneBackground = rememberUpdatedState(MaterialTheme.colorScheme.background)

        // GUARD: order is load-bearing — clip and paint the whole scene, then inset the content,
        // so a back gesture shrinks the window rather than the content area.
        val bottomBarModifier = Modifier
            .fillMaxSize()
            .clip(screenShape)
            .drawBehind { drawRect(sceneBackground.value) }
            .padding(bottom = AppDimension.BottomNavBar.height)
            .systemBarsPadding()

        val standardModifier = Modifier
            .fillMaxSize()
            .clip(screenShape)
            .drawBehind { drawRect(sceneBackground.value) }
            .systemBarsPadding()

        // The one destination the clip is wrong for: the image viewer paints `Color.Black`, so a
        // fallback radius would cut theme-coloured wedges into a black frame while it is open.
        val imageViewerModifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(sceneBackground.value) }
            .systemBarsPadding()

        val motion = AppUi.motion
        val fadeTransform = remember(motion) { navFadeTransform(motion) }

        ClearFocusOnDestinationChanged(navigatorHolder)

        NavDisplay(
            backStack = navigatorHolder.backStack,
            modifier = Modifier.fillMaxSize(),
            // GUARD: both decorators, explicitly — without the ViewModel one, `viewModel {}`
            // resolves against the Activity store and every Store becomes process-scoped.
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            onBack = {
                // System back at the root belongs to the platform (the activity finishes —
                // ApplicationBottomBarTest pins it); NavDisplay must never see an empty stack.
                if (navigatorHolder.backStack.size > 1) {
                    navigatorHolder.backStack.removeLastOrNull()
                }
            },
            // GUARD: all three, explicitly — NavDisplay never chains them, and an unpassed spec
            // keeps a library default that ends every back swipe in a cut. See NavTransitions.kt.
            transitionSpec = { fadeTransform },
            popTransitionSpec = { fadeTransform },
            // The gesture only: NavDisplay seeks this one with raw finger progress, not a clock.
            predictivePopTransitionSpec = { swipeEdge ->
                predictivePopTransform(motion, swipeEdge)
            },
            entryProvider = entryProvider {
                // The one place the navigation library's builder is wrapped: every graph below
                // registers against NavGraphScope, so re-pointing this line changes their backing.
                with(NavGraphScope(this, results)) {
                    homeGraph(
                        modifier = bottomBarModifier
                            .reportScreenPlace<Screen.BottomBar.Home>()
                            .testTag("HomeGraph"),
                    )
                    allTrainingsGraph(
                        modifier = bottomBarModifier
                            .reportScreenPlace<Screen.BottomBar.AllTrainings>()
                            .testTag("AllTrainingsGraph"),
                    )
                    allExercisesGraph(
                        modifier = bottomBarModifier
                            .reportScreenPlace<Screen.BottomBar.AllExercises>()
                            .testTag("AllExercisesGraph"),
                    )
                    singleTrainingsGraph(
                        modifier = standardModifier
                            .reportScreenPlace<Screen.Training>()
                            .testTag("SingleTrainingGraph"),
                    )
                    exerciseGraph(
                        modifier = standardModifier
                            .reportScreenPlace<Screen.Exercise>()
                            .testTag("ExerciseGraph"),
                    )
                    liveWorkoutGraph(
                        modifier = standardModifier
                            .reportScreenPlace<Screen.LiveWorkout>()
                            .testTag("LiveWorkoutGraph"),
                    )
                    pastSessionGraph(
                        modifier = standardModifier
                            .reportScreenPlace<Screen.PastSession>()
                            .testTag("PastSessionGraph"),
                    )
                    imageViewerGraph(
                        factory = imageViewerGraphFactory,
                        modifier = imageViewerModifier
                            .reportScreenPlace<Screen.ExerciseImage>()
                            .testTag("ImageViewerGraph"),
                    )
                    settingsGraph(
                        modifier = standardModifier
                            .reportScreenPlace<Screen.Settings>()
                            .testTag("SettingsGraph"),
                    )
                    archiveGraph(
                        modifier = standardModifier
                            .reportScreenPlace<Screen.Archive>()
                            .testTag("ArchiveGraph"),
                    )
                    exerciseChartGraph(
                        modifier = standardModifier
                            .reportScreenPlace<Screen.ExerciseChart>()
                            .testTag("ExerciseChartGraph"),
                    )
                    planEditorGraph(
                        factory = planEditorGraphFactory,
                        modifier = standardModifier
                            .reportScreenPlace<Screen.PlanEditor>()
                            .testTag("PlanEditorGraph"),
                    )
                }
            },
        )
    }
}

private inline fun <reified S : Screen> Modifier.reportScreenPlace(): Modifier {
    val action = RecordAction.OnScreenPlaced(S::class)
    return this.onPlaced {
        PerformanceMetricsRecorder.process(action)
    }
}
