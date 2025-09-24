package io.github.stslex.workeeper.feature.single_training.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.single_training.R
import io.github.stslex.workeeper.feature.single_training.ui.model.TextMode

@Composable
internal fun TrainingPropertyTextField(
    text: String,
    @StringRes labelRes: Int,
    mode: TextMode,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onClick: () -> Unit = {},
    onValueChange: (String) -> Unit,
) {
    val keyboardOptions = remember(mode.isText) {
        if (mode.isText) {
            KeyboardOptions.Default
        } else {
            KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Decimal,
            )
        }
    }

    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        value = text,
        onValueChange = onValueChange,
        isError = isError,
        label = {
            Text(stringResource(labelRes))
        },
        keyboardOptions = keyboardOptions,
        colors = if (mode == TextMode.DATE) {
            OutlinedTextFieldDefaults.colors(
                disabledTextColor = LocalContentColor.current,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        singleLine = true,
        readOnly = mode == TextMode.DATE,
        enabled = mode != TextMode.DATE,
    )
}

@Composable
@Preview
private fun TrainingPropertyTextFieldPreview() {
    AppTheme {
        Box(
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        ) {
            TrainingPropertyTextField(
                text = "Sample Text",
                labelRes = R.string.feature_single_training_field_name_label,
                onValueChange = {},
                mode = TextMode.DATE,
            )
        }
    }
}
