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
 * Single source of the set-row column geometry shared by both set rows and [SetColumnHeader].
 * See documentation/feature-specs/set-field-column-headers.md.
 */
object SetRowGeometry {

    /** Minimum index-column width; rows keep `widthIn(min = …)` semantics with this default. */
    val indexMinWidth: Dp = AppDimension.Space.md

    /** Weight-column flex; weights carry decimals and reps never do. */
    const val WEIGHT_COLUMN_FLEX: Float = 1.2f

    /** The chip-or-PR-tag slot both rows draw after the fields; alias of the chip's minimum. */
    val setTypeSlotWidth: Dp = CHIP_MIN_WIDTH

    /**
     * Trailing chip-or-tag slot width, measured rather than pinned: the record tag outgrows
     * the shared minimum at large font scales. Rows and the header both size it from here.
     */
    @Composable
    fun resolveTrailingSlotWidth(): Dp = max(setTypeSlotWidth, personalRecordTagIntrinsicWidth())

    /**
     * Horizontal inset set-row fields pass to `AppNumberInput.fieldInset`, and the header's
     * label inset, so each label sits exactly over the value it names.
     */
    val compactFieldInset: Dp = AppDimension.Space.sm

    /**
     * Index-column width for a card with [setCount] rows, measured because a fixed box clips
     * at large font scales. Containers resolve once and pass one value to header and rows.
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
