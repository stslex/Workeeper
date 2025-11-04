package io.github.stslex.workeeper.core.ui.kit.components.text_input_field.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.stslex.workeeper.core.ui.kit.components.text_input_field.TextInputField
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

@Composable
fun <TMenuItem : Any> TitleTextInputField(
    property: PropertyHolder.StringProperty,
    @StringRes labelRes: Int,
    onMenuClick: () -> Unit,
    onMenuClose: () -> Unit,
    onMenuItemClick: (MenuItem<TMenuItem>) -> Unit,
    menuItems: ImmutableSet<MenuItem<TMenuItem>>,
    isMenuOpen: Boolean,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextInputField(
        modifier = modifier,
        property = property,
        labelRes = labelRes,
        textMode = TextMode.TEXT,
        isMenuEnable = true,
        isMenuOpen = isMenuOpen,
        onMenuClick = onMenuClick,
        onMenuClose = onMenuClose,
        menuItems = menuItems,
        onValueChange = onValueChange,
        onMenuItemClick = onMenuItemClick,
        onClick = {},
    )
}

@Composable
fun BodyTextInputField(
    property: PropertyHolder.StringProperty,
    labelRes: Int,
    onValueChange: (String) -> Unit,
) {
    TextInputField<Any>(
        property = property,
        labelRes = labelRes,
        textMode = TextMode.TEXT,
        isMenuEnable = false,
        isMenuOpen = false,
        onMenuClick = { },
        onMenuClose = {},
        menuItems = persistentSetOf(),
        onClick = {},
        onMenuItemClick = {},
        onValueChange = onValueChange,
    )
}

@Composable
fun BodyNumberInputField(
    property: PropertyHolder.IntProperty,
    labelRes: Int,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    TextInputField<Any>(
        modifier = modifier,
        property = property,
        labelRes = labelRes,
        textMode = TextMode.DECIMAL,
        isMenuEnable = false,
        isMenuOpen = false,
        onMenuClick = { },
        onMenuClose = {},
        menuItems = persistentSetOf(),
        onClick = {},
        onMenuItemClick = {},
        onValueChange = onValueChange,
    )
}

@Composable
fun BodyFloatInputField(
    property: PropertyHolder.DoubleProperty,
    labelRes: Int,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    TextInputField<Any>(
        modifier = modifier,
        property = property,
        labelRes = labelRes,
        textMode = TextMode.DOUBLE,
        isMenuEnable = false,
        isMenuOpen = false,
        onMenuClick = { },
        onMenuClose = {},
        menuItems = persistentSetOf(),
        onClick = {},
        onMenuItemClick = {},
        onValueChange = onValueChange,
    )
}

@Composable
fun DateInputField(
    property: PropertyHolder.DateProperty,
    labelRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    TextInputField<Any>(
        modifier = modifier,
        property = property,
        labelRes = labelRes,
        textMode = TextMode.DATE,
        isMenuEnable = false,
        isMenuOpen = false,
        onMenuClick = { },
        onMenuClose = {},
        menuItems = persistentSetOf(),
        onClick = onClick,
        onMenuItemClick = {},
        onValueChange = { },
    )
}
