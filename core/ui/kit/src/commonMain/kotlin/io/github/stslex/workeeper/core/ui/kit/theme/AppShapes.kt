package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Immutable
import io.github.stslex.workeeper.core.ui.design.AppDesignDimensions

@Immutable
data class AppShapes(
    val small: RoundedCornerShape,
    val medium: RoundedCornerShape,
    val large: RoundedCornerShape,
)

fun provideAppShapes(): AppShapes = AppShapes(
    small = RoundedCornerShape(AppDesignDimensions.Shape.small),
    medium = RoundedCornerShape(AppDesignDimensions.Shape.medium),
    large = RoundedCornerShape(AppDesignDimensions.Shape.large),
)

fun AppShapes.toM3Shapes(): Shapes = Shapes(
    small = small,
    medium = medium,
    large = large,
)
