// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.theme.contrast

import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.BOTH
import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.DEAD
import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.DECORATIVE
import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.EXEMPT
import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.FOREGROUND
import io.github.stslex.workeeper.core.ui.kit.theme.contrast.SlotRole.SURFACE

/**
 * The contrast contract: [ROLES] and [DECLARED] are the pairs that co-occur and what each owes,
 * [EXCLUSIONS] the pairs that never do; `ContrastGateTest` fails on any pair in neither list.
 */
internal object ContrastContract {

    /**
     * Role per slot, derived from call sites. `ContrastGateTest` asserts this matches the slots
     * the reflection scanner finds, so a new slot cannot default into being ignored.
     */
    val ROLES: Map<String, SlotRole> = rolesOf(
        // surfaces
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
        // v3 `--donefill` wash; translucent, so its declared rows name explicit `over` backdrops.
        "donefill" to SURFACE,

        // foregrounds
        "textPrimary" to FOREGROUND,
        "textSecondary" to FOREGROUND,
        "textTertiary" to FOREGROUND,
        // v3 `dim`, aliased onto `meta`: its own slot rather than folded into `textTertiary` so
        // that reinstating a real fourth tier fails here loudly instead of shipping unmeasured.
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

        // `accent` is a fill and a text colour; `accentTintedForeground` likewise. Both sides run.
        "accent" to BOTH,
        "accentTintedForeground" to BOTH,

        // Not decoration: these outlines ARE the control when it is off/unfocused, so WCAG 1.4.11
        // applies at 3:1. A stroke that carries no state is `borderSubtle` instead.
        "borderDefault" to FOREGROUND,
        "borderStrong" to FOREGROUND,

        // not scored
        "borderSubtle" to DECORATIVE,
        // v3 `--grid` — chart gridlines. Same class as `borderSubtle`: §3.1 decorative.
        "grid" to DECORATIVE,
        "molten.border" to DECORATIVE,
        "record.border" to DECORATIVE,
        "textDisabled" to EXEMPT,
        "status.info" to DEAD,
    )

    /**
     * Builds [ROLES] and fails at construction on a duplicate key — `mapOf` would resolve one
     * last-write-wins and silently drop the slot out of the gate's enumeration (§3.3).
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

    // Deliberately not applied to DECLARED (keyed by the fg/bg/slot TRIPLE, several rows per pair
    // are the design) or EXCLUSIONS (a keyless list, so the last-write-wins hazard cannot arise).

    /**
     * A foreground/surface pair that really co-occurs, and the type slot it is painted at — the
     * slot, not the pair, carries the threshold.
     */
    data class Declared(
        val foreground: String,
        val background: String,
        val typeSlot: TypeSlot,
        val evidence: String,
        /**
         * What a translucent [background] composites over, named explicitly because the same
         * wash passes over a dialog and fails over the page. Ignored when [background] is opaque.
         */
        val over: String = PAGE,
    )

    /** Composite backdrop: the screen. */
    const val PAGE: String = "surfaceTier0"

    /** Composite backdrop: a dialog — `surfaceTier1` in dark, `surfaceTier2` in light. */
    const val DIALOG: String = "@dialog"

    /**
     * Everything the app actually paints, with the call site that proves it. A component used on
     * several surfaces gets one row per tier.
     */
    val DECLARED: List<Declared> = buildList {
        // Body text on every surface it can land on — the call sites show it is universal.
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
            // The v3 `.tag` label is the one textTertiary read at the BODY rung; same 4.5:1.
            add(Declared("textTertiary", surface, TypeSlot.BODY, "AppTag resting label"))
            // v3 `dim` at CAPTION, not META: its smallest element is the 11px uppercase label.
            add(Declared("textDim", surface, TypeSlot.CAPTION, "AppSectionHeader label, AppNumberInput unit"))
            // The v3 editors put `dim` on two larger rungs; same 4.5:1, named for what is painted.
            add(Declared("textDim", surface, TypeSlot.META, "AppFieldLabel — the drawn `.flabel`"))
            add(Declared("textDim", surface, TypeSlot.BODY, "AppTextField placeholder — `.tf.ghosty`"))
            // The screen-title rung: same colour, larger type, weaker obligation.
            add(Declared("textPrimary", surface, TypeSlot.TITLE, "PastSessionHeader"))
        }

