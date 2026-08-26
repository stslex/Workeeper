// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.ui.graphics.Color

/**
 * The same colour with zero alpha — the correct fade-out endpoint, since `Color.Transparent` is
 * transparent BLACK and the Oklab mid-frames darken. `FadeToTransparentRule` enforces it.
 */
fun Color.fadedOut(): Color = copy(alpha = 0f)
