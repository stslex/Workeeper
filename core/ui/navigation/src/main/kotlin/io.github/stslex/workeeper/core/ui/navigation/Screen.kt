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
        data object Charts : BottomBar

        @Serializable
        data object AllExercises : BottomBar

        @Serializable
        data object AllTrainings : BottomBar
    }

    @Serializable
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

    companion object {

        @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
        fun Screen.isCurrentScreen(
            route: String
        ): Boolean = this::class.serializer().descriptor.serialName == route
    }
}

inline fun <reified S : Screen> NavGraphBuilder.navScreen(
    noinline content: @Composable AnimatedContentScope.(S) -> Unit
) {
    composable<S> { backStackEntry ->
        content(backStackEntry.toRoute())
    }
}