        // accent is v3 `max`: links, timers, chart strokes, selected icons.
        add(Declared("accent", "surfaceTier0", TypeSlot.BODY, "AboutBlock links, ChartCanvas"))
        add(Declared("accent", "surfaceTier1", TypeSlot.BODY, "HomeStartCard, ActiveSessionBanner"))
        add(Declared("accent", "surfaceTier2", TypeSlot.BODY, "RestoreProgressOverlay label, light theme"))
        add(Declared("accent", "surfaceTier3", TypeSlot.BODY, "ChartTooltipPopup value; AppNumberInput cursor"))
        add(Declared("accent", "surfaceTier4", TypeSlot.BODY, "TrainingRow active glyph"))
        // The timer reads `textPrimary` through `AppTypography.timer`, not `accent`.
        add(Declared("textPrimary", "surfaceTier0", TypeSlot.DISPLAY, "LiveWorkoutHeader timer (AppTypography.timer)"))

        // The `--donefill` wash hosting the fin ordinal chip's checkmark, over both card tiers.
        add(
            Declared(
                "textTertiary",
                "donefill",
                TypeSlot.UI_COMPONENT,
                "AppOrdinalChip done checkmark, collapsed fin card",
                over = "surfaceTier1",
            ),
        )
        add(
            Declared(
                "textTertiary",
                "donefill",
                TypeSlot.UI_COMPONENT,
                "AppOrdinalChip done checkmark, lifted fin card",
                over = "surfaceTier2",
            ),
        )
        add(Declared("accent", "surfaceTier0", TypeSlot.UI_COMPONENT, "AppTextField focused outline"))
        add(Declared("accent", "surfaceTier1", TypeSlot.UI_COMPONENT, "LiveExerciseCard border"))

        // ...and the fill side of the same slot: primary button, FAB, progress bar.
        add(Declared("onAccent", "accent", TypeSlot.BODY, "AppButton.Primary label"))
        add(Declared("onAccent", "accent", TypeSlot.UI_COMPONENT, "AppFAB icon"))
        add(Declared("accent", "surfaceTier3", TypeSlot.UI_COMPONENT, "progress fill on track"))

