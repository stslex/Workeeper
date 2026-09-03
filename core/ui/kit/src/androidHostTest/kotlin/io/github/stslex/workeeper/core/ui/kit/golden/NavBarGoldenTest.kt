// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.golden

import androidx.compose.runtime.Composable
import io.github.stslex.workeeper.core.ui.kit.components.navbar.AppNavBar
import io.github.stslex.workeeper.core.ui.kit.components.navbar.AppNavBarItem
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/**
 * The v3 nav bar at rest, shot with the first and the last item selected so the pill's offset
 * arithmetic is pinned at both ends. See documentation/testing.md.
 */
internal class NavBarGoldenTest {

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun navBarSelectedFirst(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { NavBar(selectedIndex = 0) }
    }

    @ParameterizedTest
    @EnumSource(GoldenTheme::class)
    fun navBarSelectedLast(theme: GoldenTheme, testInfo: TestInfo) {
        goldenSubject(testInfo, theme) { NavBar(selectedIndex = 2) }
    }

    /**
     * No `values-ru` variant: Cyrillic reaches this only via `contentDescription` and the chosen
     * variant draws no captions. Restoring captions reopens the gap.
     */
    @Composable
    private fun NavBar(selectedIndex: Int) {
        AppNavBar(
            items = listOf(
                AppNavBarItem(AppIcons.Home, "Home", "BottomAppBarItem_HOME"),
                AppNavBarItem(AppIcons.Trainings, "Trainings", "BottomAppBarItem_TRAININGS"),
                AppNavBarItem(AppIcons.Exercises, "Exercises", "BottomAppBarItem_EXERCISES"),
            ),
            selectedIndex = selectedIndex,
            onSelect = {},
        )
    }
}
