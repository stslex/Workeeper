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
