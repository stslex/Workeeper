package io.github.stslex.workeeper.core.ui.kit.components.input

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The mockup's `.field`: a value and its unit on a recessed panel, with done / logged / record
 * colour and wash variants. See documentation/feature-specs/set-field-column-headers.md.
 */
@Composable
fun AppNumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    suffix: String? = null,
    enabled: Boolean = true,
    isError: Boolean = false,
    isRecord: Boolean = false,
    isDone: Boolean = false,
    isLogged: Boolean = false,
    fieldInset: Dp = AppDimension.Space.md,
    accessibilityLabel: String? = null,
    valueSlotProbe: ((slotWidthPx: Int, resolvedStyle: TextStyle) -> Unit)? = null,
) {
    val keyboardType = if (decimals > 0) KeyboardType.Decimal else KeyboardType.Number
    val valueColor by animateColorAsState(
        targetValue = when {
            isRecord -> AppUi.colors.record.textPrimary
            isDone || isLogged -> AppUi.colors.textPrimary
            else -> AppUi.colors.textTertiary
        },
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "field-value",
    )
    val background by animateColorAsState(
        targetValue = when {
            isRecord -> AppUi.colors.record.background
            isDone -> AppUi.colors.donefill
            else -> AppUi.colors.surfaceTier3
        },
        animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
        label = "field-bg",
    )
    val shape = RoundedCornerShape(AppDimension.Radius.small)
    // [fieldInset] is an explicit consumer choice, never a measured-width threshold, which would
    // become a silent tripwire under any future width change.
    Row(
        modifier = modifier
            .clip(shape)
            .background(background)
            .let { base ->
                if (isError) {
                    base.border(
                        width = AppDimension.borderHairline,
                        color = AppUi.colors.status.error,
                        shape = shape,
                    )
                } else {
                    base
                }
            }
            // GUARD: heightIn, not height — above ~fontScale 1.5 the 26sp line height exceeds
            // 48dp and a hard height would clip vertically.
            .heightIn(min = AppDimension.heightMd)
            .padding(horizontal = fieldInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // BoxWithConstraints, not Box: the slot width feeds the rung choice and [valueSlotProbe]
        // (test-only). GUARD: no `onGloballyPositioned` here — it fires on every scroll frame.
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            val slotWidthPx = constraints.maxWidth
            val valueStyle = resolveValueStyle(value = value, slotWidthPx = slotWidthPx)
            valueSlotProbe?.invoke(slotWidthPx, valueStyle)
            // Aliased before the semantics block: a bare `contentDescription` in the receiver
            // is a self-assign.
            val fieldLabel = accessibilityLabel
            BasicTextField(
                modifier = Modifier.semantics {
                    if (fieldLabel != null) contentDescription = fieldLabel
                },
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = valueStyle.copy(color = valueColor),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(AppUi.colors.accent),
            )
        }
        suffix?.let {
            val unitColor by animateColorAsState(
                // GUARD: `textDim` fails 4.5:1 over the washes; the unit promotes one step there.
                targetValue = if (isRecord || isDone) {
                    AppUi.colors.textSecondary
                } else {
                    AppUi.colors.textDim
                },
                animationSpec = tween(durationMillis = AppUi.motion.base, easing = AppUi.motion.out),
                label = "field-unit",
            )
            Text(
                modifier = Modifier.padding(start = AppDimension.Space.xs),
                text = it,
                // Mono at the 11 rung — the drawn `.unit` family (was a text-family meta).
                style = AppUi.typography.mono.caption,
                color = unitColor,
            )
        }
    }
}

/**
 * The rung the value renders at, measured ahead of layout through the same text stack that draws
 * it. The ladder floor is contrast-pinned at 19sp; see set-field-column-headers.md §4 D5.
 */
@Composable
private fun resolveValueStyle(value: String, slotWidthPx: Int): TextStyle {
    val measurer = rememberTextMeasurer()
    val ladder = listOf(AppUi.typography.dataValue, AppUi.typography.numeric.section)
    if (value.isEmpty()) return ladder.first()
    return ladder.firstOrNull { style ->
        val measured = measurer.measure(
            text = AnnotatedString(value),
            style = style,
            overflow = TextOverflow.Clip,
            softWrap = false,
            maxLines = 1,
        )
        measured.size.width <= slotWidthPx
    } ?: ladder.last()
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun AppNumberInputPreview() {
    AppTheme {
        Column(
            modifier = Modifier
                .background(AppUi.colors.surfaceTier0)
                .padding(AppDimension.Space.lg)
                .fillMaxWidth(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                AppDimension.Space.md,
            ),
        ) {
            AppNumberInput(value = "120", onValueChange = {}, suffix = "kg", decimals = 1)
            AppNumberInput(value = "8", onValueChange = {}, suffix = "reps", decimals = 0)
            AppNumberInput(value = "abc", onValueChange = {}, suffix = "kg", isError = true)
            AppNumberInput(value = "142.5", onValueChange = {}, suffix = "kg", isRecord = true)
            AppNumberInput(value = "100", onValueChange = {}, suffix = "kg", isDone = true)
        }
    }
}
