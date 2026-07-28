// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.BOTH
import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.DEAD
import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.DECORATIVE
import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.EXEMPT
import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.FOREGROUND
import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.SURFACE

/**
 * The contrast contract: what each slot is, which pairs are real, and what each real pair owes.
 *
 * Three parts, and the third is the one that matters:
 *
 *  a. [ROLES] and [DECLARED] — the pairs that co-occur on screen, each with the type slot it is
 *     painted at and therefore the threshold it must meet.
 *  b. [EXCLUSIONS] — pairs that provably never co-occur, each with a reason.
 *  c. `ContrastGateTest.every_foreground_surface_pair_is_declared_or_excluded` — enumerates the
 *     **full cartesian product** and fails on anything that is in neither list.
 *
 * Without (c) this file is a comment: a new screen could introduce an unverified pairing and
 * nothing would notice. With it, adding a slot or painting an existing colour somewhere new
 * forces a decision here.
 */
internal object ContrastContract {

    /**
     * Role per slot, derived from call sites. Every slot the reflection scanner finds must
     * appear here — `ContrastGateTest` asserts the two sets match exactly, so a new slot cannot
     * default into being ignored.
     */
    val ROLES: Map<String, SlotRole> = rolesOf(
        // -- surfaces ---------------------------------------------------------------------
        "surfaceTier0" to SURFACE,
        "surfaceTier1" to SURFACE,
        "surfaceTier2" to SURFACE,
        "surfaceTier3" to SURFACE,
        "surfaceTier4" to SURFACE,
        "accentTintedBackground" to SURFACE,
        "setType.warmupBackground" to SURFACE,
        "setType.workBackground" to SURFACE,
        "setType.failureBackground" to SURFACE,
        "setType.dropBackground" to SURFACE,
        "molten.background" to SURFACE,
        "molten.solid" to SURFACE,
        "record.background" to SURFACE,
        "record.solid" to SURFACE,
        "inverseSurface" to SURFACE,

        // -- foregrounds ------------------------------------------------------------------
        "textPrimary" to FOREGROUND,
        "textSecondary" to FOREGROUND,
        "textTertiary" to FOREGROUND,
        // v3 `dim`, aliased onto `meta` (AppColors.textDim). Same value as `textTertiary`, so
        // it adds rows and no distinct measurements — the report's distinct-measurement counter
        // keys on colour VALUES precisely so an alias cannot read as extra coverage. It is
        // declared as its own slot rather than folded into `textTertiary` so that reinstating a
        // real fourth tier fails here loudly instead of shipping unmeasured.
        "textDim" to FOREGROUND,
        "onAccent" to FOREGROUND,
        "inverseOnSurface" to FOREGROUND,
        "setType.warmupForeground" to FOREGROUND,
        "setType.workForeground" to FOREGROUND,
        "setType.failureForeground" to FOREGROUND,
        "setType.dropForeground" to FOREGROUND,
        "status.success" to FOREGROUND,
        "status.warning" to FOREGROUND,
        "status.error" to FOREGROUND,
        "molten.text" to FOREGROUND,
        "molten.onSolid" to FOREGROUND,
        "record.textPrimary" to FOREGROUND,
        "record.textSecondary" to FOREGROUND,
        "record.onSolid" to FOREGROUND,

        // `accent` is the primary-button fill *and* the link colour; `accentTintedForeground`
        // paints the checkmark circle *and* the text on it. Both sides get enumerated.
        "accent" to BOTH,
        "accentTintedForeground" to BOTH,

        // Not decoration: these outlines ARE the control when it is off/unfocused, so WCAG
        // 1.4.11 non-text contrast applies at 3:1.
        //
        // Call sites RE-VERIFIED at this commit (the previous list had gone stale — it cited
        // `ThemeSelector.kt:84` and `ExercisePickerSheet.kt:170`, and neither paints this slot
        // any more). What is actually live:
        //   - AppTextField.kt:57  unfocusedBorderColor — an enabled, operable field's boundary
        //   - TypeToggle.kt:65    the unselected toggle's boundary
        //   - AppProgressRail.kt:201 the skipped-segment outline, which is the ONLY thing
        //     distinguishing a skipped exercise from an unfilled one — informational, not trim
        //   - AppCheckmarkButton.kt:51 the DISABLED branch (`if (enabled) accent else this`),
        //     which 1.4.3/1.4.11 both carve out; it does not lower the requirement for the
        //     three enabled sites above
        //
        // The dividing line against `borderSubtle` is not thickness, it is whether the stroke
        // carries state. `borderSubtle` is the divider/trim slot (AppSection's rule, the bottom
        // bar, chart gridlines, AppTextField.kt:58's *disabled* border) and is decorative under
        // §3.1. This one is never a divider.
        "borderDefault" to FOREGROUND,
        "borderStrong" to FOREGROUND,

        // -- not scored -------------------------------------------------------------------
        "borderSubtle" to DECORATIVE,
        "molten.border" to DECORATIVE,
        "record.border" to DECORATIVE,
        "textDisabled" to EXEMPT,
        "status.info" to DEAD,
    )

