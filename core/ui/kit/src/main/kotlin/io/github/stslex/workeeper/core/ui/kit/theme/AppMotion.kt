package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.runtime.Immutable

@Immutable
data class AppMotion(
    val instant: Int,
    val fast: Int,
    val normal: Int,
    val slow: Int,
    val deliberate: Int,
    val standard: Easing,
    val emphasized: Easing,
    val decelerate: Easing,
    val accelerate: Easing,
)

@Suppress("MagicNumber")
fun provideAppMotion(): AppMotion = AppMotion(
    instant = 100,
    fast = 200,
    normal = 300,
    slow = 400,
    deliberate = 600,
    standard = FastOutSlowInEasing,
    emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    decelerate = LinearOutSlowInEasing,
    accelerate = FastOutLinearInEasing,
)
