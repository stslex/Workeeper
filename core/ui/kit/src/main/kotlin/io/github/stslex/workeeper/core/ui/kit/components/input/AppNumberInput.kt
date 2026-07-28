package io.github.stslex.workeeper.core.ui.kit.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The mockup's `.field` — a value and its unit on a recessed panel.
 *
 * ## [isRecord]
 *
 * `.set.pr .field{background:var(--molten-bg)}` — a record row washes **both** of its fields
 * molten, and that wash is the row's signal. Before this, only the trailing tag changed, which
 * is a chip's worth of molten on a row the mockup paints end to end.
 *
 * ### One measured deviation: the wash STACKS on the field tier
 *
 * The CSS replaces the background outright, so `--molten-bg` (9% / 11% molten) composites over
 * whatever row is behind the field. Here it is painted over the field's own `surfaceTier3`
 * instead, and that is a measurement, not a shortcut. Composited the CSS way, the unit label
 * lands on a different backdrop per row and **fails** on the one the live DONE row provides:
 *
 * | backdrop | `textDim` unit, dark | light |
 * |---|---|---|
 * | wash over `sec` (past-session card, live pending row) | 5.16 | 5.01 |
 * | wash over `raise` (live DONE row) | **3.99** | **4.46** |
 * | wash over `field` — what this component does | 4.84 | 4.82 |
 *
 * Stacking makes the field's contrast a property of the field rather than of the row it happens
 * to sit in, and it clears 4.5:1 in both themes. It also keeps a record field reading as a
 * *field*: the mockup's version discards the recessed panel entirely.
 *
 * ### What is deliberately NOT implemented: `.set.pr .data-l{color:var(--molten)}`
 *
 * The mockup turns the value molten too. On the stacked wash that measures **6.94 dark / 4.14
 * light**, against the 4.5:1 this value owes at `titleLarge` = 19sp regular. Dark passes; light
 * does not, so it is a light-theme question rather than a design error.
 *
 * Note what makes it fail: the mockup draws `.data-l` at 25px Archivo Expanded 700, which is
 * large-scale text at 3:1, where 4.14 passes comfortably. "As drawn" is legal; our under-sized
 * value is what makes it illegal.
 *
 * Two resolutions, both outside this component and both somebody else's call:
 *  1. bring the value to the drawn type (`numeric.title`, 26sp bold) — a visual change to every
 *     numeric input in the app, and the typography work rather than this;
 *  2. darken light `molten` to #B43B0B, the smallest step that clears 4.5:1 here — redmean 17.6
 *     from #BE3E0C, i.e. twice the 8.9 this palette records as indistinguishable, and it would
 *     move the PR accent on every screen.
 *
 * Until one is chosen the value stays `textPrimary`, which is why `ContrastContract` declares
 * `textPrimary`/`textDim` on `record.background` rather than the molten pair. The row still
 * reads molten: both fields are washed and the tag is molten.
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
) {
    val keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number
    val textStyle = AppUi.typography.titleLarge.copy(
        color = AppUi.colors.textPrimary,
        fontFeatureSettings = "tnum",
    )
    val borderColor = when {
        isError -> AppUi.colors.status.error
        else -> AppUi.colors.borderSubtle
    }
    Row(
        modifier = modifier
            .clip(AppUi.shapes.small)
            // The mockup's `.field{background:var(--field)}` — `surfaceTier3`, whose own KDoc
            // already names it "recessed panels: input fills". This painted `surfaceTier2`,
            // which is the *floating* tier (dialogs, dropdowns, and now the lifted surface);
            // an input is the opposite of floating, and on a lifted light-theme card the two
            // whites cancelled and the field disappeared.
            //
            .background(AppUi.colors.surfaceTier3)
            // `.set.pr .field{background:var(--molten-bg)}` — stacked rather than substituted,
            // see the KDoc. Always applied so the modifier graph is stable across the flag.
            .background(if (isRecord) AppUi.colors.record.background else Color.Transparent)
            .border(
                width = AppDimension.borderHairline,
                color = borderColor,
                shape = AppUi.shapes.small,
            )
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
                textStyle = textStyle,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(AppUi.colors.accent),
            )
        }
        suffix?.let {
            Text(
                modifier = Modifier.padding(start = AppDimension.Space.xs),
                text = it,
                style = AppUi.typography.bodySmall.copy(letterSpacing = 0.5.sp),
                // The mockup's `.unit`, which it paints in `--dim`. `textDim` is that role,
                // aliased onto `meta` — see AppColors.textDim for the measurement that forced
                // the merge. Reading the role rather than `textTertiary` keeps the unit
                // distinguishable from the value it annotates if the tier is ever reinstated.
                color = AppUi.colors.textDim,
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
        }
    }
}