        // Selected states. `AppTagChip` drives both slots off one flag; the nav bar adds no row,
        // its pairs are already declared at stricter slots above (§24).
        add(
            Declared(
                "accentTintedForeground",
                "accentTintedBackground",
                TypeSlot.META,
                "AppTagChip selected label on selected chip fill",
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
        add(Declared("setType.failureForeground", "setType.failureBackground", TypeSlot.CAPTION, "AppSetTypeChip F"))
        add(Declared("setType.dropForeground", "setType.dropBackground", TypeSlot.CAPTION, "AppSetTypeChip D"))

        add(
            Declared(
                "setType.warmupForeground",
                "surfaceTier1",
                TypeSlot.META,
                "attention tint, 4 of 5 call sites",
            ),
        )

        // Destructive. Every site is body-size, and this is the set that moved the dark rust value.
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
        // `status.warning` has no declared pair because it has no production reader; the
        // exclusion below carries the obligation on whoever reintroduces one.

        // Molten — the PR accent. Its text only ever sits on a card tier or the toast, so `field`
        // and `raise` are excluded below rather than declared.
        add(Declared("molten.text", "surfaceTier1", TypeSlot.CAPTION, "PR tag, 11sp"))
        add(Declared("molten.text", "surfaceTier2", TypeSlot.CAPTION, "PR tag on active card"))
        add(Declared("molten.text", "surfaceTier2", TypeSlot.BODY, "toast action button"))
        add(Declared("molten.onSolid", "molten.solid", TypeSlot.CAPTION, "PR pill label"))
        add(Declared("record.onSolid", "record.solid", TypeSlot.CAPTION, "PersonalRecordBadge"))
        // PersonalRecordHero paints the wash directly on the page, so PAGE is the backdrop. This
        // is the pair that moved the light molten text value; three rungs share it.
        add(
            Declared(
                foreground = "record.textPrimary",
                background = "record.background",
                typeSlot = TypeSlot.BODY,
                evidence = "PersonalRecordHero — pair proven at 4.5:1",
                over = PAGE,
            ),
        )
        add(
            Declared(
                foreground = "record.textPrimary",
                background = "record.background",
                typeSlot = TypeSlot.TITLE,
                evidence = "PersonalRecordHero value (dataValue 26sp)",
                over = PAGE,
            ),
        )
        add(
            Declared(
                foreground = "record.textPrimary",
                background = "record.background",
                typeSlot = TypeSlot.CAPTION,
                evidence = "PersonalRecordHero mdot label (mono.caption)",
                over = PAGE,
            ),
        )
        // The hero's meta line is plain `--meta` on the wash — the mockup does not override it.
        add(
            Declared(
                foreground = "textTertiary",
                background = "record.background",
                typeSlot = TypeSlot.META,
                evidence = "PersonalRecordHero meta line, as drawn",
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
        // FinishConfirmDialog's own surface: tier1 in dark, tier2 in light. The gate runs both.
        add(Declared("record.textPrimary", "surfaceTier1", TypeSlot.BODY, "FinishConfirmDialog heading, dark"))
        add(Declared("record.textSecondary", "surfaceTier1", TypeSlot.META, "FinishConfirmDialog rows, dark"))
        add(Declared("record.textPrimary", "surfaceTier2", TypeSlot.BODY, "FinishConfirmDialog heading, light"))
        add(Declared("record.textSecondary", "surfaceTier2", TypeSlot.META, "FinishConfirmDialog rows, light"))

        // Control outlines, declared on every tier: a control can be dropped on any surface.
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
                    evidence = "AppTextField unfocusedBorderColor / progress-rail skipped, enabled",
                ),
            )
        }

        // `accentTintedForeground` is v3 `max`, the same value as textPrimary; most of its reads
        // are ordinary foreground text, so it is declared across the tiers rather than excluded.
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

        // The PR set row: the molten wash SUBSTITUTES the field tier, so the real backdrops are
        // the card tiers. The value is `record.textPrimary` at TITLE (3:1); the unit stays dim.
        listOf("surfaceTier1" to "resting card", "surfaceTier2" to "lifted card").forEach { (tier, host) ->
            add(
                Declared(
                    foreground = "record.textPrimary",
                    background = "record.background",
                    typeSlot = TypeSlot.TITLE,
                    evidence = "AppNumberInput PR value (dataValue, 26sp bold), $host",
                    over = tier,
                ),
            )
            add(
                Declared(
                    foreground = "textSecondary",
                    background = "record.background",
                    typeSlot = TypeSlot.CAPTION,
                    evidence = "AppNumberInput unit on a PR set row, $host — promoted from " +
                        "textDim, which measures 4.40 dark over the lifted card",
                    over = tier,
                ),
            )
            // The done field: value promoted to max, unit still dim — same two backdrops.
            add(
                Declared(
                    foreground = "textPrimary",
                    background = "donefill",
                    typeSlot = TypeSlot.TITLE,
                    evidence = "AppNumberInput done value (dataValue, 26sp bold), $host",
                    over = tier,
                ),
            )
            add(
                Declared(
                    foreground = "textSecondary",
                    background = "donefill",
                    typeSlot = TypeSlot.CAPTION,
                    evidence = "AppNumberInput unit on a done set row, $host — promoted from " +
                        "textDim, which measures 4.45 dark over the lifted card",
                    over = tier,
                ),
            )
        }

        // The resting value deviates from the drawn `--idle`: it reads the meta value in both
        // themes, declared at TITLE for the evidence trail.
        add(Declared("textTertiary", "surfaceTier3", TypeSlot.TITLE, "AppNumberInput resting value (dataValue)"))
    }

