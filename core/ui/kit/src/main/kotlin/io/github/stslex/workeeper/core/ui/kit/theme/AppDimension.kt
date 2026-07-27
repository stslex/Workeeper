// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Suppress("unused", "TooManyFunctions")
object AppDimension {

    object Radius {

        val smallest = 4.dp
        val small = 8.dp
        val medium = 16.dp
        val big = 32.dp
        val large = 64.dp
        val largest = 128.dp
    }

    object Elevation {

        val smallest = 2.dp
        val small = 4.dp
        val medium = 8.dp
    }

    object Border {

        val small = 1.dp
        val medium = 2.dp
        val large = 3.dp
    }

    object Icon {

        val small = 16.dp
        val medium = 24.dp
        val big = 32.dp
        val large = 48.dp
        val huge = 72.dp
    }

    object Button {

        val smallest = 12.dp
        val small = 36.dp
        val medium = 60.dp
        val big = 72.dp
    }

    object BottomNavBar {

        val height = 72.dp

        val heightWithInsets: Dp
            @Composable
            get() = height + WindowInsets.navigationBars.getBottom(LocalDensity.current).toDp
    }

    object Space {

        val none: Dp = 0.dp
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp
        val xxxl: Dp = 48.dp
    }

    val screenEdge: Dp = Space.lg
    val sectionSpacing: Dp = Space.xl
    val listItemPadding: Dp = Space.sm
    val cardPadding: Dp = Space.md
    val componentPadding: Dp = Space.sm

    val iconXs: Dp = 14.dp
    val iconSm: Dp = 18.dp
    val iconMd: Dp = 24.dp
    val iconLg: Dp = 32.dp
    val iconXl: Dp = 48.dp

    val heightXs: Dp = 32.dp
    val heightSm: Dp = 40.dp
    val heightMd: Dp = 48.dp
    val heightLg: Dp = 56.dp
    val heightXl: Dp = 64.dp

    val borderHairline: Dp = 0.5.dp
    val phoneFrame: Dp = 24.dp

    /**
     * The section row's resting height — **derived, not transcribed**.
     *
     * The mockup writes `--row-h:88px` (`pass2d.html:16`). That number is not copied here; it is
     * re-derived from this project's own type scale and spacing ladder, and lands on the same
     * value, which is the reason to trust it:
     *
     * ```
     *   2 x LINE_BODY_SP   42   two lines of title — the row's worst case
     *     + Space.xs        4   title-to-supporting gap
     *     + LINE_META_SP   18   one line of supporting text
     *   + 2 x Space.md     24   vertical padding, symmetric
     *   ------------------------
     *                      88
     * ```
     *
     * So the height is what the content needs, not a box the content must fit into. It is applied
     * as a **minimum** ([androidx.compose.foundation.layout.heightIn]), so a title that wraps past
     * two lines, or a user at a larger font scale, grows the row instead of clipping it.
     *
     * A single-line row has no supporting text and therefore no reason to be this tall; it uses
     * [heightXl] (64.dp), which is already on the ladder and is the mockup's `.srow` height
     * (`pass2d.html:157`). Both clear the 48.dp minimum touch target with room to spare.
     */
    val rowHeight: Dp = 88.dp
}
