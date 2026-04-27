package io.github.stslex.workeeper.core.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer

@Serializable
@Stable
sealed interface Screen {

    val isSingleTop: Boolean get() = false

    @Serializable
    sealed interface BottomBar : Screen {

        override val isSingleTop: Boolean
            get() = true

        @Serializable
        data object Home : BottomBar

        @Serializable
        data object Charts : BottomBar

        @Serializable
        data object AllExercises : BottomBar

        @Serializable
        data object AllTrainings : BottomBar
    }

    @Serializable
    data class Training(
        val uuid: String?,
    ) : Screen

    @Serializable
    data class Exercise(
        val uuid: String?,
    ) : Screen

    /**
     * Live workout screen. At least one of [sessionUuid] / [trainingUuid] must be non-null:
     *  - `sessionUuid` non-null: resume the in-progress session.
     *  - `sessionUuid` null + `trainingUuid` non-null: create a fresh session for that
     *    training.
     */
    @Serializable
    data class LiveWorkout(
        val sessionUuid: String?,
        val trainingUuid: String?,
    ) : Screen

    @Serializable
    data object Settings : Screen

    @Serializable
    data object Archive : Screen

    companion object {

        @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
        fun Screen.isCurrentScreen(
            route: String,
        ): Boolean = this::class.serializer().descriptor.serialName == route
    }
}

inline fun <reified S : Screen> NavGraphBuilder.navScreen(
    noinline content: @Composable AnimatedContentScope.(S) -> Unit,
) {
    composable<S> { backStackEntry ->
        content(backStackEntry.toRoute())
    }
}
