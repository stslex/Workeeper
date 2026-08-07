// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.feature.home.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import io.github.stslex.workeeper.feature.home.mvi.model.TagIdleRowUi
import io.github.stslex.workeeper.feature.home.mvi.model.WeekDayUi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * The start card — a readout plus an action (home-start-card.md §2), replacing the centred
 * icon-over-title-over-subtitle column whose four defects §0 lists: accent tint on a routine
 * action, a filled Material glyph, centring, and a subtitle describing the mechanism.
 *
 * Shell (HS1): **head** — the mode's label in the `.label` treatment plus a caret, one
 * target; **body** — the mode's readout; **action** — the primary button right of the body,
 * compact, never full-bleed. The mode changes the body only; the head, the button and the
 * card's geometry hold across all four modes and their empty states. Surface `--slab` +
 * `--slabtop` via [liftedSurface].
 */
@Composable
internal fun HomeStartCard(
    mode: StartCardModeUi,
    body: StartCardBodyUi?,
    onStartClick: () -> Unit,
    onOtherTrainingClick: () -> Unit,
    onModeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AppDimension.Radius.medium)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .liftedSurface(shape = shape)
            .clip(shape)
            .testTag("HomeStartCard"),
    ) {
        Column(modifier = Modifier.padding(AppDimension.cardPadding)) {
            StartCardHead(mode = mode, onClick = onModeClick)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppDimension.Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    when (body) {
                        is StartCardBodyUi.Week -> WeekReading(body)
                        is StartCardBodyUi.DaysSince -> DaysSinceReading(body)
                        is StartCardBodyUi.TagIdle -> TagIdleRows(body.rows)
                        is StartCardBodyUi.Forgotten -> ForgottenReading(body)
                        is StartCardBodyUi.Empty -> EmptyReading(body.message)
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

                is StartCardBodyUi.TagIdle -> Text(
                    modifier = Modifier.padding(top = AppDimension.Space.sm),
                    text = body.footnoteLabel,
                    style = AppUi.typography.mono.caption,
                    color = AppUi.colors.textDim,
                )

                is StartCardBodyUi.DaysSince,
                is StartCardBodyUi.Forgotten,
                is StartCardBodyUi.Empty,
                null,
                -> Unit
            }
        }
        // `.setbar` — «Забытая тренировка» only (§3.4): the way out to any other template.
        if (body is StartCardBodyUi.Forgotten) {
            OtherTrainingBar(onClick = onOtherTrainingClick)
        }
    }
}

/**
 * HS4 — the head is the switcher: the mode's label (`.label` treatment, [AppLabel])
 * carrying a caret, ONE target opening the mode sheet — not a `⋮` at the right edge. Hit
 * area holds the platform minimum height regardless of the 11sp type; width stays on the
 * label so the target is the label, not the whole card width. No ripple, like the `.exhead`
 * referent (`ExerciseHeader`): a Material ripple would be a different treatment than drawn.
 *
 * «Забытая тренировка»'s head reads «Дольше всего не делали» (the arc's RU copy) — the one
 * mode whose card label is not the mode's name; the sheet still names it «Забытая
 * тренировка».
 */
