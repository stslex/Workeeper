package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * The v3 palette: fifteen tokens onto twenty-three v2-named slots. v3 is achromatic — emphasis
 * comes from a higher contrast tier, never a hue. See the v3 redesign spec §2.
 */

/** Set-type chips. Neutral by decision — the letter carries meaning, only failure is chromatic. */
@Immutable
data class SetTypeColors(
    val warmupBackground: Color,
    val warmupForeground: Color,
    val workBackground: Color,
    val workForeground: Color,
    val failureBackground: Color,
    val failureForeground: Color,
    val dropBackground: Color,
    val dropForeground: Color,
)

@Immutable
data class StatusColors(
    val success: Color,
    val warning: Color,
    /** Destructive text. See [SetTypeColors.failureForeground] — same colour, same reason. */
    val error: Color,
    /** Dead slot — no readers; kept as public API and excluded by name in the contrast gate. */
    val info: Color,
)

/**
 * Molten — the personal-record accent and the transient wow state, nothing else. Four slots:
 * a tint dark enough to read as text cannot fill a pill, so light darkens where dark lightens.
 */
@Immutable
data class MoltenAccent(
    /** Molten as **text**. Meets 4.5:1 on every surface it is declared against. */
    val text: Color,
    /** Molten as an **opaque fill** — the PR pill, the completed-PR set mark. Never text. */
    val solid: Color,
    /** Content on [solid]. Near-black in both themes; the theme's own `onAccent` fails in light. */
    val onSolid: Color,
    /** Molten as a **translucent wash** behind PR content. Composited before measurement. */
    val background: Color,
    /** Molten as a **border**. Decorative reinforcement; the PR is never signalled by it alone. */
    val border: Color,
)

/** Personal-record colours. Every slot is a view onto [MoltenAccent]. */
@Immutable
data class RecordColors(
    val background: Color,
    val border: Color,
    val solid: Color,
    val onSolid: Color,
    val textPrimary: Color,
    val textSecondary: Color,
)

@Suppress("LongParameterList")
@Immutable
data class AppColors(
    /** v3 `max` — the maximum-contrast neutral, not a hue. Primary fill, links, selected icons. */
    val accent: Color,
    /** v3 `base`. What sits on [accent] when [accent] is a fill. */
    val onAccent: Color,
    /** v3 `raise`. Selected-state container fill (nav indicator, selected chip, toggle). */
    val accentTintedBackground: Color,
    /** v3 `max`. Content on [accentTintedBackground]; also a fill in two components. */
    val accentTintedForeground: Color,
    /** v3 `base` — the page. */
    val surfaceTier0: Color,
    /** v3 `sec` — the card, row and sheet tier. The busiest surface. */
    val surfaceTier1: Color,
    /** v3 `slab` — floating above the page: dialogs, dropdowns. White in light. */
    val surfaceTier2: Color,
    /** v3 `field` — recessed panels: input fills, tooltip, progress track. */
    val surfaceTier3: Color,
    /** v3 `raise` — inert chips, tiles and disabled fills. */
    val surfaceTier4: Color,
    /**
     * v3 `--donefill` — the translucent completed-content wash (finished ordinal chip, done set
     * field). Its contrast pairs declare an explicit `over` backdrop.
     */
    val donefill: Color,
    /** v3 `max`. */
    val textPrimary: Color,
    /** v3 `body`. */
    val textSecondary: Color,
    /**
     * v3 `meta`. Light ships #596169, deliberately not the mockup's #69727C, which fails 4.5:1
     * on three of the five light surfaces.
     */
    val textTertiary: Color,
    /**
     * v3 `dim`, deliberately aliased onto `meta`: a `dim` value that passes AA is perceptually
     * indistinguishable from `meta`. See the v3 redesign spec §2.5 before reinstating a tier.
     */
    val textDim: Color,
    /** v3 `idle`. WCAG-exempt — every reader is an inactive control; never scored by the gate. */
    val textDisabled: Color,
    /** v3 `hair` — translucent. Decorative separator; takes no contrast threshold (spec §3.1). */
    val borderSubtle: Color,
    /**
     * v3 `--grid` — chart gridlines, one alpha step above `hair` so retuning either cannot drag
     * the other. Decorative: no contrast threshold (spec §3.1).
     */
    val grid: Color,
    /**
     * Control outline for an enabled, unfocused control (text-field border, skipped rail
     * segment), so WCAG 1.4.11 applies and it owes 3:1. Decorative strokes read [borderSubtle].
     */
    val borderDefault: Color,
    /**
     * Control outline, like [borderDefault] — the ring that *is* the control when it is off.
     * Not `hair-s`: that measures 1.12–1.52:1 against the 3:1 owed. See the v3 spec §2.7.
     */
    val borderStrong: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val setType: SetTypeColors,
    val status: StatusColors,
    val molten: MoltenAccent,
    val record: RecordColors,
    val isDark: Boolean,
)

