// SPDX-License-Identifier: GPL-3.0-only
package io.github.stslex.workeeper.core.ui.kit.components.setrow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.resources.Res
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_plan_editor_unit_kg
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_header_reps
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_header_reps_full
import io.github.stslex.workeeper.core.ui.kit.resources.core_ui_kit_set_header_weight
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi
import io.github.stslex.workeeper.core.ui.kit.theme.PREVIEW_UI_MODE_NIGHT_YES
import org.jetbrains.compose.resources.stringResource

/**
 * The set-row column header; geometry mirrors the rows from [SetRowGeometry], and name plus unit
 * are one `Text` so the unit truncates first. See the set-field-column-headers spec.
 */
@Composable
fun SetColumnHeader(
    isWeighted: Boolean,
    indexColumnWidth: Dp,
    trailingWidth: Dp,
    modifier: Modifier = Modifier,
    indexGutterProbe: ((widthPx: Int) -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppDimension.Space.xs)
            .padding(top = AppDimension.Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimension.Space.sm),
    ) {
        // [indexGutterProbe] is test-only — production never passes it.
        Spacer(
            modifier = Modifier
                .width(indexColumnWidth)
                .onSizeChanged { size -> indexGutterProbe?.invoke(size.width) },
        )
        if (isWeighted) {
            HeaderCell(
                label = buildSetColumnHeaderLabel(
                    name = stringResource(Res.string.core_ui_kit_set_header_weight),
                    unit = stringResource(Res.string.core_ui_kit_plan_editor_unit_kg),
                    unitColor = AppUi.colors.textDim,
                ),
                modifier = Modifier.weight(SetRowGeometry.WEIGHT_COLUMN_FLEX),
            )
            HeaderCell(
                label = buildSetColumnHeaderLabel(
                    name = stringResource(Res.string.core_ui_kit_set_header_reps),
                    unit = null,
                    unitColor = AppUi.colors.textDim,
                ),
                modifier = Modifier.weight(1f),
            )
        } else {
            HeaderCell(
                label = buildSetColumnHeaderLabel(
                    name = stringResource(Res.string.core_ui_kit_set_header_reps_full),
                    unit = null,
                    unitColor = AppUi.colors.textDim,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.width(trailingWidth))
    }
}

/**
 * One column's label, inset by the same [SetRowGeometry.compactFieldInset] the rows pass to
 * their fields, so label and value cannot drift apart.
 */
@Composable
private fun HeaderCell(
    label: AnnotatedString,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        SetColumnHeaderLabel(
            label = label,
            modifier = Modifier.padding(start = SetRowGeometry.compactFieldInset),
        )
    }
}

/**
 * The header's single `Text`, internal so the ellipsis order can be proven on the real
 * parameters. GUARD: [onTextLayout] is a test oracle only — never drive state from it.
 */
@Composable
internal fun SetColumnHeaderLabel(
    label: AnnotatedString,
    modifier: Modifier = Modifier,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    Text(
        modifier = modifier,
        text = label,
        style = AppUi.typography.mono.caption,
        color = AppUi.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result -> onTextLayout?.invoke(result) },
    )
}

/**
 * `ВЕС (КГ)` from `вес` + `кг`, uppercased with the locale-invariant overload. The unit span
 * is the string's tail, which is what hands the truncation order to `TextOverflow`.
 */
internal fun buildSetColumnHeaderLabel(
    name: String,
    unit: String?,
    unitColor: Color,
): AnnotatedString = buildAnnotatedString {
    append(name.uppercase())
    if (unit != null) {
        append(' ')
        withStyle(SpanStyle(color = unitColor)) {
            append("(${unit.uppercase()})")
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    showBackground = true,
    uiMode = PREVIEW_UI_MODE_NIGHT_YES,
)
@Composable
private fun SetColumnHeaderPreview() {
    AppTheme {
        Column {
            SetColumnHeader(
                isWeighted = true,
                indexColumnWidth = SetRowGeometry.indexMinWidth,
                trailingWidth = AppDimension.Space.xxl,
            )
            SetColumnHeader(
                isWeighted = false,
                indexColumnWidth = SetRowGeometry.indexMinWidth,
                trailingWidth = AppDimension.Space.xxl,
            )
        }
    }
}
