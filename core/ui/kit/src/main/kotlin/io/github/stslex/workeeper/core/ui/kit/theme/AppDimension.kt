package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Suppress("unused", "TooManyFunctions")
object AppDimension {

    object Padding {

        val smallest = 2.dp
        val small = 4.dp
        val medium = 8.dp
        val big = 16.dp
        val large = 32.dp
        val largest = 64.dp
    }

    object Radius {

        val smallest = 4.dp
        val small = 8.dp
        val medium = 16.dp
        val big = 32.dp
        val large = 64.dp
        val largest = 128.dp
    }

    object Elevation {

        val smallest = 2.dp
        val small = 4.dp
        val medium = 8.dp
    }

    object Border {

        val small = 1.dp
        val medium = 2.dp
        val large = 3.dp
    }

    object Icon {

        val small = 16.dp
        val medium = 24.dp
        val big = 32.dp
        val large = 48.dp
        val huge = 72.dp
    }

    object Button {

        val smallest = 12.dp
        val small = 36.dp
        val medium = 60.dp
        val big = 72.dp
    }

    object BottomNavBar {

        val height = 72.dp
    }

    object Space {

        val none: Dp = 0.dp
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val sm: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 24.dp
        val xxl: Dp = 32.dp
        val xxxl: Dp = 48.dp
    }

    val screenEdge: Dp = Space.lg
    val sectionSpacing: Dp = Space.xl
    val listItemPadding: Dp = Space.sm
    val cardPadding: Dp = Space.md
    val componentPadding: Dp = Space.sm

    val iconXs: Dp = 14.dp
    val iconSm: Dp = 18.dp
    val iconMd: Dp = 24.dp
    val iconLg: Dp = 32.dp
    val iconXl: Dp = 48.dp

    val heightXs: Dp = 32.dp
    val heightSm: Dp = 40.dp
    val heightMd: Dp = 48.dp
    val heightLg: Dp = 56.dp
    val heightXl: Dp = 64.dp

    val borderHairline: Dp = 0.5.dp
    val phoneFrame: Dp = 24.dp
}
