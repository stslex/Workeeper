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
 * **One mutation stayed green, and what it means is narrower than it first looked.** Three targeted
 * mutations were run against this pair: the track's tier (`surfaceTier1` → `surfaceTier0`) and the
 * bar's height (56 → the untransformed 60) each reddened all four images. The third —
 * `textTertiary` → `textDim` on the inactive glyph — stayed **byte-identical green**, because
 * `textDim` resolves to `*_META` in both themes (§2.5 / #184 C1), so the two roles are one colour.
 *
 * That was first written up here as "§26's most-argued rejected decision, restored and unguarded".
 * **It is not, and B28 is why.** The rejected value was `--dim`'s `#6B7078` / `#98A0A9`, and
 * `textDim` does not carry it — **nothing in the tree does**, measured. The mutation renamed a role
 * rather than restoring a decision, so it is a **no-op, not a gate hole**, and the two accuse
 * opposite things: a hole accuses the suite, a no-op accuses the mutation. §27 carries the general
 * rule (confirm a green mutation changes an observable before reporting it as a hole).
 *
 * What survives is worth keeping and is smaller: **a role kept as a name at another role's value is
 * ungated by any picture, by construction.** Nothing to patch — a picture is the wrong instrument
 * for a claim about which *name* was read. Reinstate `dim` as a distinct value (§2.5's path) and
 * these goldens start discriminating on their own; `ContrastContract` already declares `textDim` on
 * every surface and would speak the moment the values diverged.
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
