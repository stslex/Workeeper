package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

@Immutable
data class AppShapes(
    val small: RoundedCornerShape,
    val medium: RoundedCornerShape,
    val large: RoundedCornerShape,
)

fun provideAppShapes(): AppShapes = AppShapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
)

fun AppShapes.toM3Shapes(): Shapes = Shapes(
    small = small,
    medium = medium,
    large = large,
)