private const val DARK_BASE: Long = 0xFF0B0D0F
private const val DARK_SEC: Long = 0xFF12161A
private const val DARK_FIELD: Long = 0xFF171C21
private const val DARK_SLAB: Long = 0xFF1E242A
private const val DARK_RAISE: Long = 0xFF242B32
private const val DARK_MAX: Long = 0xFFF1F5F9
private const val DARK_BODY: Long = 0xFFB7C0CA
private const val DARK_META: Long = 0xFF8B95A1
private const val DARK_IDLE: Long = 0xFF8B95A1
private const val DARK_HAIR: Long = 0x0DFFFFFF
/** `--grid`, dark: `rgba(255,255,255,.07)` — one alpha step above [DARK_HAIR]'s 5%. */
private const val DARK_GRID: Long = 0x12FFFFFF
/** GUARD: equals [DARK_HAIR] by coincidence, not one token — light breaks the tie (6% vs 7%). */
private const val DARK_DONEFILL: Long = 0x0DFFFFFF
/** Control outline, dark. See [AppColors.borderStrong] — `hair-s` lifted to clear 3:1. */
private const val DARK_CONTROL_OUTLINE: Long = 0xFF627587
private const val DARK_MOLTEN: Long = 0xFFF0A22E
private const val DARK_MOLTEN_BACKGROUND: Long = 0x17F0A22E
private const val DARK_MOLTEN_BORDER: Long = 0x6BF0A22E

/**
 * Destructive text in dark: #DF714B, not the spec's #C4574A, which clears 4.5:1 on no dark
 * surface in this palette. Light needs no such adjustment.
 */
private const val DARK_RUST: Long = 0xFFDF714B
private const val DARK_RUST_WASH: Long = 0x1FDF714B

private const val LIGHT_BASE: Long = 0xFFF6F7F9
private const val LIGHT_SEC: Long = 0xFFEFF1F4
private const val LIGHT_FIELD: Long = 0xFFE9ECF0
private const val LIGHT_SLAB: Long = 0xFFFFFFFF
private const val LIGHT_RAISE: Long = 0xFFDFE3E8
private const val LIGHT_MAX: Long = 0xFF0D1114
private const val LIGHT_BODY: Long = 0xFF2C333A

/** See [AppColors.textTertiary] — #596169, deliberately not the mockup's #69727C. */
private const val LIGHT_META: Long = 0xFF596169
private const val LIGHT_IDLE: Long = 0xFF7C858F
private const val LIGHT_HAIR: Long = 0x120D1114
/** `--grid`, light: `rgba(13,17,20,.09)` — one alpha step above [LIGHT_HAIR]'s 7%. */
private const val LIGHT_GRID: Long = 0x170D1114
/** `--donefill`, light: `rgba(13,17,20,.06)`. */
private const val LIGHT_DONEFILL: Long = 0x0F0D1114
/** Control outline, light. See [AppColors.borderStrong] — `hair-s` darkened to clear 3:1. */
private const val LIGHT_CONTROL_OUTLINE: Long = 0xFF748396
/**
 * Molten as text in light: #BE3E0C, nudged off the spec's #C2410C, which measures 4.325:1 on
 * the PR card's real backdrop — the molten wash over the page.
 */
private const val LIGHT_MOLTEN: Long = 0xFFBE3E0C
private const val LIGHT_MOLTEN_SOLID: Long = 0xFFF97316
private const val LIGHT_MOLTEN_BACKGROUND: Long = 0x1CF97316
private const val LIGHT_MOLTEN_BORDER: Long = 0x57C2410C
private const val LIGHT_RUST: Long = 0xFFB03B2E
private const val LIGHT_RUST_WASH: Long = 0x1FB03B2E

