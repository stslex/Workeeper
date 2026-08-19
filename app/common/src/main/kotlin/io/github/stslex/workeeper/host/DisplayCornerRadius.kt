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
 * The shape the display's own corners are cut with, for the clip every screen carries.
 *
 * ## Why a screen is clipped at all, and why always rather than during the gesture
 *
 * The predictive-back preview shrinks the leaving screen into a card, and a card with square
 * corners is the one thing about it that reads as wrong — the platform's own preview is rounded.
 * There is no corner-radius channel in a `ContentTransform` (`TransitionData` is exhaustively fade
 * / slide / changeSize / scale / veil / hold), so the rounding cannot come from the transition.
 *
 * It does not need to. **The platform does not round the window for the gesture either — the window
 * is always that shape, and you only ever notice once it shrinks away from the display's edge.**
 * So the clip is unconditional, on every screen, at rest as well as in motion, and the gesture gets
 * its rounded card for free with no signal to plumb and no per-frame work.
 *
 * At rest the clip is invisible by construction: `android:windowBackground` is the literal value of
 * `AppColors.surfaceTier0` and `App.kt`'s root `Box` paints `colorScheme.background`, which is the
 * same colour every graph paints. Whatever the corners cut away, what shows through is that colour.
 * On hardware whose display is physically rounded — which is where this radius comes from — the
 * cut pixels are outside the panel anyway.
 *
 * ## Where the number comes from
 *
 * `RoundedCorner` is API 31+; below that the platform reports nothing and
 * [AppDimension.Radius.big] stands in. A device that reports a zero or absent radius (square panel,
 * most emulators) also takes the fallback, which is deliberate: the point of the clip is the
 * shrunken card, and a square card is the defect being fixed.
 *
 * Keyed on the two things that can change the answer — the configuration and the window insets —
 * rather than on the View alone, and read per corner rather than once: see the body.
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
