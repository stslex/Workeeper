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
            .background(AppUi.colors.surfaceTier2)
            .border(width = AppDimension.borderHairline, color = borderColor, shape = AppUi.shapes.small)
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
                cursorBrush = androidx.compose.ui.graphics.SolidColor(AppUi.colors.accent),
            )
        }
        suffix?.let {
            Text(
                modifier = Modifier.padding(start = AppDimension.Space.xs),
                text = it,
                style = AppUi.typography.bodySmall.copy(letterSpacing = 0.5.sp),
                color = AppUi.colors.textTertiary,
            )
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppNumberInputPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg)
                .fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            AppNumberInput(value = "120", onValueChange = {}, suffix = "kg", decimals = 1)
            AppNumberInput(value = "8", onValueChange = {}, suffix = "reps", decimals = 0)
            AppNumberInput(value = "abc", onValueChange = {}, suffix = "kg", isError = true)
        }
    }
}
