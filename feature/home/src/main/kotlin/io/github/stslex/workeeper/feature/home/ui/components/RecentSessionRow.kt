// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListRow
import io.github.stslex.workeeper.core.ui.kit.components.list.AppListRowSlot
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem

/**
 * One finished session on Home, on the [AppListRow] skeleton; the meta line reads
 * when · how long · how much. No lift and no slot selector — this list is FINISHED-only.
 */
@Composable
internal fun RecentSessionRow(
    item: RecentSessionItem,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppListRow(
        modifier = modifier,
        rowModifier = Modifier
            .clickable(onClick = onClick)
            .testTag("HomeRecentRow_${item.sessionUuid}"),
        name = item.trainingName,
        nameTestTag = "HomeRecentName_${item.sessionUuid}",
        meta = item.metaLine(),
        metaTestTag = "HomeRecentMeta_${item.sessionUuid}",
        showDivider = showDivider,
        // Always a chevron: Home has no selection mode.
        content = {
            AppListRowSlot {
                Icon(
                    modifier = Modifier.size(SLOT),
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = AppUi.colors.textTertiary,
                )
            }
        },
    )
}

/** Joins the pre-formatted meta tokens; the order is pinned by `RecentMetaLineTest`. */
internal fun RecentSessionItem.metaLine(): String =
    listOf(finishedAtRelativeLabel, durationLabel, statsLabel)
        .filter { it.isNotEmpty() }
        .joinToString(META_SEPARATOR)

/** The interpunct the drawing joins meta tokens with. */
private const val META_SEPARATOR = " · "

/** The drawn 20px glyph, on the icon ladder — the slot's own width is `AppListRowSlot`'s. */
private val SLOT = AppDimension.iconSm

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun RecentSessionRowPreview() {
    AppTheme {
        Column {
            RecentSessionRow(
                item = RecentSessionItem(
                    sessionUuid = "s1",
                    trainingName = "Верх (с подтягиваниями)",
                    isAdhoc = false,
                    finishedAtRelativeLabel = "вчера",
                    durationLabel = "47:12",
                    statsLabel = "5 упражнений · 18 подходов",
                ),
                showDivider = true,
                onClick = {},
            )
            RecentSessionRow(
                item = RecentSessionItem(
                    sessionUuid = "s2",
                    trainingName = "Свободная тренировка",
                    isAdhoc = true,
                    finishedAtRelativeLabel = "3 дня назад",
                    durationLabel = "22:04",
                    statsLabel = "2 упражнения · 6 подходов",
                ),
                showDivider = false,
                onClick = {},
            )
        }
    }
}
