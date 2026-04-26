package io.github.stslex.workeeper.core.ui.kit.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        modifier = modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = AppUi.typography.bodyMedium,
        label = label?.let { { Text(text = it, style = AppUi.typography.bodySmall) } },
        placeholder = placeholder?.let { { Text(text = it, style = AppUi.typography.bodyMedium) } },
        leadingIcon = leadingIcon?.let { { Icon(imageVector = it, contentDescription = null) } },
        trailingIcon = trailingIcon?.let { { Icon(imageVector = it, contentDescription = null) } },
        shape = AppUi.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppUi.colors.accent,
            unfocusedBorderColor = AppUi.colors.borderDefault,
            disabledBorderColor = AppUi.colors.borderSubtle,
            errorBorderColor = AppUi.colors.status.error,
            focusedContainerColor = AppUi.colors.surfaceTier0,
            unfocusedContainerColor = AppUi.colors.surfaceTier1,
            disabledContainerColor = AppUi.colors.surfaceTier1,
            errorContainerColor = AppUi.colors.surfaceTier0,
            focusedTextColor = AppUi.colors.textPrimary,
            unfocusedTextColor = AppUi.colors.textPrimary,
            disabledTextColor = AppUi.colors.textTertiary,
            errorTextColor = AppUi.colors.textPrimary,
            focusedLabelColor = AppUi.colors.accent,
            unfocusedLabelColor = AppUi.colors.textTertiary,
            disabledLabelColor = AppUi.colors.textDisabled,
            errorLabelColor = AppUi.colors.status.error,
            cursorColor = AppUi.colors.accent,
        ),
    )
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppTextFieldPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            AppTextField(value = "", onValueChange = {}, label = "Name", placeholder = "Bench press")
            AppTextField(value = "Search", onValueChange = {}, leadingIcon = Icons.Default.Search)
            AppTextField(value = "Bad", onValueChange = {}, label = "With error", isError = true)
            AppTextField(value = "Disabled", onValueChange = {}, enabled = false, label = "Disabled")
        }
    }
}
