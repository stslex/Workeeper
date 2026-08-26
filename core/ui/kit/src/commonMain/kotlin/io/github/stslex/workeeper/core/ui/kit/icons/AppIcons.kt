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
 * The v3 icon set, transcribed stroke-for-stroke from the mockups' inline SVGs as stroked paths in
 * a 24-unit viewBox. Stroke widths are in viewBox units, declared per context.
 */
object AppIcons {

    /** Top-bar back affordance — session-v3f L190. Mirrors in RTL: "back" is a direction. */
    val ChevronLeft: ImageVector by lazy {
        strokeIcon("ChevronLeft", TOPBAR_STROKE, "M15 5l-7 7 7 7", autoMirror = true)
    }

    /** Vertical three-dot overflow — session-v3f L192 (top bar) and L353 (card `.mini.menu`). */
    val MoreVertical: ImageVector by lazy {
        strokeIcon(
            "MoreVertical",
            TOPBAR_STROKE,
            circlePath(cy = "5") + circlePath(cy = "12") + circlePath(cy = "19"),
        )
    }

    /** Card expand affordance, rotates 90 degrees when the card is active — session-v3f L354. */
    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight", CARD_STROKE, "M9 6l6 6-6 6", autoMirror = true)
    }

    /** The chart's exercise-switcher chevron, inside `.exhead .swap` — pass2d L236, 16dp. */
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

    /** The chart empty state's glyph — a rising zig-zag, pass2d `.empty .glyph svg`, 22dp. */
    val ChartLine: ImageVector by lazy {
        strokeIcon("ChartLine", EMPTY_GLYPH_STROKE, "M3 17l5-6 4 4 5-8")
    }

    /** External-link chevron — settings' out-of-app rows. Not the in-app [ChevronRight]. */
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
     * The ordinal chip's done checkmark — session-v3f L345, 13dp at a 3-unit stroke. Not the set
     * mark's tick (L390, `M5 12.5l4.5 4.5L19 7.5`): the mockup draws two different checks.
     */
    val OrdinalCheck: ImageVector by lazy {
        strokeIcon("OrdinalCheck", ORDINAL_CHECK_STROKE, "M4 12.5l5 5L20 7")
    }

    /** The selected-row check — pass2d `sh-pick` `.mitem.on` (L366): [OrdinalCheck]'s path. */
    val Check: ImageVector by lazy {
        strokeIcon("Check", CARD_STROKE, "M4 12.5l5 5L20 7")
    }

    /** The FAB's own plus — `pass2d.html` `.gplus` inside `#morphA`, at [FAB_STROKE]. */
    val FabPlus: ImageVector by lazy {
        strokeIcon("FabPlus", FAB_STROKE, "M12 5v14M5 12h14")
    }

    /** The archive mark at FAB weight — `.garch`. Not a trash glyph: archiving is reversible. */
    val FabArchive: ImageVector by lazy {
        strokeIcon("FabArchive", FAB_STROKE, ARCHIVE_PATH)
    }

    /** The same archive mark at the top-bar weight — the selection bar's trailing action. */
    val Archive: ImageVector by lazy {
        strokeIcon("Archive", TOPBAR_STROKE, ARCHIVE_PATH)
    }

    /** The selected-row check as `#s-list` draws it — [Check]'s path at [ROW_CHECK_STROKE]. */
    val RowCheck: ImageVector by lazy {
        strokeIcon("RowCheck", ROW_CHECK_STROKE, "M4 12.5l5 5L20 7")
    }

    /** The home mark — `pass2d.html` `#s-nav`'s first `.nb button`, at [NAV_GLYPH_STROKE]. */
    val Home: ImageVector by lazy {
        strokeIcon(
            "Home",
            NAV_GLYPH_STROKE,
            "M4 10.5L12 4l8 6.5V19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1z",
        )
    }

    /**
     * The trainings mark — `pass2d.html` `#s-empty`'s first `.empty .glyph` and `#s-nav`'s second
     * `.nb button`. One [ImageVector] so the two surfaces cannot drift apart.
     */
    val Trainings: ImageVector by lazy {
        strokeIcon("Trainings", EMPTY_GLYPH_STROKE, "M4 12h3l2.5-7 5 14L17 12h3")
    }

    /**
     * The exercises mark — `#s-empty`'s third `.empty .glyph` and `#s-nav`'s third `.nb button`.
     * The three `h.01` segments are zero-length strokes, drawn as dots by [StrokeCap.Round].
     */
    val Exercises: ImageVector by lazy {
        strokeIcon(
            "Exercises",
            EMPTY_GLYPH_STROKE,
            "M8 6h12M8 12h12M8 18h12M4 6h.01M4 12h.01M4 18h.01",
        )
    }

    /** Weighted exercise type mark — a dumbbell. `#s-editor` form 5, at [THUMB_STROKE]. */
    val ExerciseWeighted: ImageVector by lazy {
        strokeIcon("ExerciseWeighted", THUMB_STROKE, "M4 9v6M7 7v10M17 7v10M20 9v6M7 12h10")
    }

    /** Weightless exercise type mark — a figure. `#s-editor` form 5, at [THUMB_STROKE]. */
    val ExerciseWeightless: ImageVector by lazy {
        strokeIcon(
            "ExerciseWeightless",
            THUMB_STROKE,
            "M9.9 5a2.1 2.1 0 1 0 4.2 0a2.1 2.1 0 1 0-4.2 0Z" +
                " M12 8v6M12 14l-3 6M12 14l3 6M6 10.5h12",
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

    /** 1.7 — `.thumb svg{stroke-width:1.7}`, the two exercise type marks. */
    private const val THUMB_STROKE = TOPBAR_STROKE

    /** 1.8 — the card/sheet stroke weight (17dp and 19dp glyphs). */
    private const val CARD_STROKE = 1.8f

    /** 1.9 — the `.addex` plus is drawn a touch heavier than the card glyphs (L145). */
    private const val ADDEX_STROKE = 1.9f

    /** 2.2 — the `.exhead .swap` chevron (pass2d L219). */
    private const val SWAP_STROKE = 2.2f

    /** 1.6 — the empty-state glyph (pass2d `.empty .glyph svg`). */
    private const val EMPTY_GLYPH_STROKE = 1.6f

    /**
     * The nav bar's glyph weight. `.nb button svg` declares 1.7; resolved to [EMPTY_GLYPH_STROKE]
     * so the nav and empty-state marks share one [ImageVector].
     */
    private const val NAV_GLYPH_STROKE = EMPTY_GLYPH_STROKE

    /** 1.9 — the `.mseg` theme glyphs (pass2d `.mseg svg`), same weight as the addex plus. */
    private const val MSEG_STROKE = 1.9f

    /** 3 — the ordinal chip's check is a heavy stroke at a tiny render size (L94). */
    private const val ORDINAL_CHECK_STROKE = 3f

    private const val VIEWPORT = 24f

    /** SVG `<circle cx="12" cy="…" r="1.4">` as a two-arc path, centred on the glyph axis. */
    private fun circlePath(cy: String): String =
        "M10.6 ${cy}a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0-2.8 0Z"

    /** @param autoMirror flips the glyph under RTL — set it on directional marks only. */
    private fun strokeIcon(
        name: String,
        strokeWidth: Float,
        pathData: String,
        autoMirror: Boolean = false,
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = VIEWPORT.dp,
        defaultHeight = VIEWPORT.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
        autoMirror = autoMirror,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = strokeWidth,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ).build()
}