fun provideDarkAppColors(): AppColors = AppColors(
    accent = Color(DARK_MAX),
    onAccent = Color(DARK_BASE),
    accentTintedBackground = Color(DARK_RAISE),
    accentTintedForeground = Color(DARK_MAX),
    surfaceTier0 = Color(DARK_BASE),
    surfaceTier1 = Color(DARK_SEC),
    surfaceTier2 = Color(DARK_SLAB),
    surfaceTier3 = Color(DARK_FIELD),
    surfaceTier4 = Color(DARK_RAISE),
    donefill = Color(DARK_DONEFILL),
    textPrimary = Color(DARK_MAX),
    textSecondary = Color(DARK_BODY),
    textTertiary = Color(DARK_META),
    // v3 `dim`, merged into `meta`. See AppColors.textDim.
    textDim = Color(DARK_META),
    textDisabled = Color(DARK_IDLE),
    borderSubtle = Color(DARK_HAIR),
    grid = Color(DARK_GRID),
    borderDefault = Color(DARK_CONTROL_OUTLINE),
    borderStrong = Color(DARK_CONTROL_OUTLINE),
    inverseSurface = Color(DARK_MAX),
    inverseOnSurface = Color(DARK_BASE),
    setType = SetTypeColors(
        warmupBackground = Color(DARK_RAISE),
        warmupForeground = Color(DARK_BODY),
        workBackground = Color(DARK_RAISE),
        workForeground = Color(DARK_MAX),
        failureBackground = Color(DARK_RUST_WASH),
        failureForeground = Color(DARK_RUST),
        dropBackground = Color(DARK_RAISE),
        dropForeground = Color(DARK_META),
    ),
    status = StatusColors(
        success = Color(DARK_MAX),
        warning = Color(DARK_BODY),
        error = Color(DARK_RUST),
        info = Color(DARK_META),
    ),
    molten = MoltenAccent(
        text = Color(DARK_MOLTEN),
        solid = Color(DARK_MOLTEN),
        onSolid = Color(DARK_BASE),
        background = Color(DARK_MOLTEN_BACKGROUND),
        border = Color(DARK_MOLTEN_BORDER),
    ),
    record = RecordColors(
        background = Color(DARK_MOLTEN_BACKGROUND),
        border = Color(DARK_MOLTEN_BORDER),
        solid = Color(DARK_MOLTEN),
        onSolid = Color(DARK_BASE),
        textPrimary = Color(DARK_MOLTEN),
        textSecondary = Color(DARK_MOLTEN),
    ),
    isDark = true,
)

fun provideLightAppColors(): AppColors = AppColors(
    accent = Color(LIGHT_MAX),
    onAccent = Color(LIGHT_BASE),
    accentTintedBackground = Color(LIGHT_RAISE),
    accentTintedForeground = Color(LIGHT_MAX),
    surfaceTier0 = Color(LIGHT_BASE),
    surfaceTier1 = Color(LIGHT_SEC),
    surfaceTier2 = Color(LIGHT_SLAB),
    surfaceTier3 = Color(LIGHT_FIELD),
    surfaceTier4 = Color(LIGHT_RAISE),
    donefill = Color(LIGHT_DONEFILL),
    textPrimary = Color(LIGHT_MAX),
    textSecondary = Color(LIGHT_BODY),
    textTertiary = Color(LIGHT_META),
    // v3 `dim`, merged into `meta`. See AppColors.textDim.
    textDim = Color(LIGHT_META),
    textDisabled = Color(LIGHT_IDLE),
    borderSubtle = Color(LIGHT_HAIR),
    grid = Color(LIGHT_GRID),
    borderDefault = Color(LIGHT_CONTROL_OUTLINE),
    borderStrong = Color(LIGHT_CONTROL_OUTLINE),
    inverseSurface = Color(LIGHT_MAX),
    inverseOnSurface = Color(LIGHT_BASE),
    setType = SetTypeColors(
        warmupBackground = Color(LIGHT_RAISE),
        warmupForeground = Color(LIGHT_BODY),
        workBackground = Color(LIGHT_RAISE),
        workForeground = Color(LIGHT_MAX),
        failureBackground = Color(LIGHT_RUST_WASH),
        failureForeground = Color(LIGHT_RUST),
        dropBackground = Color(LIGHT_RAISE),
        dropForeground = Color(LIGHT_META),
    ),
    status = StatusColors(
        success = Color(LIGHT_MAX),
        warning = Color(LIGHT_BODY),
        error = Color(LIGHT_RUST),
        info = Color(LIGHT_META),
    ),
    molten = MoltenAccent(
        text = Color(LIGHT_MOLTEN),
        solid = Color(LIGHT_MOLTEN_SOLID),
        onSolid = Color(LIGHT_MAX),
        background = Color(LIGHT_MOLTEN_BACKGROUND),
        border = Color(LIGHT_MOLTEN_BORDER),
    ),
    record = RecordColors(
        background = Color(LIGHT_MOLTEN_BACKGROUND),
        border = Color(LIGHT_MOLTEN_BORDER),
        solid = Color(LIGHT_MOLTEN_SOLID),
        onSolid = Color(LIGHT_MAX),
        textPrimary = Color(LIGHT_MOLTEN),
        textSecondary = Color(LIGHT_MOLTEN),
    ),
    isDark = false,
)
