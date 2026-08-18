// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.live_workout.gate

import android.view.ViewGroup.LayoutParams
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import io.github.stslex.workeeper.core.ui.kit.components.button.AppCheckmarkButtonTouchSize
import io.github.stslex.workeeper.core.ui.kit.components.setrow.SetColumnHeader
import io.github.stslex.workeeper.core.ui.kit.components.setrow.SetRowGeometry
import io.github.stslex.workeeper.core.ui.kit.golden.GOLDEN_DEVICE
import io.github.stslex.workeeper.core.ui.kit.golden.OverflowGateSdk
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.plan_editor.model.SetTypeUiModel
import io.github.stslex.workeeper.feature.live_workout.mvi.model.LiveSetUiModel
import io.github.stslex.workeeper.feature.live_workout.ui.components.LiveSetRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.math.abs
import io.github.stslex.workeeper.core.ui.kit.R as KitR

/**
 * The set-column header/row alignment assertion of
 * documentation/feature-specs/set-field-column-headers.md §7a, in the layoutlib stack.
 *
 * TWO claims, both necessary:
 *  - EDGES, the contract itself: each header label's rendered left edge equals its column's
 *    value left edge. Width equality alone cannot see an inset drift — a field inset can
 *    move while the header label's stays put and every column width stays equal.
 *  - GUTTER: the header's index gutter equals the row's index column — necessary and not
 *    sufficient; it stays because a gutter drift at 10+ sets moves every edge at once and
 *    this assert names the culprit directly.
 *
 * The edges are read from the SEMANTICS TREE of the rendered composition: the label is
 * addressable by its text, the field by its accessibility label, and
 * `ViewRootForTest.semanticsOwner` is the same public access path Paparazzi's own
 * accessibility extension uses under layoutlib — so the edge capture leaves ZERO trace in
 * production composables; do not add test tags to reach it. The gutter/index pair keeps its
 * `onSizeChanged` probes: they fire only on size change, never per frame.
 *
 * Asserted at ONE set and at TEN sets — the count where the index text outgrows the 12dp
 * minimum of the §5 width budget. layoutlib rather than Robolectric because the claim is
 * about real text metrics: Robolectric does not widen the index column with digit count, so
 * the drift scenario is unreachable there and the growth precondition below fails.
 */
internal class SetColumnAlignmentGateTest {

    @Test
    fun headerLabelsSitOnTheirColumns() {
        val gate = OverflowGateSdk()
        gate.setup()
        val samples = mutableListOf<Sample>()
        try {
            val weightHeaderText = gate.context
                .getString(KitR.string.core_ui_kit_set_header_weight).uppercase() +
                " (" +
                gate.context.getString(KitR.string.core_ui_kit_plan_editor_unit_kg).uppercase() +
                ")"
            val repsHeaderText = gate.context
                .getString(KitR.string.core_ui_kit_set_header_reps).uppercase()
            val weightFieldLabel = gate.context
                .getString(KitR.string.core_ui_kit_set_field_a11y_weight)
            val repsFieldLabel = gate.context
                .getString(KitR.string.core_ui_kit_set_field_a11y_reps)

            for (case in CASES) {
                gate.setFontScale(case.fontScale)
                val capture = Capture()
                val host = ComposeView(gate.context).apply {
                    layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                    setContent {
                        AppTheme(themeMode = ThemeMode.LIGHT) {
                            AlignedPair(case = case, capture = capture)
                        }
                    }
                }
                gate.renderView(host) {
                    val viewRoot = host.getChildAt(0) as ViewRootForTest
                    val nodes = viewRoot.semanticsOwner.getAllSemanticsNodes(mergingEnabled = false)
                    fun leftOfText(text: String): Float = nodes.single { node ->
                        node.config.getOrNull(SemanticsProperties.Text)
                            ?.any { it.text == text } == true
                    }.boundsInRoot.left
                    fun leftOfField(label: String): Float = nodes.single { node ->
                        node.config.getOrNull(SemanticsProperties.ContentDescription)
                            ?.contains(label) == true
                    }.boundsInRoot.left
                    capture.weightLabelLeft = leftOfText(weightHeaderText)
                    capture.repsLabelLeft = leftOfText(repsHeaderText)
                    capture.weightFieldLeft = leftOfField(weightFieldLabel)
                    capture.repsFieldLeft = leftOfField(repsFieldLabel)
                }
                samples += capture.toSample(case)
            }
        } finally {
            gate.teardown()
        }
        samples.forEach {
            println(
                "alignment: ${it.case.label} -> gutter ${it.gutterPx}px / index ${it.indexPx}px, " +
                    "weight label ${it.weightLabelLeft} vs field ${it.weightFieldLeft}, " +
                    "reps label ${it.repsLabelLeft} vs field ${it.repsFieldLeft}",
            )
        }
        check(samples.size == CASES.size) { "gate ran over ${samples.size} cases" }
        // Precondition: the ten-set index column must outgrow the one-set minimum in this
        // stack, or the gutter-drift scenario is unreachable here and a pass is vacuous.
        val oneSet = samples.first { it.case.setCount == 1 && it.case.fontScale == 1f }
        val tenSet = samples.first { it.case.setCount == TEN_SETS }
        check(tenSet.indexPx > oneSet.indexPx) {
            "index column did not grow (${oneSet.indexPx} -> ${tenSet.indexPx}px)" +
                " — defective instrument, not a passing gate"
        }
        assertAll(
            samples.flatMap { s ->
                listOf(
                    {
                        assertEquals(
                            s.indexPx,
                            s.gutterPx,
                            "${s.case.label}: the header gutter (${s.gutterPx}px) " +
                                "drifted from the row index column (${s.indexPx}px)",
                        )
                    },
                    {
                        assertTrue(
                            abs(s.weightLabelLeft - s.weightFieldLeft) <= EDGE_TOLERANCE_PX,
                            "${s.case.label}: the WEIGHT label left (${s.weightLabelLeft}px) " +
                                "drifted from its value left (${s.weightFieldLeft}px)",
                        )
                    },
                    {
                        assertTrue(
                            abs(s.repsLabelLeft - s.repsFieldLeft) <= EDGE_TOLERANCE_PX,
                            "${s.case.label}: the REPS label left (${s.repsLabelLeft}px) " +
                                "drifted from its value left (${s.repsFieldLeft}px)",
                        )
                    },
                )
            },
        )
    }

