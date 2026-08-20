// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.ViewTreeObserver
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

    // GUARD: refresh on the TREE's layout pass, and on nothing narrower.
    //
    // Not an inset value: `rootWindowInsets` is null until attach, and the dispatch that first
    // makes it answerable need not move any edge Compose exposes. Not the configuration either:
    // `MainActivity` declares `configChanges`, so neither the activity nor this View is replaced,
    // and at the instant the configuration changes the insets still describe the previous
    // orientation. And not this View's OWN layout: `OnLayoutChangeListener` can stay silent when
    // the bounds are unchanged, which is exactly a 180-degree rotation — same width and height,
    // different physical corners.
    //
    // A global layout has none of those holes. `ViewRootImpl` dispatches insets during the same
    // traversal BEFORE layout, so the answer is current when this fires; a configuration change
    // lays the tree out whether or not any bounds move; and attach lays out too, which resolves
    // the initial null. In a Compose app the signal is also rare — Compose lays out inside one
    // View, so tree-level passes are attach, configuration and window changes rather than content
    // ones. Assigning an equal shape is a no-op (`AbsoluteRoundedCornerShape` implements `equals`),
    // so a pass that changes nothing costs no recomposition.
    //
    // GUARD: do NOT reach for `OnApplyWindowInsetsListener` instead. It is single-listener per
    // View and `AndroidComposeView` owns it, so taking it breaks Compose's own insets. A
    // `ViewTreeObserver` listener is additive and displaces nothing.
    DisposableEffect(view, density) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            view.displayCorners(density)?.let { corners -> shape = corners }
        }
        observer.addOnGlobalLayoutListener(listener)
        onDispose {
            // The observer captured at registration can be dead by now (it is replaced when the
            // View is re-attached); the live one is the View's current observer.
            val current = if (observer.isAlive) observer else view.viewTreeObserver
            current.removeOnGlobalLayoutListener(listener)
        }
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
