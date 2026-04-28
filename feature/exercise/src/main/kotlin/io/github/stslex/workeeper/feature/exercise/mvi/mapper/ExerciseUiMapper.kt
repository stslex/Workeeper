// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.mvi.mapper

import io.github.stslex.workeeper.core.core.resources.ResourceWrapper
import io.github.stslex.workeeper.core.exercise.exercise.model.HistoryEntry
import io.github.stslex.workeeper.core.exercise.exercise.model.SetSummary
import io.github.stslex.workeeper.core.exercise.tags.model.TagDataModel
import io.github.stslex.workeeper.core.ui.plan_editor.mappers.formatPlanSummary
import io.github.stslex.workeeper.core.ui.plan_editor.model.PlanSetUiModel
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.model.TagUiModel
import kotlinx.collections.immutable.ImmutableList

private const val MAX_VISIBLE_SETS = 5

internal fun TagDataModel.toUi(): TagUiModel = TagUiModel(uuid = uuid, name = name)

internal fun HistoryEntry.toUi(
    resourceWrapper: ResourceWrapper,
): HistoryUiModel {
    val dateLabel = resourceWrapper.formatMediumDate(finishedAt)
    val metaLabel = if (isAdhoc) {
        resourceWrapper.getString(R.string.feature_exercise_detail_history_meta_adhoc_format, dateLabel)
    } else {
        resourceWrapper.getString(
            R.string.feature_exercise_detail_history_meta_format,
            dateLabel,
            trainingName,
        )
    }
    return HistoryUiModel(
        sessionUuid = sessionUuid,
        setsSummaryLabel = sets.toSummaryLabel(),
        metaLabel = metaLabel,
    )
}

internal fun ImmutableList<PlanSetUiModel>?.toAdhocPlanSummary(
    resourceWrapper: ResourceWrapper,
): String = this
    ?.takeIf { it.isNotEmpty() }
    ?.formatPlanSummary()
    ?: resourceWrapper.getString(R.string.feature_exercise_edit_plan_summary_no_plan)

private fun List<SetSummary>.toSummaryLabel(): String {
    val visible = take(MAX_VISIBLE_SETS).map { it.toSummaryPart() }
    val suffix = if (size > MAX_VISIBLE_SETS) " · …" else ""
    return visible.joinToString(separator = " · ") + suffix
}

private fun SetSummary.toSummaryPart(): String {
    val setWeight = weight ?: return reps.toString()
    val weightLabel = if (setWeight % 1.0 == 0.0) {
        setWeight.toLong().toString()
    } else {
        setWeight.toString().trimEnd('0').trimEnd('.')
    }
    return "$weightLabel × $reps"
}