    /**
     * Pairs that never co-occur, each with the reason. A rule that stops being true shows up as
     * a declared pair failing, not as a silent pass.
     */
    data class Exclusion(val reason: String, val matches: (fg: String, bg: String) -> Boolean)

    val EXCLUSIONS: List<Exclusion> = listOf(
        Exclusion(
            "`donefill` is the completed-content wash: the fin ordchip's checkmark " +
                "(`textTertiary`), the done field's value (`textPrimary`, B7) and its unit " +
                "(`textSecondary` — promoted off `textDim`, see the DECLARED rows). Nothing " +
                "else is painted on it.",
        ) { fg, bg ->
            bg == "donefill" && fg !in setOf("textTertiary", "textPrimary", "textSecondary")
        },
        Exclusion(
            "Set-type chip colours are scoped to their own chip: `AppSetTypeChip` picks one " +
                "(background, foreground) pair by `when(type)` and paints both. A warm-up " +
                "foreground can never land on a drop background.",
        ) { fg, bg ->
            fg.startsWith("setType.") && bg.startsWith("setType.") &&
                fg.removeSuffix("Foreground") != bg.removeSuffix("Background")
        },
        Exclusion(
            "The WORK chip paints the mockup's `.tchip` treatment (`textDim` on transparent " +
                "with a `borderDefault` ring — extraction §1.6), so `setType.workForeground` " +
                "never lands on `setType.workBackground` any more. Both slots stay in the " +
                "palette: WARMUP/FAIL/DROP still read theirs, and reinstating a loud WORK " +
                "chip is a one-line revert that would fail here loudly.",
        ) { fg, bg -> fg == "setType.workForeground" && bg == "setType.workBackground" },
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
            "The molten fill and wash host only personal-record content. NARROWED for two " +
                "carve-outs: the PR set row's unit (`textSecondary` on the wash — declared " +
                "above with its two card backdrops) and the record hero's meta line " +
                "(`textTertiary` on the wash — §3.3 draws `.meta` un-overridden; declared " +
                "above over PAGE). The VALUE is no longer in the carve-out: B1 brought it to " +
                "26sp bold, where the mockup's molten (`record.textPrimary`) is legal, so " +
                "`textPrimary` left the wash entirely.",
        ) { fg, bg ->
            (
                bg == "molten.solid" || bg == "record.solid" ||
                    bg == "molten.background" || bg == "record.background"
                ) &&
                !(fg.startsWith("molten.") || fg.startsWith("record.")) &&
                !(bg == "record.background" && fg in setOf("textSecondary", "textTertiary"))
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
            "`status.warning` currently has NO production reader on any surface. Its only " +
                "one was the past-session skipped chip (`sec`), retired by the v3 rebuild " +
                "when the card adopted the session screen's alpha + strikethrough skip " +
                "treatment. The previous premise here — 'one chip label in the past-session " +
                "card header, `sec` only' — is no longer true, and left standing it would " +
                "wave through the next screen that paints warning text on a lifted card or " +
                "a sheet. The slot stays in the palette (retiring it is a palette change, " +
                "out of the rebuild's scope), so it is excluded everywhere rather than " +
                "declared anywhere. REINTRODUCING A READER MEANS REPLACING THIS EXCLUSION " +
                "with a measured `Declared` row naming the real backdrop and type slot.",
        ) { fg, _ -> fg == "status.warning" },
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
