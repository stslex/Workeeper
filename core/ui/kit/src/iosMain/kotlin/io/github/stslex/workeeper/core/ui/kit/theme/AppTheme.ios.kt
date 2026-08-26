package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Composable

/** No-op: the iOS host owns native window chrome; the theme must not reach for UIKit here. */
@Composable
internal actual fun PlatformWindowChrome(darkTheme: Boolean) = Unit