@Composable
private fun StartCardHead(
    mode: StartCardModeUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (mode) {
        StartCardModeUi.FORGOTTEN_TRAINING ->
            stringResource(R.string.feature_home_start_mode_forgotten_label)

        StartCardModeUi.WEEK,
        StartCardModeUi.DAYS_SINCE_LAST,
        StartCardModeUi.LAGGING_GROUPS,
        -> startCardModeName(mode)
    }
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(AppDimension.heightMd)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = stringResource(R.string.feature_home_start_mode_switch),
                onClick = onClick,
            )
            .testTag("HomeStartModeHead"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.xs),
    ) {
        AppLabel(text = label)
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
private fun HeroReading(
    countLabel: String,
    unitLabel: String,
    modifier: Modifier = Modifier,
    anchorLabel: String? = null,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = countLabel,
                style = AppUi.typography.numeric.display,
                color = AppUi.colors.textPrimary,
            )
            Text(
                modifier = Modifier.padding(
                    start = AppDimension.Space.sm,
                    bottom = AppDimension.Space.xs,
                ),
                text = unitLabel,
                style = AppUi.typography.mono.meta,
                color = AppUi.colors.textTertiary,
            )
        }
        anchorLabel?.let { anchor ->
            Text(
                modifier = Modifier.padding(top = AppDimension.Space.xs),
                text = anchor,
                style = AppUi.typography.mono.meta,
                color = AppUi.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WeekReading(week: StartCardBodyUi.Week) {
    HeroReading(countLabel = week.sessionsCountLabel, unitLabel = week.sessionsUnitLabel)
}

/** §3.2 — the gap, anchored: the last session's name and date give the number its footing. */
@Composable
private fun DaysSinceReading(daysSince: StartCardBodyUi.DaysSince) {
    HeroReading(
        countLabel = daysSince.daysCountLabel,
        unitLabel = daysSince.daysUnitLabel,
        anchorLabel = daysSince.anchorLabel,
    )
}

/**
 * §3.3 — up to three tags, longest idle first: name, a monochrome bar proportional to days
 * idle (the `.rail` fill treatment stretched to a bar), and the bare count at the right
 * edge — «дней» appears once, in the footnote under the group.
 */
@Composable
private fun TagIdleRows(rows: ImmutableList<TagIdleRowUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm)) {
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.weight(TAG_NAME_WEIGHT),
                    text = row.name,
                    style = AppUi.typography.text.body,
                    color = AppUi.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .weight(TAG_BAR_WEIGHT)
                        .padding(horizontal = AppDimension.Space.sm)
                        .height(WEEK_PILL_HEIGHT)
                        .clip(RoundedCornerShape(AppDimension.Radius.smallest))
                        .background(AppUi.colors.surfaceTier4),
                ) {
                    if (row.barFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(row.barFraction)
                                .height(WEEK_PILL_HEIGHT)
                                .clip(RoundedCornerShape(AppDimension.Radius.smallest))
                                .background(AppUi.colors.accent),
                        )
                    }
                }
                Text(
                    text = row.daysCountLabel,
                    style = AppUi.typography.mono.meta,
                    color = AppUi.colors.textSecondary,
                )
            }
        }
    }
}

/** §3.4 — the template's name is the reading; the meta line carries idleness and makeup. */
@Composable
private fun ForgottenReading(forgotten: StartCardBodyUi.Forgotten) {
    Column {
        Text(
            text = forgotten.trainingName,
            style = AppUi.typography.text.section,
            color = AppUi.colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            modifier = Modifier.padding(top = AppDimension.Space.xs),
            text = forgotten.metaLabel,
            style = AppUi.typography.mono.meta,
            color = AppUi.colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** HD2–HD4 — the mode's own empty copy; the shell and the plain «Начать» stay. */
@Composable
private fun EmptyReading(message: String) {
    Text(
        text = message,
        style = AppUi.typography.text.body,
        color = AppUi.colors.textSecondary,
    )
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

/**
 * `.setbar` (session-v3f L137–141, the same foot the session card wears): a hairline, then
 * one full-width mono uppercase button — press promotes `textTertiary` → `textPrimary`, no
 * ripple, exactly the `SetBarButton` treatment in `LiveExerciseCard`.
 */
@Composable
private fun OtherTrainingBar(onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(
            thickness = AppDimension.Border.small,
            color = AppUi.colors.borderSubtle,
        )
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val color by animateColorAsState(
            targetValue = if (isPressed) AppUi.colors.textPrimary else AppUi.colors.textTertiary,
            animationSpec = tween(durationMillis = AppUi.motion.fast, easing = AppUi.motion.out),
            label = "start-setbar-color",
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = AppDimension.Space.md)
                .testTag("HomeStartOtherTraining"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.feature_home_start_other_training).uppercase(),
                style = AppUi.typography.mono.meta.copy(letterSpacing = SETBAR_TRACKING),
                color = color,
            )
        }
    }
}

/** `.rail{height:9px}` — drawn value, off the dimension scale; named, not rounded. */
private val WEEK_PILL_HEIGHT: Dp = 9.dp

/** `.rail .grp{gap:3px}` — same: the drawn gap, between the xxs and xs steps. */
private val WEEK_PILL_GAP: Dp = 3.dp

/** `.setbar{letter-spacing:.06em}` at the 12.5sp meta rung — `LiveExerciseCard`'s number. */
private val SETBAR_TRACKING = 0.75.sp

/** Name : bar space split for the «Отставшие группы» rows — bar carries the comparison. */
private const val TAG_NAME_WEIGHT = 0.45f
private const val TAG_BAR_WEIGHT = 0.55f

@Preview(name = "Week — Light")
@Composable
private fun HomeStartCardLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        HomeStartCard(
            mode = StartCardModeUi.WEEK,
            body = previewWeek(),
            onStartClick = {},
            onOtherTrainingClick = {},
            onModeClick = {},
        )
    }
}

