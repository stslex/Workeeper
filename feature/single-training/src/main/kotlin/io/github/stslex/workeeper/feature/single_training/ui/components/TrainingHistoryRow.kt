// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui.components

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
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.single_training.mvi.model.HistorySessionItem
import java.text.DateFormat
import java.util.Date

@Composable
internal fun TrainingHistoryRow(
    item: HistorySessionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val dateLabel = remember(item.finishedAt) { dateFormatter.format(Date(item.finishedAt)) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppUi.shapes.medium)
            .background(AppUi.colors.surfaceTier1)
            .clickable(onClick = onClick)
            .padding(AppDimension.cardPadding)
            .testTag("TrainingHistoryRow_${item.sessionUuid}"),
        verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xxs),
    ) {
        Text(
            text = dateLabel,
            style = AppUi.typography.bodyMedium,
            color = AppUi.colors.textPrimary,
        )
        Text(
            text = item.trainingName,
            style = AppUi.typography.bodySmall,
            color = AppUi.colors.textTertiary,
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TrainingHistoryRowPreview() {
    AppTheme {
        TrainingHistoryRow(
            item = HistorySessionItem(
                sessionUuid = "1",
                finishedAt = System.currentTimeMillis() - 24 * 60 * 60 * 1000L,
                trainingName = "Push day A",
                exerciseCount = 5,
            ),
            onClick = {},
        )
    }
}
