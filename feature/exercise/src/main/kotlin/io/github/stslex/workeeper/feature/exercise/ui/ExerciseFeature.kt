package io.github.stslex.workeeper.feature.exercise.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

fun NavGraphBuilder.exerciseGraph(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    composable<Screen.Exercise.Data> { screen ->
        ExerciseFeature(
            modifier = modifier,
            component = remember {
                ExerciseComponent.create(navigator, screen.toRoute())
            }
        )
    }
}

fun NavGraphBuilder.exerciseNewGraph(
    navigator: Navigator,
    modifier: Modifier = Modifier,
) {
    composable<Screen.Exercise.New> { screen ->
        ExerciseFeature(
            modifier = modifier,
            component = remember {
                ExerciseComponent.create(navigator, null)
            }
        )
    }
}

@Composable
fun ExerciseFeature(
    component: ExerciseComponent,
    modifier: Modifier = Modifier,
) {
    NavComponentScreen(ExerciseFeature, component) { processor ->

        val context = LocalContext.current

        val snackbarHostState = remember {
            SnackbarHostState()
        }

//        LaunchedEffect(Unit) {
//            processor.consume(Action.InitDialog(component.data))
//        }

        BackHandler {
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
            }
        }

        ExerciseFeatureWidget(
            consume = processor::consume,
            state = processor.state.value,
            snackbarHostState = snackbarHostState,
            modifier = modifier
        )
    }
}

private fun showToast(context: Context) {
    val msg = context.resources.getString(R.string.toast_invalid_params_msg)
    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
}