@Preview(name = "Week — Dark")
@Composable
private fun HomeStartCardDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeStartCard(
            mode = StartCardModeUi.WEEK,
            body = previewWeek(),
            onStartClick = {},
            onOtherTrainingClick = {},
            onModeClick = {},
        )
    }
}

@Preview(name = "Days since — Dark")
@Composable
private fun HomeStartCardDaysSincePreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeStartCard(
            mode = StartCardModeUi.DAYS_SINCE_LAST,
            body = StartCardBodyUi.DaysSince(
                daysCountLabel = "4",
                daysUnitLabel = "дня",
                anchorLabel = "Ноги и плечи · 03/08/26",
            ),
            onStartClick = {},
            onOtherTrainingClick = {},
            onModeClick = {},
        )
    }
}

@Preview(name = "Lagging groups — Dark")
@Composable
private fun HomeStartCardTagIdlePreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeStartCard(
            mode = StartCardModeUi.LAGGING_GROUPS,
            body = StartCardBodyUi.TagIdle(
                rows = persistentListOf(
                    TagIdleRowUi(name = "спина", barFraction = 1f, daysCountLabel = "14"),
                    TagIdleRowUi(name = "грудь", barFraction = 0.5f, daysCountLabel = "7"),
                    TagIdleRowUi(name = "ноги", barFraction = 0.14f, daysCountLabel = "2"),
                ),
                footnoteLabel = "дней с последней тренировки группы",
            ),
            onStartClick = {},
            onOtherTrainingClick = {},
            onModeClick = {},
        )
    }
}

@Preview(name = "Forgotten — Dark")
@Composable
private fun HomeStartCardForgottenPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeStartCard(
            mode = StartCardModeUi.FORGOTTEN_TRAINING,
            body = StartCardBodyUi.Forgotten(
                trainingUuid = "t1",
                trainingName = "Спина и бицепс",
                metaLabel = "21 день · 6 упражнений",
            ),
            onStartClick = {},
            onOtherTrainingClick = {},
            onModeClick = {},
        )
    }
}

@Preview(name = "Empty — Dark")
@Composable
private fun HomeStartCardEmptyPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        HomeStartCard(
            mode = StartCardModeUi.LAGGING_GROUPS,
            body = StartCardBodyUi.Empty(
                message = "Упражнения без тегов — отмечайте группы, и они появятся здесь",
            ),
            onStartClick = {},
            onOtherTrainingClick = {},
            onModeClick = {},
        )
    }
}

private fun previewWeek(): StartCardBodyUi.Week = StartCardBodyUi.Week(
    sessionsCountLabel = "3",
    sessionsUnitLabel = "тренировки",
    days = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").mapIndexed { index, label ->
        WeekDayUi(label = label, isFilled = index == 0 || index == 2 || index == 4)
    }.toImmutableList(),
)