    /**
     * The header and the WIDEST row (the last: its index label is the set count itself)
     * exactly as `SetsColumn` composes them: one resolution, both consumers.
     */
    @Composable
    private fun AlignedPair(case: Case, capture: Capture) {
        val contentWidth = with(LocalDensity.current) {
            val consumedPx = 2 * (AppDimension.screenEdge + AppDimension.Space.md).roundToPx()
            (GOLDEN_DEVICE.screenWidth - consumedPx).toDp()
        }
        Box(modifier = Modifier.width(contentWidth)) {
            Column {
                val indexColumnWidth = SetRowGeometry.resolveIndexColumnWidth(case.setCount)
                SetColumnHeader(
                    isWeighted = true,
                    indexColumnWidth = indexColumnWidth,
                    trailingWidth = SetRowGeometry.resolveTrailingSlotWidth() +
                        AppDimension.Space.sm +
                        AppCheckmarkButtonTouchSize,
                    indexGutterProbe = { capture.gutterPx = it },
                )
                LiveSetRow(
                    set = LiveSetUiModel(
                        position = case.setCount - 1,
                        weight = 100.0,
                        reps = 5,
                        type = SetTypeUiModel.WORK,
                        isDone = false,
                        isPersonalRecord = case.isRecord,
                    ),
                    isWeighted = true,
                    onWeightChange = {},
                    onRepsChange = {},
                    onTypeChange = {},
                    onMarkDone = {},
                    onUncheck = {},
                    editable = true,
                    indexColumnWidth = indexColumnWidth,
                    indexColumnProbe = { capture.indexPx = it },
                )
            }
        }
    }

    private class Capture {
        var gutterPx: Int = -1
        var indexPx: Int = -1
        var weightLabelLeft: Float = Float.NaN
        var repsLabelLeft: Float = Float.NaN
        var weightFieldLeft: Float = Float.NaN
        var repsFieldLeft: Float = Float.NaN

        fun toSample(case: Case): Sample {
            check(gutterPx >= 0) { "header gutter probe never fired for ${case.label}" }
            check(indexPx >= 0) { "row index probe never fired for ${case.label}" }
            check(!weightLabelLeft.isNaN()) { "weight label not found for ${case.label}" }
            check(!repsLabelLeft.isNaN()) { "reps label not found for ${case.label}" }
            check(!weightFieldLeft.isNaN()) { "weight field not found for ${case.label}" }
            check(!repsFieldLeft.isNaN()) { "reps field not found for ${case.label}" }
            return Sample(
                case = case,
                gutterPx = gutterPx,
                indexPx = indexPx,
                weightLabelLeft = weightLabelLeft,
                repsLabelLeft = repsLabelLeft,
                weightFieldLeft = weightFieldLeft,
                repsFieldLeft = repsFieldLeft,
            )
        }
    }

    private data class Sample(
        val case: Case,
        val gutterPx: Int,
        val indexPx: Int,
        val weightLabelLeft: Float,
        val repsLabelLeft: Float,
        val weightFieldLeft: Float,
        val repsFieldLeft: Float,
    )

    /** One rendered header+row pair under test. */
    private data class Case(
        val label: String,
        val setCount: Int,
        val fontScale: Float,
        val isRecord: Boolean = false,
    )

    private companion object {
        const val TEN_SETS = 10

        /**
         * The axes that can move a column edge: the index gutter (set count), the text
         * scale, and WHICH trailing component the row draws — a record row swaps the type
         * chip for `PersonalRecordTag`, whose label outgrows the shared 34dp minimum at
         * fontScale 2.0, so a trailing slot pinned to that minimum leaves the record row's
         * fields narrower than the header's columns.
         */
        val CASES = listOf(
            Case(label = "1 set @1.0", setCount = 1, fontScale = 1f),
            Case(label = "$TEN_SETS sets @1.0", setCount = TEN_SETS, fontScale = 1f),
            Case(label = "1 set @2.0", setCount = 1, fontScale = 2f),
            Case(label = "1 record set @2.0", setCount = 1, fontScale = 2f, isRecord = true),
        )

        /** Two independent Rows accumulate px rounding; beyond one step is a drift. */
        const val EDGE_TOLERANCE_PX = 1.5f
    }
}