    /**
     * Builds [ROLES] from a list of entries and **fails at construction on a duplicate key**.
     *
     * `mapOf` resolves a repeated key last-write-wins, silently, so the map's behaviour is
     * decided by declaration order and neither line reads like what actually happens. That is
     * not hypothetical here: this file shipped `"borderDefault" to FOREGROUND` (with a reasoned
     * 3:1 justification) above `"borderDefault" to DECORATIVE` (with none), and the net effect
     * matched neither — the slot resolved to DECORATIVE and dropped out of the part-(c)
     * enumeration entirely, while its five DECLARED rows went on being measured at 3:1. Both
     * halves of the gate were green and the coverage was wrong.
     *
     * §3.3 requires the construction itself to reject this, not the two offending lines to be
     * edited: "the next duplicate would arrive just as quietly".
     */
    private fun rolesOf(vararg entries: Pair<String, SlotRole>): Map<String, SlotRole> {
        val duplicates = entries
            .groupBy { (slot, _) -> slot }
            .filterValues { it.size > 1 }
        require(duplicates.isEmpty()) {
            buildString {
                appendLine("ContrastContract.ROLES declares ${duplicates.size} slot(s) twice.")
                appendLine()
                appendLine("`mapOf` would resolve these last-write-wins and the gate would run")
                appendLine("on whichever line happens to be lower in the file, with no failure")
                appendLine("and no way to read the outcome off the source.")
                appendLine()
                duplicates.forEach { (slot, rows) ->
                    appendLine("  $slot declared as ${rows.joinToString(", ") { it.second.name }}")
                }
            }
        }
        return entries.toMap()
    }

    // Deliberately NOT applied to [DECLARED] or [EXCLUSIONS]. Checked, not assumed:
    //
    //  - [DECLARED] is keyed by the (foreground, background, typeSlot) TRIPLE, not the pair.
    //    Several rows per pair is the design — "the type slot, not the pair, carries the
    //    threshold" — and `textPrimary on surfaceTier0` is legitimately declared at both BODY
    //    and TITLE. A pair-keyed guard rejects 14 correct rows; measured, by writing one.
    //  - [EXCLUSIONS] is a list of (reason, predicate) with no key at all, so the last-write-
    //    wins hazard cannot arise. Overlapping predicates are additive, not substitutive.

