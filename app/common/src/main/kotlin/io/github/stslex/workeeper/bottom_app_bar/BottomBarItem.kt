// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.bottom_app_bar

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.stslex.workeeper.app.common.R
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.navigation.Screen

/**
 * The bottom bar's destinations: routing, not chrome — the treatment is the kit's `AppNavBar`.
 * They stay here because the kit can name neither [Screen.BottomBar] nor this module's resources.
 */
@Stable
enum class BottomBarItem(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val screen: Screen.BottomBar,
) {
    HOME(
        titleRes = R.string.bottom_bar_label_home,
        icon = AppIcons.Home,
        screen = Screen.BottomBar.Home,
    ),
    TRAININGS(
        titleRes = R.string.bottom_bar_label_trainings,
        icon = AppIcons.Trainings,
        screen = Screen.BottomBar.AllTrainings,
    ),
    EXERCISES(
        titleRes = R.string.bottom_bar_label_exercises,
        icon = AppIcons.Exercises,
        screen = Screen.BottomBar.AllExercises,
    ),
    ;

    /**
     * The tag `ApplicationBottomBarTest` and `NavigationLifecycleRegressionTest` look items up
     * by; renaming it mixes a chrome change into a test change.
     */
    val testTag: String get() = "BottomAppBarItem_$name"

    companion object {

        /**
         * Resolve the visible [Screen] to its bar item by value identity — the three roots are
         * `data object`s, so `==` is type identity.
         */
        fun getByScreen(
            screen: Screen,
        ): BottomBarItem? = entries.find { entry -> entry.screen == screen }
    }
}
