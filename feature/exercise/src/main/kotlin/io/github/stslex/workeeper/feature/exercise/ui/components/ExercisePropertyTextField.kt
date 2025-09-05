package io.github.stslex.workeeper.feature.exercise.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.ExerciseUiModel
import io.github.stslex.workeeper.feature.exercise.ui.mvi.model.TextMode
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@Composable
fun ExercisePropertyTextField(
    text: String,
    label: String,
    mode: TextMode,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onClick: () -> Unit = {},
    onMenuClick: () -> Unit = {},
    onMenuClose: () -> Unit = {},
    onMenuItemClick: (ExerciseUiModel) -> Unit = {},
    menuItems: ImmutableSet<ExerciseUiModel> = persistentSetOf(),
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

    val trailingIcon: (@Composable () -> Unit)? = {
        PropertyTrailingIcon(
            mode = mode,
            menuItems = menuItems,
            onMenuClick = onMenuClick,
            onMenuClose = onMenuClose,
            onMenuItemClick = onMenuItemClick
        )
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
        trailingIcon = if (mode.isMenuEnable && menuItems.isNotEmpty()) trailingIcon else null,
        colors = if (mode == TextMode.DATE) {
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
        readOnly = mode == TextMode.DATE,
        enabled = mode != TextMode.DATE,
    )
}

@Composable
private fun PropertyTrailingIcon(
    mode: TextMode,
    menuItems: ImmutableSet<ExerciseUiModel>,
    onMenuClick: () -> Unit,
    onMenuClose: () -> Unit,
    onMenuItemClick: (ExerciseUiModel) -> Unit,
) {
    val focus = LocalFocusManager.current
    IconButton(
        onClick = {
            focus.clearFocus(force = true)
            onMenuClick()
        }
    ) {
        val rotation = animateFloatAsState(
            targetValue = if (mode.isMenuOpen) 180f else 0f
        )
        Icon(
            modifier = Modifier.rotate(rotation.value),
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = "Input mode"
        )
    }
    DropdownMenu(
        expanded = mode.isMenuOpen,
        onDismissRequest = {
            onMenuClose()
        }
    ) {
        menuItems.forEach { item ->
            DropdownMenuItem(
                text = { Text(item.name) },
                onClick = {
                    onMenuItemClick(item)
                }
            )
        }
    }
}

@Composable
@Preview
private fun ExercisePropertyTextFieldPreview() {
    AppTheme {
        ExercisePropertyTextField(
            text = "Sample Text",
            label = "Label",
            onValueChange = {},
            mode = TextMode.DATE,
        )
    }
}