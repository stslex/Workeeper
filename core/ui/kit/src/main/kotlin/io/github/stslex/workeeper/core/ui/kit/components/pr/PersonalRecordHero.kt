// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.pr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.components.section.AppLabel
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.ThemeMode

/**
 * `.prhero` (extraction §3.3) — the exercise-detail record block. Molten wash + 1dp molten
 * border on a 16dp shape; left column is the `.mdot` + `Рекорд` label over a meta line,
 * right side is the record value at [io.github.stslex.workeeper.core.ui.kit.theme.AppTypography.dataValue]
 * (26sp — the B1 rung), all in the record accent.
 *
 * The value renders `{weight}×{reps}` with the `×` in a **mono SemiBold span**, not in
 * Archivo: `numericFontFamily` takes digits and `: . , - + / %` only (spec §4 C2 — the cut
 * carries no `×` glyph), so the multiplication sign is painted by the family that has it.
 * The mockup draws the whole run in one face; the substitution is invisible at 26sp and is
 * the charset rule's cost. Weightless records render `{reps}` plus the reps unit in
 * `mono.caption` — the same value/unit split `AppNumberInput` uses.
 *
 * The meta line is [io.github.stslex.workeeper.core.ui.kit.theme.AppColors.textTertiary]
 * on the wash, exactly as drawn (`.meta` is not colour-overridden in the mockup) — the
 * pair is declared in the contrast map with this composable as evidence.
 */
@Composable
fun PersonalRecordHero(
    weightLabel: String?,
    repsLabel: String,
    metaLabel: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(AppDimension.Radius.medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color = AppUi.colors.record.background, shape = shape)
            .border(
                width = AppDimension.Border.small,
                color = AppUi.colors.record.border,
                shape = shape,
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(AppDimension.Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.lg),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(MDOT_SIZE)
                        .background(color = AppUi.colors.record.solid, shape = CircleShape),
                )
                Spacer(Modifier.width(AppDimension.Space.sm))
                AppLabel(
                    text = stringResource(R.string.core_ui_kit_record_label),
                    color = AppUi.colors.record.textPrimary,
                )
            }
            Text(
                modifier = Modifier.padding(top = AppDimension.Space.sm),
                text = metaLabel,
                style = AppUi.typography.mono.meta,
                color = AppUi.colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RecordValue(weightLabel = weightLabel, repsLabel = repsLabel)
    }
}

@Composable
private fun RecordValue(
    weightLabel: String?,
    repsLabel: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (weightLabel != null) {
            Text(
                text = buildAnnotatedString {
                    append(weightLabel)
                    withStyle(
                        SpanStyle(
                            fontFamily = AppUi.typography.monoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(MULTIPLY_SIGN)
                    }
                    append(repsLabel)
                },
                style = AppUi.typography.dataValue,
                color = AppUi.colors.record.textPrimary,
                maxLines = 1,
            )
        } else {
            Text(
                text = repsLabel,
                style = AppUi.typography.dataValue,
                color = AppUi.colors.record.textPrimary,
                maxLines = 1,
            )
            Text(
                modifier = Modifier.padding(start = AppDimension.Space.xs),
                text = stringResource(R.string.core_ui_kit_plan_editor_unit_reps),
                style = AppUi.typography.mono.caption,
                color = AppUi.colors.record.textPrimary,
                maxLines = 1,
            )
        }
    }
}

/** `.mdot` — 9px → the 8dp rung, filled `--molten-solid`. */
private val MDOT_SIZE = 8.dp

/** `U+00D7`, painted by the mono span — see the class KDoc for why not Archivo. */
private const val MULTIPLY_SIGN = "×"

@Preview
@Composable
private fun PersonalRecordHeroDarkPreview() {
    AppTheme(themeMode = ThemeMode.DARK) {
        PersonalRecordHero(
            weightLabel = "9",
            repsLabel = "12",
            metaLabel = "12 июля 2026 г.",
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun PersonalRecordHeroWeightlessLightPreview() {
    AppTheme(themeMode = ThemeMode.LIGHT) {
        PersonalRecordHero(
            weightLabel = null,
            repsLabel = "15",
            metaLabel = "12 июля 2026 г.",
        )
    }
}
