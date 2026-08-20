// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.host

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.annotation.RequiresApi
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
    val configuration = LocalConfiguration.current
    val fallback: Shape = remember(density) { AbsoluteRoundedCornerShape(AppDimension.Radius.big) }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback

    // The platform's answer, bridged into Compose state rather than inferred from a proxy.
    //
    // GUARD: do NOT key this on an inset VALUE. `rootWindowInsets` is null until the window is
    // attached, and the dispatch that first makes it answerable need not move any edge Compose
    // exposes — bars hidden, or an all-zero dispatch — so an inset value is a proxy with a hole in
    // it, and so is any other key cheap enough to be worth using. This asks the platform directly
    // and asks again next frame until it can answer, which is a question with no proxy in it. It
    // stops on the first answer, so the cost is a null check per frame during attach and nothing
    // afterwards.
    //
    // `configuration` is a key rather than a subscription here: `MainActivity` declares
    // `configChanges`, so a rotation replaces neither the activity nor the View, and re-keying is
    // what re-asks — the physical top-left after a rotation can be a different corner.
    return produceState(fallback, view, density, configuration) {
        while (true) {
            val resolved = view.displayCorners(density)
            if (resolved != null) {
                value = resolved
                break
            }
            withFrameNanos { }
        }
    }.value
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
