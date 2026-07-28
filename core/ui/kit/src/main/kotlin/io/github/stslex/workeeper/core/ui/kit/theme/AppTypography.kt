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
     * Archivo, cut at `wdth 116 / wght 700` — the width the mockups draw numerals at.
     * **Digits and `: . , - + / %` only — never a translatable string.**
     * See [archivoFontFamily] for why this is a hard constraint and not a preference.
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
    //
    // Seven of the fifteen sit on a heading rung and therefore carry HEADING_WEIGHT:
    // displayLarge/Medium (34), displaySmall + headlineLarge/Medium/Small (26), titleLarge
    // (19). The other eight are body/meta/caption and are untouched by it — including the four
    // that .copy() to Medium, whose 500 overrides the rung's weight either way.

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

    /**
     * Screen titles — `DetailTopbar`, `PlanEditorScreen`, `PastSessionScreen`,
     * `PastSessionHeader`. All four, not three: `DetailTopbar` reaches every detail screen in
     * the app, so this alias is wider than its call-site count suggests.
     */
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
 * `values-ru` strings use, at every bundled weight.
 *
 * 400/500/600 ship — those are the weights the slots consume, and they are the three the
 * mockups request (`IBM+Plex+Sans:wght@400;500;600`). `FontWeight.Bold` still resolves, but by
 * synthesis; add a real 700 file before relying on it.
 */
private val plexSansFontFamily = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

/**
 * Display family for numerals and the timer, and for nothing else.
 *
 * ## The cut: `wdth 116, wght 700`
 *
 * Both mockups set every numeral through `font-variation-settings`, never through a published
 * named instance: `.data-l` at `"wdth" 115`, `.data-s` at `"wdth" 116`, `.data-hero` at
 * `"wdth" 122` — three widths, `"wght" 700` throughout. The bundled file is the **116** cut,
 * the one the session timer and the record value are drawn at.
 *
 * "Expanded" survives as the *role* word the spec uses for wide numerals; it is not this
 * file's width. Archivo's published `Expanded` static is the `wdth 125` edge of the axis, and
 * the previous bundle used it because 125 was reachable as a published artifact. 116 is not:
 * it has no `fvar` named instance (all nine sit at `wdth 100`) and no `STAT` axis value, so
 * `fonttools` refuses to name it and the name table is written by hand. The file is therefore
 * derived, and the provenance argument moves from "hash matches a published URL" to
 * "derivation is reproducible" — `core/ui/kit/licenses/README.md` carries the input hash, the
 * tool version and the command, and the file self-describes as `Archivo wdth116 Bold` at
 * `usWeightClass 700` rather than inheriting the variable font's default-instance names.
 *
 * ## O2 — a hard constraint, not a preference
 *
 * **Archivo has zero Cyrillic coverage.** Not "partial", not "missing a few" — none of the 55
 * Cyrillic characters the shipped `values-ru` corpus uses. (It *does* cover `« » · × — … → •`,
 * which earlier copies of this note claimed it did not; the gap is Cyrillic letters, and the
 * distinction matters because a bullet-prefixed timer needs no text split.) A translatable
 * string routed through this family renders as tofu boxes in Russian, or silently resolves to
 * whatever the system fallback chain offers, which is not this typeface.
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
 * Archivo's digits are proportional at every width (at 116: `0` is 706 units, `1` is 652), so
 * a ticking timer visibly wobbles as digits change. Every [numeric] style therefore sets
 * `fontFeatureSettings = "tnum"`. That feature makes **20 substitutions** — the ten lining
 * digits to their `.tf` forms and the ten oldstyle digits to `.tosf` — and every one of them
 * advances 700. `TnumCanaryGoldenTest` is the mechanical detector.
 * See `core/ui/kit/licenses/README.md`.
 */
private val archivoFontFamily = FontFamily(
    Font(R.font.archivo_bold_wdth116, FontWeight.Bold),
)

