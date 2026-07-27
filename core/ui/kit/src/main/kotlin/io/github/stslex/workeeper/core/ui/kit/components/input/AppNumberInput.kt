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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppNumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    suffix: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
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
            .background(AppUi.colors.surfaceTier3)
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
        }
    }
}
