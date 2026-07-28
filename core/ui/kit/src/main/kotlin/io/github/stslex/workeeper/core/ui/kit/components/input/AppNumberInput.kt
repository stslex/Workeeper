package io.github.stslex.workeeper.core.ui.kit.components.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The mockup's `.field` — a value and its unit on a recessed panel (extraction §1.6).
 *
 * ## The value is `dataValue` — blocker B1, landed
 *
 * `.data-l` is 25px Archivo `wdth 115 / wght 700` → the 26 rung, named
 * `AppTypography.dataValue`. At 26sp bold the WCAG threshold is **3:1**, which retires the
 * measurement that used to sit here: at the previous 19sp/600 the value owed 4.5:1 and molten
 * could not pay it in light (4.14). At this rung the mockup's colour ramp ships as drawn:
 *
 *  - resting: `textTertiary`. The mockup paints `--idle`, whose light value (#7C858F) sits at
 *    3.11:1 on the field — inside quantisation noise of the 3:1 line — and whose dark value IS
 *    `meta`'s hex. Same correction §2.4 applied to light `meta`: the mockup was drawn, not
 *    measured; the pending value uses the meta value in both themes. The brightness principle
 *    survives — pending is dimmer than done.
 *  - done ([isDone]): `textPrimary` — `.set.done .data-l{color:var(--max)}`.
 *  - record ([isRecord]): `record.textPrimary` — `.set.pr .data-l{color:var(--molten)}`,
 *    legal at TITLE. Record wins over done, as in the stylesheet (`.pr` is declared after
 *    `.done`).
 *
 * ## The washes replace the fill — blocker B7, landed
 *
 * `.set.done .field{background:var(--donefill)}` and `.set.pr .field{background:var(--molten-bg)}`
 * **replace** the field tier; the translucent wash composites over the card behind the row
 * (`sec`, or `slab` when the card is lifted). An earlier revision stacked the record wash on
 * `surfaceTier3` because the done ROW washed `surfaceTier4` behind it and the CSS-faithful
 * composite failed on that backdrop (3.99 dark). B7 removed the row wash — the backdrop that
 * failed no longer exists, and over the card tiers every pair clears its threshold
 * (`ContrastContract`, the `donefill` and `record.background` rows `over` tier1/tier2).
 *
 * No default border: the mockup's field is recessed by tier alone. The error outline remains.
 */
@Composable
fun AppNumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    suffix: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    isRecord: Boolean = false,
    isDone: Boolean = false,
) {
    val keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number
    val valueColor by animateColorAsState(
        targetValue = when {
            isRecord -> AppUi.colors.record.textPrimary
            isDone -> AppUi.colors.textPrimary
            else -> AppUi.colors.textTertiary
        },
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "field-value",
    )
    val background by animateColorAsState(
        targetValue = when {
            isRecord -> AppUi.colors.record.background
            isDone -> AppUi.colors.donefill
            else -> AppUi.colors.surfaceTier3
        },
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "field-bg",
    )
    val shape = RoundedCornerShape(AppDimension.Radius.small)
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .let { base ->
                if (isError) {
                    base.border(
                        width = AppDimension.borderHairline,
                        color = AppUi.colors.status.error,
                        shape = shape,
                    )
                } else {
                    base
                }
            }
            .height(AppDimension.heightMd)
            .padding(horizontal = AppDimension.Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = AppUi.typography.dataValue.copy(color = valueColor),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(AppUi.colors.accent),
            )
        }
        suffix?.let {
            val unitColor by animateColorAsState(
                // The mockup's `.unit` is `--dim` in every state. Measured, that fails on the
                // washes: over a LIFTED card (slab) in dark, `textDim` lands at 4.40 (record
                // wash) / 4.45 (donefill) against the 4.5 an 11sp label owes — the same
                // failure B7 exists to close, one backdrop later. On washed fields the unit
                // promotes one step to `textSecondary`; a completed row brightening as a
                // whole is §1's principle, not a contradiction of it.
                targetValue = if (isRecord || isDone) {
                    AppUi.colors.textSecondary
                } else {
                    AppUi.colors.textDim
                },
                animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
                label = "field-unit",
            )
            Text(
                modifier = Modifier.padding(start = AppDimension.Space.xs),
                text = it,
                // Mono at the 11 rung — the drawn `.unit` family (was a text-family meta).
                style = AppUi.typography.mono.caption,
                color = unitColor,
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppNumberInputPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg)
                .fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                AppDimension.Space.md,
            ),
        ) {
            AppNumberInput(value = "120", onValueChange = {}, suffix = "kg", decimals = 1)
            AppNumberInput(value = "8", onValueChange = {}, suffix = "reps", decimals = 0)
            AppNumberInput(value = "abc", onValueChange = {}, suffix = "kg", isError = true)
            AppNumberInput(value = "142.5", onValueChange = {}, suffix = "kg", isRecord = true)
            AppNumberInput(value = "100", onValueChange = {}, suffix = "kg", isDone = true)
        }
    }
}