/**
 * Monospace family for units and meta text. Shares its vertical metrics exactly with
 * [plexSansFontFamily], so it stays on the same baseline when set inline beside body text.
 * Tabular by default — every digit is 600 units — and it covers Cyrillic in full, so unlike
 * [archivoFontFamily] it is safe for localized text.
 *
 * The 600 cut is bundled but **no slot consumes it yet**. It is here because the mockups set
 * exactly one mono selector at 600 — `.prtag`, the record tag — and that component
 * (`PersonalRecordBadge`) still reads a *text*-family caption. Wiring it is the record-row
 * work, not this file's. Until then the weight is reachable only by asking a mono style for
 * [FontWeight.SemiBold] explicitly; no [mono] rung does.
 */
private val plexMonoFontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
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

/**
 * The weight every heading rung is set in — [AppTypeStyles.display], [AppTypeStyles.title],
 * [AppTypeStyles.section].
 *
 * Every `h1`/`h2`/`h3`/`h4` in both mockups declares `font-weight:600` and nothing else does at
 * heading size: `.topbar h1` (20px), `.shead h2` (22px), `.exhead h2` (24px), `.sheet h3`
 * (19px), `.empty h4` (18px). Those five land on the 26 and 19 rungs, which is why both move.
 * The 34 rung moves with them: it carries no sans heading in either mockup, and a scale whose
 * largest step is *lighter* than the step below it is broken rather than unspecified.
 *
 * 600 is a **real bundled cut**, not synthesis — see [plexSansFontFamily]. Before this weight
 * shipped these rungs rendered at 400.
 *
 * Not WCAG-bold. §1.4.3's large-text boundary is "18pt, or 14pt bold", and bold there means
 * 700; 600 does not reach it. So `text.section` at 19sp stays `TypeSlot.SECTION` (4.5:1) and
 * does not become `SECTION_BOLD` (3:1). The contrast contract is unchanged by this weight.
 */
private val HEADING_WEIGHT = FontWeight.SemiBold

/**
 * Builds the six rungs of one family.
 *
 * Weight is split heading-vs-rest rather than being one value per family, because the mockups
 * split it that way: the three heading rungs carry [headingWeight] and the three text rungs
 * carry [bodyWeight]. A family with one weight — Archivo, which ships only 700 — passes the
 * same value for both and the split costs it nothing.
 */
private fun buildStyles(
    family: FontFamily,
    bodyWeight: FontWeight,
    headingWeight: FontWeight = bodyWeight,
    fontFeatureSettings: String? = null,
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
    val default = TextStyle.Default.letterSpacing
    return AppTypeStyles(
        display = step(SIZE_DISPLAY_SP, LINE_DISPLAY_SP, headingWeight, default),
        title = step(SIZE_TITLE_SP, LINE_TITLE_SP, headingWeight, default),
        section = step(SIZE_SECTION_SP, LINE_SECTION_SP, headingWeight, default),
        body = step(SIZE_BODY_SP, LINE_BODY_SP, bodyWeight, default),
        meta = step(SIZE_META_SP, LINE_META_SP, bodyWeight, default),
        caption = step(SIZE_CAPTION_SP, LINE_CAPTION_SP, bodyWeight, CAPTION_LETTER_SPACING),
    )
}

fun provideAppTypography(): AppTypography = AppTypography(
    textFontFamily = plexSansFontFamily,
    numericFontFamily = archivoFontFamily,
    monoFontFamily = plexMonoFontFamily,
    text = buildStyles(
        family = plexSansFontFamily,
        bodyWeight = FontWeight.Normal,
        headingWeight = HEADING_WEIGHT,
    ),
    // Archivo ships one weight (700) and its digits are proportional, hence tnum on every rung.
    numeric = buildStyles(
        family = archivoFontFamily,
        bodyWeight = FontWeight.Bold,
        fontFeatureSettings = TABULAR_FIGURES,
    ),
    // No mono selector in either mockup is a heading, so mono has no heading weight to carry.
    mono = buildStyles(family = plexMonoFontFamily, bodyWeight = FontWeight.Normal),
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
