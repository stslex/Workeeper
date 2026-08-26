// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable

/**
 * Continuity transit spec for position, size and placement: `motion.base` with `motion.out`.
 * Alpha interpolation takes [continuityAlphaSpec] instead. See the v3 redesign spec §26.
 */
fun <T> continuityPositionalSpec(motion: AppMotion): TweenSpec<T> = tween(
    durationMillis = motion.base,
    easing = motion.out,
)

/** The positional half of the class, at a call site. See [continuityPositionalSpec]. */
@Composable
fun <T> continuityPositionalSpec(): TweenSpec<T> = continuityPositionalSpec(AppUi.motion)

/**
 * Continuity transit spec for alpha: `motion.base` with `motion.linear`, so perceived duration
 * equals declared duration. See the v3 redesign spec §26.
 */
fun <T> continuityAlphaSpec(motion: AppMotion): TweenSpec<T> = tween(
    durationMillis = motion.base,
    easing = motion.linear,
)

/** The alpha half of the class, at a call site. See [continuityAlphaSpec]. */
@Composable
fun <T> continuityAlphaSpec(): TweenSpec<T> = continuityAlphaSpec(AppUi.motion)
