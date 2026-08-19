// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import io.github.stslex.workeeper.feature.image_viewer.ui.imageViewerGraph
import io.github.stslex.workeeper.feature.live_workout.ui.liveWorkoutGraph
import io.github.stslex.workeeper.feature.past_session.ui.pastSessionGraph
import io.github.stslex.workeeper.feature.plan_editor.ui.planEditorGraph
import io.github.stslex.workeeper.feature.settings.ui.settingsGraph
import io.github.stslex.workeeper.feature.single_training.ui.singleTrainingsGraph

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AppNavigationHost(
    navigatorHolder: NavigatorHolder,
    results: NavResultsSource,
    modifier: Modifier = Modifier,
) {
    SharedTransitionLayout(
        modifier = modifier,
    ) {
        // The display's own corner radius, clipped onto every screen unconditionally — which is
        // what gives the predictive-back preview a rounded card without a corner-radius channel
        // (a ContentTransform has none) and without a signal to plumb. The platform rounds the
        // window at all times too; it only becomes visible once the window shrinks. Invisible at
        // rest: the root Box and the window background are the colour these screens paint.
        val screenShape = RoundedCornerShape(displayCornerRadius())

        // ORDER IS LOAD-BEARING: clip and paint the WHOLE scene, then inset the content inside it.
        // With `systemBarsPadding()` ahead of the clip, the rounded corners begin at the inset
        // boundary instead of the display edge and the bar strips fall outside the card — so the
        // thing that shrinks under a back gesture is the content area, not the window. The
        // platform shrinks the window; so does this. `padding` still insets children exactly as
        // before, so no screen's layout moves.
        val bottomBarModifier = Modifier
            .fillMaxSize()
            .clip(screenShape)
            .background(MaterialTheme.colorScheme.background)
            .padding(bottom = AppDimension.BottomNavBar.height)
            .systemBarsPadding()

        val standardModifier = Modifier
            .fillMaxSize()
            .clip(screenShape)
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()

        val motion = AppUi.motion
        val fadeTransform = remember(motion) { navFadeTransform(motion) }

        ClearFocusOnDestinationChanged(navigatorHolder)

        NavDisplay(
            backStack = navigatorHolder.backStack,
            modifier = Modifier.fillMaxSize(),
            // EXPLICIT, both of them. NavDisplay's default is the saveable decorator ONLY —
            // without rememberViewModelStoreNavEntryDecorator, viewModel {} resolves against
            // the Activity's store, nothing crashes, and every Store silently becomes
            // process-scoped. That exact failure is StoreRetentionTest.isolation's
            // activity-scoped-store mutation, proven red before this swap landed.
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
            // All three, explicitly. NavDisplay has NO fallback between them: a spec left
            // unpassed keeps the library default, and the predictive default is a spring shrink
            // with no fade — which, given the incoming scene is placed BELOW during a gesture,
            // ends every back swipe in a visible cut. See NavTransitions.kt.
            transitionSpec = { fadeTransform },
            popTransitionSpec = { fadeTransform },
            // The gesture, and only the gesture: NavDisplay seeks this one with raw finger
            // progress, while the two above run on a clock.
            predictivePopTransitionSpec = { swipeEdge ->
                predictivePopTransform(motion, swipeEdge)
            },
            entryProvider = entryProvider {
                // The one place the navigation library's builder is wrapped. Every graph below
                // registers against NavGraphScope and never names the library's own builder, so
                // re-pointing this line is enough to change what backs them — which is exactly
                // what the Nav2 -> Nav3 swap did: the twelve registrations are byte-identical
                // across it.
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
                        modifier = standardModifier
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