    /**
     * A foreground/surface pair that really co-occurs, and the type slot it is painted at.
     *
     * The type slot — not the pair — carries the threshold. `molten.text` on `surfaceTier1`
     * measures 8.58 in dark either way, but the same number is a pass at [TypeSlot.TITLE] and
     * would be a fail at [TypeSlot.CAPTION] if the ratio were 4.2. Declaring the slot is what
     * makes the verdict meaningful.
     */
    data class Declared(
        val foreground: String,
        val background: String,
        val typeSlot: TypeSlot,
        val evidence: String,
        /**
         * What a **translucent** [background] is composited over, named explicitly because
         * guessing it is how a wash gets waved through.
         *
         * Measured: the molten wash over a dialog gives molten text 4.64:1 and over the page
         * 4.33:1. Same wash, same text, one passes and one does not — and `PersonalRecordCard`
         * is the page case. Assuming a single backdrop for every wash would have certified the
         * failing site using the passing site's number.
         *
         * [PAGE] and [DIALOG] name the two real hosts; [DIALOG] is theme-dependent because
         * production is (`val dialogBg = if (isDark) surfaceTier1 else surfaceTier2`, nine
         * sites). Ignored when [background] is opaque.
         */
        val over: String = PAGE,
    )

    /** Composite backdrop: the screen. */
    const val PAGE: String = "surfaceTier0"

    /** Composite backdrop: a dialog — `surfaceTier1` in dark, `surfaceTier2` in light. */
    const val DIALOG: String = "@dialog"

