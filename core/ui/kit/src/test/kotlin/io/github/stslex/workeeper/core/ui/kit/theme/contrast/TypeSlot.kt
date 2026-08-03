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
 * The per-rung derivation — WCAG's 18pt / 14pt-bold boundaries converted to 24sp / 18.66sp, and
 * the resulting table across all six rungs and both weight columns — is §3.4, "Where the boundary
 * comes from, rung by rung".
 *
 * **"Bold" there means 700, and only 700.** `text.section` is set in SemiBold (600), which does
 * not reach the 14pt-bold boundary, so the section rung keeps [SECTION]'s 4.5:1. That is why
 * [SECTION] and [SECTION_BOLD] are separate slots rather than one — 19sp is the only rung where
 * weight changes the answer, and collapsing them would either over-constrain the numerals or wave
 * through a 19sp regular label at 3:1.
 *
 * **If a heading rung ever moves to a real 700, §3.4's table and every triple naming the affected
 * slot must be revisited together.** That change *loosens* a threshold, which is the direction a
 * gate cannot catch for you.
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
