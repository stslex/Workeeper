package io.github.stslex.workeeper.bottom_app_bar

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import io.github.stslex.workeeper.app.app.R
import io.github.stslex.workeeper.core.ui.navigation.Screen

@Stable
enum class BottomBarItem(
    @param:StringRes val titleRes: Int,
    val screen: Screen
) {
    CHARTS(
        titleRes = R.string.bottom_bar_label_charts,
        screen = Screen.Charts
    ),
    TRAININGS(
        titleRes = R.string.bottom_bar_label_trainings,
        screen = Screen.AllTrainings
    ),
    EXERCISES(
        titleRes = R.string.bottom_bar_label_exercises,
        screen = Screen.AllExercises
    );

    companion object {

        fun Screen?.isAppbar(): Boolean = entries.any { it.screen == this }

        fun getByRoute(
            route: String
        ): BottomBarItem? = entries.find { entry ->
            route.startsWith(checkNotNull(entry.screen.javaClass.canonicalName))
        }

        fun getByScreen(screen: Screen?): BottomBarItem? = entries.find { it.screen == screen }
    }
}