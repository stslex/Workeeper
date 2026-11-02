// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.bottom_app_bar

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.stslex.workeeper.app.common.resources.Res
import io.github.stslex.workeeper.app.common.resources.bottom_bar_label_exercises
import io.github.stslex.workeeper.app.common.resources.bottom_bar_label_home
import io.github.stslex.workeeper.app.common.resources.bottom_bar_label_trainings
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.navigation.Screen
import org.jetbrains.compose.resources.StringResource

/**
 * The bottom bar's destinations: routing, not chrome — the treatment is the kit's `AppNavBar`.
 * They stay here because the kit can name neither [Screen.BottomBar] nor this module's resources.
 */
@Stable
enum class BottomBarItem(
    val titleRes: StringResource,
    val icon: ImageVector,
    val screen: Screen.BottomBar,
) {
    HOME(
        titleRes = Res.string.bottom_bar_label_home,
        icon = AppIcons.Home,
        screen = Screen.BottomBar.Home,
    ),
    TRAININGS(
        titleRes = Res.string.bottom_bar_label_trainings,
        icon = AppIcons.Trainings,
        screen = Screen.BottomBar.AllTrainings,
    ),
    EXERCISES(
        titleRes = Res.string.bottom_bar_label_exercises,
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
