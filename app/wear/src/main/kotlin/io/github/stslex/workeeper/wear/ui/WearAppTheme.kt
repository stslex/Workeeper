// SPDX-License-Identifier: GPL-3.0-only
@file:Suppress("MagicNumber") // Stable ARGB values for the explicit light Wear palette.

package io.github.stslex.workeeper.wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.material3.LocalContentColor
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.dynamicColorScheme

@Composable
internal fun WearAppTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        val base = dynamicColorScheme(LocalContext.current) ?: MaterialTheme.colorScheme
        val colors = if (isSystemInDarkTheme()) {
            base
        } else {
            base.copy(
                background = LIGHT_BACKGROUND,
                onBackground = Color.Black,
                surfaceContainerLow = LIGHT_SURFACE,
                surfaceContainer = LIGHT_SURFACE,
                surfaceContainerHigh = LIGHT_SURFACE_HIGH,
                onSurface = Color.Black,
                onSurfaceVariant = Color.DarkGray,
                outline = Color.DarkGray,
                outlineVariant = Color.Gray,
            )
        }
        MaterialTheme(colorScheme = colors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.background),
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
                    content()
                }
            }
        }
    }
}

private val LIGHT_BACKGROUND = Color(0xfffbf8ff)
private val LIGHT_SURFACE = Color(0xfff3edf7)
private val LIGHT_SURFACE_HIGH = Color(0xffece6f0)
