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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The v3 `.tf` — the typing field (extraction §7.2). **Outlined, and not filled.**
 *
 * ## Why it is not `.field`
 *
 * `AppNumberInput` is the mockup's `.field`: the session's tap-to-enter value box, with no caret,
 * no label and no error. It is not the referent for a thing you type into, so this component is
 * its own drawing rather than a variant of that one. The two differ in every property that
 * matters — fill, outline, type family, what happens when the value is wrong.
 *
 * ## Geometry, derived rather than transcribed (§0.2)
 *
 * - `background:none` → **no fill**. The page tier shows through; on a sheet, the sheet does.
 *   Before v3 this was an `OutlinedTextField` with `unfocusedContainerColor = surfaceTier1`, which
 *   is outlined *and* filled — two treatments doing one job.
 * - radius 12px → **8dp** (`Radius.small`). The 12 rung does not exist (extraction E7) and every
 *   site rounds it down with its reason at the site, as `AppIconButton` already does.
 * - `min-height:52px` → **48dp** (`heightMd`), the same way `.field`'s own 52 resolves.
 * - `.tf.multi{min-height:96px}` → **96dp**, which is `heightMd × 2` exactly. Multiline is the
 *   same box, taller — [singleLine] moves one height and nothing else, so no call site sets its
 *   own. Two of them used to set `.height(120.dp)` by hand, which is what a component that does
 *   not own its own multiline size costs.
 * - padding `14px 12px` → **12dp** on both axes, and a multiline field aligns its text to the
 *   **top** rather than centring it.
 *
 * ## The outline, and the ONE deliberate divergence from the drawing
 *
 * The drawing paints the outline **`--idle`** and this paints **`borderDefault`**, on purpose.
 * `--idle` maps to [io.github.stslex.workeeper.core.ui.kit.theme.AppColors.textDisabled], which
 * `ContrastContract` declares **EXEMPT** — WCAG carves disabled controls out of the non-text
 * requirement — so painting an *enabled* field's outline with it is wrong on the semantics and
 * would drag every `textDisabled` pair into the gate to make it right. `borderDefault` is
 * `*_CONTROL_OUTLINE`, the slot the app created when `--hair-s` forced the same move (B19's
 * non-mapping). Both clear the 3:1 an enabled outline owes, measured with the gate's arithmetic:
 * `--idle` **6.40 dark / 3.49 light** on `--base`, `borderDefault` **4.09 / 3.60**. The rejected
 * `--hair-s` candidate measures **1.51 / 1.35**. Full table and reasoning: §26 "The editors' text
 * field", extraction §7.2. **Do not "fix" this to `textDisabled`.**
 *
 * Four outline states, and only two of them are drawn:
 *
 * | State | Colour | Width | Drawn? |
 * |---|---|---|---|
 * | resting, enabled | `borderDefault` | 1dp | yes — `.tf` |
 * | error | `status.error` | **1.5dp** | yes — `.tf.err` |
 * | focused | `accent` | 1dp | **no** |
 * | disabled | `borderSubtle` | 1dp | **no** |
 *
 * The error width is the second half of the signal: the same contour is changing colour, and
 * weight is what makes that legible without adding an element. It is why this is a
 * [BasicTextField] and not an `OutlinedTextField` — M3 exposes border thickness only through
 * `OutlinedTextFieldDefaults.Container`, and only as focused/unfocused, so an *unfocused* error
 * would have drawn at 1dp.
 *
 * Error beats focus: an error is the more important thing the outline can be saying, and the two
 * cannot be shown at once by one contour.
 *
 * The focused and disabled states are **not drawn anywhere** and are kept rather than invented
 * away. Dropping the focus tint would remove a real affordance (WCAG 2.4.7) to satisfy a drawing
 * that is silent about it, and §0.1 gives the drawing what it draws, not what it omits.
 *
 * ## The label is above the field, and this component does not draw one
 *
 * The `label` parameter is gone. M3's floating label is drawn nowhere in either mockup; the form
 * puts a `.flabel` above the box, which is an ordinary `Text` the caller already owns. The
 * parameter had no production reader — the only call passing it was `AppTagPicker`, which has no
 * production call sites at all (B37).
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
    BasicTextField(
        modifier = modifier.fillMaxWidth(),
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
                            // `.tf.ghosty` is `--dim`. Declared at BODY in `ContrastContract`
                            // alongside the CAPTION row it already had — same 4.5:1 either way,
                            // but the slot names what is painted.
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

/** `.tf.err{border-width:1.5px}` — off the `Border` ladder on purpose; see the KDoc. */
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
