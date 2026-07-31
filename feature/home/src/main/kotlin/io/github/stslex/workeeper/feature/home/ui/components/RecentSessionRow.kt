// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.feature.home.mvi.model.RecentSessionItem

/**
 * One finished session on Home — `pass2d.html` `#s-list` `.row`, the **third payload** in the
 * skeleton §26 "List row" says carries four.
 *
 * ## What it was
 *
 * An `AppCard` holding three stacked texts — name at `titleMedium`, stats at `bodyMedium`, then
 * «relative · duration» at `labelSmall` — inset in a 12dp-gapped `LazyColumn`. Three lines and a
 * card where the drawing has two lines and a rule; the v2.4 treatment, unchanged since before the
 * arc.
 *
 * ## What it is
 *
 * The drawn skeleton, identical to `TrainingRow` and `ExerciseRow` in structure: an 88dp ruled
 * full-bleed row, name clamped to two lines with ellipsis, a single-line meta, a fixed-width
 * trailing slot with a chevron. `min-height` holds every row to one size, which is what lets four
 * payloads share one skeleton — measured on this module's own goldens at **88.0 / 88.0 / 88.0 /
 * 88.0 dp**, the fourth of which is the two-line clamp.
 *
 * ## The meta line's order, which is a decision and not a transcription
 *
 * §26 "Meta-line order" fixes the *rule* — information first, tags last, because the line does not
 * wrap so what truncates is always the tail. It does not fix Home's tokens, because **Home is not
 * drawn**: `pass2d.html` has eight sections and none of them is this screen. The nearest drawn
 * session row is `#s-nav`'s demo — «Спина и бицепс» over «12 июля · 6 упражнений» — which puts
 * *when* first and *how much* second.
 *
 * This row follows it and inserts duration between the two: **when · how long · how much**
 * («вчера · 47:12 · 5 упражнений · 18 подходов»). Rationale, so the order is arguable rather than
 * arbitrary: the tail is what disappears at narrow widths, and of the three tokens the counts are
 * the ones a user can recover by opening the session, while *when* is the one that orders the list
 * they are scanning. Home has no tags, so the rule's own tail clause never binds here.
 *
 * ## No trailing-slot states, and no lift
 *
 * `TrainingRow` and `ExerciseRow` animate their trailing slot across chevron / check / empty
 * because they have a selection mode. Home has none, so the slot holds a chevron always — the
 * `AnimatedContent`, the `TrailingSlotKind` selector and its test are all absent here rather than
 * copied with two unreachable branches, on the same reasoning `HomeListSurface` uses for its enum.
 * `TrainingRow`'s `lifted` flag is likewise absent: nothing on this list is running, by
 * construction — the query filters `state = 'FINISHED'`, and a running session is Home's banner.
 *
 * ## The divider
 *
 * `--hair-s` has no app token by design and the slot that would take it (`borderSubtle`) is
 * `--hair`, a different value — D3, owed its own palette PR. The row rules with `borderSubtle`
 * until then: a known approximation, not a transcription error, and the same one both siblings
 * ship.
 */
@Composable
internal fun RecentSessionRow(
    item: RecentSessionItem,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = AppDimension.rowHeight)
                .padding(horizontal = AppDimension.screenEdge)
                .testTag("HomeRecentRow_${item.sessionUuid}"),
            horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
            ) {
                Text(
                    modifier = Modifier.testTag("HomeRecentName_${item.sessionUuid}"),
                    text = item.trainingName,
                    style = AppUi.typography.titleMedium,
                    color = AppUi.colors.textPrimary,
                    maxLines = NAME_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.testTag("HomeRecentMeta_${item.sessionUuid}"),
                    text = item.metaLine(),
                    style = AppUi.typography.mono.meta,
                    color = AppUi.colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier.width(SLOT),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    modifier = Modifier.size(SLOT),
                    imageVector = AppIcons.ChevronRight,
                    contentDescription = null,
                    tint = AppUi.colors.textTertiary,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = AppDimension.borderHairline,
                color = AppUi.colors.borderSubtle,
            )
        }
    }
}

/**
 * **Pure and extractable, so the order is asserted rather than reviewed.**
 *
 * The tokens arrive already formatted from `HomeUiMapper` — this function only joins them, and the
 * join is the decision. `RecentMetaLineTest` pins the order and the separator; nothing else can,
 * since a golden of one row shows the string that resulted and cannot distinguish a deliberate
 * order from an accidental one.
 */
internal fun RecentSessionItem.metaLine(): String =
    listOf(finishedAtRelativeLabel, durationLabel, statsLabel)
        .filter { it.isNotEmpty() }
        .joinToString(META_SEPARATOR)

/** The interpunct the drawing joins meta tokens with. */
private const val META_SEPARATOR = " · "

private const val NAME_MAX_LINES = 2

/** The drawn 20px slot, on the icon ladder. Holds the chevron, centred. */
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
