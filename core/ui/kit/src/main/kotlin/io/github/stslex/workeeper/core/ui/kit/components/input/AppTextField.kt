// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The v3 `.tf` typing field: outlined, never filled. GUARD: no `label` parameter (the label is a
 * sibling `AppFieldLabel`) and the outline stays `borderDefault`, never `textDisabled`.
 */
@Suppress("LongParameterList")
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    accessibilityLabel: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(AppDimension.Radius.small)
    val outline = when {
        !enabled -> AppUi.colors.borderSubtle
        isError -> AppUi.colors.status.error
        isFocused -> AppUi.colors.accent
        else -> AppUi.colors.borderDefault
    }
    val outlineWidth = if (isError && enabled) FIELD_ERROR_BORDER else AppDimension.Border.small
    val contentColor = if (enabled) AppUi.colors.textPrimary else AppUi.colors.textTertiary
    val minHeight = if (singleLine) AppDimension.heightMd else MULTILINE_MIN_HEIGHT
    // M3 sets error semantics from `isError` internally; a `BasicTextField` owes it explicitly.
    val errorMessage = stringResource(R.string.core_ui_kit_field_error)
    BasicTextField(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                if (isError) error(errorMessage)
                // `.flabel` is a SIBLING node, so it names the field on screen but not to
                // TalkBack. Every caller drawing an `AppFieldLabel` passes the same string here.
                if (accessibilityLabel != null) contentDescription = accessibilityLabel
            },
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        textStyle = AppUi.typography.text.body.copy(color = contentColor),
        cursorBrush = SolidColor(AppUi.colors.accent),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = outlineWidth, color = outline, shape = shape)
                    .defaultMinSize(minHeight = minHeight)
                    .padding(AppDimension.Space.md),
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
            ) {
                leadingIcon?.let {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconSm),
                        imageVector = it,
                        contentDescription = null,
                        tint = AppUi.colors.textTertiary,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            style = AppUi.typography.text.body,
                            // `.tf.ghosty` is `--dim`, declared at BODY in `ContrastContract`.
                            color = AppUi.colors.textDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                trailingIcon?.let {
                    Icon(
                        modifier = Modifier.size(AppDimension.iconSm),
                        imageVector = it,
                        contentDescription = null,
                        tint = AppUi.colors.textTertiary,
                    )
                }
            }
        },
    )
}

/** `.tf.err{border-width:1.5px}` — off the `Border` ladder: width is half the error signal. */
private val FIELD_ERROR_BORDER: Dp = 1.5.dp

/** `.tf.multi{min-height:96px}` — `heightMd × 2`, so the rung is the one below it doubled. */
private val MULTILINE_MIN_HEIGHT: Dp = AppDimension.heightMd * 2

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppTextFieldPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg),
            verticalArrangement = Arrangement.spacedBy(AppDimension.Space.md),
        ) {
            AppTextField(value = "Жим лёжа", onValueChange = {})
            AppTextField(value = "", onValueChange = {}, placeholder = "Название упражнения")
            AppTextField(value = "", onValueChange = {}, leadingIcon = Icons.Default.Search, placeholder = "Поиск")
            AppTextField(value = "Румынская тяга", onValueChange = {}, isError = true)
            AppTextField(value = "", onValueChange = {}, placeholder = "Заметка", singleLine = false)
            AppTextField(value = "Disabled", onValueChange = {}, enabled = false)
        }
    }
}
