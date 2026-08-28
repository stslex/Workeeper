// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.plan_editor.model

import kotlinx.collections.immutable.ImmutableList

object PlanEditorUIMapper {

    private const val MAX_VISIBLE_SETS = 5

    /**
     * Compact "100×5 · 100×5 · 102.5×5" string used by Training-detail / Training-edit rows.
     * Trims trailing zeros from weights and falls back to reps-only when weight is null.
     */
    fun ImmutableList<PlanSetUiModel>.formatPlanSummary(): String {
        val visible = take(MAX_VISIBLE_SETS).map { it.formatSet() }
        val suffix = if (size > MAX_VISIBLE_SETS) " · …" else ""
        return visible.joinToString(separator = " · ") + suffix
    }

    private fun PlanSetUiModel.formatSet(): String {
        val w = weight ?: return reps.toString()
        val weightStr = if (w % 1.0 == 0.0) w.toLong().toString() else w.toString()
        return "$weightStr×$reps"
    }
}
