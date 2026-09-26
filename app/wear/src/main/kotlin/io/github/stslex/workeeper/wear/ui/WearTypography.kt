package io.github.stslex.workeeper.wear.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material3.Shapes
import androidx.wear.compose.material3.Typography
import io.github.stslex.workeeper.core.ui.design.AppDesignDimensions
import io.github.stslex.workeeper.core.ui.design.workeeperMonoFontFamily
import io.github.stslex.workeeper.core.ui.design.workeeperNumericFontFamily
import io.github.stslex.workeeper.core.ui.design.workeeperTextFontFamily

@Composable
internal fun rememberWearTypography(): Typography {
    val text = workeeperTextFontFamily()
    val numeric = workeeperNumericFontFamily()
    val mono = workeeperMonoFontFamily()
    return remember(text, numeric, mono) {
        Typography(
            defaultFontFamily = text,
            arcLarge = CurvedTextStyle(wearTextStyle(mono, META_SIZE, META_LINE)),
            arcMedium = CurvedTextStyle(wearTextStyle(mono, CAPTION_SIZE, CAPTION_LINE)),
            arcSmall = CurvedTextStyle(wearTextStyle(mono, CAPTION_SIZE, CAPTION_LINE)),
            displayLarge = wearTextStyle(text, DISPLAY_SIZE, DISPLAY_LINE, FontWeight.SemiBold),
            displayMedium = wearTextStyle(text, TITLE_SIZE, TITLE_LINE, FontWeight.SemiBold),
            displaySmall = wearTextStyle(text, SECTION_SIZE, SECTION_LINE, FontWeight.SemiBold),
            titleLarge = wearTextStyle(text, SECTION_SIZE, SECTION_LINE, FontWeight.SemiBold),
            titleMedium = wearTextStyle(text, BODY_LARGE_SIZE, BODY_LARGE_LINE, FontWeight.Medium),
            titleSmall = wearTextStyle(text, BODY_SIZE, COMPACT_LINE, FontWeight.Medium),
            labelLarge = wearTextStyle(text, BODY_SIZE, BODY_LINE, FontWeight.Medium),
            labelMedium = wearTextStyle(text, META_SIZE, META_LINE, FontWeight.Medium),
            labelSmall = wearTextStyle(text, CAPTION_SIZE, CAPTION_LINE, FontWeight.Medium),
            bodyLarge = wearTextStyle(text, BODY_LARGE_SIZE, BODY_LARGE_LINE),
            bodyMedium = wearTextStyle(text, BODY_SIZE, BODY_LINE),
            bodySmall = wearTextStyle(text, META_SIZE, META_LINE),
            bodyExtraSmall = wearTextStyle(mono, CAPTION_SIZE, CAPTION_LINE),
            numeralExtraLarge = wearNumeralStyle(numeric, HERO_SIZE, HERO_LINE),
            numeralLarge = wearNumeralStyle(numeric, DISPLAY_LINE, HERO_SIZE),
            numeralMedium = wearNumeralStyle(numeric, DISPLAY_SIZE, DISPLAY_LINE),
            numeralSmall = wearNumeralStyle(numeric, TITLE_SIZE, TITLE_LINE),
            numeralExtraSmall = wearNumeralStyle(numeric, BODY_LARGE_SIZE, BODY_LARGE_LINE),
        )
    }
}

private fun wearTextStyle(
    family: FontFamily,
    size: Float,
    line: Float,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = family,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    hyphens = Hyphens.None,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun wearNumeralStyle(family: FontFamily, size: Float, line: Float) =
    wearTextStyle(family, size, line, FontWeight.Bold).copy(fontFeatureSettings = "tnum")

internal val WearShapes = Shapes(
    extraSmall = RoundedCornerShape(AppDesignDimensions.Shape.small),
    small = RoundedCornerShape(AppDesignDimensions.Shape.small),
    medium = RoundedCornerShape(AppDesignDimensions.Shape.medium),
    large = RoundedCornerShape(AppDesignDimensions.Shape.large),
    extraLarge = RoundedCornerShape(AppDesignDimensions.Shape.large),
)

private const val HERO_SIZE = 48f
private const val HERO_LINE = 54f
private const val DISPLAY_SIZE = 34f
private const val DISPLAY_LINE = 42f
private const val TITLE_SIZE = 26f
private const val TITLE_LINE = 32f
private const val SECTION_SIZE = 18f
private const val SECTION_LINE = 22f
private const val BODY_LARGE_SIZE = 16f
private const val BODY_LARGE_LINE = 21f
private const val BODY_SIZE = 14f
private const val BODY_LINE = 19f
private const val COMPACT_LINE = 16.5f
private const val META_SIZE = 12.5f
private const val META_LINE = 17f
private const val CAPTION_SIZE = 11f
private const val CAPTION_LINE = 15f
