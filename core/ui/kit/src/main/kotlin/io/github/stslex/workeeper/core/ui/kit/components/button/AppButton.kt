package io.github.stslex.workeeper.core.ui.kit.components.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

enum class AppButtonSize { LARGE, MEDIUM, SMALL }

object AppButton {

    @Composable
    fun Primary(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        size: AppButtonSize = AppButtonSize.LARGE,
        leadingIcon: ImageVector? = null,
        trailingIcon: ImageVector? = null,
    ) {
        BaseFilledButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            size = size,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppUi.colors.accent,
                contentColor = AppUi.colors.onAccent,
                disabledContainerColor = AppUi.colors.surfaceTier4,
                disabledContentColor = AppUi.colors.textDisabled,
            ),
        )
    }

    @Composable
    fun Secondary(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        size: AppButtonSize = AppButtonSize.LARGE,
        leadingIcon: ImageVector? = null,
        trailingIcon: ImageVector? = null,
    ) {
        BaseFilledButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            size = size,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppUi.colors.surfaceTier1,
                contentColor = AppUi.colors.textPrimary,
                disabledContainerColor = AppUi.colors.surfaceTier4,
                disabledContentColor = AppUi.colors.textDisabled,
            ),
        )
    }

    @Composable
    fun Tertiary(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        size: AppButtonSize = AppButtonSize.LARGE,
        leadingIcon: ImageVector? = null,
        trailingIcon: ImageVector? = null,
    ) {
        val (height, horizontalPad, textStyle) = sizeMetrics(size)
        TextButton(
            modifier = modifier.height(height),
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = horizontalPad),
            colors = ButtonDefaults.textButtonColors(
                contentColor = AppUi.colors.accent,
                disabledContentColor = AppUi.colors.textDisabled,
            ),
            shape = AppUi.shapes.medium,
        ) {
            ButtonContent(
                text = text,
                textStyle = textStyle,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
            )
        }
    }

    @Composable
    fun Destructive(
        text: String,
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        size: AppButtonSize = AppButtonSize.LARGE,
        leadingIcon: ImageVector? = null,
        trailingIcon: ImageVector? = null,
    ) {
        BaseFilledButton(
            text = text,
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            size = size,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppUi.colors.setType.failureBackground,
                contentColor = AppUi.colors.setType.failureForeground,
                disabledContainerColor = AppUi.colors.surfaceTier4,
                disabledContentColor = AppUi.colors.textDisabled,
            ),
        )
    }
}

@Composable
internal fun BaseFilledButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    size: AppButtonSize,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
    colors: ButtonColors,
) {
    val (height, horizontalPad, textStyle) = sizeMetrics(size)
    Button(
        modifier = modifier.height(height),
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = horizontalPad),
        colors = colors,
        shape = AppUi.shapes.medium,
    ) {
        ButtonContent(
            text = text,
            textStyle = textStyle,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
        )
    }
}

@Composable
internal fun ButtonContent(
    text: String,
    textStyle: TextStyle,
    leadingIcon: ImageVector?,
    trailingIcon: ImageVector?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        leadingIcon?.let {
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                imageVector = it,
                contentDescription = null,
            )
        }
        Text(text = text, style = textStyle)
        trailingIcon?.let {
            Icon(
                modifier = Modifier.size(AppDimension.iconSm),
                imageVector = it,
                contentDescription = null,
            )
        }
    }
}

internal data class ButtonMetrics(
    val height: Dp,
    val horizontalPadding: Dp,
    val textStyle: TextStyle,
)

@Composable
internal fun sizeMetrics(size: AppButtonSize): ButtonMetrics = when (size) {
    AppButtonSize.LARGE -> ButtonMetrics(AppDimension.heightMd, 16.dp, AppUi.typography.labelLarge)
    AppButtonSize.MEDIUM -> ButtonMetrics(AppDimension.heightSm, 14.dp, AppUi.typography.labelMedium)
    AppButtonSize.SMALL -> ButtonMetrics(AppDimension.heightXs, 12.dp, AppUi.typography.labelMedium)
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppButtonPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
        ) {
            AppButton.Primary(text = "Primary", onClick = {}, leadingIcon = Icons.Default.Add)
            AppButton.Secondary(text = "Secondary", onClick = {})
            AppButton.Tertiary(text = "Tertiary", onClick = {})
            AppButton.Destructive(text = "Delete", onClick = {})
            AppButton.Primary(text = "Disabled", onClick = {}, enabled = false)
            AppButton.Primary(text = "Medium", onClick = {}, size = AppButtonSize.MEDIUM)
            AppButton.Primary(text = "Small", onClick = {}, size = AppButtonSize.SMALL)
        }
    }
}
