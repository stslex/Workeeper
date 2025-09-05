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

    val isSingleTop: Boolean
        get() = true

    @Serializable
    data object Home : Screen

    @Serializable
    sealed interface Exercise : Screen {

        @Serializable
        data object New : Exercise

        @Serializable
        data class Data(
            val uuid: String,
            val name: String,
            val sets: Int,
            val reps: Int,
            val weight: Double,
            val timestamp: Long,
            val converted: String
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