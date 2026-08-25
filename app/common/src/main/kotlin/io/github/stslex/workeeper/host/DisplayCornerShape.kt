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
 * The shape the display's own corners are cut with, for the clip the host puts on its clipped
 * destinations. See architecture.md § "Navigation host and shared element transitions".
 */
@Composable
internal fun displayCornerShape(): Shape {
    val view = LocalView.current
    val density = LocalDensity.current
    val fallback: Shape = remember(density) { AbsoluteRoundedCornerShape(AppDimension.Radius.big) }

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return fallback

    var shape by remember(view, density) { mutableStateOf(view.displayCorners(density) ?: fallback) }

    // GUARD: refresh on the TREE's layout pass — insets, configuration and attach all reach it,
    // while this View's own layout does not (a 180° rotation leaves its bounds unchanged).
    // GUARD: do NOT use `OnApplyWindowInsetsListener` — it is single-listener and Compose owns it.
    DisposableEffect(view, density) {
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            view.displayCorners(density)?.let { corners -> shape = corners }
        }
        observer.addOnGlobalLayoutListener(listener)
        onDispose {
            // The captured observer can be dead (it is replaced on re-attach); use the live one.
            val current = if (observer.isAlive) observer else view.viewTreeObserver
            current.removeOnGlobalLayoutListener(listener)
        }
    }

    return shape
}

/**
 * Null while the window cannot answer — `rootWindowInsets` is null until attach.
 * GUARD: `RoundedCorner.POSITION_*` are compile-time constants — naming them outside a version
 * check inlines an API-31 field into an API-28 binary (lint `InlinedApi`).
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
