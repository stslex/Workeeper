// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme

/**
 * Fixed monochrome theme (D-A): no dynamic colour source, no watch-face adaptation, no light
 * branch. Every colour slot resolves to a [WearPalette] value so no default hue can reach the
 * composition through a component that reads the theme. Gate G2 scans this module's main
 * sources for the dynamic-theming entry point by name, comments included — do not name it here.
 */
@Composable
internal fun WearAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WearColorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(WearPalette.screen),
        ) {
            CompositionLocalProvider(LocalContentColor provides WearPalette.textPrimary) {
                content()
            }
        }
    }
}

/**
 * All 29 colour slots pinned to the ten-role palette. The secondary and tertiary families are
 * aliases of the primary mapping: the palette is monochrome, so a component that reaches for a
 * "different" tonal family must land on the same greys.
 */
internal val WearColorScheme = ColorScheme(
    primary = WearPalette.textPrimary,
    primaryDim = WearPalette.textSecondary,
    primaryContainer = WearPalette.card,
    onPrimary = WearPalette.onAccent,
    onPrimaryContainer = WearPalette.textPrimary,
    secondary = WearPalette.textSecondary,
    secondaryDim = WearPalette.textMuted,
    secondaryContainer = WearPalette.card,
    onSecondary = WearPalette.onAccent,
    onSecondaryContainer = WearPalette.textPrimary,
    tertiary = WearPalette.textSecondary,
    tertiaryDim = WearPalette.textMuted,
    tertiaryContainer = WearPalette.card,
    onTertiary = WearPalette.onAccent,
    onTertiaryContainer = WearPalette.textPrimary,
    surfaceContainerLow = WearPalette.cardInactive,
    surfaceContainer = WearPalette.card,
    surfaceContainerHigh = WearPalette.pillPending,
    onSurface = WearPalette.textPrimary,
    onSurfaceVariant = WearPalette.textSecondary,
    outline = WearPalette.stroke,
    outlineVariant = WearPalette.stroke,
    background = WearPalette.screen,
    onBackground = WearPalette.textPrimary,
    error = WearPalette.error,
    errorDim = WearPalette.error,
    errorContainer = WearPalette.card,
    onError = WearPalette.onAccent,
    onErrorContainer = WearPalette.error,
)
