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
 * The drawn skeleton — [AppListRow], where the 88dp floor, the clamp, the gutter and the rule are
 * documented. Measured on this module's own goldens at **88.0 / 88.0 / 88.0 / 88.0 dp**, the fourth
 * of which is the two-line clamp.
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
        // Always a chevron: Home has no selection mode, so the crossfading slot and its selector
        // are absent here rather than copied with two unreachable branches.
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
