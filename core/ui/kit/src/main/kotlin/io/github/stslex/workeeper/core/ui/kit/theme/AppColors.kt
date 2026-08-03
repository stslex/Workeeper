package io.github.stslex.workeeper.core.ui.kit.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/*
 * ============================================================================================
 * The v3 palette.
 * ============================================================================================
 *
 * Fifteen design tokens; twenty-three slots. The slot *names* are v2's and are deliberately
 * left alone — renaming `surfaceTier1` to `sec` across every feature module would be a
 * thousand-line mechanical diff with no pixel behind it, and this PR's contract is that only
 * colours move. Each slot's KDoc names the v3 token it now carries.
 *
 * ## The one thing to understand before editing this file
 *
 * **v3 is achromatic.** There is no brand hue. Emphasis is carried by contrast, not colour:
 * the primary button is `max` filled with `base` text (mockup `.btn`), the progress fill is
 * `max`, the "on" switch is `max`. Exactly two chromatic roles survive, and both are earned:
 *
 *  - [MoltenAccent] — the personal-record accent and the transient wow state. Nothing else.
 *  - [StatusColors.error] / [SetTypeColors.failureForeground] — destructive intent.
 *
 * If you are reaching for a colour to make something stand out, the answer is a higher
 * contrast tier, not a hue.
 *
 * ## Token → slot map
 *
 * | v3 token       | slot                                        |
 * |----------------|---------------------------------------------|
 * | base           | surfaceTier0, onAccent                      |
 * | sec            | surfaceTier1                                |
 * | field          | surfaceTier3                                |
 * | slab           | surfaceTier2                                |
 * | raise          | surfaceTier4, accentTintedBackground, chips |
 * | max            | textPrimary, accent, accentTintedForeground |
 * | body           | textSecondary                               |
 * | meta           | textTertiary, **textDim** — see below       |
 * | dim            | textDim (aliased onto meta) — see below     |
 * | idle           | textDisabled                                |
 * | hair           | borderSubtle — decorative, no threshold     |
 * | grid           | grid — chart gridlines, decorative          |
 * | hair-s lifted  | borderDefault, borderStrong — control       |
 * |                | outlines; enabled, so each owes 3:1         |
 * | hair-s         | (no slot — see below)                       |
 * | molten ×4      | record.{textPrimary,solid,background,border}|
 * | rust           | status.error, setType.failure*              |
 *
 * `dim` now has a slot, [AppColors.textDim], and that slot is an **alias of `meta`**. The whole
 * measurement lives on the slot's KDoc; read it before "restoring" a fifth tier here. Note that
 * an earlier revision of this header carried the two `dim` values in the wrong order — the
 * mockups draw dark `#6B7078` and light `#98A0A9`, which is the opposite of spec revision 3
 * §2.1/§2.2.
 *
 * One token ends up with no slot at all.
 *
 * `hair-s` (#2B333B / #D2D7DD) has no slot because both slots that would have taken it are
 * **enabled control outlines**, which owe 3:1 — and it delivers 1.12–1.52:1 here. The measured
 * case, and the lift that replaced it, are §2.7. If a genuinely decorative *solid* rule is ever
 * needed, `hair-s` is the value to bring back; today `borderSubtle` covers every decorative
 * stroke in the app except the chart's gridlines, which carry `--grid` ([AppColors.grid]).
 */

/**
 * Set-type chips. **Neutral by decision, not by omission.**
 *
 * v2 encoded the four set types as four hues (amber / teal / red / violet). v3 supplies no
 * such family, and inventing one would put a second amber next to [MoltenAccent] — the exact
 * confusion molten's "not a general accent" rule exists to prevent.
 *
 * The chip already carries a letter — `W`, `·`, `F`, `D` (`AppSetTypeChip`) — so the meaning
 * never rested on hue alone, which is also what WCAG 1.4.1 asks for. Three of the four are
 * therefore neutral tiers separated by *text* weight, and only `failure` stays chromatic,
 * because destructive intent is the one meaning that must survive a glance.
 */
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
    /**
     * **Proposed for deletion — not deleted here.**
     *
     * Zero readers: not one production site, not one test, not one `@Preview`, not one mention
     * in `documentation/`. The only occurrences of the identifier in the repository are its
     * declaration and its two assignments in this file. It has no v3 token either, so it is
     * dead in both directions.
     *
     * Left in place because removing it is a public-API change to the design system and this
     * PR's contract is colours only. The gate excludes it by name with that reason, so it
     * cannot quietly acquire a reader without someone revisiting this comment.
     */
    val info: Color,
)

