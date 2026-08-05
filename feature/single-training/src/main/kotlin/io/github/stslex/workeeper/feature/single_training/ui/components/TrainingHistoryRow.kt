// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.single_training.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.single_training.mvi.model.HistorySessionItem

/**
 * ED9: history stays a RULED LIST while the exercises above it are cards — a session is an
 * event, not a thing with contents, and two lists of identical rows merge visually no matter
 * how much air sits between them. One full-bleed `.row` on the exercise read screen's own
 * grammar (extraction §3.5): the date as the row name, the static dim chevron trailing.
 * Rules between rows belong to the list, not the row.
 */
@Composable
internal fun TrainingHistoryRow(
    item: HistorySessionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimension.screenEdge)
            .defaultMinSize(minHeight = AppDimension.rowHeight)
            .testTag("TrainingHistoryRow_${item.sessionUuid}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = AppDimension.Space.md),
            text = item.dateLabel,
            style = AppUi.typography.text.body.copy(fontWeight = FontWeight.Medium),
            color = AppUi.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            modifier = Modifier.size(AppDimension.iconSm),
            imageVector = AppIcons.ChevronRight,
            contentDescription = null,
            tint = AppUi.colors.textDim,
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
                dateLabel = "27 июля 2026 г.",
            ),
            onClick = {},
        )
    }
}
