// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.components.button.AppButton
import io.github.stslex.workeeper.core.ui.kit.components.section.AppLabel
import io.github.stslex.workeeper.core.ui.kit.components.surface.liftedSurface
import io.github.stslex.workeeper.core.ui.kit.icons.AppIcons
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode
import io.github.stslex.workeeper.core.ui.start_mode.model.StartCardModeUi
import io.github.stslex.workeeper.core.ui.start_mode.startCardModeName
import io.github.stslex.workeeper.feature.home.R
import io.github.stslex.workeeper.feature.home.mvi.model.StartCardBodyUi
import io.github.stslex.workeeper.feature.home.mvi.model.WeekDayUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The start card — a readout plus an action (home-start-card.md §2), replacing the centred
 * icon-over-title-over-subtitle column whose four defects §0 lists: accent tint on a routine
 * action, a filled Material glyph, centring, and a subtitle describing the mechanism.
 *
 * Shell (HS1): **head** — the mode's name in the `.label` treatment plus a caret, one target;
 * **body** — the mode's readout; **action** — the primary button right of the body, compact,
 * never full-bleed. Surface `--slab` + `--slabtop` via [liftedSurface] — the card is the
 * band's standing summary, not another `--sec` row.
 *
 * The caret is inert in this commit by design: the mode sheet is a later commit of the same
 * arc, and an inert caret is honest where a stub sheet would not be. The head already takes
 * the platform-minimum hit area (HS4) so wiring the tap later changes no geometry.
 */
@Composable
internal fun HomeStartCard(
    body: StartCardBodyUi?,
    onStartClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppDimension.Radius.medium)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liftedSurface(shape = shape)
            .clip(shape)
            .padding(AppDimension.cardPadding)
            .testTag("HomeStartCard"),
    ) {
        StartCardHead()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimension.Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                when (body) {
                    is StartCardBodyUi.Week -> WeekReading(body)
                    null -> Unit
                }
            }
            AppButton.Primary(
                text = stringResource(R.string.feature_home_start_action),
                onClick = onStartClick,
                modifier = Modifier
                    .padding(start = AppDimension.Space.md)
                    .testTag("HomeStartButton"),
            )
        }
        when (body) {
            is StartCardBodyUi.Week -> WeekRail(
                days = body.days,
                modifier = Modifier.padding(top = AppDimension.Space.md),
            )
            null -> Unit
        }
    }
}

/**
 * HS4 — the head is the switcher: the mode's name (`.label` treatment, [AppLabel]) carrying
 * a caret, one target, not a `⋮` at the right edge. Hit area holds the platform minimum
 * height regardless of the 11sp type; width stays on the label so the target is the label,
 * not the whole card width.
 */
@Composable
private fun StartCardHead(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(AppDimension.heightMd)
            .testTag("HomeStartModeHead"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        AppLabel(text = startCardModeName(StartCardModeUi.WEEK))
        Icon(
            modifier = Modifier.size(AppDimension.Icon.small),
            imageVector = AppIcons.ChevronDown,
            contentDescription = null,
            tint = AppUi.colors.textDim,
        )
    }
}

/** The `.data-hero` pair: count in the Archivo display slot, unit beside it in mono meta. */
@Composable
private fun WeekReading(week: StartCardBodyUi.Week) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = week.sessionsCountLabel,
            style = AppUi.typography.numeric.display,
            color = AppUi.colors.textPrimary,
        )
        Text(
            modifier = Modifier.padding(
                start = AppDimension.Space.sm,
                bottom = AppDimension.Space.xs,
            ),
            text = week.sessionsUnitLabel,
            style = AppUi.typography.mono.meta,
            color = AppUi.colors.textTertiary,
        )
    }
}

/**
 * Seven pills, one per weekday, filled where a session finished, labels beneath — the
 * `.rail` form (`pass2d.html` L87–90: 9px track, 4px radius, 3px gaps; track `--raise`,
 * fill `--max`) with the unit changed from set to day, exactly as §3.1 describes it.
 */
@Composable
private fun WeekRail(
    days: ImmutableList<WeekDayUi>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WEEK_PILL_GAP),
        ) {
            days.forEach { day ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(WEEK_PILL_HEIGHT)
                        .clip(RoundedCornerShape(AppDimension.Radius.smallest))
                        .background(
                            if (day.isFilled) {
                                AppUi.colors.accent
                            } else {
                                AppUi.colors.surfaceTier4
                            },
                        ),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppDimension.Space.xs),
            horizontalArrangement = Arrangement.spacedBy(WEEK_PILL_GAP),
        ) {
            days.forEach { day ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.label,
                        style = AppUi.typography.mono.caption,
                        color = AppUi.colors.textDim,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** `.rail{height:9px}` — drawn value, off the dimension scale; named, not rounded. */
private val WEEK_PILL_HEIGHT: Dp = 9.dp

/** `.rail .grp{gap:3px}` — same: the drawn gap, between the xxs and xs steps. */
private val WEEK_PILL_GAP: Dp = 3.dp

@Preview(name = "Light")
@Composable
private fun HomeStartCardLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        HomeStartCard(body = previewWeek(), onStartClick = {})
    }
}

@Preview(name = "Dark")
@Composable
private fun HomeStartCardDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeStartCard(body = previewWeek(), onStartClick = {})
    }
}

private fun previewWeek(): StartCardBodyUi.Week = StartCardBodyUi.Week(
    sessionsCountLabel = "3",
    sessionsUnitLabel = "тренировки",
    days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").mapIndexed { index, label ->
        WeekDayUi(label = label, isFilled = index == 0 || index == 2 || index == 4)
    }.toImmutableList(),
)
