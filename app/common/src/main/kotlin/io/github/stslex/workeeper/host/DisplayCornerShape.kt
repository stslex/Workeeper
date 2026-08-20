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

    // Two SUBSCRIPTIONS, and no cache. Reading these is what makes this composable recompose when
    // a rotation arrives (`MainActivity` declares `configChanges`, so neither the activity nor the
    // View is replaced) or when the window insets are dispatched (`rootWindowInsets` is null until
    // the first dispatch, so an earlier composition resolves to the fallback).
    //
    // GUARD: do NOT reintroduce a `remember` here. Two staleness defects came out of caching this,
    // and the second survived a fix for the first: keyed on the View it missed rotation, and keyed
    // additionally on ONE inset edge it missed a first dispatch that left that edge at zero — bars
    // hidden, or a landscape layout whose bars are on the sides. Any key narrow enough to be cheap
    // is narrow enough to have such a hole. Read fresh, the value is correct on every composition
    // that happens for any reason, and the two reads above are what make one happen.
    LocalConfiguration.current
    WindowInsets.systemBars.getTop(density)

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        view.displayCorners(density)
    } else {
        AbsoluteRoundedCornerShape(AppDimension.Radius.big)
    }
}

/**
 * GUARD: `RoundedCorner.POSITION_*` are compile-time constants, so naming them at a call site
 * outside a version check inlines an API-31 field into an API-28 binary (lint `InlinedApi`). They
 * are named only here, and this is reached only from inside the check.
 */
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
