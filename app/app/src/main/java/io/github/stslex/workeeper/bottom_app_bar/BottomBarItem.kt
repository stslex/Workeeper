// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.bottom_app_bar

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.stslex.workeeper.app.app.R
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.navigation.Screen
import io.github.stslex.workeeper.core.ui.navigation.Screen.Companion.isCurrentScreen
import kotlinx.serialization.InternalSerializationApi

/**
 * The bottom bar's destinations — **routing, and it stays in `app/app` for a reason the compiler
 * enforces rather than a preference.**
 *
 * It carries [Screen.BottomBar] and [getByRoute] — routing, not chrome. The *treatment* is
 * `core:ui:kit`'s `AppNavBar`, and the destinations deliberately did not follow it there: the kit
 * depends on neither `core:ui:navigation` (so it cannot name [Screen.BottomBar]) nor this module's
 * resources (so it cannot resolve `R.string.bottom_bar_label_*`). The deleted
 * `AppBottomBarDestination` is what happens when they do — it hardcoded `label = "Home"`, English
 * literals in a Russian-language app, because nothing else compiled.
 *
 * [titleRes] stays a `@StringRes` and is resolved by the caller with `stringResource`. The icons
 * are now `AppIcons` vectors rather than `@DrawableRes` XML: §26 takes trainings and exercises
 * from the drawn empty-state marks verbatim and gives home the one new mark, so the three
 * `ic_bottom_app_bar_*.xml` drawables — v2 filled Material glyphs — go with the old bar.
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
     * The tag `ApplicationBottomBarTest` and `NavigationLifecycleRegressionTest` look this item up
     * by — **nine lookups across the two files, kept verbatim through the rebuild.** Those tests
     * are about navigation lifecycle; renaming their tags would mix a chrome change and a test
     * change into one PR (§24). The string is built here rather than at the call site so it sits
     * next to the enum whose `name` it is built from.
     */
    val testTag: String get() = "BottomAppBarItem_$name"

    companion object {

        @OptIn(InternalSerializationApi::class)
        fun getByRoute(
            route: String,
        ): BottomBarItem? = entries.find { entry -> entry.screen.isCurrentScreen(route) }
    }
}
