package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

val Dp.toPx: Float
    @Composable
    @ReadOnlyComposable
    get() = with(LocalDensity.current) {
        toPx()
    }

val Dp.roundToPx: Int
    @Composable
    @ReadOnlyComposable
    get() = with(LocalDensity.current) {
        roundToPx()
    }

val Float.toDp: Dp
    @Composable
    @ReadOnlyComposable
    get() = with(LocalDensity.current) {
        toDp()
    }

val Int.toDp: Dp
    @Composable
    @ReadOnlyComposable
    get() = with(LocalDensity.current) {
        toDp()
    }