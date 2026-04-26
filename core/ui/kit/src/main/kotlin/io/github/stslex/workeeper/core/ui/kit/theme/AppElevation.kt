package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class AppElevation(
    val level0: Color,
    val level1: Color,
    val level2: Color,
    val level3: Color,
    val level4: Color,
    val level5: Color,
    val shadow: Dp,
    val borderHairline: Dp,
)

fun provideAppElevation(colors: AppColors): AppElevation = AppElevation(
    level0 = colors.surfaceTier0,
    level1 = colors.surfaceTier1,
    level2 = colors.surfaceTier2,
    level3 = colors.surfaceTier3,
    level4 = colors.surfaceTier4,
    level5 = colors.surfaceTier4,
    shadow = 0.dp,
    borderHairline = 0.5.dp,
)
