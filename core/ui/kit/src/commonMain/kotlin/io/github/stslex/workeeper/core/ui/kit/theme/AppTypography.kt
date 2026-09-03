package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.archivo_bold_wdth116
import io.github.stslex.workeeper.core.ui.kit.resources.ibm_plex_mono_medium
import io.github.stslex.workeeper.core.ui.kit.resources.ibm_plex_mono_regular
import io.github.stslex.workeeper.core.ui.kit.resources.ibm_plex_mono_semibold
import io.github.stslex.workeeper.core.ui.kit.resources.ibm_plex_sans_medium
import io.github.stslex.workeeper.core.ui.kit.resources.ibm_plex_sans_regular
import io.github.stslex.workeeper.core.ui.kit.resources.ibm_plex_sans_semibold
import org.jetbrains.compose.resources.Font

/**
 * One rung of the v3 type scale, in one family. Six steps and nothing between them — a seventh
 * step is a design decision, not a call site's.
 */
@Immutable
data class AppTypeStyles(
    /** 34sp — hero numerals and the live timer. Heading weight. */
    val display: TextStyle,
    /** 26sp — screen titles. Heading weight. */
    val title: TextStyle,
    /** 19sp — section and dialog titles. Heading weight. */
    val section: TextStyle,
    /** 15sp — body text and row titles. */
    val body: TextStyle,
    /** 12.5sp — supporting text, units, meta. */
    val meta: TextStyle,
    /** 11sp — chips and the smallest captions. */
    val caption: TextStyle,
)

/**
 * Three families, six sizes. The fifteen Material 3 names below are aliases onto [text], since
 * `toM3Typography` feeds `MaterialTheme`. M3 1.5's `*Emphasized` twins stay unmapped.
 */
@Immutable
data class AppTypography(
    /** IBM Plex Sans. Everything that is words. */
    val textFontFamily: FontFamily,
    /** Archivo `wdth 116 / wght 700`. Digits and separators only — never a translatable string. */
    val numericFontFamily: FontFamily,
    /** IBM Plex Mono. Units and meta, inline beside body text. */
    val monoFontFamily: FontFamily,
    val text: AppTypeStyles,
    val numeric: AppTypeStyles,
    val mono: AppTypeStyles,
) {

    // Material 3 aliases: fifteen names onto six sizes, stored rather than `get()`. Seven sit on
    // a heading rung and carry HEADING_WEIGHT.

    /** Unused in production; exists so `Typography()` is complete. */
    val displayLarge: TextStyle = text.display

    /** Unused in production; exists so `Typography()` is complete. */
    val displayMedium: TextStyle = text.display

    /** Unused in production; exists so `Typography()` is complete. */
    val displaySmall: TextStyle = text.title

    /** Unused in production; exists so `Typography()` is complete. */
    val headlineLarge: TextStyle = text.title

    /** Unused in production; exists so `Typography()` is complete. */
    val headlineMedium: TextStyle = text.title

    /** Screen titles — `PlanEditorScreen`, `PastSessionScreen`, `PastSessionHeader`. */
    val headlineSmall: TextStyle = text.title

    /** Dialog titles — `AppConfirmationDialog`, `AppBlockedArchiveDialog`, and friends. */
    val titleLarge: TextStyle = text.section

    /** Row and card titles — exercise names, PR labels, the training-name header. */
    val titleMedium: TextStyle = text.body.copy(fontWeight = FontWeight.Medium)

    /** Banner titles. */
    val titleSmall: TextStyle = text.body.copy(fontWeight = FontWeight.Medium)

    /** Primary list-item text — picker entries, frequency labels. */
    val bodyLarge: TextStyle = text.body

    /** The dominant body style, by a wide margin. */
    val bodyMedium: TextStyle = text.body

    /** Supporting and secondary text — warnings, field labels, relative dates. */
    val bodySmall: TextStyle = text.meta

    /** Dialog action labels. */
    val labelLarge: TextStyle = text.body.copy(fontWeight = FontWeight.Medium)

    /** Bottom-bar and segmented-control labels. */
    val labelMedium: TextStyle = text.meta.copy(fontWeight = FontWeight.Medium)

    /** Chips and the smallest captions. */
    val labelSmall: TextStyle = text.caption

    // Named slots that are not M3 names.

    /**
     * The live session timer — an alias of `numeric.display`, so it carries `tnum` by
     * construction. Registered with `NumericFontFamilyOnLocalizedTextRule`; the canary reads it.
     */
    val timer: TextStyle = numeric.display

    /**
     * The large data value — `numeric.title`, 26sp bold, so its threshold is 3:1 rather than
     * 4.5:1. Registered with `NumericFontFamilyOnLocalizedTextRule` like [timer].
     */
    val dataValue: TextStyle = numeric.title
}

