// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

/**
 * What a colour pair is *for*, and therefore which WCAG threshold it must meet.
 *
 * The threshold is not a property of the pair. `molten` on `slab` measures 7.39 in dark and
 * 5.17 in light either way — but the same ratio is a pass at 26sp and a fail at 12.5sp. One
 * ratio, two verdicts. So every declared triple names the slot, and the slot carries the
 * number.
 *
 * ## Where the boundary comes from
 *
 * WCAG 2.1 §1.4.3 defines *large-scale text* as **18pt, or 14pt bold**. Android's `sp` is a
 * CSS-pixel-like unit, and WCAG's own understanding document fixes the conversion at
 * 1pt = 1.333px, so:
 *
 *  - 18pt  → **24sp** regular
 *  - 14pt  → **18.66sp** bold
 *
 * Against the six-step v3 scale (34 / 26 / 19 / 15 / 12.5 / 11 sp) that lands as:
 *
 * | rung    | size   | regular          | bold (`numeric`)      |
 * |---------|--------|------------------|-----------------------|
 * | display | 34sp   | large → 3:1      | large → 3:1           |
 * | title   | 26sp   | large → 3:1      | large → 3:1           |
 * | section | 19sp   | **normal → 4.5** | ≥18.66 bold → 3:1     |
 * | body    | 15sp   | normal → 4.5:1   | normal → 4.5:1        |
 * | meta    | 12.5sp | normal → 4.5:1   | normal → 4.5:1        |
 * | caption | 11sp   | normal → 4.5:1   | normal → 4.5:1        |
 *
 * The 19sp row is the only one where weight changes the answer, and it is why [SECTION] and
 * [SECTION_BOLD] are separate slots rather than one. `AppTypography.numeric` is built at
 * `FontWeight.Bold`, so `numeric.section` is genuinely large-scale text and `text.section` is
 * genuinely not. Collapsing them would either over-constrain the numerals or wave through a
 * 19sp regular label at 3:1.
 *
 * `title` at 26sp is the anchor the v3 spec quotes ("3:1 under 26sp, 4.5:1 under 15sp"); both
 * quoted numbers fall out of the 24sp boundary above rather than being independent decisions.
 */
/** WCAG 1.4.3 AA, normal-scale text. */
internal const val NORMAL_TEXT: Double = 4.5

/** WCAG 1.4.3 AA large-scale text; also WCAG 1.4.11 non-text contrast. */
internal const val LARGE_TEXT: Double = 3.0

internal enum class TypeSlot(val threshold: Double, val why: String) {

    /** 34sp — hero numerals, the live timer. Large-scale text. */
    DISPLAY(LARGE_TEXT, "34sp ≥ 24sp — WCAG 1.4.3 large-scale text"),

    /** 26sp — screen titles. Large-scale text. */
    TITLE(LARGE_TEXT, "26sp ≥ 24sp — WCAG 1.4.3 large-scale text"),

    /** 19sp regular — section and dialog titles. Below 24sp, so normal text. */
    SECTION(NORMAL_TEXT, "19sp regular < 24sp — WCAG 1.4.3 normal text"),

    /** 19sp bold (`numeric`/`mono` at FontWeight.Bold) — above the 14pt-bold boundary. */
    SECTION_BOLD(LARGE_TEXT, "19sp bold ≥ 18.66sp — WCAG 1.4.3 large-scale bold text"),

    /** 15sp — body text, row titles, button labels. */
    BODY(NORMAL_TEXT, "15sp < 24sp — WCAG 1.4.3 normal text"),

    /** 12.5sp — supporting text, units, meta. */
    META(NORMAL_TEXT, "12.5sp < 24sp — WCAG 1.4.3 normal text"),

    /** 11sp — chips, smallest captions. */
    CAPTION(NORMAL_TEXT, "11sp < 24sp — WCAG 1.4.3 normal text"),

    /**
     * Not text: an icon, a control boundary, a focus ring, a filled indicator — anything whose
     * shape carries meaning the user must be able to perceive.
     */
    UI_COMPONENT(LARGE_TEXT, "WCAG 1.4.11 non-text contrast"),
}
