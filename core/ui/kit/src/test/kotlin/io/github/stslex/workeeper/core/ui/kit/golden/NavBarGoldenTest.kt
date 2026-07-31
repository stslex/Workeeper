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
 * The v3 nav bar at rest — `pass2d.html` `#s-nav`, the `.nb.track.slide` variant.
 *
 * **Two selections, not one, and the pair is the assertion.** A bar photographed only with the
 * first item selected cannot distinguish "the pill is positioned by the selection" from "the pill
 * is pinned to the left edge" — both produce the identical image, and the left edge is exactly
 * where a broken offset calculation would leave it. `selectedFirst` and `selectedLast` differ only
 * in the pill's x, so between them they pin the offset arithmetic at both ends of its range.
 *
 * The bar sits on `surfaceTier0`, which is the page it overlays. That backdrop is load-bearing
 * here rather than decoration: the whole drawn treatment is a `--sec` track *distinguished from
 * the page behind it*, and the palette's tier steps sit 1.05–1.16 apart (§26), so a golden taken
 * on the track's own colour would photograph the one difference the component exists to make.
 *
 * **What these images cannot see, and what covers it instead** (§27, "a golden image gates only
 * what a single static frame contains"): the pill's 340ms travel, its `gel` stretch peak, and the
 * leading-edge origin are all mid-transit facts. `NavPillTest` asserts them directly, on the same
 * pure function and the same constants the component reads.
 *
 * **And one thing nothing covers, found by mutating rather than by reading.** Three targeted
 * mutations were run against this pair: the track's tier (`surfaceTier1` → `surfaceTier0`) and the
 * bar's height (56 → the untransformed 60) each reddened all four images. The third —
 * **`textTertiary` → `textDim` on the inactive glyph — stayed byte-identical green.** That is the
 * one decision §26 "Bottom navigation" argues at length: the device pass measured `--dim` on this
 * track at 3.64 dark / **2.33 light**, below the 3:1 a glyph owes, and picked `--meta` (5.98 /
 * 5.55) because of it. The mutation is the rejected choice, it compiles, and it moves zero pixels.
 *
 * The mechanism is an alias, not an oversight in this file: `textDim` resolves to `*_META` in both
 * themes (§2.5 / #184 C1 merged `dim` into `meta`), so the two roles are one colour and no picture
 * can hold them apart. So the blindness is exactly co-extensive with the alias — reinstate `dim` as
 * a distinct value (§2.5 records the path) and these goldens start discriminating on their own,
 * while `ContrastContract` already declares `textDim` on every surface and would speak the moment
 * the values diverged. Recorded here rather than patched: a picture is the wrong instrument for a
 * claim about which *name* was read, and inventing an assertion that the component reads a
 * particular token would be asserting the token rather than the frames (§27).
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
     * Cyrillic reaches this component only through `contentDescription`, which is invisible, so
     * there is deliberately no `values-ru` variant here — unlike every component that draws a
     * string. Recorded as a decision rather than left as an omission: the chosen `#s-nav` variant
     * draws no captions at all (`.cap` exists in the stylesheet and the normative markup has
     * none), which is the only reason the omission is safe. Restoring captions restores this gap.
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
