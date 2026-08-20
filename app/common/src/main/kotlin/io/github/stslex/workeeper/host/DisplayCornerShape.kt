// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Shape
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
    val fallback: Shape = remember(density) { AbsoluteRoundedCornerShape(AppDimension.Radius.big) }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback

    var shape by remember(view, density) { mutableStateOf(view.displayCorners(density) ?: fallback) }

    // The platform's own signal, bridged into Compose state — a LAYOUT of this View.
    //
    // GUARD: neither an inset VALUE nor "the first non-null answer" is enough, and both were tried.
    // An inset value is a proxy: `rootWindowInsets` is null until attach, and the dispatch that
    // first makes it answerable need not move any edge Compose exposes. And a first-non-null answer
    // is not necessarily a CURRENT one: `MainActivity` declares `configChanges`, so a rotation
    // neither recreates the activity nor replaces this View, and at the moment the configuration
    // changes `rootWindowInsets` still holds the previous orientation's corners — read then, a
    // display with asymmetric corners keeps the old shape until something else disturbs it.
    //
    // A layout has neither problem. `ViewRootImpl` dispatches insets during the same traversal,
    // BEFORE layout, so by the time this fires `rootWindowInsets` is current; and a rotation
    // relayouts this View by construction, since its bounds swap. Attach lays out too, which is
    // what resolves the initial null. Assigning an equal shape is a no-op —
    // `AbsoluteRoundedCornerShape` implements `equals` — so a layout that changes nothing costs no
    // recomposition.
    //
    // NOT an `OnApplyWindowInsetsListener`, which would be the more direct read of "observe the
    // dispatch": that hook is single-listener per View and `AndroidComposeView` owns it, so taking
    // it would break Compose's own insets to fix a corner radius. `addOnLayoutChangeListener` is
    // additive and displaces nothing.
    DisposableEffect(view, density) {
        val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            view.displayCorners(density)?.let { corners -> shape = corners }
        }
        view.addOnLayoutChangeListener(listener)
        onDispose { view.removeOnLayoutChangeListener(listener) }
    }

    return shape
}

/**
 * Null while the window cannot answer — `rootWindowInsets` is null until attach — which is the
 * signal the caller loops on. A non-null return is an answer even when every radius is zero: the
 * display is square, and each corner takes [AppDimension.Radius.big] on its own.
 *
 * GUARD: `RoundedCorner.POSITION_*` are compile-time constants, so naming them at a call site
 * outside a version check inlines an API-31 field into an API-28 binary (lint `InlinedApi`). They
 * are named only here, and this is reached only from inside the check.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun View.displayCorners(density: Density): Shape? {
    val insets = rootWindowInsets ?: return null
    fun corner(position: Int): Dp = insets
        .getRoundedCorner(position)
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
