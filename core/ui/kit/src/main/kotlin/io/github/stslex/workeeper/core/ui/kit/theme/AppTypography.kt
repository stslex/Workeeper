package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.R

/**
 * One rung of the v3 type scale, in one family.
 *
 * The scale has six steps and nothing between them. Anything that needs a size not on this
 * list is either a mistake or a seventh step, and a seventh step is a design decision, not
 * a call site's decision.
 */
@Immutable
data class AppTypeStyles(
    /** 34sp — hero numerals and the live timer. */
    val display: TextStyle,
    /** 26sp — screen titles. */
    val title: TextStyle,
    /** 19sp — section and dialog titles. */
    val section: TextStyle,
    /** 15sp — body text and row titles. */
    val body: TextStyle,
    /** 12.5sp — supporting text, units, meta. */
    val meta: TextStyle,
    /** 11sp — chips and the smallest captions. */
    val caption: TextStyle,
)

/**
 * Three families, six sizes.
 *
 * The fifteen Material 3 style names below are **derived**, not stored: they are aliases onto
 * [text], because `toM3Typography` feeds `MaterialTheme` and every stock M3 component reads
 * through it. Fifteen names collapse onto six sizes; the alias block *is* the mapping, so
 * there is exactly one place to read it.
 */
@Immutable
data class AppTypography(
    /** IBM Plex Sans. Everything that is words. */
    val textFontFamily: FontFamily,
    /**
     * Archivo Expanded. **Digits and `: . , - + / %` only — never a translatable string.**
     * See [archivoExpandedFontFamily] for why this is a hard constraint and not a preference.
     */
    val numericFontFamily: FontFamily,
    /** IBM Plex Mono. Units and meta, inline beside body text. */
    val monoFontFamily: FontFamily,
    val text: AppTypeStyles,
    val numeric: AppTypeStyles,
    val mono: AppTypeStyles,
) {

    // ---- Material 3 aliases: fifteen names onto six sizes -------------------------------
    //
    // Derived from measured usage, not from the M3 spec's nominal sizes. Production usage
    // counts at the time of the remap are in the PR body; the semantic level each name
    // actually occupies is what decided its rung.
    //
    // Stored, not `get()` — computed once per theme instance rather than on every read.

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

    /** Screen titles — `DetailTopbar`, `PastSessionScreen`, `PastSessionHeader`. */
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
}

/**
 * Text family for every worded slot. Bundled rather than fetched from the GMS
 * downloadable-font provider, so the first frame is never set in a fallback face and the app
 * renders identically on devices without Play Services. Covers the full Cyrillic range the
 * `values-ru` strings use.
 *
 * Only 400/500 ship — those are the weights the slots consume. `FontWeight.Bold` still
 * resolves, but by synthesis; add a real 700 file before relying on it.
 */
private val plexSansFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
)

/**
 * Display family for numerals and the timer, and for nothing else.
 *
 * ## O2 — a hard constraint, not a preference
 *
 * **Archivo Expanded has zero Cyrillic coverage.** Not "partial", not "missing a few" —
 * none of the 55 Cyrillic characters the shipped `values-ru` corpus uses, nor `« » · × — … →`.
 * A translatable string routed through this family renders as tofu boxes in Russian, or
 * silently resolves to whatever the system fallback chain offers, which is not this typeface.
 *
 * So: **digits and the `: . , - + / %` separators only. Never a `stringResource`.** A number
 * formatted into a string is still a string — `"20 повт."` is a violation even though it
 * starts with digits.
 *
 * This is enforced mechanically, because a comment does not survive inattention:
 *  - `NumericFontFamilyOnLocalizedTextRule` in `:lint-rules` fails detekt on a `Text` that
 *    combines this family with a `stringResource` argument;
 *  - `CyrillicTextGoldenTest` renders real `values-ru` strings, so a family swap that
 *    produces tofu moves pixels and fails the visual gate.
 *
 * ## O1 — tabular figures
 *
 * Archivo's digits are proportional (`0` is 769 units wide, `1` is 683), so a ticking timer
 * visibly wobbles as digits change. Every [numeric] style therefore sets
 * `fontFeatureSettings = "tnum"`. `TnumCanaryGoldenTest` is the mechanical detector.
 * See `core/ui/kit/licenses/README.md`.
 */
private val archivoExpandedFontFamily = FontFamily(
    Font(R.font.archivo_expanded_bold, FontWeight.Bold),
)

/**
 * Monospace family for units and meta text. Shares its vertical metrics exactly with
 * [plexSansFontFamily], so it stays on the same baseline when set inline beside body text.
 * Tabular by default — every digit is 600 units — and it covers Cyrillic in full, so unlike
 * [archivoExpandedFontFamily] it is safe for localized text.
 */
private val plexMonoFontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/**
 * The six steps. Sizes are the v3 scale; line heights are ~1.3x, rounded to whole sp.
 * `const` so the scale is one list of named numbers rather than positional lookups.
 */
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

private fun buildStyles(
    family: FontFamily,
    weight: FontWeight,
    fontFeatureSettings: String? = null,
): AppTypeStyles {
    fun step(sizeSp: Float, lineHeightSp: Float, letterSpacing: TextUnit) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = sizeSp.sp,
        lineHeight = lineHeightSp.sp,
        fontFeatureSettings = fontFeatureSettings,
        letterSpacing = letterSpacing,
    )
    val default = TextStyle.Default.letterSpacing
    return AppTypeStyles(
        display = step(SIZE_DISPLAY_SP, LINE_DISPLAY_SP, default),
        title = step(SIZE_TITLE_SP, LINE_TITLE_SP, default),
        section = step(SIZE_SECTION_SP, LINE_SECTION_SP, default),
        body = step(SIZE_BODY_SP, LINE_BODY_SP, default),
        meta = step(SIZE_META_SP, LINE_META_SP, default),
        caption = step(SIZE_CAPTION_SP, LINE_CAPTION_SP, CAPTION_LETTER_SPACING),
    )
}

fun provideAppTypography(): AppTypography = AppTypography(
    textFontFamily = plexSansFontFamily,
    numericFontFamily = archivoExpandedFontFamily,
    monoFontFamily = plexMonoFontFamily,
    text = buildStyles(plexSansFontFamily, FontWeight.Normal),
    // Archivo ships one weight (700) and its digits are proportional, hence tnum on every rung.
    numeric = buildStyles(archivoExpandedFontFamily, FontWeight.Bold, TABULAR_FIGURES),
    mono = buildStyles(plexMonoFontFamily, FontWeight.Normal),
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