/**
 * **Molten — a four-part role, not a colour.**
 *
 * One hex cannot do this job. A tint dark enough to read as text is too dark to fill a pill;
 * a fill bright enough to read as a fill is too bright to sit as text on a light card. So
 * molten is four slots, and — this is the part that gets "fixed" back by mistake — **light
 * darkens where dark lightens**:
 *
 * |          | dark                  | light                 |
 * |----------|-----------------------|-----------------------|
 * | [text]   | #F0A22E (lightened)   | #C2410C (darkened)    |
 * | [solid]  | #F0A22E               | #F97316               |
 * | [background] | 9% #F0A22E        | 11% #F97316           |
 * | [border] | 42% #F0A22E           | 34% #C2410C           |
 *
 * [text] and [solid] are the same hex in dark and deliberately different in light: on a white
 * card, #F97316 is a fine *fill* (2.80:1 against slab — irrelevant, nothing reads it as text)
 * and an unusable *label* (the same 2.80:1, now a WCAG failure). Splitting them is the whole
 * point of the role.
 *
 * **Molten appears in exactly two places: the personal-record accent, and the transient wow
 * state.** It is not a general accent, it is not "the warm one", and it is not available for
 * a new screen that wants some colour. v3 is achromatic; molten is the earned exception.
 */
@Immutable
data class MoltenAccent(
    /** Molten as **text**. Meets 4.5:1 on every surface it is declared against. */
    val text: Color,
    /** Molten as an **opaque fill** — the PR pill, the completed-PR set mark. Never text. */
    val solid: Color,
    /**
     * What is legible **on** [solid]. Near-black in *both* themes, which is the one place this
     * role does not mirror: [solid] is a mid-bright orange either way, so the content on it
     * does not flip. Using the theme's own `onAccent` here fails in light — `base` on #F97316
     * is 2.61:1 — which is exactly the trap a "solid fill" slot without a content slot sets.
     */
    val onSolid: Color,
    /** Molten as a **translucent wash** behind PR content. Composited before measurement. */
    val background: Color,
    /** Molten as a **border**. Decorative reinforcement; the PR is never signalled by it alone. */
    val border: Color,
)

/**
 * Personal-record colours. Every slot is a view onto [MoltenAccent] — `record` is the concrete
 * consumer, `molten` is the role.
 */
@Immutable
data class RecordColors(
    val background: Color,
    val border: Color,
    /** Opaque fill for the PR badge pill. Reads [MoltenAccent.solid]. */
    val solid: Color,
    /** Content on [solid]. Reads [MoltenAccent.onSolid]. */
    val onSolid: Color,
    val textPrimary: Color,
    val textSecondary: Color,
)

