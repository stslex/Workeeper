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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import io.github.stslex.workeeper.core.ui.kit.R
import io.github.stslex.workeeper.core.ui.kit.theme.AppDimension
import io.github.stslex.workeeper.core.ui.kit.theme.AppTheme
import io.github.stslex.workeeper.core.ui.kit.theme.AppUi

/**
 * The set-row column header — the unit lives here, not in the field
 * (documentation/feature-specs/set-field-column-headers.md §2).
 *
 * ## Geometry mirrors the row, from one source
 *
 * `[index gutter] [weight header] [reps header] [trailing gutter]`. The index gutter takes
 * [indexColumnWidth] — the SAME resolved value the container passes to every row
 * ([SetRowGeometry], §4 D3) — and the flex split reads [SetRowGeometry.WEIGHT_COLUMN_FLEX],
 * so header and rows cannot drift apart. [trailingWidth] is the consumer's trailing cluster
 * (chip slot + gap + checkmark / drag handle), computed by the feature from its own
 * components' widths, because the two rows deliberately differ there.
 *
 * ## Two-tone, one Text
 *
 * The NAME is `textSecondary`, the UNIT in parentheses a `SpanStyle` of `textDim` — never
 * dimmer than that: a caption-rung label owes 4.5:1 (§2). One `AnnotatedString` in one
 * `Text` with `TextOverflow.Ellipsis` is what makes the unit truncate before the name;
 * splitting the two into separate `Text`s breaks that order (§4 D2).
 *
 * Casing is applied here, locale-aware, and kept out of strings.xml. Style is
 * `mono.caption`, never the numeric family — Archivo has zero Cyrillic, and
 * `NumericFontFamilyOnLocalizedTextRule` guards that boundary.
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
                    name = stringResource(R.string.core_ui_kit_set_header_weight),
                    unit = stringResource(R.string.core_ui_kit_plan_editor_unit_kg),
                    unitColor = AppUi.colors.textDim,
                ),
                modifier = Modifier.weight(SetRowGeometry.WEIGHT_COLUMN_FLEX),
            )
            HeaderCell(
                label = buildSetColumnHeaderLabel(
                    name = stringResource(R.string.core_ui_kit_set_header_reps),
                    unit = null,
                    unitColor = AppUi.colors.textDim,
                ),
                modifier = Modifier.weight(1f),
            )
        } else {
            HeaderCell(
                label = buildSetColumnHeaderLabel(
                    name = stringResource(R.string.core_ui_kit_set_header_reps_full),
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
 * One column's label, inset to sit over the field's VALUE rather than its edge — by the
 * same [SetRowGeometry.compactFieldInset] the rows pass to their fields, so label and
 * value cannot drift apart (§7a).
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
 * The header's single `Text`. Internal so the ellipsis order can be proven on the REAL
 * text parameters rather than on a lookalike; [onTextLayout] is a test oracle only —
 * never drive state from it (§4 D5).
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
 * `ВЕС (КГ)` from `вес` + `кг`: locale-aware uppercase at the edge, parentheses added here
 * — formatting is the component's, not the translation's. The unit span is the string's
 * TAIL, which is what hands the truncation order to `TextOverflow` (§2).
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
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
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