    /**
     * Everything the app actually paints, with the call site that proves it.
     *
     * Evidence is a real file, because "these two probably appear together" is how an
     * accessibility gate turns into a fiction. Where a component is used on several surfaces
     * (`AppButton.Tertiary` has ~28 call sites across four tiers) each tier gets its own row.
     */
    val DECLARED: List<Declared> = buildList {
        // Body text on every surface it can land on. textPrimary/Secondary/Tertiary are read
        // 95 / 45 / 55 times across every screen; treating them as universal is not laziness,
        // it is what the call sites show.
        val everySurface = listOf(
            "surfaceTier0",
            "surfaceTier1",
            "surfaceTier2",
            "surfaceTier3",
            "surfaceTier4",
        )
        everySurface.forEach { surface ->
            add(Declared("textPrimary", surface, TypeSlot.BODY, "95 reads; row/card titles"))
            add(Declared("textSecondary", surface, TypeSlot.BODY, "45 reads; supporting text"))
            add(Declared("textTertiary", surface, TypeSlot.META, "55 reads; captions, meta"))
            // v3 `dim`. Declared at CAPTION, not META: the mockups' smallest `dim` element is
            // the 11px uppercase `.label`, and 11sp is the tightest rung this role reaches.
            // Same threshold as META numerically (4.5:1), but the slot names what is actually
            // painted, which is what makes the verdict re-checkable if the alias is ever undone.
            add(Declared("textDim", surface, TypeSlot.CAPTION, "AppSectionHeader label, AppNumberInput unit"))
            // The screen-title rung. Same colour, larger type, weaker obligation — declared so
            // the distinction is visible rather than implied.
            add(Declared("textPrimary", surface, TypeSlot.TITLE, "DetailTopbar, PastSessionHeader"))
        }

        // accent is v3 `max`: links, timers, chart strokes, selected icons.
        add(Declared("accent", "surfaceTier0", TypeSlot.BODY, "AboutBlock links, ChartCanvas"))
        add(Declared("accent", "surfaceTier1", TypeSlot.BODY, "HomeStartCard, ActiveSessionBanner"))
        // RE-VERIFIED: this row used to cite "AppNumberInput cursor", which moved to
        // `surfaceTier3` when the input adopted the mockup's `.field` tier. The live sites are
        // RestoreProgressOverlay.kt:65 (accent label on a card that is `surfaceTier2` in light)
        // and ExercisePickerSheet.kt:169 (Checkbox checkedColor on a `surfaceTier2` sheet).
        add(Declared("accent", "surfaceTier2", TypeSlot.BODY, "RestoreProgressOverlay label, light theme"))
        add(Declared("accent", "surfaceTier3", TypeSlot.BODY, "ChartTooltipPopup value; AppNumberInput cursor"))
        add(Declared("accent", "surfaceTier4", TypeSlot.BODY, "TrainingRow active glyph"))
        // RE-TARGETED (session rebuild C1): the timer left its tier1 header card — `.shead`
        // is three texts directly on the page — and reads `textPrimary` through
        // `AppTypography.timer` now, not `accent`. Same colour value in this palette; the row
        // follows the slot the call site actually names.
        add(Declared("textPrimary", "surfaceTier0", TypeSlot.DISPLAY, "LiveWorkoutHeader timer (AppTypography.timer)"))
        add(Declared("accent", "surfaceTier0", TypeSlot.UI_COMPONENT, "AppTextField focused outline"))
        add(Declared("accent", "surfaceTier1", TypeSlot.UI_COMPONENT, "LiveExerciseCard border"))

        // ...and the fill side of the same slot: primary button, FAB, progress bar.
        add(Declared("onAccent", "accent", TypeSlot.BODY, "AppButton.Primary label"))
        add(Declared("onAccent", "accent", TypeSlot.UI_COMPONENT, "AppFAB icon"))
        add(Declared("accent", "surfaceTier3", TypeSlot.UI_COMPONENT, "progress fill on track"))

        // Selected states.
        add(
            Declared(
                "accentTintedForeground",
                "accentTintedBackground",
                TypeSlot.META,
                "AppBottomBar selected icon on indicator pill",
            ),
        )
        add(
            Declared(
                "onAccent",
                "accentTintedForeground",
                TypeSlot.UI_COMPONENT,
                "AppCheckmarkButton checkmark on filled circle",
            ),
        )
        add(
            Declared(
                "textPrimary",
                "accentTintedBackground",
                TypeSlot.BODY,
                "ReorderableColumn drag highlight; selected AppTagChip",
            ),
        )

        // Snackbar — the one inverse surface.
        add(Declared("inverseOnSurface", "inverseSurface", TypeSlot.BODY, "AppSnackbar.kt:30"))
        add(
            Declared(
                "inverseOnSurface",
                "inverseSurface",
                TypeSlot.UI_COMPONENT,
                "AppSnackbar dismiss icon",
            ),
        )

        // Set-type chips. 11sp labels inside an 18dp chip — the smallest text in the app.
        add(Declared("setType.warmupForeground", "setType.warmupBackground", TypeSlot.CAPTION, "AppSetTypeChip W"))
        add(Declared("setType.workForeground", "setType.workBackground", TypeSlot.CAPTION, "AppSetTypeChip ·"))
        add(Declared("setType.failureForeground", "setType.failureBackground", TypeSlot.CAPTION, "AppSetTypeChip F"))
        add(Declared("setType.dropForeground", "setType.dropBackground", TypeSlot.CAPTION, "AppSetTypeChip D"))

        // warmupForeground drifted off its name: four of its five call sites are the amber
        // "attention" tint on ordinary cards, not the chip.
        add(
            Declared(
                "setType.warmupForeground",
                "surfaceTier1",
                TypeSlot.META,
                "attention tint, 4 of 5 call sites",
            ),
        )

        // Destructive. Every site is body-size — a sheet menu item, a settings row, a dialog
        // button label. This is the set of pairs that forced the dark rust value to move.
        listOf("surfaceTier0", "surfaceTier1", "surfaceTier2", "surfaceTier3", "surfaceTier4")
            .forEach { surface ->
                add(
                    Declared(
                        "status.error",
                        surface,
                        TypeSlot.BODY,
                        "destructive labels: ExerciseEditScreen, FinishConfirmDialog, settings rows",
                    ),
                )
                add(
                    Declared(
                        "setType.failureForeground",
                        surface,
                        TypeSlot.BODY,
                        "AppButton.Destructive label; destructive banner",
                    ),
                )
            }
        add(
            Declared(
                "setType.failureForeground",
                "setType.failureBackground",
                TypeSlot.BODY,
                "AppButton.Destructive filled variant, on a screen",
                over = PAGE,
            ),
        )
        add(
            Declared(
                "setType.failureForeground",
                "setType.failureBackground",
                TypeSlot.BODY,
                "AppConfirmDialog destructive banner",
                over = DIALOG,
            ),
        )

        // Status.
        add(
            Declared(
                "status.success",
                "surfaceTier1",
                TypeSlot.UI_COMPONENT,
                "RestoreProgressOverlay check icon, iconLg",
            ),
        )
        add(
            Declared(
                "status.success",
                "surfaceTier2",
                TypeSlot.UI_COMPONENT,
                "RestoreProgressOverlay, light theme",
            ),
        )
        add(
            Declared(
                "status.warning",
                "surfaceTier1",
                TypeSlot.CAPTION,
                "past-session skipped chip, labelSmall",
            ),
        )

        // Molten — the PR accent. Its text only ever sits inside a card (`sec` inactive, `slab`
        // active) or on the toast, which is also `slab`. That is why `field` and `raise` are
        // excluded below rather than declared: it is a fact about the layout, not a hope.
        add(Declared("molten.text", "surfaceTier1", TypeSlot.CAPTION, "PR tag, 11sp"))
        add(Declared("molten.text", "surfaceTier2", TypeSlot.CAPTION, "PR tag on active card"))
        add(Declared("molten.text", "surfaceTier2", TypeSlot.BODY, "toast action button"))
        add(Declared("molten.onSolid", "molten.solid", TypeSlot.CAPTION, "PR pill label"))
        add(Declared("record.onSolid", "record.solid", TypeSlot.CAPTION, "PersonalRecordBadge"))
        // PersonalRecordCard paints the wash and sits directly on the page (ExerciseDetailScreen
        // is surfaceTier0), so PAGE — not a dialog — is the backdrop. This is the pair that
        // moved the light molten text value.
        add(
            Declared(
                foreground = "record.textPrimary",
                background = "record.background",
                typeSlot = TypeSlot.BODY,
                evidence = "PersonalRecordCard value",
                over = PAGE,
            ),
        )
        add(
            Declared(
                foreground = "record.textSecondary",
                background = "record.background",
                typeSlot = TypeSlot.META,
                evidence = "PersonalRecordCard date",
                over = PAGE,
            ),
        )
        // The same wash inside FinishConfirmDialog, over the dialog surface.
        add(
            Declared(
                "record.textPrimary",
                "record.background",
                TypeSlot.BODY,
                "FinishConfirmDialog PR block",
                over = DIALOG,
            ),
        )
        add(
            Declared(
                "record.textSecondary",
                "record.background",
                TypeSlot.META,
                "FinishConfirmDialog PR rows",
                over = DIALOG,
            ),
        )
        // FinishConfirmDialog's own surface: tier1 in dark, tier2 in light. Both declared,
        // because the dialog idiom picks by theme and the gate measures both themes.
        add(Declared("record.textPrimary", "surfaceTier1", TypeSlot.BODY, "FinishConfirmDialog heading, dark"))
        add(Declared("record.textSecondary", "surfaceTier1", TypeSlot.META, "FinishConfirmDialog rows, dark"))
        add(Declared("record.textPrimary", "surfaceTier2", TypeSlot.BODY, "FinishConfirmDialog heading, light"))
        add(Declared("record.textSecondary", "surfaceTier2", TypeSlot.META, "FinishConfirmDialog rows, light"))

        // Control outlines. Declared on every tier: the theme selector sits on the settings
        // page, the exercise picker checkbox on a sheet row, and a control can be dropped on
        // any surface without anyone thinking to revisit this file.
        everySurface.forEach { surface ->
            add(
                Declared(
                    foreground = "borderStrong",
                    background = surface,
                    typeSlot = TypeSlot.UI_COMPONENT,
                    evidence = "RadioButton unselectedColor / Checkbox uncheckedColor, enabled",
                ),
            )
            add(
                Declared(
                    foreground = "borderDefault",
                    background = surface,
                    typeSlot = TypeSlot.UI_COMPONENT,
                    evidence = "AppTextField unfocusedBorderColor / TypeToggle unselected, enabled",
                ),
            )
        }

        // `accentTintedForeground` is v3 `max` — the same colour as textPrimary and accent.
        // Nine of its thirteen reads are ordinary foreground text/icons on ordinary surfaces,
        // so it is declared across the tiers rather than excluded. The report counts distinct
        // measurements precisely so this aliasing does not read as extra coverage.
        everySurface.forEach { surface ->
            add(
                Declared(
                    "accentTintedForeground",
                    surface,
                    TypeSlot.BODY,
                    "9 of 13 reads are foreground text/icon; same value as textPrimary",
                ),
            )
        }
        add(Declared("molten.text", "molten.background", TypeSlot.CAPTION, "PR tag inside its own wash", over = PAGE))

        // The PR set row: `.set.pr .field{background:var(--molten-bg)}` washes BOTH fields, so
        // the field's own value and unit now sit on the molten wash.
        //
        // ONE backdrop, `surfaceTier3`, because `AppNumberInput` STACKS the wash on the field
        // tier rather than substituting it the way the CSS does. That is a measurement: the CSS
        // way puts the unit on a different backdrop per row and fails on the live DONE row's
        // `surfaceTier4` (3.99 dark / 4.46 light against 4.5). Stacked, it is 4.84 / 4.82 and
        // the field's contrast stops depending on what is behind it.
        //
        // The foreground is `textPrimary`, NOT `record.textPrimary`, and that is a finding
        // rather than a preference — see AppNumberInput's KDoc. The mockup also turns the value
        // molten, which measures 4.14 in light against the 4.5:1 a 19sp regular value owes.
        add(
            Declared(
                foreground = "textPrimary",
                background = "record.background",
                typeSlot = TypeSlot.SECTION,
                evidence = "AppNumberInput value on a PR set row",
                over = "surfaceTier3",
            ),
        )
        add(
            Declared(
                foreground = "textDim",
                background = "record.background",
                typeSlot = TypeSlot.META,
                evidence = "AppNumberInput unit on a PR set row",
                over = "surfaceTier3",
            ),
        )
    }

