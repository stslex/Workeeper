package io.github.stslex.workeeper.core.ui.kit.components.text_input_field

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.MenuItem
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.PropertyHolder
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model.TextMode
import kotlinx.collections.immutable.ImmutableSet

@Composable
internal fun <TMenuItem : Any> TextInputField(
    property: PropertyHolder<*>,
    labelRes: Int,
    textMode: TextMode,
    isMenuEnable: Boolean,
    isMenuOpen: Boolean,
    onMenuClick: () -> Unit,
    onMenuClose: () -> Unit,
    onMenuItemClick: (MenuItem<TMenuItem>) -> Unit,
    menuItems: ImmutableSet<MenuItem<TMenuItem>>,
    onClick: () -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboardOptions = remember(textMode.isText) {
        if (textMode.isText) {
            KeyboardOptions.Default
        } else {
            KeyboardOptions.Default.copy(
                keyboardType = KeyboardType.Decimal
            )
        }
    }

    val trailingIcon: (@Composable () -> Unit)? = {
        PropertyTrailingIcon(
            mode = textMode,
            isMenuOpen = isMenuOpen,
            menuItems = menuItems,
            onMenuClick = onMenuClick,
            onMenuClose = onMenuClose,
            onMenuItemClick = onMenuItemClick,
        )
    }
    OutlinedTextField(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        value = property.uiValue,
        onValueChange = onValueChange,
        isError = property.isValid,
        label = {
            Text(stringResource(labelRes))
        },
        keyboardOptions = keyboardOptions,
        trailingIcon = if (isMenuEnable && menuItems.isNotEmpty()) trailingIcon else null,
        colors = if (textMode == TextMode.DATE) {
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
        readOnly = textMode == TextMode.DATE,
        enabled = textMode != TextMode.DATE,
    )
}

@Composable
private fun <TMenuItem : Any> PropertyTrailingIcon(
    mode: TextMode,
    isMenuOpen: Boolean,
    menuItems: ImmutableSet<MenuItem<TMenuItem>>,
    onMenuClick: () -> Unit,
    onMenuClose: () -> Unit,
    onMenuItemClick: (MenuItem<TMenuItem>) -> Unit,
) {
    val focus = LocalFocusManager.current
    IconButton(
        onClick = {
            focus.clearFocus(force = true)
            onMenuClick()
        }
    ) {
        val rotation = animateFloatAsState(
            targetValue = if (isMenuOpen) 180f else 0f
        )
        Icon(
            modifier = Modifier.rotate(rotation.value),
            imageVector = Icons.Outlined.KeyboardArrowDown,
            contentDescription = "Input mode"
        )
    }
    DropdownMenu(
        expanded = isMenuOpen,
        onDismissRequest = {
            onMenuClose()
        }
    ) {
        menuItems.forEach { item ->
            DropdownMenuItem(
                text = { Text(item.text) },
                onClick = { onMenuItemClick(item) }
            )
        }
    }
}
