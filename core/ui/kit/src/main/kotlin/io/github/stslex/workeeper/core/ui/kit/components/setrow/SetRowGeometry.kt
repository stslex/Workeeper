// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.setrow

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.max
import io.github.stslex.workeeper.core.ui.kit.components.setchip.CHIP_MIN_WIDTH
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The single source of the set-row column geometry shared by `LiveSetRow`,
 * `PastSetEditRow` and [SetColumnHeader] (set-field-column-headers.md §4 D3).
 *
 * Before this object the index minimum lived twice (`LiveSetRow`'s inline `widthIn` and
 * `PastSetEditRow.SetIndexWidth`) and the weight flex twice (`WEIGHT_FIELD_FLEX` /
 * `WEIGHT_COLUMN_FLEX`, whose own KDoc named this lift for "if a third consumer appears").
 * The header is that third consumer: a header laid out from its own copies of these
 * numbers drifts silently the day either row changes — two identical numbers in two files
 * was the failure mode D3 exists to close.
 */
object SetRowGeometry {

    /**
     * `.set-i { width: 13px }` → 12dp, as a **minimum** — the drawn column for a
     * single-digit index. Rows keep `widthIn(min = …)` semantics with this default, so a
     * bare row (previews, direct-row goldens) is byte-identical to the pre-header layout.
     */
    val indexMinWidth: Dp = AppDimension.Space.md

    /**
     * Weights carry decimals ("102.5"), reps never do — the extra fifth softens the width
     * budget. 1.2 vs the mockup's flex:1/1; the deviation predates this object and is
     * reported with the original row PRs.
     */
    const val WEIGHT_COLUMN_FLEX: Float = 1.2f

    /**
     * The chip-or-PR-tag slot both rows draw after the fields — a REFERENCE to the chip's
     * own `CHIP_MIN_WIDTH` (which `PersonalRecordTag` shares), re-exposed so the features
     * can hand `SetColumnHeader` a trailing gutter built from component numbers, never
     * from copies.
     */
    val setTypeSlotWidth: Dp = CHIP_MIN_WIDTH

    /**
     * The horizontal inset a SET-ROW field passes to `AppNumberInput.fieldInset` (R13,
     * set-field-column-headers.md §7a): the dense weighted split earns back 8dp of value
     * budget per field over the drawn `Space.md`, by explicit consumer choice — the first
     * cut's 105dp width threshold was a tripwire calibrated to a 3.3dp gap in today's
     * geometry. One source for the rows AND the header's label inset, so the label sits
     * exactly over the value it names; `PlanSetCard` never reads it and keeps the default.
     */
    val compactFieldInset: Dp = AppDimension.Space.sm

    /**
     * The index column width for a card with [setCount] visible rows, resolved by
     * **measurement** rather than by a table: the widest index label is `setCount` itself
     * (`mono.meta` digits are tabular), measured through the same text stack `Text` uses,
     * at the current density and font scale — so the resolution survives fontScale where
     * the old fixed 12dp box broke a single digit at ~1.6.
     *
     * Containers (`SetsColumn`, the past card body) resolve once and pass the same value
     * to the header and to every row; at 1-9 sets this is exactly [indexMinWidth] and the
     * layout is unchanged, at 10+ the header and the rows grow **together** instead of
     * the rows shifting ~3dp out from under a static header.
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
