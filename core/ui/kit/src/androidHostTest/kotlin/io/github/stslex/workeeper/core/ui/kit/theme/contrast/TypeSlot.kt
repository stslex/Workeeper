// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

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

    /** Not text: an icon, a control boundary, a focus ring, a filled indicator. */
    UI_COMPONENT(LARGE_TEXT, "WCAG 1.4.11 non-text contrast"),
}
