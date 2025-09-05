package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme

enum class Mode(
    val isText: Boolean = true,
    val isMenuEnable: Boolean = false,
    val isMenuOpen: Boolean = false,
) {

    TITLE(
        isMenuEnable = true,
    ),
    NUMBER(
        isText = false
    ),
    DATE,
    PICK_RESULT(
        isMenuEnable = true,
        isMenuOpen = true
    );
}

@Composable
fun ExercisePropertyTextField(
    text: String,
    label: String,
    mode: Mode,
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
                keyboardType = KeyboardType.Decimal
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
            Text(label)
        },
        keyboardOptions = keyboardOptions,
        colors = if (mode == Mode.DATE) {
            OutlinedTextFieldDefaults.colors(
                disabledTextColor = LocalContentColor.current,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surface
            )
        } else {
            OutlinedTextFieldDefaults.colors()
        },
        singleLine = true,
        readOnly = mode == Mode.DATE,
        enabled = mode != Mode.DATE,
        interactionSource = remember { MutableInteractionSource() },
    )
}

@Composable
@Preview
private fun ExercisePropertyTextFieldPreview() {
    AppTheme {
        ExercisePropertyTextField(
            text = "Sample Text",
            label = "Label",
            onValueChange = {},
            mode = Mode.DATE
        )
    }
}