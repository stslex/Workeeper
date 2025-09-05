package io.github.stslex.workeeper.feature.exercise.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import io.github.stslex.workeeper.core.ui.mvi.NavComponentScreen
import io.github.stslex.workeeper.core.ui.navigation.Navigator
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.di.ExerciseFeature
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.exerciseGraph(
    sharedTransitionScope: SharedTransitionScope,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    composable<Screen.Exercise.Data> { screen ->
        ExerciseFeature(
            modifier = modifier,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = this,
            component = remember {
                ExerciseComponent.create(navigator, screen.toRoute())
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.exerciseNewGraph(
    sharedTransitionScope: SharedTransitionScope,
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    composable<Screen.Exercise.New> { screen ->
        ExerciseFeature(
            modifier = modifier,
            sharedTransitionScope = sharedTransitionScope,
            animatedContentScope = this,
            component = remember {
                ExerciseComponent.create(navigator, null)
            }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExerciseFeature(
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    component: ExerciseComponent,
    modifier: Modifier = Modifier,
) {
    NavComponentScreen(ExerciseFeature, component) { processor ->
        val context = LocalContext.current

        val snackbarHostState = remember { SnackbarHostState() }
        val hapticFeedback = LocalHapticFeedback.current

        val backHandlerEnable = remember(processor.state.value) { processor.state.value.allowBack }

        BackHandler(backHandlerEnable.not()) {
            processor.consume(Action.Navigation.BackWithConfirmation)
        }

        processor.Handle { event ->
            when (event) {
                ExerciseStore.Event.InvalidParams -> showToast(context)
                is ExerciseStore.Event.Snackbar -> snackbarHostState.showSnackbar(
                    message = when (event.type) {
                        SnackbarType.DELETE -> "Delete this note?"
                        SnackbarType.DISMISS -> "Leave without saving?"
                    },
                    actionLabel = event.type.value,
                    withDismissAction = true
                )
                ExerciseStore.Event.HapticClick -> {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
                }
            }
        }
        with(sharedTransitionScope) {
            ExerciseFeatureWidget(
                consume = processor::consume,
                state = processor.state.value,
                snackbarHostState = snackbarHostState,
                modifier = modifier
                    .sharedBounds(
                        sharedContentState = sharedTransitionScope.rememberSharedContentState(component.data?.uuid ?: "createExercise"),
                        animatedVisibilityScope = animatedContentScope,
                        resizeMode = SharedTransitionScope.ResizeMode.ScaleToBounds()
                    )
            )
        }
    }
}

private fun showToast(context: Context) {
    val msg = context.resources.getString(R.string.toast_invalid_params_msg)
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}