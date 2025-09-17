package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
@Stable
sealed interface Screen {

    val isSingleTop: Boolean get() = false

    sealed interface BottomBar : Screen {

        override val isSingleTop: Boolean
            get() = true

        @Serializable
        data object Charts : BottomBar

        @Serializable
        data object AllExercises : BottomBar

        @Serializable
        data object AllTrainings : BottomBar

    }

    sealed interface Training : Screen {

        @Serializable
        data object New : Training

        @Serializable
        data class Data(
            val uuid: String,
        ) : Training
    }

    @Serializable
    sealed interface Exercise : Screen {

        @Serializable
        data object New : Exercise

        @Serializable
        data class Data(
            val uuid: String,
        ) : Exercise
    }

}

inline fun <reified S : Screen> NavGraphBuilder.navScreen(
    noinline content: @Composable AnimatedContentScope.(S) -> Unit
) {
    composable<S> { backStackEntry ->
        content(backStackEntry.toRoute())
    }
}