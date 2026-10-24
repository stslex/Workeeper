// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.pr.PersonalRecordTag
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.HistoryUiModel

/**
 * §3.5 — one full-bleed 88dp `.row`: the date as the row name (two-line clamp), the
 * compact set summary as the meta sub-line, and ONE trailing element — the static dim
 * chevron, or [PersonalRecordTag] on the record row (chip-or-tag, never both; the record
 * row keeps its whole-row tap while the tag itself opens the explainer). Rules between
 * rows belong to the list, not the row.
 */
@Composable
internal fun ExerciseHistoryRow(
    item: HistoryUiModel,
    isRecord: Boolean,
    onClick: () -> Unit,
    onPrTagClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AppDimension.screenEdge)
            .defaultMinSize(minHeight = AppDimension.rowHeight)
            .testTag("ExerciseHistoryRow_${item.sessionUuid}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = AppDimension.Space.md),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
        ) {
            Text(
                text = item.dateLabel,
                style = AppUi.typography.text.body.copy(fontWeight = FontWeight.Medium),
                color = AppUi.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.setsSummaryLabel,
                style = AppUi.typography.mono.meta,
                color = AppUi.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isRecord) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(AppDimension.Radius.small))
                    .clickable(onClick = onPrTagClick),
            ) {
                PersonalRecordTag()
            }
        } else {
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = AppUi.colors.textDim,
            )
        }
    }
}

@Preview
@Composable
private fun ExerciseHistoryRowLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        ExerciseHistoryRow(
            item = HistoryUiModel(
                sessionUuid = "session-1",
                dateLabel = "22 июля",
                setsSummaryLabel = "7×12 · 7×12 · 7×12 · 7×12",
            ),
            isRecord = false,
            onClick = {},
            onPrTagClick = {},
        )
    }
}

@Preview
@Composable
private fun ExerciseHistoryRowRecordDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        ExerciseHistoryRow(
            item = HistoryUiModel(
                sessionUuid = "session-2",
                dateLabel = "12 июля",
                setsSummaryLabel = "5×12 · 6×12 · 9×12 · 7×12",
            ),
            isRecord = true,
            onClick = {},
            onPrTagClick = {},
        )
    }
}
