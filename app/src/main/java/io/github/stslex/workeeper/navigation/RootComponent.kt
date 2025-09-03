package io.github.stslex.workeeper.navigation

import com.arkivanov.decompose.Cancellation
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import io.github.stslex.workeeper.core.ui.navigation.Config
import io.github.stslex.workeeper.core.ui.navigation.DialogConfig
import io.github.stslex.workeeper.dialog.exercise.ui.mvi.ExerciseDialogComponent
import io.github.stslex.workeeper.feature.home.ui.mvi.handler.HomeComponent

interface RootComponent {

    val stack: Value<ChildStack<Config, Child>>

    val dialogStack: Value<ChildSlot<DialogConfig, DialogChild>>

    fun onConfigChanged(block: (Config) -> Unit): Cancellation

    sealed interface Child {

        data class Home(val component: HomeComponent) : Child

    }

    sealed interface DialogChild {

        data class Exercise(val component: ExerciseDialogComponent) : DialogChild
    }

}
