// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.setrow

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import io.github.stslex.workeeper.core.ui.kit.components.pr.personalRecordTagIntrinsicWidth
import io.github.stslex.workeeper.core.ui.kit.components.setchip.CHIP_MIN_WIDTH
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The single source of the set-row column geometry shared by `LiveSetRow`,
 * `PastSetEditRow` and [SetColumnHeader] (set-field-column-headers.md §4 D3).
 *
 * Every consumer reads these values from here: a header laid out from its own copies of
 * these numbers drifts silently the day either row changes.
 */
object SetRowGeometry {

    /**
     * `.set-i { width: 13px }` → 12dp, as a **minimum** — the drawn column for a
     * single-digit index. Rows keep `widthIn(min = …)` semantics with this default, so a
     * bare row (previews, direct-row goldens) lays out from this value alone.
     */
    val indexMinWidth: Dp = AppDimension.Space.md

    /**
     * Weights carry decimals ("102.5"), reps never do — the extra fifth softens the width
     * budget (set-field-column-headers.md §5). The 1.2 deviates from the mockup's
     * `flex: 1/1` deliberately.
     */
    const val WEIGHT_COLUMN_FLEX: Float = 1.2f

    /**
     * The chip-or-PR-tag slot both rows draw after the fields — an alias of the chip's own
     * `CHIP_MIN_WIDTH` (which `PersonalRecordTag` shares), so the trailing gutter the
     * features hand `SetColumnHeader` is built from the component's number, never a copy.
     */
    val setTypeSlotWidth: Dp = CHIP_MIN_WIDTH

    /**
     * The width of the trailing chip-or-tag slot, resolved by MEASUREMENT: the type chip and
     * the record tag share a 34dp *minimum*, and the tag's label outgrows it above roughly
     * fontScale 1.6. A slot pinned to the minimum makes a record row's fields narrower than
     * its non-record siblings' and than the header's columns — the same drift class
     * [resolveIndexColumnWidth] closes on the leading side, on the axis that is easy to miss
     * because every fixture at fontScale 1.0 sees both components at exactly the minimum.
     *
     * Both rows and `SetColumnHeader` size this slot from here, so the columns cannot
     * disagree about it.
     */
    @Composable
    fun resolveTrailingSlotWidth(): Dp = max(setTypeSlotWidth, personalRecordTagIntrinsicWidth())

    /**
     * The horizontal inset a SET-ROW field passes to `AppNumberInput.fieldInset`
     * (set-field-column-headers.md §7a): the dense weighted split earns back 8dp of value
     * budget per field over the drawn `Space.md`, by explicit consumer choice. One source
     * for the rows AND the header's label inset, so the label sits exactly over the value
     * it names; `PlanSetCard` never reads it and keeps the default.
     */
    val compactFieldInset: Dp = AppDimension.Space.sm

    /**
     * The index column width for a card with [setCount] visible rows, resolved by
     * **measurement** rather than by a table: the widest index label is `setCount` itself
     * (`mono.meta` digits are tabular), measured through the same text stack `Text` uses,
     * at the current density and font scale — a fixed 12dp box clips a single digit at
     * fontScale ~1.6.
     *
     * Containers (`SetsColumn`, the past card body) resolve once and pass the same value
     * to the header and to every row; at 1-9 sets this is exactly [indexMinWidth], and at
     * 10+ the header and the rows must grow **together** or the rows shift ~3dp out from
     * under a static header.
     */
    @Composable
    fun resolveIndexColumnWidth(setCount: Int): Dp {
        val measurer = rememberTextMeasurer()
        val measured = measurer.measure(
            text = AnnotatedString(setCount.toString()),
            style = AppUi.typography.mono.meta,
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
        )
        val measuredWidth = with(LocalDensity.current) { measured.size.width.toDp() }
        return max(indexMinWidth, measuredWidth)
    }
}
