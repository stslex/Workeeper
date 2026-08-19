// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension

/**
 * The shape the display's own corners are cut with, for the clip the host puts on its **clipped**
 * destinations — every graph except `image-viewer`, which paints `Color.Black` and is exempt (see
 * `AppNavigationHost`, and `architecture.md` § "Navigation host and shared element transitions"
 * for both the exemption and its reason).
 *
 * The clip is unconditional rather than gesture-scoped because the platform's window is always this
 * shape too; it only becomes visible once a predictive-back preview shrinks the window off the
 * display's edge, and a `ContentTransform` has no corner-radius channel to do it with. On a
 * clipped destination it is invisible at rest, because what the corners cut away is the colour that
 * destination paints.
 *
 * Falls back to [AppDimension.Radius.big] when the platform reports no radius — deliberately, since
 * a square card is the defect being fixed; the API cutoff and the zero-radius case are derived in
 * the same architecture section.
 */
@Composable
internal fun displayCornerShape(): Shape {
    val view = LocalView.current
    val density = LocalDensity.current

    // Both reads are KEYS, not inputs, and each answers a different way of going stale.
    //
    // `LocalConfiguration` covers rotation: `MainActivity` declares `configChanges` for
    // orientation and screenSize, so the activity is not recreated and the View is not replaced —
    // keying on `view` alone would hold the pre-rotation shape forever, and a rotation moves which
    // physical corner is which.
    //
    // `WindowInsets.systemBars` covers the arrival race, and covers it by subscribing to the very
    // dispatch that resolves it: `rootWindowInsets` is null until the first one, so a composition
    // that beats it takes the fallback. Configuration does not change when insets land, so nothing
    // would have recomposed this — the fallback would simply stay. The first dispatch moves this
    // key off zero, which re-reads the corners.
    val configuration = LocalConfiguration.current
    val systemBarsTop = WindowInsets.systemBars.getTop(density)

    return remember(view, density, configuration, systemBarsTop) {
        // All FOUR corners, and an ABSOLUTE shape to receive them. A display whose corners differ
        // is exactly the case the rotation key exists for, and copying the top-left onto the other
        // three would either clip visible pixels at rest or leave a square edge under the gesture.
        // `AbsoluteRoundedCornerShape` because `RoundedCorner` positions are PHYSICAL: a
        // layout-direction-aware shape would mirror them under RTL and put the wrong radius in the
        // wrong corner.
        //
        // The whole read sits behind ONE version check, and the positions are named only inside it:
        // `RoundedCorner.POSITION_*` are compile-time constants, so referencing them at a call site
        // outside the check inlines an API-31 field into an API-28 binary (lint `InlinedApi`).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.displayCorners(density)
        } else {
            AbsoluteRoundedCornerShape(AppDimension.Radius.big)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun View.displayCorners(density: Density): Shape {
    val insets = rootWindowInsets
    fun corner(position: Int): Dp = insets
        ?.getRoundedCorner(position)
        ?.radius
        ?.takeIf { it > 0 }
        ?.let { with(density) { it.toDp() } }
        ?: AppDimension.Radius.big
    return AbsoluteRoundedCornerShape(
        topLeft = corner(RoundedCorner.POSITION_TOP_LEFT),
        topRight = corner(RoundedCorner.POSITION_TOP_RIGHT),
        bottomRight = corner(RoundedCorner.POSITION_BOTTOM_RIGHT),
        bottomLeft = corner(RoundedCorner.POSITION_BOTTOM_LEFT),
    )
}
