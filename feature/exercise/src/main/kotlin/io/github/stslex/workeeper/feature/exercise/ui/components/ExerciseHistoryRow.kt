// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.exercise.R
import io.github.stslex.workeeper.feature.exercise.mvi.model.HistoryUiModel
import io.github.stslex.workeeper.feature.exercise.mvi.model.SetSummaryUi
import java.text.DateFormat
import java.util.Date

private const val MAX_VISIBLE_SETS = 5

@Composable
internal fun ExerciseHistoryRow(
    item: HistoryUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val dateLabel = remember(item.finishedAt) { dateFormatter.format(Date(item.finishedAt)) }
    val metaLabel = if (item.isAdhoc) {
        stringResource(R.string.feature_exercise_detail_history_meta_adhoc_format, dateLabel)
    } else {
        stringResource(
            R.string.feature_exercise_detail_history_meta_format,
            dateLabel,
            item.trainingName,
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .clickable(onClick = onClick)
            .padding(AppDimension.cardPadding)
            .testTag("ExerciseHistoryRow_${item.sessionUuid}"),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        Text(
            text = item.sets.formatSummary(),
            style = AppUi.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = metaLabel,
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
    }
}

private fun List<SetSummaryUi>.formatSummary(): String {
    val visible = take(MAX_VISIBLE_SETS).map { it.formatSet() }
    val suffix = if (size > MAX_VISIBLE_SETS) " · …" else ""
    return visible.joinToString(separator = " · ") + suffix
}

private fun SetSummaryUi.formatSet(): String {
    val weight = weight ?: return reps.toString()
    val weightStr = if (weight % 1.0 == 0.0) {
        weight.toLong().toString()
    } else {
        weight.toString().trimEnd('0').trimEnd('.')
    }
    return "$weightStr × $reps"
}
