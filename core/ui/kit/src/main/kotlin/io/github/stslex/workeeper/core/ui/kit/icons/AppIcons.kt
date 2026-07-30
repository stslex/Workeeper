// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The v3 icon set, transcribed stroke-for-stroke from the mockups' inline SVGs.
 *
 * Material's stock vectors are filled 24dp glyphs; the mockups draw every icon as a **stroked**
 * path — `fill:none; stroke:currentColor; stroke-linecap/join:round` — in a 24-unit viewBox,
 * with the stroke width declared per context (1.7 on the 21dp top-bar glyphs, 1.8 on the 17dp
 * and 19dp card/sheet glyphs). Swapping in a filled Material glyph changes the whole texture of
 * the chrome, which is exactly the step-5 defect ("design system applied, design absent"), so
 * the paths ship **verbatim as SVG path data** instead — diffable against the mockup line the
 * KDoc cites. `<circle>` elements are rewritten as two-arc paths, the only change of notation.
 *
 * Stroke widths are in viewBox units: the SVG scales its stroke with the glyph, and so does
 * [ImageVector], so a 1.7 stroke rendered at 21dp is the same picture in both worlds. Tint
 * arrives from [androidx.compose.material3.Icon]'s `tint` exactly as `currentColor` does in
 * the mockup.
 */
object AppIcons {

    /** Top-bar back affordance — session-v3f L190. */
    val ChevronLeft: ImageVector by lazy {
        strokeIcon("ChevronLeft", TOPBAR_STROKE, "M15 5l-7 7 7 7")
    }

    /**
     * Vertical three-dot overflow: three circles `r=1.4` at cy 5 / 12 / 19 — session-v3f
     * L192 (top bar, 1.7 stroke) and L353 (card `.mini.menu`, 1.8 stroke). The stroke
     * difference between those two contexts is 0.1 viewBox units — invisible below 24dp —
     * so one vector serves both.
     */
    val MoreVertical: ImageVector by lazy {
        strokeIcon(
            "MoreVertical",
            TOPBAR_STROKE,
            circlePath(cy = "5") + circlePath(cy = "12") + circlePath(cy = "19"),
        )
    }

