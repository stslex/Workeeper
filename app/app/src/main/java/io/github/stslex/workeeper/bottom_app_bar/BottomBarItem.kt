package io.github.stslex.workeeper.bottom_app_bar

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.app.app.R
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.Screen.Companion.isCurrentScreen
import kotlinx.serialization.InternalSerializationApi

@Stable
enum class BottomBarItem(
    @param:StringRes val titleRes: Int,
    val screen: Screen.BottomBar
) {
    CHARTS(
        titleRes = R.string.bottom_bar_label_charts,
        screen = Screen.BottomBar.Charts
    ),
    TRAININGS(
        titleRes = R.string.bottom_bar_label_trainings,
        screen = Screen.BottomBar.AllTrainings
    ),
    EXERCISES(
        titleRes = R.string.bottom_bar_label_exercises,
        screen = Screen.BottomBar.AllExercises
    );

    companion object {

        fun Screen?.isAppbar(): Boolean = entries.any { it.screen == this }

        @OptIn(InternalSerializationApi::class)
        fun getByRoute(
            route: String
        ): BottomBarItem? = entries.find { entry -> entry.screen.isCurrentScreen(route) }

        fun getByScreen(screen: Screen?): BottomBarItem? = entries.find { it.screen == screen }
    }
}