/**
 * Text family for every worded slot. Bundled rather than fetched, so the first frame is never
 * set in a fallback face; 400/500/600 ship and Bold still resolves by synthesis off the 600.
 * Composable because Compose-resource `Font(...)` is composable — see [rememberAppTypography].
 */
@Composable
private fun plexSansFontFamily(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(Res.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(Res.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

/**
 * Display family for numerals only — Archivo `wdth 116 / wght 700`; `licenses/README.md` has
 * its provenance. GUARD: zero Cyrillic coverage — digits and `: . , - + / %`, never a string.
 */
@Composable
private fun archivoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.archivo_bold_wdth116, FontWeight.Bold),
)

/**
 * Monospace family for units and meta text. Shares vertical metrics with [plexSansFontFamily]
 * so it co-baselines inline; tabular by default and covers Cyrillic in full.
 */
@Composable
private fun plexMonoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(Res.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(Res.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** The six steps. Sizes are the v3 scale; line heights are ~1.3x, rounded to whole sp. */
private const val SIZE_DISPLAY_SP = 34.0f
private const val SIZE_TITLE_SP = 26.0f
private const val SIZE_SECTION_SP = 19.0f
private const val SIZE_BODY_SP = 15.0f
private const val SIZE_META_SP = 12.5f
private const val SIZE_CAPTION_SP = 11.0f

private const val LINE_DISPLAY_SP = 42.0f
private const val LINE_TITLE_SP = 32.0f
private const val LINE_SECTION_SP = 26.0f
private const val LINE_BODY_SP = 21.0f
private const val LINE_META_SP = 18.0f
private const val LINE_CAPTION_SP = 15.0f

/** Tabular figures. O1: without this a ticking timer re-flows on every second. */
private const val TABULAR_FIGURES = "tnum"

/** The caption rung is small enough to need opening up. */
private val CAPTION_LETTER_SPACING = 0.5.sp

/**
 * Tracking on the screen-title rung: −0.39sp, the mockups' `-.015em` at 26sp. Attached to the
 * rung, not the role — a title moved to another rung must take its tracking with it (§4.1).
 */
private const val TITLE_LETTER_SPACING_SP = -0.39f

private val TITLE_LETTER_SPACING = TITLE_LETTER_SPACING_SP.sp

/**
 * Per-rung tracking for one family: platform default everywhere except [caption], whose 0.5sp
 * predates this and applies to all three families.
 */
private data class Tracking(
    val display: TextUnit = TextStyle.Default.letterSpacing,
    val title: TextUnit = TextStyle.Default.letterSpacing,
    val section: TextUnit = TextStyle.Default.letterSpacing,
    val body: TextUnit = TextStyle.Default.letterSpacing,
    val meta: TextUnit = TextStyle.Default.letterSpacing,
    val caption: TextUnit = CAPTION_LETTER_SPACING,
)

/**
 * The weight every heading rung is set in — a real bundled 600 cut, not synthesis. Not
 * WCAG-bold (1.4.3 means 700), so the contrast contract is unchanged by it.
 */
private val HEADING_WEIGHT = FontWeight.SemiBold

/**
 * Builds the six rungs of one family. Weight is split heading-vs-rest because the mockups split
 * it that way; a one-weight family passes the same value for both.
 */
private fun buildStyles(
    family: FontFamily,
    bodyWeight: FontWeight,
    headingWeight: FontWeight = bodyWeight,
    fontFeatureSettings: String? = null,
    tracking: Tracking = Tracking(),
): AppTypeStyles {
    fun step(
        sizeSp: Float,
        lineHeightSp: Float,
        weight: FontWeight,
        letterSpacing: TextUnit,
    ) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = sizeSp.sp,
        lineHeight = lineHeightSp.sp,
        fontFeatureSettings = fontFeatureSettings,
        letterSpacing = letterSpacing,
    )
    return AppTypeStyles(
        display = step(SIZE_DISPLAY_SP, LINE_DISPLAY_SP, headingWeight, tracking.display),
        title = step(SIZE_TITLE_SP, LINE_TITLE_SP, headingWeight, tracking.title),
        section = step(SIZE_SECTION_SP, LINE_SECTION_SP, headingWeight, tracking.section),
        body = step(SIZE_BODY_SP, LINE_BODY_SP, bodyWeight, tracking.body),
        meta = step(SIZE_META_SP, LINE_META_SP, bodyWeight, tracking.meta),
        caption = step(SIZE_CAPTION_SP, LINE_CAPTION_SP, bodyWeight, tracking.caption),
    )
}

/**
 * The resource-backed typography: families load from the seven bundled TTFs inside composition,
 * then the pure [provideAppTypography] builds the scale. GUARD: never call a composable from a
 * `remember` calculation block — the family reads happen here, before the `remember`.
 */
@Composable
fun rememberAppTypography(): AppTypography {
    val textFontFamily = plexSansFontFamily()
    val numericFontFamily = archivoFontFamily()
    val monoFontFamily = plexMonoFontFamily()
    return remember(textFontFamily, numericFontFamily, monoFontFamily) {
        provideAppTypography(
            textFontFamily = textFontFamily,
            numericFontFamily = numericFontFamily,
            monoFontFamily = monoFontFamily,
        )
    }
}

/** Pure type-scale construction over the given families; the contract test drives this directly. */
fun provideAppTypography(
    textFontFamily: FontFamily,
    numericFontFamily: FontFamily,
    monoFontFamily: FontFamily,
): AppTypography = AppTypography(
    textFontFamily = textFontFamily,
    numericFontFamily = numericFontFamily,
    monoFontFamily = monoFontFamily,
    text = buildStyles(
        family = textFontFamily,
        bodyWeight = FontWeight.Normal,
        headingWeight = HEADING_WEIGHT,
        tracking = Tracking(title = TITLE_LETTER_SPACING),
    ),
    // Archivo ships one weight (700) and its digits are proportional, hence tnum on every rung.
    numeric = buildStyles(
        family = numericFontFamily,
        bodyWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    // No mono selector in either mockup is a heading, so mono carries no heading weight.
    mono = buildStyles(family = monoFontFamily, bodyWeight = FontWeight.Normal),
)

fun AppTypography.toM3Typography(): Typography = Typography(
    displayLarge = displayLarge,
    displayMedium = displayMedium,
    displaySmall = displaySmall,
    headlineLarge = headlineLarge,
    headlineMedium = headlineMedium,
    headlineSmall = headlineSmall,
    titleLarge = titleLarge,
    titleMedium = titleMedium,
    titleSmall = titleSmall,
    bodyLarge = bodyLarge,
    bodyMedium = bodyMedium,
    bodySmall = bodySmall,
    labelLarge = labelLarge,
    labelMedium = labelMedium,
    labelSmall = labelSmall,
)
