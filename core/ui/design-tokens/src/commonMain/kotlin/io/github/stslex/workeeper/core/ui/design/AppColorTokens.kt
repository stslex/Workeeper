package io.github.stslex.workeeper.core.ui.design

const val DARK_BASE: Long = 0xFF0B0D0F
const val DARK_SEC: Long = 0xFF12161A
const val DARK_FIELD: Long = 0xFF171C21
const val DARK_SLAB: Long = 0xFF1E242A
const val DARK_RAISE: Long = 0xFF242B32
const val DARK_MAX: Long = 0xFFF1F5F9
const val DARK_BODY: Long = 0xFFB7C0CA
const val DARK_META: Long = 0xFF8B95A1
const val DARK_IDLE: Long = 0xFF8B95A1
const val DARK_HAIR: Long = 0x0DFFFFFF
/** `--grid`, dark: `rgba(255,255,255,.07)` — one alpha step above [DARK_HAIR]'s 5%. */
const val DARK_GRID: Long = 0x12FFFFFF
/** GUARD: equals [DARK_HAIR] by coincidence, not one token — light breaks the tie (6% vs 7%). */
const val DARK_DONEFILL: Long = 0x0DFFFFFF
/** Control outline, dark. See `AppColors.borderStrong` — `hair-s` lifted to clear 3:1. */
const val DARK_CONTROL_OUTLINE: Long = 0xFF627587
const val DARK_MOLTEN: Long = 0xFFF0A22E
const val DARK_MOLTEN_BACKGROUND: Long = 0x17F0A22E
const val DARK_MOLTEN_BORDER: Long = 0x6BF0A22E

/**
 * Destructive text in dark: #DF714B, not the spec's #C4574A, which clears 4.5:1 on no dark
 * surface in this palette. Light needs no such adjustment.
 */
const val DARK_RUST: Long = 0xFFDF714B
const val DARK_RUST_WASH: Long = 0x1FDF714B

const val LIGHT_BASE: Long = 0xFFF6F7F9
const val LIGHT_SEC: Long = 0xFFEFF1F4
const val LIGHT_FIELD: Long = 0xFFE9ECF0
const val LIGHT_SLAB: Long = 0xFFFFFFFF
const val LIGHT_RAISE: Long = 0xFFDFE3E8
const val LIGHT_MAX: Long = 0xFF0D1114
const val LIGHT_BODY: Long = 0xFF2C333A

/** See `AppColors.textTertiary` — #596169, deliberately not the mockup's #69727C. */
const val LIGHT_META: Long = 0xFF596169
const val LIGHT_IDLE: Long = 0xFF7C858F
const val LIGHT_HAIR: Long = 0x120D1114
/** `--grid`, light: `rgba(13,17,20,.09)` — one alpha step above [LIGHT_HAIR]'s 7%. */
const val LIGHT_GRID: Long = 0x170D1114
/** `--donefill`, light: `rgba(13,17,20,.06)`. */
const val LIGHT_DONEFILL: Long = 0x0F0D1114
/** Control outline, light. See `AppColors.borderStrong` — `hair-s` darkened to clear 3:1. */
const val LIGHT_CONTROL_OUTLINE: Long = 0xFF748396
/**
 * Molten as text in light: #BE3E0C, nudged off the spec's #C2410C, which measures 4.325:1 on
 * the PR card's real backdrop — the molten wash over the page.
 */
const val LIGHT_MOLTEN: Long = 0xFFBE3E0C
const val LIGHT_MOLTEN_SOLID: Long = 0xFFF97316
const val LIGHT_MOLTEN_BACKGROUND: Long = 0x1CF97316
const val LIGHT_MOLTEN_BORDER: Long = 0x57C2410C
const val LIGHT_RUST: Long = 0xFFB03B2E
const val LIGHT_RUST_WASH: Long = 0x1FB03B2E