    /** Card expand affordance, rotates 90° when the card is active — session-v3f L354. */
    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight", CARD_STROKE, "M9 6l6 6-6 6")
    }

    /**
     * The chart's exercise-switcher chevron, inside `.exhead .swap` — pass2d L236. Drawn at
     * 16dp with its own 2.2 stroke (`.exhead .swap svg{stroke-width:2.2}`), the heaviest in
     * the set: a small glyph on a filled tile needs the weight to read.
     */
    val ChevronDown: ImageVector by lazy {
        strokeIcon("ChevronDown", SWAP_STROKE, "M6 9l6 6 6-6")
    }

    /** `.mini.info` — a circle `r=9` plus the i-glyph strokes — session-v3f L352. */
    val Info: ImageVector by lazy {
        strokeIcon(
            "Info",
            CARD_STROKE,
            "M3 12a9 9 0 1 0 18 0a9 9 0 1 0-18 0Z" + "M12 11v5M12 7.6v.1",
        )
    }

    /** Plus — `.addex` (1.9 stroke, L145) and the sheet's add item (1.8, L232) share the path. */
    val Plus: ImageVector by lazy {
        strokeIcon("Plus", ADDEX_STROKE, "M12 5v14M5 12h14")
    }

    /** Skip (sheet item): closed play-triangle outline plus a bar — session-v3f L215. */
    val Skip: ImageVector by lazy {
        strokeIcon("Skip", CARD_STROKE, "M5 5l8 7-8 7zM19 5v14")
    }

    /** Trash (destructive sheet item) — session-v3f L216. */
    val Trash: ImageVector by lazy {
        strokeIcon("Trash", CARD_STROKE, "M4 7h16M9 7V5h6v2M6 7l1 13h10l1-13")
    }

    /** Cancel-session (X) — session-v3f L234. */
    val Close: ImageVector by lazy {
        strokeIcon("Close", CARD_STROKE, "M6 6l12 12M18 6L6 18")
    }

    /**
     * The chart empty state's glyph — a rising zig-zag, pass2d §`s-empty` (`.empty .glyph
     * svg`, drawn at 22dp with the empty tile's own 1.6 stroke).
     */
    val ChartLine: ImageVector by lazy {
        strokeIcon("ChartLine", EMPTY_GLYPH_STROKE, "M3 17l5-6 4 4 5-8")
    }

    /**
     * External-link chevron — settings' out-of-app rows (pass2d §`s-set`): an arrow leaving
     * a box, `M7 17L17 7M9 7h8v8`, drawn at the `.chev` 18dp/1.8. Deliberately NOT the
     * in-app [ChevronRight]: the extraction (§5.3) keeps the two destinations visually
     * distinct.
     */
    val ExternalLink: ImageVector by lazy {
        strokeIcon("ExternalLink", CARD_STROKE, "M7 17L17 7M9 7h8v8")
    }

    /** `.mseg` theme glyph 1/3 — a monitor (`title="Системная"`), pass2d §`s-set`. */
    val ThemeSystem: ImageVector by lazy {
        strokeIcon(
            "ThemeSystem",
            MSEG_STROKE,
            "M5 4h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z" +
                " M8 21h8",
        )
    }

    /** `.mseg` theme glyph 2/3 — a sun (`title="Светлая"`). Circle rewritten as two arcs. */
    val ThemeLight: ImageVector by lazy {
        strokeIcon(
            "ThemeLight",
            MSEG_STROKE,
            "M8 12a4 4 0 1 0 8 0a4 4 0 1 0-8 0Z" +
                " M12 2v2M12 20v2M2 12h2M20 12h2" +
                " M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M19.1 4.9l-1.4 1.4M6.3 17.7l-1.4 1.4",
        )
    }

    /** `.mseg` theme glyph 3/3 — a moon (`title="Тёмная"`). */
    val ThemeDark: ImageVector by lazy {
        strokeIcon("ThemeDark", MSEG_STROKE, "M20 13.5A8 8 0 1 1 10.5 4a6.5 6.5 0 0 0 9.5 9.5z")
    }

    /**
     * The ordinal chip's done checkmark — session-v3f L345, rendered at 13dp with a 3-unit
     * stroke. Not the set mark's tick (L390, `M5 12.5l4.5 4.5L19 7.5`): the mockup draws two
     * subtly different checks and the difference ships as drawn.
     */
    val OrdinalCheck: ImageVector by lazy {
        strokeIcon("OrdinalCheck", ORDINAL_CHECK_STROKE, "M4 12.5l5 5L20 7")
    }

    /**
     * The selected-row check — pass2d's `sh-pick` `.mitem.on` (L366): [OrdinalCheck]'s path
     * at the standard `.chev` weight (18×18, 1.8 stroke, L73) instead of the ordinal chip's
     * heavy 3. Same drawing, two declared weights; both ship as drawn.
     */
    val Check: ImageVector by lazy {
        strokeIcon("Check", CARD_STROKE, "M4 12.5l5 5L20 7")
    }

    /**
     * The FAB's own plus — `pass2d.html` `.gplus` inside `#morphA`, drawn at the FAB's heavier
     * [FAB_STROKE] rather than [Plus]'s 1.9. Same path, third declared weight; the mockup
     * declares stroke per context and so does this file.
     */
    val FabPlus: ImageVector by lazy {
        strokeIcon("FabPlus", FAB_STROKE, "M12 5v14M5 12h14")
    }

    /**
     * The archive mark, at the FAB weight — `pass2d.html` `.garch`.
     *
     * It replaces a trash glyph that was drawing a promise the action does not make: the bulk
     * action archives and archive is reversible, so §1's "`rust` marks destruction only" and a
     * deletion glyph were both describing something else. See §26 "FAB in selection mode".
     */
    val FabArchive: ImageVector by lazy {
        strokeIcon("FabArchive", FAB_STROKE, ARCHIVE_PATH)
    }

    /** The same archive mark at the top-bar weight — the selection bar's trailing action. */
    val Archive: ImageVector by lazy {
        strokeIcon("Archive", TOPBAR_STROKE, ARCHIVE_PATH)
    }

    /**
     * The selected-row check as `#s-list` draws it — [Check]'s path at [ROW_CHECK_STROKE].
     * The list redraws the picker's mark heavier (2.2 against 1.8); the third weight of one
     * drawing, and all three ship as drawn.
     */
    val RowCheck: ImageVector by lazy {
        strokeIcon("RowCheck", ROW_CHECK_STROKE, "M4 12.5l5 5L20 7")
    }

    /**
     * The trainings mark — `pass2d.html` `#s-empty`'s first `.empty .glyph`.
     *
     * **Owed a second consumer.** §26 "Bottom navigation" takes the nav bar's trainings icon from
     * this exact path ("the drawn empty-state glyphs verbatim"), so when the bar is rebuilt the
     * empty state and the nav bar become one mark and changing either changes both. Today the bar
     * still draws `@DrawableRes` XML (`BottomBarItem`), so this path has one call site. The
     * coupling is a decision already taken, not a fact about the tree — do not read it as one.
     */
    val Trainings: ImageVector by lazy {
        strokeIcon("Trainings", EMPTY_GLYPH_STROKE, "M4 12h3l2.5-7 5 14L17 12h3")
    }

    /**
     * The exercises mark — `pass2d.html` `#s-empty`'s third `.empty .glyph`.
     *
     * **Doubly load-bearing, exactly as [Trainings] is:** §26 "Bottom navigation" takes the nav
     * bar's icons from "the drawn empty-state glyphs verbatim", and `#s-list`'s bottom-clearance
     * frame draws this same path as the bar's third tab. The empty state and the nav bar are one
     * mark; changing either changes both.
     *
     * The three `h.01` segments are zero-length strokes. With [StrokeCap.Round] they render as
     * dots — the bullet column of a list glyph — which is how the SVG draws them and why they are
     * not rewritten as circles.
     */
    val Exercises: ImageVector by lazy {
        strokeIcon(
            "Exercises",
            EMPTY_GLYPH_STROKE,
            "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01",
        )
    }

    /** Lid, body, pull. One path so it diffs against the mockup line-for-line. */
    private const val ARCHIVE_PATH = "M4 8h16M6 8v11h12V8M10 12h4"

    /** 2.1 — the FAB glyph weight (`.fab svg`, 24dp glyphs). */
    private const val FAB_STROKE = 2.1f

    /** 2.2 — the list row's check (`.chk`), heavier than the picker's [CARD_STROKE]. */
    private const val ROW_CHECK_STROKE = 2.2f

    /** 1.7 — the top-bar stroke weight (21dp glyphs). */
    private const val TOPBAR_STROKE = 1.7f

    /** 1.8 — the card/sheet stroke weight (17dp and 19dp glyphs). */
    private const val CARD_STROKE = 1.8f

    /** 1.9 — the `.addex` plus is drawn a touch heavier than the card glyphs (L145). */
    private const val ADDEX_STROKE = 1.9f

    /** 2.2 — the `.exhead .swap` chevron (pass2d L219). */
    private const val SWAP_STROKE = 2.2f

    /** 1.6 — the empty-state glyph (pass2d `.empty .glyph svg`). */
    private const val EMPTY_GLYPH_STROKE = 1.6f

    /** 1.9 — the `.mseg` theme glyphs (pass2d `.mseg svg`), same weight as the addex plus. */
    private const val MSEG_STROKE = 1.9f

    /** 3 — the ordinal chip's check is a heavy stroke at a tiny render size (L94). */
    private const val ORDINAL_CHECK_STROKE = 3f

    private const val VIEWPORT = 24f

    /** SVG `<circle cx="12" cy="…" r="1.4">` as a two-arc path, centred on the glyph axis. */
    private fun circlePath(cy: String): String =
        "M10.6 ${cy}a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0-2.8 0Z"

    private fun strokeIcon(
        name: String,
        strokeWidth: Float,
        pathData: String,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = VIEWPORT.dp,
        defaultHeight = VIEWPORT.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}
