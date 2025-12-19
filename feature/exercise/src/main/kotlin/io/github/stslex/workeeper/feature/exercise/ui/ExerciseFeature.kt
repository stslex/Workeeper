package io.github.stslex.workeeper.feature.exercise.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.navigation.NavGraphBuilder
import io.github.stslex.workeeper.core.ui.mvi.navComponentScreen
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.di.ExerciseFeature
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.SnackbarType
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore.Action

@OptIn(ExperimentalSharedTransitionApi::class)
fun NavGraphBuilder.exerciseGraph(
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    navComponentScreen(ExerciseFeature) { processor ->
        val context = LocalContext.current

        val snackbarHostState = remember { SnackbarHostState() }
        val hapticFeedback = LocalHapticFeedback.current

        val backHandlerEnable =
            remember(processor.state.value) { processor.state.value.allowBack }

        BackHandler(backHandlerEnable.not()) {
            processor.consume(Action.NavigationMiddleware.BackWithConfirmation)
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
                    withDismissAction = true,
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
                        sharedContentState = sharedTransitionScope.rememberSharedContentState(
                            processor.state.value.uuid ?: "createExercise",
                        ),
                        animatedVisibilityScope = this@navComponentScreen,
                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                            ContentScale.FillBounds,
                            Alignment.Center,
                        ),
                    ),
            )
        }
    }
}

private fun showToast(context: Context) {
    val msg = context.resources.getString(R.string.toast_invalid_params_msg)
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}