    /**
     * Pairs that never co-occur, each with the reason. Rules cover families; a rule that stops
     * being true shows up as a *declared* pair failing, not as a silent pass.
     */
    data class Exclusion(val reason: String, val matches: (fg: String, bg: String) -> Boolean)

    val EXCLUSIONS: List<Exclusion> = listOf(
        Exclusion(
            "Set-type chip colours are scoped to their own chip: `AppSetTypeChip` picks one " +
                "(background, foreground) pair by `when(type)` and paints both. A warm-up " +
                "foreground can never land on a drop background.",
        ) { fg, bg ->
            fg.startsWith("setType.") && bg.startsWith("setType.") &&
                fg.removeSuffix("Foreground") != bg.removeSuffix("Background")
        },
        Exclusion(
            "The set-type chip backgrounds host only their own chip label — no other " +
                "foreground is ever drawn inside a chip.",
        ) { fg, bg -> bg.startsWith("setType.") && !fg.startsWith("setType.") },
        Exclusion(
            "`inverseSurface` is the snackbar container and nothing else (AppSnackbar.kt:29). " +
                "Only the snackbar's own message and dismiss icon are drawn on it.",
        ) { fg, bg -> bg == "inverseSurface" && !fg.startsWith("inverse") },
        Exclusion(
            "`inverseOnSurface` is read only by AppSnackbar, so it never appears off the " +
                "snackbar container.",
        ) { fg, bg -> fg == "inverseOnSurface" && bg != "inverseSurface" },
        Exclusion(
            "Molten is not a general accent (spec §9): it appears only on the personal-record " +
                "surfaces and the transient wow state. `.set`/`.prtag` live inside `.card`, " +
                "whose fill is `sec` or `slab` — never `field`, never `raise`.",
        ) { fg, bg ->
            (fg.startsWith("molten.") || fg.startsWith("record.")) &&
                bg in setOf("surfaceTier0", "surfaceTier3", "surfaceTier4", "accentTintedBackground")
        },
        Exclusion(
            "The molten fill and wash host only personal-record content. NARROWED: since the " +
                "PR set row washes its fields (`.set.pr .field`), the record's own value and " +
                "unit are drawn on the wash in `textPrimary`/`textDim` rather than in molten — " +
                "see the DECLARED rows above for the measurement that forced that. They are " +
                "personal-record content by the rule's own logic; they are simply not painted " +
                "in the molten namespace.",
        ) { fg, bg ->
            (
                bg == "molten.solid" || bg == "record.solid" ||
                    bg == "molten.background" || bg == "record.background"
                ) &&
                !(fg.startsWith("molten.") || fg.startsWith("record.")) &&
                !(bg == "record.background" && fg in setOf("textPrimary", "textDim"))
        },
        Exclusion(
            "`onAccent` is v3 `base`, the page colour. It is legible only on a filled accent " +
                "or the checkmark circle; on any ordinary surface it would be page-on-page.",
        ) { fg, bg -> fg == "onAccent" && bg !in setOf("accent", "accentTintedForeground") },
        Exclusion(
            "`accent` as a fill hosts only `onAccent` — AppButton.Primary, AppFAB and the " +
                "Resume dialog button all pass `onAccent` as their content colour.",
        ) { fg, bg -> bg == "accent" && fg != "onAccent" },
        Exclusion(
            "`accentTintedForeground` as a fill (AppCheckmarkButton circle, ReorderableColumn " +
                "drag highlight) carries only the checkmark, which is `onAccent`.",
        ) { fg, bg -> bg == "accentTintedForeground" && fg != "onAccent" },
        Exclusion(
            "The nav indicator pill and selected chip host their own selected content only.",
        ) { fg, bg ->
            bg == "accentTintedBackground" &&
                fg !in setOf("accentTintedForeground", "textPrimary")
        },
        Exclusion(
            "`status.success` is one icon in one overlay (RestoreProgressOverlay), which is a " +
                "card: `sec` in dark, `slab` in light. It reaches no other surface.",
        ) { fg, bg -> fg == "status.success" && bg !in setOf("surfaceTier1", "surfaceTier2") },
        Exclusion(
            "`status.warning` is one chip label in the past-session card header — `sec` only.",
        ) { fg, bg -> fg == "status.warning" && bg != "surfaceTier1" },
        Exclusion(
            "The molten fill carries exactly one content colour, `onSolid`. That is the whole " +
                "reason the slot exists: molten text on the molten fill is 1.0:1 by " +
                "construction in dark, where `text` and `solid` are the same hex.",
        ) { fg, bg ->
            bg.endsWith(".solid") && !fg.endsWith(".onSolid")
        },
        Exclusion(
            "`molten.*` and `record.*` are two names for one role — `record` is the concrete " +
                "consumer, `molten` is the role it reads. A call site resolves one namespace " +
                "or the other (`AppUi.colors.record` at every PR site), so a molten foreground " +
                "never lands on a record surface or vice versa.",
        ) { fg, bg ->
            (fg.startsWith("molten.") && bg.startsWith("record.")) ||
                (fg.startsWith("record.") && bg.startsWith("molten."))
        },
        Exclusion(
            "`molten.onSolid` / `record.onSolid` exist to be legible on the molten fill and are " +
                "painted nowhere else.",
        ) { fg, bg ->
            fg.endsWith(".onSolid") && bg !in setOf("molten.solid", "record.solid")
        },
        Exclusion(
            "`setType.warmupForeground`'s off-chip use is the attention tint on cards (`sec`); " +
                "it is not painted on the page, dialogs, fields or chips.",
        ) { fg, bg ->
            fg == "setType.warmupForeground" &&
                bg in setOf("surfaceTier0", "surfaceTier2", "surfaceTier3", "surfaceTier4", "accentTintedBackground")
        },
        Exclusion(
            "`setType.workForeground` and `setType.dropForeground` have exactly one reader each " +
                "— their own chip (AppSetTypeChip.kt:53).",
        ) { fg, bg ->
            fg in setOf("setType.workForeground", "setType.dropForeground") &&
                !bg.startsWith("setType.")
        },
    )
}