@Suppress("LongParameterList")
@Immutable
data class AppColors(
    /**
     * v3 `max`. **Not a hue** — the maximum-contrast neutral. Fills the primary button and the
     * progress bar, tints selected icons, draws links and the live timer. See the file KDoc:
     * v3 carries emphasis on contrast, so the "accent" is simply the strongest text colour.
     */
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
     * v3 `--donefill` — the **completed-content wash**: a translucent film that marks a thing
     * as done without repainting it. `rgba(255,255,255,.05)` dark / `rgba(13,17,20,.06)`
     * light (`session-v3f.html:21,29`).
     *
     * Two hosts, both on the session screen: the finished exercise's ordinal chip
     * (`.card.fin .ordchip`), and — blocker **B7** — the done set's *field*
     * (`.set.done .field`), where it replaces the field fill entirely. B7's finding is that
     * step 5 washed the whole ROW in `surfaceTier4` instead, which is what pushed the unit
     * label below its threshold; the wash belongs on the field, and this slot is what makes
     * that expressible (extraction B8 records that it previously was not).
     *
     * Translucent by design: it composites over whatever card tier hosts it, so the contrast
     * map declares its pairs with explicit `over` backdrops. In dark the value coincides with
     * `--hair` ([borderSubtle]); they are separate tokens in the mockup with separate jobs,
     * and they stay separate slots here so a divider retune cannot silently move the wash.
     */
    val donefill: Color,
    /** v3 `max`. */
    val textPrimary: Color,
    /** v3 `body`. */
    val textSecondary: Color,
    /**
     * v3 `meta`.
     *
     * **Light is #596169, which is NOT the mockup's #69727C. This is deliberate — do not
     * "correct" it back.** The mockup value fails WCAG 1.4.3 AA on three of the five light
     * surfaces: `raise` 3.79:1, `field` 4.12:1, `sec` 4.32:1, all under the 4.5:1 this slot
     * needs at 12.5sp. #596169 clears all five (4.88 / 5.31 / 5.56 / 5.87 / 6.29). The mockup
     * was drawn at one size on one surface; this slot is read 55 times across all of them.
     */
    val textTertiary: Color,
    /**
     * v3 `dim` — **merged into `meta`. This slot is an alias, deliberately, and not a mistake
     * to be tidied away.**
     *
     * ## The derivation lives in §2.5 — this is the conclusion
     *
     * As drawn, `dim` fails hard: worst-surface **2.87 dark / 2.05 light**, worst backing `raise`
     * in both themes. The legal value collapses perceptually onto `meta` (redmean 16.3 dark /
     * 17.0 light), so a fourth step that passes AA is indistinguishable from the third. The full
     * argument, the per-surface table and the spec-vs-mockup swap are §2.5 — cited by section, not
     * restated here, and **B28** records that the drawing's own `--dim` has since been corrected to
     * the shipped meta values because nothing ever produced the drawn ones.
     *
     * ## Why this is an alias and not a deletion
     *
     * Deleting it would scatter the decision across every call site as a silent `textTertiary`,
     * which is exactly how the substitution has been happening so far — documented once in
     * `AppSectionHeader` and applied without comment everywhere else. Naming the slot means the
     * `dim` role is *readable in the code*, and reinstating it is a one-line change to the two
     * assignments below rather than an archaeology exercise.
     *
     * **If Ilya reinstates `dim`**, the only defensible form is a *restricted* role: large type
     * only (≥24sp regular or ≥18.66sp bold, where the threshold drops to 3:1 and the corrected
     * values above are no longer forced), on dark surfaces only, since light's worst case at
     * 2.05:1 misses even 3:1. That is a different slot with a different contract, and the commit
     * that introduced this alias is its revert point.
     */
    val textDim: Color,
    /**
     * v3 `idle`. **WCAG-exempt** — 1.4.3 and 1.4.11 both carve out inactive components, and
     * all seven readers are genuinely inactive: `disabledContentColor` on the four `AppButton`
     * variants, `disabledLabelColor` on `AppTextField`, and the two reorder arrows that are
     * greyed exactly when they are inoperable at a list boundary. Never scored by the gate.
     */
    val textDisabled: Color,
    /** v3 `hair` — translucent. Decorative separator; takes no contrast threshold (spec §3.1). */
    val borderSubtle: Color,
    /**
     * v3 `--grid` — the chart's horizontal gridlines, exactly as the mockup declares them:
     * `rgba(255,255,255,.07)` dark / `rgba(13,17,20,.09)` light. One alpha step above `hair`
     * in both themes — a gridline must read *under* a data series without reading as a rule
     * between content, and `hair` tuned for either job would drag the other with it (the same
     * argument that gave `donefill` its own slot). Decorative under §3.1: it separates
     * nothing, carries no state, and takes no contrast threshold. The chart canvas is its
     * only intended reader.
     */
    val grid: Color,
    /**
     * **Control outline**, like [borderStrong] — the boundary that identifies an enabled,
     * unfocused control.
     *
     * Readers: `AppTextField.kt:57` (`unfocusedBorderColor` — the resting outline of every text
     * field in the app, nine call sites) and `TypeToggle.kt:65` (the unselected option
     * boundary). Both are enabled and operable, so WCAG 1.4.11 applies and this owes 3:1.
     *
     * The neighbouring `disabledBorderColor` at `AppTextField.kt:58` reads [borderSubtle]
     * instead, and *that* one is genuinely exempt — which is the line this palette draws:
     * [borderSubtle] is the hairline (separators, disabled outlines — chart gridlines have
     * their own [grid] slot) and takes no threshold; these two are controls and do.
     */
    val borderDefault: Color,
    /**
     * **Control outline — not a hairline.** The ring that *is* the control when it is off.
     *
     * Both readers are enabled, operable controls in their unselected state: `RadioButton`'s
     * `unselectedColor` in `ThemeSelector.kt` and `Checkbox`'s `uncheckedColor` in
     * `ExercisePickerSheet.kt`. In that state the outline carries the entire affordance — no
     * fill, no label inside it, nothing else to see — so WCAG 1.4.11 applies and it owes **3:1**,
     * not the nothing a decorative separator owes.
     *
     * **That is why this is not `hair-s`**, which the mockup draws here and which measures
     * 1.12–1.52:1 — fine for a rule between two rows, disqualifying for a checkbox. The lift keeps
     * `hair-s`'s hue and saturation and moves only lightness, by the smallest step that clears 3:1
     * on all five surfaces; the ten ratios are in §2.7.
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

// ---- v3 tokens ------------------------------------------------------------------------------
//
// Named once here so a slot assignment reads as a mapping rather than as a hex literal, and so
// a token used by two slots is provably the same colour rather than two copies that drift.

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
/**
 * `--donefill`, dark: `rgba(255,255,255,.05)`. Numerically identical to [DARK_HAIR] — a
 * coincidence of the mockup's dark theme, not a shared token; light breaks the tie (6% vs 7%).
 */
private const val DARK_DONEFILL: Long = 0x0DFFFFFF
/** Control outline, dark. See [AppColors.borderStrong] — `hair-s` lifted to clear 3:1. */
private const val DARK_CONTROL_OUTLINE: Long = 0xFF627587
private const val DARK_MOLTEN: Long = 0xFFF0A22E
private const val DARK_MOLTEN_BACKGROUND: Long = 0x17F0A22E
private const val DARK_MOLTEN_BORDER: Long = 0x6BF0A22E

/**
 * Destructive text in dark.
 *
 * **This is #DF714B, not the spec's #C4574A, and the difference is not taste.** Measured: at
 * body size — which is every destructive site, a sheet menu item, a settings row, a dialog
 * button label — #C4574A meets 4.5:1 on *no dark surface in the palette*: base 4.46, sec 4.16,
 * field 3.93, slab 3.59, raise 3.28. The spec flagged only `raise`; the floor is lower than
 * that everywhere.
 *
 * Darkening the surface instead cannot work: #C4574A reaches 4.5:1 only below #0B0D0F, i.e.
 * on a surface darker than the darkest one this palette has, and destructive labels live on
 * `field` and `base`, not on a new near-black tier.
 *
 * So the foreground moves. #DF714B holds the hue and clears 4.5:1 on all five surfaces
 * (6.12 / 5.71 / 5.39 / 4.92 / 4.50). Light needs no such adjustment — #B03B2E already clears
 * all five (5.58 / 5.29 / 5.05 / 5.98 / 4.64) — which is why only the dark value differs.
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
 * Molten as text in light: **#BE3E0C, a 9-unit nudge off the spec's #C2410C.**
 *
 * The spec value is fine on the surfaces you would guess and fails on the one that matters.
 * `PersonalRecordCard` paints its own [Color(LIGHT_MOLTEN_BACKGROUND)] wash and sits *directly on the
 * page* (`ExerciseDetailScreen` is `surfaceTier0`), so the real backdrop is 11% #F97316 over
 * `base` = #F6E8E0, where #C2410C measures **4.325:1** against a 4.5:1 obligation at 15sp and
 * 12.5sp. Composited over a *dialog* it would have passed at 4.638 — which is exactly how this
 * gets missed: measure the wash over white, tick the box, ship a failure on the only screen
 * that renders it.
 *
 * #BE3E0C clears every backdrop molten text actually meets — wash-over-page 4.52,
 * wash-over-dialog 4.84, `sec` 4.78, `slab` 5.41 — at a redmean distance of 8.9 from the spec
 * value, which is below the threshold at which the two are distinguishable side by side.
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
    // v3 `dim`, merged into `meta`. See [AppColors.textDim] — the mockup's #6B7078 measures
    // 2.87:1 on `raise` and only reaches 4.5:1 at #8C9198, 16.3 redmean from this value.
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
    // v3 `dim`, merged into `meta`. See [AppColors.textDim] — the mockup's #98A0A9 measures
    // 2.05:1 on `raise` and only reaches 4.5:1 at #5E6670, 17.0 redmean from this value.
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
