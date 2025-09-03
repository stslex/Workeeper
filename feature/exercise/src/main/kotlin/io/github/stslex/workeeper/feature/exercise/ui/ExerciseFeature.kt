package io.github.stslex.workeeper.feature.exercise.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.stslex.workeeper.core.ui.mvi.NavComponentScreen
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.di.ExerciseFeature
import io.github.stslex.workeeper.feature.exercise.ui.mvi.handler.ExerciseComponent
import io.github.stslex.workeeper.feature.exercise.ui.mvi.store.ExerciseStore

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

        processor.Handle { event ->
            when (event) {
                ExerciseStore.Event.InvalidParams -> showToast(context)
                ExerciseStore.Event.SnackbarDelete -> snackbarHostState.showSnackbar(
                    message = "Delete this note?",
                    actionLabel = "smth",
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