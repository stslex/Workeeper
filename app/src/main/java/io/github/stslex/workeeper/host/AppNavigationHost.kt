package io.github.stslex.workeeper.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import io.github.stslex.workeeper.dialog.create_exercise.ui.CreateNewExerciseDialog
import io.github.stslex.workeeper.feature.home.ui.HomeScreen
import io.github.stslex.workeeper.navigation.RootComponent

@Composable
internal fun AppNavigationHost(
    rootComponent: RootComponent,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Children(
            stack = rootComponent.stack,
            modifier = modifier.fillMaxSize(),
            animation = stackAnimation(),
        ) { created ->
            when (val instance = created.instance) {
                is RootComponent.Child.Home -> HomeScreen(instance.component)
            }
        }

        val dialogSlot by remember { rootComponent.dialogStack }.subscribeAsState()

        dialogSlot.child?.instance?.let { dialogChild ->
            when (dialogChild) {
                is RootComponent.DialogChild.ExerciseCreate -> CreateNewExerciseDialog(
                    modifier = Modifier.align(Alignment.Center),
                    component = dialogChild.component
                )
            }
        }

    }
}
